package vn.giapha.kinship.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.config.RedisConfig;
import vn.giapha.kinship.domain.KinshipRelationAnalyzer;
import vn.giapha.kinship.domain.KinshipResolution;
import vn.giapha.kinship.domain.KinshipResolver;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.LcaPort;
import vn.giapha.kinship.domain.LcaResult;
import vn.giapha.kinship.domain.PersonLookupPort;
import vn.giapha.kinship.domain.PersonView;
import vn.giapha.kinship.domain.RelationContext;
import vn.giapha.kinship.domain.RelationFacts;
import vn.giapha.kinship.domain.RelationFactsFactory;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.kinship.domain.RuleSetRepository;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.PersonId;

/**
 * Use case <b>tra danh xưng giữa hai người</b> (FR-1.3, TDD §8.2) — nơi các mảnh được ghép lại.
 *
 * <p>Trình tự, và lý do của từng bước:</p>
 * <ol>
 *   <li>Nạp bản chiếu của ego <b>trước</b>, vì chi/ngành của ego mới quyết định bộ luật hiệu lực,
 *       mà bộ luật lại là một phần của khoá cache. Không có bước này thì không thể tra cache trước
 *       khi chạm đồ thị — tức là mất gần hết giá trị của cache.</li>
 *   <li>Hợp nhất bộ luật {@code DEFAULT -> REGION -> CLAN -> BRANCH} qua {@link RuleSetRepository}.</li>
 *   <li>Tra Redis theo khoá cặp đã chuẩn hoá (xem {@link KinshipCacheKeys}).</li>
 *   <li>Trượt cache thì mới chạm đồ thị: {@link KinshipRelationAnalyzer} gom LCA
 *       ({@code LcaPort.findLca}), cạnh trực tiếp ({@code directLinks}) và quan hệ dâu/rể qua
 *       {@code spousesOf}, rồi nạp <b>một lô</b> {@code PersonLookupPort.byIds} cho toàn bộ hai
 *       đường đi lên LCA. Gom một lần như vậy là cách duy nhất tránh N+1 khi cây sâu chục đời, và
 *       cũng là điều kiện để {@link KinshipResolver} giữ được tính thuần (không hỏi CSDL giữa
 *       chừng, tính được cả danh xưng chiều ngược trên cùng bộ bằng chứng).</li>
 *   <li>{@link KinshipResolver#resolve} so khớp luật; dựng đường quan hệ; ghi cache.</li>
 *   <li><b>Che tên theo phân tầng riêng tư ở bước cuối</b>, sau cache — xem
 *       {@link KinshipDisplayPolicy}.</li>
 * </ol>
 *
 * <p>Service này <b>không</b> chứa một danh xưng nào và không có nhánh {@code if} nào theo vùng
 * miền: toàn bộ tri thức đó nằm ở dữ liệu {@code kinship_rule} (FR-1.3a).</p>
 */
@Service
public class ResolveKinshipTitleService {

    private static final Logger log = LoggerFactory.getLogger(ResolveKinshipTitleService.class);

    private final PersonLookupPort personLookup;
    private final RuleSetRepository ruleSets;
    private final KinshipRelationAnalyzer analyzer;
    private final KinshipResolver resolver;
    private final RelationFactsFactory factsFactory;
    private final KinshipDisplayPolicy displayPolicy;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public ResolveKinshipTitleService(LcaPort lcaPort, PersonLookupPort personLookup,
            RuleSetRepository ruleSets, KinshipDisplayPolicy displayPolicy,
            CacheManager cacheManager, ObjectMapper objectMapper) {
        this.personLookup = personLookup;
        this.ruleSets = ruleSets;
        this.displayPolicy = displayPolicy;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        // Domain thuần POJO: dựng bằng new, không cho Spring quản lý, để bộ test danh xưng chạy
        // được không cần Spring context (yêu cầu đầu ra của W3).
        this.factsFactory = new RelationFactsFactory();
        this.resolver = new KinshipResolver(factsFactory);
        this.analyzer = new KinshipRelationAnalyzer(lcaPort, personLookup);
    }

    /** Danh xưng ego gọi alter, kèm đường quan hệ, dùng bộ luật hiệu lực của chi ego. */
    @Transactional(readOnly = true)
    public KinshipQueryResult resolve(PersonId from, PersonId to) {
        return resolve(from, to, null, true);
    }

    /**
     * @param forcedRuleSetId ép dùng một bộ luật cụ thể — màn hình thử nghiệm luật của Hội đồng.
     *                        Kết quả khi ép luật <b>không</b> được ghi vào cache dùng chung
     * @param includePath     có dựng đường quan hệ hay không
     */
    @Transactional(readOnly = true)
    public KinshipQueryResult resolve(PersonId from, PersonId to, UUID forcedRuleSetId,
            boolean includePath) {
        PersonView ego = personLookup.byId(from)
                .orElseThrow(() -> NotFoundException.of("Person", from));

        KinshipRuleSet rules = ruleSetFor(ego, forcedRuleSetId);
        boolean cacheable = forcedRuleSetId == null;
        String cacheKey = KinshipCacheKeys.pairKey(from, to, rules);

        if (cacheable) {
            Optional<KinshipQueryResult> hit = readCache(cacheKey);
            if (hit.isPresent()) {
                KinshipQueryResult cached = hit.get().withCached(true);
                return displayPolicy.apply(cached, peopleOnPath(cached));
            }
        }

        RelationContext context = analyzer.analyze(from, to)
                .orElseThrow(() -> NotFoundException.of("Person", to));
        RelationFacts facts = factsFactory.from(context);
        KinshipResolution resolution = resolver.resolve(context, rules);

        List<RelationPathStep> path =
                includePath ? RelationPathBuilder.build(context, facts) : List.of();
        KinshipQueryResult result = toResult(from, to, resolution, facts, context, rules, path);

        if (cacheable) {
            writeCache(cacheKey, result);
        }
        return displayPolicy.apply(result, peopleOnContext(context));
    }

    private KinshipRuleSet ruleSetFor(PersonView ego, UUID forcedRuleSetId) {
        if (forcedRuleSetId == null) {
            return ruleSets.resolveFor(ego.branchId());
        }
        return ruleSets.byId(forcedRuleSetId)
                .orElseThrow(() -> NotFoundException.of("KinshipRuleSet", forcedRuleSetId));
    }

    private KinshipQueryResult toResult(PersonId from, PersonId to, KinshipResolution resolution,
            RelationFacts facts, RelationContext context, KinshipRuleSet rules,
            List<RelationPathStep> path) {
        List<RuleScope> chain = new ArrayList<>();
        for (KinshipRuleSet set : rules.chain()) {
            chain.add(set.scope());
        }
        return new KinshipQueryResult(
                resolution.status(),
                from.value(),
                to.value(),
                resolution.title(),
                resolution.titleShort(),
                resolution.egoSelfTerm(),
                resolution.reciprocalTitle(),
                resolution.relationCode() == null ? null : resolution.relationCode().value(),
                resolution.ruleId(),
                resolution.ruleSetId() != null ? resolution.ruleSetId() : rules.id(),
                resolution.ruleSetScope(),
                chain,
                toFacts(facts),
                toLca(context),
                path,
                false);
    }

    private KinshipQueryResult.Facts toFacts(RelationFacts facts) {
        if (facts == null) {
            return null;
        }
        return new KinshipQueryResult.Facts(facts.genDelta(), facts.collateralDegree(), facts.side(),
                facts.targetGender(), facts.isElder(), facts.linkSide(), facts.linkGender(),
                facts.inLawDirection(), facts.throughMarriage(), facts.throughAdoption());
    }

    private KinshipQueryResult.Lca toLca(RelationContext context) {
        LcaResult lca = context.lca();
        if (lca == null) {
            return null;
        }
        PersonView view = context.view(lca.lca());
        return new KinshipQueryResult.Lca(lca.lca().value(), view.displayName(), view.generation(),
                lca.distEgo(), lca.distAlter());
    }

    private Map<UUID, PersonView> peopleOnContext(RelationContext context) {
        Map<UUID, PersonView> people = new HashMap<>();
        context.people().forEach((id, view) -> people.put(id.value(), view));
        return people;
    }

    /**
     * Trên đường về từ cache không còn {@link RelationContext}, nên nạp lại đúng những người nằm
     * trên đường đi — một lượt {@code byIds} duy nhất, để {@link KinshipDisplayPolicy} biết ai đã
     * bị xoá mềm. Không tái sử dụng tên đã cache mà bỏ qua bước này: cache dùng chung cho mọi người
     * gọi, nên quyết định che phải tính lại theo danh tính người gọi hiện tại.
     */
    private Map<UUID, PersonView> peopleOnPath(KinshipQueryResult result) {
        List<PersonId> ids = new ArrayList<>();
        for (RelationPathStep step : result.path()) {
            ids.add(PersonId.of(step.personId()));
        }
        if (result.lca() != null) {
            ids.add(PersonId.of(result.lca().personId()));
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PersonView> people = new HashMap<>();
        personLookup.byIds(ids).forEach((id, view) -> people.put(id.value(), view));
        return people;
    }

    // ---------------------------------------------------------------------------------------
    // Cache - Redis khong phai nguon chan ly; Redis chet thi tinh lai, khong duoc do loi ra API.
    // Gia tri luu duoi dang chuoi JSON tu tuan tu hoa, khong phu thuoc vao viec serializer cua
    // cache manager co ghi kem thong tin kieu hay khong (record la lop final -> default typing cua
    // GenericJackson2JsonRedisSerializer bo qua, doc lai se ra LinkedHashMap va vang ClassCast).
    // ---------------------------------------------------------------------------------------

    private Optional<KinshipQueryResult> readCache(String key) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_KINSHIP);
        if (cache == null) {
            return Optional.empty();
        }
        try {
            String json = cache.get(key, String.class);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, KinshipQueryResult.class));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Bo qua cache danh xung (khoa {}): {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(String key, KinshipQueryResult result) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_KINSHIP);
        if (cache == null) {
            return;
        }
        try {
            cache.put(key, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Khong ghi duoc cache danh xung (khoa {}): {}", key, ex.getMessage());
        }
    }
}

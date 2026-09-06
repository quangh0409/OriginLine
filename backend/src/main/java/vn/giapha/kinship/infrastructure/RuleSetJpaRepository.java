package vn.giapha.kinship.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.kinship.domain.KinshipRule;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.Region;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.kinship.domain.RuleSetRepository;

/**
 * Hiện thực {@link RuleSetRepository} trên PostgreSQL.
 *
 * <p>Nhiệm vụ của lớp này là <b>chọn</b> bộ luật lá và <b>nối sẵn chuỗi cha</b>. Phép hợp nhất
 * {@code DEFAULT → REGION → CLAN → BRANCH} thật sự nằm ở {@link KinshipRuleSet#effectiveRules()},
 * tức là ở domain — nhờ vậy quy tắc ghi đè test được không cần CSDL, và lớp này chỉ còn là chuyện
 * đọc bảng.</p>
 *
 * <h2>Hai chuỗi khác nhau, đừng nhầm</h2>
 * <ul>
 *   <li><b>Chuỗi chi</b> ({@code branch.path}, ltree) dùng để <i>tìm</i> bộ luật nào áp cho chi
 *       này: leo từ chính nó lên tới gốc dòng họ.</li>
 *   <li><b>Chuỗi kế thừa</b> ({@code kinship_rule_set.parent_id}) dùng để <i>hợp nhất</i> luật sau
 *       khi đã chọn được bộ lá.</li>
 * </ul>
 * Hai chuỗi này độc lập: một bộ luật của chi có thể kế thừa thẳng từ DEFAULT mà không đi qua CLAN.
 * V3 ràng buộc REGION/CLAN/BRANCH đều phải có {@code parent_id}, nên chuỗi kế thừa luôn dẫn về
 * DEFAULT.
 *
 * <h2>Không import gì của context {@code genealogy}</h2>
 * Bảng {@code branch} được đọc thẳng bằng {@link JdbcTemplate} chỉ-đọc. Dùng
 * {@code BranchJpaEntity} của {@code genealogy} sẽ tiện hơn vài dòng nhưng phá ranh giới context
 * và {@code ModularityTests} sẽ đỏ.
 */
@Repository
public class RuleSetJpaRepository implements RuleSetRepository {

    private static final Logger log = LoggerFactory.getLogger(RuleSetJpaRepository.class);

    /** Chặn chuỗi kế thừa hỏng. CSDL đã có trigger chống chu trình; đây là lớp phòng thứ hai. */
    private static final int MAX_INHERITANCE_DEPTH = 32;

    /**
     * Chi và toàn bộ tổ tiên của nó theo ltree, <b>sâu nhất trước</b>. Thứ tự này chính là thứ tự
     * ưu tiên khi chọn bộ luật: luật của chính chi mình thắng luật của chi cha.
     */
    private static final String BRANCH_LINEAGE_SQL = """
            SELECT anc.id, anc.region, nlevel(anc.path) AS lvl
            FROM branch self
            JOIN branch anc ON anc.path @> self.path
            WHERE self.id = ? AND NOT anc.is_deleted
            ORDER BY nlevel(anc.path) DESC
            """;

    private final KinshipRuleSetJpaRepository ruleSets;
    private final KinshipRuleJpaRepository rules;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public RuleSetJpaRepository(KinshipRuleSetJpaRepository ruleSets, KinshipRuleJpaRepository rules,
            JdbcTemplate jdbc, EntityManager entityManager) {
        this.ruleSets = Objects.requireNonNull(ruleSets);
        this.rules = Objects.requireNonNull(rules);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    @Transactional(readOnly = true)
    public KinshipRuleSet resolveFor(UUID branchId) {
        if (branchId == null) {
            return defaultRuleSet();
        }
        List<BranchRow> lineage = branchLineage(branchId);
        if (lineage.isEmpty()) {
            log.debug("Chi {} khong ton tai hoac da xoa — dung bo luat mac dinh", branchId);
            return defaultRuleSet();
        }

        return pickByBranch(RuleScope.BRANCH, lineage)
                .or(() -> pickByBranch(RuleScope.CLAN, lineage))
                .or(() -> pickByRegion(lineage))
                .map(this::hydrate)
                .orElseGet(this::defaultRuleSet);
    }

    @Override
    @Transactional(readOnly = true)
    public KinshipRuleSet defaultRuleSet() {
        KinshipRuleSetEntity entity = ruleSets.findFirstByScopeAndActiveIsTrue(RuleScope.DEFAULT.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Khong tim thay bo luat danh xung DEFAULT dang hoat dong. "
                                + "Migration R__seed_kinship_rules_default.sql chua chay?"));
        return hydrate(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KinshipRuleSet> byId(UUID ruleSetId) {
        return ruleSets.findById(ruleSetId).map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KinshipRuleSet> findAll(RuleScope scope, Region region, UUID branchId) {
        List<KinshipRuleSetEntity> found =
                ruleSets.search(name(scope), name(region), branchId);
        if (found.isEmpty()) {
            return List.of();
        }
        // Liệt kê quản trị trả bộ luật THÔ: không nối chuỗi cha, không hợp nhất. Màn hình
        // "bộ luật đang hiệu lực" là việc của EffectiveRuleSetService, không phải của danh sách này.
        Map<UUID, List<KinshipRule>> byOwner = rulesOf(found.stream().map(KinshipRuleSetEntity::getId).toList());
        return found.stream()
                .map(entity -> toDomain(entity, null, byOwner.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    /**
     * Ghi đè trọn vẹn một bộ luật.
     *
     * <p><b>Vì sao phải ép tăng version bằng tay:</b> luật nằm ở bảng {@code kinship_rule}, không
     * phải ở {@code kinship_rule_set}. Sửa mỗi danh sách luật thì dòng rule set không "bẩn", nên
     * Hibernate <b>không</b> tăng {@code @Version} — và hai người trong Hội đồng cùng sửa một bộ
     * luật sẽ cùng ghi thành công, người sau lặng lẽ đè mất người trước.
     * {@link LockModeType#OPTIMISTIC_FORCE_INCREMENT} là thứ đóng lại lỗ đó.</p>
     */
    @Override
    @Transactional
    public KinshipRuleSet replace(RuleSetCommand command) {
        Objects.requireNonNull(command, "command khong duoc null");
        KinshipRuleSetEntity entity = command.id() == null
                ? createEntity(command)
                : loadForUpdate(command);

        applyFields(entity, command);
        KinshipRuleSetEntity saved = ruleSets.saveAndFlush(entity);

        // Xoá trước rồi mới chèn, và PHẢI flush ở giữa: unique index
        // ux_kinship_rule_code(rule_set_id, relation_code) sẽ nổ nếu Hibernate xếp lệnh INSERT
        // trước DELETE — mà mặc định nó xếp đúng như vậy.
        rules.deleteByRuleSetId(saved.getId());
        rules.flush();

        List<KinshipRule> incoming = command.rules() == null ? List.of() : command.rules();
        if (!incoming.isEmpty()) {
            rules.saveAll(incoming.stream()
                    .map(rule -> KinshipRuleMapper.toEntity(rule, saved.getId()))
                    .toList());
            rules.flush();
        }
        log.info("Ghi de bo luat danh xung {} ({}): {} luat", saved.getCode(), saved.getId(),
                incoming.size());
        return hydrate(saved);
    }

    // ------------------------------------------------------------------ chọn bộ luật

    private Optional<KinshipRuleSetEntity> pickByBranch(RuleScope scope, List<BranchRow> lineage) {
        List<UUID> ids = lineage.stream().map(BranchRow::id).toList();
        List<KinshipRuleSetEntity> candidates = ruleSets.findByScopeAndBranchIds(scope.name(), ids);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // Chi càng sâu càng cụ thể càng thắng. lineage đã sắp sâu-trước nên vị trí trong danh sách
        // chính là thứ hạng ưu tiên.
        Map<UUID, Integer> rank = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            rank.put(ids.get(i), i);
        }
        return candidates.stream()
                .min(Comparator.comparingInt(entity -> rank.getOrDefault(entity.getBranchId(), Integer.MAX_VALUE)));
    }

    /**
     * Bộ luật theo vùng miền. Vùng lấy từ chi gần nhất có khai {@code region} — chi con thường để
     * trống và thừa hưởng vùng của chi cha.
     */
    private Optional<KinshipRuleSetEntity> pickByRegion(List<BranchRow> lineage) {
        return lineage.stream()
                .map(BranchRow::region)
                .filter(Objects::nonNull)
                .findFirst()
                .flatMap(region -> ruleSets.findFirstByScopeAndRegionAndActiveIsTrue(
                        RuleScope.REGION.name(), region));
    }

    private List<BranchRow> branchLineage(UUID branchId) {
        return jdbc.query(BRANCH_LINEAGE_SQL,
                (rs, rowNum) -> new BranchRow(rs.getObject("id", UUID.class), rs.getString("region")),
                branchId);
    }

    /** Một đời trong chuỗi chi. {@code region} có thể null — chi con thường thừa hưởng của cha. */
    private record BranchRow(UUID id, String region) {
    }

    // ------------------------------------------------------------------ dựng domain

    /**
     * Nối chuỗi kế thừa theo {@code parent_id} và nạp luật của cả chuỗi bằng <b>một</b> truy vấn.
     * Nạp từng bộ một sẽ là 4 lượt đi CSDL cho mỗi lần tra danh xưng.
     */
    private KinshipRuleSet hydrate(KinshipRuleSetEntity leaf) {
        List<KinshipRuleSetEntity> chain = inheritanceChain(leaf);
        Map<UUID, List<KinshipRule>> byOwner =
                rulesOf(chain.stream().map(KinshipRuleSetEntity::getId).toList());

        KinshipRuleSet built = null;
        for (KinshipRuleSetEntity entity : chain) {
            built = toDomain(entity, built, byOwner.getOrDefault(entity.getId(), List.of()));
        }
        return built;
    }

    /** Chuỗi kế thừa theo thứ tự áp dụng: gốc (DEFAULT) trước, bộ lá sau cùng. */
    private List<KinshipRuleSetEntity> inheritanceChain(KinshipRuleSetEntity leaf) {
        List<KinshipRuleSetEntity> reversed = new ArrayList<>();
        KinshipRuleSetEntity current = leaf;
        int hops = 0;
        while (current != null) {
            if (++hops > MAX_INHERITANCE_DEPTH) {
                throw new IllegalStateException("Chuoi ke thua kinship_rule_set qua sau hoac co chu trinh tai "
                        + current.getCode());
            }
            reversed.add(current);
            UUID parentId = current.getParentId();
            current = parentId == null ? null : ruleSets.findById(parentId).orElse(null);
        }
        List<KinshipRuleSetEntity> chain = new ArrayList<>(reversed);
        java.util.Collections.reverse(chain);
        return chain;
    }

    private Map<UUID, List<KinshipRule>> rulesOf(List<UUID> ruleSetIds) {
        if (ruleSetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<KinshipRule>> byOwner = new LinkedHashMap<>();
        for (KinshipRuleEntity entity : rules.findByRuleSetIdInOrderByPriorityAsc(ruleSetIds)) {
            byOwner.computeIfAbsent(entity.getRuleSetId(), key -> new ArrayList<>())
                    .add(KinshipRuleMapper.toDomain(entity));
        }
        return byOwner;
    }

    private static KinshipRuleSet toDomain(KinshipRuleSetEntity entity, KinshipRuleSet parent,
            List<KinshipRule> ownRules) {
        return new KinshipRuleSet(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                RuleScope.valueOf(entity.getScope()),
                region(entity.getRegion()),
                entity.getBranchId(),
                parent,
                ownRules,
                entity.isActive(),
                entity.getVersion());
    }

    // ------------------------------------------------------------------ ghi

    private KinshipRuleSetEntity createEntity(RuleSetCommand command) {
        return new KinshipRuleSetEntity(UUID.randomUUID(), command.code(), command.name(),
                name(command.scope()), command.parentRuleSetId(), name(command.region()),
                command.branchId(), command.description(), true);
    }

    private KinshipRuleSetEntity loadForUpdate(RuleSetCommand command) {
        KinshipRuleSetEntity entity = ruleSets.findById(command.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Khong tim thay bo luat danh xung " + command.id()));
        if (command.expectedVersion() != null && command.expectedVersion() != entity.getVersion()) {
            throw new IllegalStateException("Bo luat " + entity.getCode() + " da bi nguoi khac sua"
                    + " (phien ban mong doi " + command.expectedVersion()
                    + ", hien tai " + entity.getVersion() + ")");
        }
        // Xem javadoc của replace(): thay đổi chỉ nằm ở bảng luật con nên phải ép tăng version.
        entityManager.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return entity;
    }

    private static void applyFields(KinshipRuleSetEntity entity, RuleSetCommand command) {
        entity.setCode(command.code());
        entity.setName(command.name());
        entity.setScope(name(command.scope()));
        entity.setRegion(name(command.region()));
        entity.setBranchId(command.branchId());
        entity.setParentId(command.parentRuleSetId());
        entity.setDescription(command.description());
    }

    private static Region region(String value) {
        return value == null || value.isBlank() ? null : Region.valueOf(value);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}

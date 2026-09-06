package vn.giapha.kinship.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import vn.giapha.kinship.api.dto.EffectiveKinshipRuleSetDto;
import vn.giapha.kinship.api.dto.KinshipResultDto;
import vn.giapha.kinship.api.dto.KinshipRuleSetDto;
import vn.giapha.kinship.application.EffectiveRuleSetService;
import vn.giapha.kinship.application.ResolveKinshipTitleService;
import vn.giapha.kinship.application.RuleAdminService;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.Region;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.shared.vo.PersonId;

/**
 * Resolver GraphQL cho phần danh xưng — ba truy vấn của {@code contracts/schema.graphqls}:
 * {@code kinship}, {@code kinshipRuleSets}, {@code effectiveKinshipRuleSet}.
 *
 * <p>Trả về đúng những DTO mà REST trả, cố ý: hai lối vào cùng một use case thì không được có hai
 * hình dạng kết quả, nếu không giao diện sẽ có hai đường code phải bảo trì song song và một trong
 * hai sẽ trôi.</p>
 *
 * <p><b>Bộ quy tắc chỉ ĐỌC qua GraphQL.</b> Sửa luật đi bằng {@code PUT /api/v1/kinship-rules} —
 * quyền hẹp và có {@code audit_log}; mở thêm một cửa mutation nữa chỉ làm khó cho việc kiểm
 * soát.</p>
 *
 * <hr>
 *
 * <p><b>Ghi chú bàn giao — schema runtime chưa có các type này.</b>
 * {@code src/main/resources/graphql/schema.graphqls} hiện mới chỉ có trường {@code ping} (agent
 * khác đang giữ file đó, W3 không được sửa). Khi phần schema danh xưng của
 * {@code contracts/schema.graphqls} được chép vào, cần lưu ý hai chỗ mà hợp đồng GraphQL tham chiếu
 * sang type của context {@code genealogy}:</p>
 * <ul>
 *   <li>{@code KinshipResult.from}/{@code to} và {@code KinshipPathStep.person} khai kiểu
 *       {@code Person}, {@code KinshipRuleSet.branch} khai kiểu {@code Branch}. Context này
 *       <b>không</b> import lớp nào của {@code genealogy}, nên hai trường {@code from}/{@code to}
 *       dưới đây chỉ trả một tham chiếu mang {@code id}; phần còn lại của {@code Person} phải do
 *       resolver/batch loader của {@code genealogy} nạp từ {@code id} đó. Nếu tới lúc ráp mà cách
 *       này không khớp, phương án sạch hơn là để hợp đồng dùng {@code fromPersonId}/{@code toPersonId}
 *       như bản REST và để client tự hỏi {@code person(id:)}.</li>
 *   <li>Trường {@code Person.kinshipTo(personId:)} thuộc type {@code Person} nên resolver của nó
 *       phải nằm ở {@code genealogy/api}; nó chỉ cần gọi
 *       {@link ResolveKinshipTitleService#resolve} — service này là điểm vào công khai của context
 *       {@code kinship}, đúng cách hai context nói chuyện với nhau.</li>
 * </ul>
 */
@Controller
public class KinshipGraphQlController {

    private final ResolveKinshipTitleService resolveKinshipTitle;
    private final RuleAdminService ruleAdmin;
    private final EffectiveRuleSetService effectiveRuleSets;

    public KinshipGraphQlController(ResolveKinshipTitleService resolveKinshipTitle,
            RuleAdminService ruleAdmin, EffectiveRuleSetService effectiveRuleSets) {
        this.resolveKinshipTitle = resolveKinshipTitle;
        this.ruleAdmin = ruleAdmin;
        this.effectiveRuleSets = effectiveRuleSets;
    }

    /** Danh xưng giữa hai người — tương đương {@code GET /api/v1/kinship}. */
    @QueryMapping
    public KinshipResultDto kinship(@Argument UUID from, @Argument UUID to) {
        return KinshipApiMapper.toDto(
                resolveKinshipTitle.resolve(PersonId.of(from), PersonId.of(to)));
    }

    /** Các bộ luật thô, có lọc. Chỉ đọc. */
    @QueryMapping
    public List<KinshipRuleSetDto> kinshipRuleSets(@Argument RuleScope scope,
            @Argument Region region, @Argument UUID branchId) {
        List<KinshipRuleSetDto> result = new ArrayList<>();
        for (KinshipRuleSet set : ruleAdmin.list(scope, region, branchId)) {
            result.add(KinshipApiMapper.toDto(set));
        }
        return result;
    }

    /** Bộ luật đã hợp nhất đang hiệu lực cho một chi — chính là bộ mà {@code kinship} dùng. */
    @QueryMapping
    public EffectiveKinshipRuleSetDto effectiveKinshipRuleSet(@Argument UUID branchId) {
        return KinshipApiMapper.toDto(effectiveRuleSets.forBranch(branchId));
    }

    @SchemaMapping(typeName = "KinshipResult", field = "from")
    public PersonRef from(KinshipResultDto result) {
        return new PersonRef(result.fromPersonId());
    }

    @SchemaMapping(typeName = "KinshipResult", field = "to")
    public PersonRef to(KinshipResultDto result) {
        return new PersonRef(result.toPersonId());
    }

    /**
     * Tham chiếu nhân khẩu tối giản — chỉ mang {@code id}.
     *
     * <p>Cố ý không mang tên: tên là thứ phải đi qua bộ lọc phân tầng hiển thị, và context này
     * không phải nơi quyết định điều đó cho một hồ sơ đầy đủ. Trên đường quan hệ thì tên đã được
     * che sẵn ở {@code KinshipDisplayPolicy} và nằm ở trường {@code displayName} của từng chặng.</p>
     */
    public record PersonRef(UUID id) {
    }
}

package vn.giapha.kinship.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.kinship.api.dto.KinshipResultDto;
import vn.giapha.kinship.application.ResolveKinshipTitleService;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;
import vn.giapha.shared.vo.PersonId;

/**
 * {@code GET /api/v1/kinship?from=&to=} — tra danh xưng giữa hai người (FR-1.3).
 *
 * <p>Trả về không chỉ danh xưng mà cả <b>bằng chứng</b>: đường quan hệ đi qua tổ chung gần nhất,
 * cộng {@code ruleId}/{@code ruleSetId}/{@code ruleSetScope}. Đây là phần quan trọng nhất của
 * endpoint này về mặt nghiệp vụ — khi dòng họ báo "gọi sai" thì có ngay chỗ để đối chiếu thay vì
 * tranh luận bằng trí nhớ, và Hội đồng biết phải sửa luật nào ở bộ luật cấp nào.</p>
 *
 * <p><b>Không tìm được vẫn là {@code 200}</b>, phân biệt bằng {@code status}
 * ({@code NO_COMMON_ANCESTOR}, {@code NO_MATCHING_RULE}, {@code SELF}). Chỉ {@code 404} khi một
 * trong hai nhân khẩu không tồn tại.</p>
 *
 * <hr>
 *
 * <p><b>Lệch giữa hai hợp đồng — cần chốt, hiện đang theo CSDL:</b> {@code contracts/openapi.yaml}
 * và {@code schema.graphqls} mô tả {@code facts.genDelta} là "<i>âm = to ở đời trên</i>" và ví dụ
 * {@code bacHo} ghi {@code genDelta: -1}. Nhưng migration {@code V3__kinship_rules.sql} định nghĩa
 * {@code gen_delta = dist_a - dist_b} và cả 111 luật đã seed dùng dấu ngược lại — luật {@code CHA}
 * có {@code gen_delta = 1}, {@code CON_TRAI} có {@code -1}. API này trả theo <b>quy ước của CSDL</b>
 * (dương = người được gọi ở đời trên). Đảo dấu riêng ở {@code facts} mà không đảo ở
 * {@code KinshipRuleDto.genDelta} sẽ tạo ra một cái bẫy im lặng ngay giữa màn hình sửa luật của Hội
 * đồng — nơi hai con số đó luôn được đọc cạnh nhau. Đề nghị sửa phần mô tả trong contract.</p>
 */
@RestController
@RequestMapping("/api/v1/kinship")
@Tag(name = "kinship", description = "Danh xưng & bộ quy tắc danh xưng")
public class KinshipController {

    /** Chỉ hai vai này được ép bộ luật — đây là màn hình thử nghiệm luật của Hội đồng. */
    private static final Set<String> RULE_SET_OVERRIDE_ROLES = Set.of("COUNCIL", "ADMIN");

    private final ResolveKinshipTitleService resolveKinshipTitle;

    public KinshipController(ResolveKinshipTitleService resolveKinshipTitle) {
        this.resolveKinshipTitle = resolveKinshipTitle;
    }

    /**
     * @param from        người xưng hô (chủ ngữ) — "A gọi B là gì?"
     * @param to          người được gọi (tân ngữ)
     * @param includePath có trả đường quan hệ qua LCA hay không
     * @param ruleSetId   ép dùng một bộ luật cụ thể thay vì bộ hiệu lực của chi người {@code from};
     *                    chỉ {@code COUNCIL}/{@code ADMIN}
     */
    @GetMapping
    @Operation(summary = "Tra cứu danh xưng giữa hai người",
            description = "Trả danh xưng, cách tự xưng, danh xưng chiều ngược, đường quan hệ qua LCA "
                    + "và luật đã khớp. Danh xưng đến từ dữ liệu kinship_rule, không hard-code.")
    public KinshipResultDto resolve(
            @RequestParam UUID from,
            @RequestParam UUID to,
            @RequestParam(defaultValue = "true") boolean includePath,
            @RequestParam(required = false) UUID ruleSetId) {

        if (ruleSetId != null) {
            requireRuleSetOverridePermission();
        }
        return KinshipApiMapper.toDto(resolveKinshipTitle.resolve(
                PersonId.of(from), PersonId.of(to), ruleSetId, includePath));
    }

    private void requireRuleSetOverridePermission() {
        boolean allowed = CurrentUserProvider.current()
                .map(KinshipController::hasOverrideRole)
                .orElse(false);
        if (!allowed) {
            throw new ForbiddenException("FORBIDDEN",
                    "Chi Hoi dong Toc bieu (COUNCIL) hoac ADMIN duoc ep bo luat khi tra danh xung");
        }
    }

    private static boolean hasOverrideRole(CurrentUser user) {
        return RULE_SET_OVERRIDE_ROLES.stream().anyMatch(user::hasRole);
    }
}

package vn.giapha.kinship.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.kinship.api.dto.KinshipRuleSetDto;
import vn.giapha.kinship.api.dto.KinshipRuleSetUpdateRequest;
import vn.giapha.kinship.api.dto.PageMetaDto;
import vn.giapha.kinship.application.EffectiveRuleSetService;
import vn.giapha.kinship.application.RuleAdminService;
import vn.giapha.kinship.application.RuleSetConflictException;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.Region;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.shared.api.ProblemTypes;
import vn.giapha.shared.exception.DomainException;

/**
 * {@code GET|PUT /api/v1/kinship-rules} — đọc và ghi đè bộ quy tắc danh xưng (FR-1.3a).
 *
 * <p>Hai chế độ đọc, phục vụ hai màn hình khác nhau:</p>
 * <ul>
 *   <li>{@code effective=false} (mặc định) — từng bộ luật <b>thô</b> đúng như đã lưu, kèm
 *       {@code parentRuleSetId}. Dùng khi Hội đồng sửa đúng một cấp.</li>
 *   <li>{@code effective=true} — <b>một</b> bộ đã hợp nhất {@code DEFAULT -> REGION -> CLAN ->
 *       BRANCH} cho {@code branchId}, mỗi luật kèm nguồn gốc và cờ {@code overridden}. Đây đúng là
 *       bộ mà {@code /api/v1/kinship} dùng, nên là màn hình để đối chiếu khi có khiếu nại.</li>
 * </ul>
 *
 * <p>Ghi thì chỉ {@code COUNCIL}/{@code ADMIN} — quyền kiểm ở {@link RuleAdminService} bằng
 * {@code @PreAuthorize}, tức là ở tầng use case chứ không phải ở tầng HTTP, để lối vào GraphQL hay
 * lối vào từ job nhập liệu về sau cũng được chặn y hệt.</p>
 */
@RestController
@RequestMapping("/api/v1/kinship-rules")
@Tag(name = "kinship", description = "Danh xưng & bộ quy tắc danh xưng")
public class KinshipRuleController {

    private static final Logger log = LoggerFactory.getLogger(KinshipRuleController.class);

    private static final int MAX_PAGE_SIZE = 200;

    private final RuleAdminService ruleAdmin;
    private final EffectiveRuleSetService effectiveRuleSets;

    public KinshipRuleController(RuleAdminService ruleAdmin,
            EffectiveRuleSetService effectiveRuleSets) {
        this.ruleAdmin = ruleAdmin;
        this.effectiveRuleSets = effectiveRuleSets;
    }

    /**
     * @return {@code KinshipRuleSetPage} khi {@code effective=false}, hoặc
     *         {@code EffectiveKinshipRuleSet} khi {@code effective=true}
     */
    @GetMapping
    @Operation(summary = "Đọc bộ quy tắc danh xưng",
            description = "effective=false: các bộ luật thô. effective=true: bộ đã hợp nhất cho branchId.")
    public Object read(
            @RequestParam(required = false) RuleScope scope,
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "false") boolean effective,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (effective) {
            if (branchId == null) {
                throw new DomainException("VALIDATION_FAILED",
                        "effective=true thi bat buoc co 'branchId'");
            }
            return KinshipApiMapper.toDto(effectiveRuleSets.forBranch(branchId));
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<KinshipRuleSet> all = ruleAdmin.list(scope, region, branchId);
        List<KinshipRuleSetDto> items = new ArrayList<>();
        for (KinshipRuleSet set : RuleAdminService.page(all, safePage, safeSize)) {
            items.add(KinshipApiMapper.toDto(set));
        }
        return new PageMetaDto.Page<>(items, PageMetaDto.of(safePage, safeSize, all.size()));
    }

    /**
     * Ghi đè <b>trọn vẹn</b> một bộ luật. {@code 201} khi tạo mới, {@code 200} khi cập nhật.
     *
     * <p>Không có biến thể "patch một luật" là quyết định có chủ ý — xem
     * {@link RuleAdminService}.</p>
     */
    @PutMapping
    @Operation(summary = "Ghi đè một bộ quy tắc danh xưng",
            description = "Chỉ COUNCIL/ADMIN. Mảng rules thay thế trọn vẹn luật hiện có của bộ đó.")
    public ResponseEntity<KinshipRuleSetDto> replace(
            @Valid @RequestBody KinshipRuleSetUpdateRequest request) {

        if (request.clanId() != null) {
            // Bang kinship_rule_set (V3) khong co cot clan_id - khong lang le nuot mat gia tri.
            log.warn("Bo qua 'clanId' khi ghi bo luat danh xung: migration V3 chua co cot tuong ung");
        }
        RuleAdminService.Result result = ruleAdmin.replace(KinshipApiMapper.toCommand(request));
        KinshipRuleSetDto body = KinshipApiMapper.toDto(result.ruleSet());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }

    /**
     * Nhóm {@code 409} của endpoint này — {@code OPTIMISTIC_LOCK_CONFLICT}, {@code RULE_SET_CYCLE},
     * {@code DUPLICATE_RULE}.
     *
     * <p>Xử lý ngay tại controller thay vì thêm nhánh vào {@code GlobalExceptionHandler} của shared
     * kernel: đây là ba mã lỗi riêng của một context, và {@code DomainException} ở tầng chung được
     * ánh xạ sang {@code 422} — đúng cho vi phạm quy tắc nghiệp vụ, sai cho xung đột ghi đồng thời
     * (client cần biết là "tải lại rồi thử lại", không phải "sửa dữ liệu nhập").</p>
     */
    @ExceptionHandler(RuleSetConflictException.class)
    public ProblemDetail handleConflict(RuleSetConflictException ex, HttpServletRequest request) {
        log.info("409 {} - [{}] {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(ProblemTypes.CONFLICT);
        problem.setTitle("Xung đột khi ghi bộ quy tắc danh xưng");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ex.getCode());
        // Ba ca nay deu la xung dot cung: khong co co xac nhan nao ghi de duoc, phai nap lai.
        problem.setProperty("overridable", false);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}

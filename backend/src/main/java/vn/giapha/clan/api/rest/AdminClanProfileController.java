package vn.giapha.clan.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.clan.api.rest.dto.ClanProfileDto;
import vn.giapha.clan.api.rest.dto.ClanProfileWriteRequest;
import vn.giapha.clan.api.rest.dto.ClanReadinessDto;
import vn.giapha.clan.api.rest.dto.CouncilAccountDto;
import vn.giapha.clan.api.rest.dto.CreateCouncilAccountRequest;
import vn.giapha.clan.application.ClanCouncilService;
import vn.giapha.clan.application.ClanPreconditionException;
import vn.giapha.clan.application.ClanProfileService;
import vn.giapha.clan.application.ClanProfileView;
import vn.giapha.clan.application.ClanProfileWriteResult;
import vn.giapha.clan.application.ClanReadinessService;

/**
 * <b>Khởi tạo dòng họ</b> — {@code /api/v1/admin/clan-profile}, chỉ vai kỹ thuật {@code ADMIN}.
 *
 * <h2>Vì sao chỉ ADMIN, không cả Hội đồng</h2>
 * Hồ sơ này quyết định <b>nhãn gốc {@code ltree}</b> (tức phạm vi phân quyền của cả họ) và vùng
 * của bộ luật danh xưng; bước kế tiếp là lập tài khoản Hội đồng. Cho Hội đồng tự làm những việc ấy
 * là để thẩm quyền nội dung tự dựng lại khung phân quyền của chính nó. Hội đồng sửa luật danh xưng
 * ở {@code /kinship-rules}, không ở đây.
 *
 * <h2>{@code ETag} là khoá lạc quan, giống {@code GET /persons/{id}}</h2>
 * Giá trị là {@code version}; {@code PUT} đọc lại qua {@code If-Match}. Ở đây thân phản hồi
 * <b>giống nhau</b> với mọi người được phép gọi (chỉ ADMIN), nên không có cái bẫy "một ETag, hai
 * thân" của hồ sơ nhân khẩu — {@code no-store} vẫn đặt vì đây là màn quản trị và không có lý do
 * gì để trình duyệt giữ nó.
 */
@RestController
@RequestMapping("/api/v1/admin/clan-profile")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "clan", description = "Hồ sơ & khởi tạo dòng họ")
public class AdminClanProfileController {

    private static final String SELF = "/api/v1/admin/clan-profile";

    private final ClanProfileService profiles;
    private final ClanReadinessService readiness;
    private final ClanCouncilService council;

    public AdminClanProfileController(ClanProfileService profiles, ClanReadinessService readiness,
                                      ClanCouncilService council) {
        this.profiles = profiles;
        this.readiness = readiness;
        this.council = council;
    }

    /** {@code 404 CLAN_NOT_INITIALIZED} khi chưa có hồ sơ — giao diện mở biểu mẫu khởi tạo. */
    @GetMapping
    @Operation(summary = "Hồ sơ dòng họ (quản trị), kèm ETag = version")
    public ResponseEntity<ClanProfileDto> get() {
        ClanProfileView view = profiles.adminProfile();
        return ResponseEntity.ok()
                .eTag(etagOf(view))
                .cacheControl(CacheControl.noStore())
                .body(ClanProfileDto.from(view));
    }

    /**
     * Tạo ({@code 201}) hoặc thay ({@code 200}) hồ sơ dòng họ.
     *
     * <p>Mã lỗi: {@code 400} hình dạng · {@code 412 PRECONDITION_REQUIRED} (sửa mà thiếu/sai
     * {@code If-Match}) · {@code 409 OPTIMISTIC_LOCK_CONFLICT} · {@code 409 CLAN_ROOT_LOCKED} ·
     * {@code 422 VALIDATION_FAILED}.</p>
     */
    @PutMapping
    @Operation(summary = "Khởi tạo hoặc sửa hồ sơ dòng họ")
    public ResponseEntity<ClanProfileDto> put(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ClanProfileWriteRequest body) {
        ClanProfileWriteResult result = profiles.write(body.toCommand(parseIfMatch(ifMatch)));
        ClanProfileView view = result.profile();
        ResponseEntity.BodyBuilder response = result.created()
                ? ResponseEntity.created(URI.create(SELF))
                : ResponseEntity.ok();
        return response.eTag(etagOf(view))
                .cacheControl(CacheControl.noStore())
                .body(ClanProfileDto.from(view));
    }

    @GetMapping("/readiness")
    @Operation(summary = "Dòng họ còn thiếu bước khởi tạo nào")
    public ResponseEntity<ClanReadinessDto> readiness() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClanReadinessDto.from(readiness.readiness()));
    }

    /**
     * Lập tài khoản Hội đồng Tộc biểu. {@code setPasswordUrl} chỉ xuất hiện trong phản hồi này.
     *
     * <p>Không kèm {@code Location}: không có endpoint đọc lại một lượt lập (cùng lý do với
     * {@code POST /password-resets}).</p>
     */
    @PostMapping("/council-accounts")
    @Operation(summary = "Lập tài khoản Hội đồng Tộc biểu kèm liên kết đặt mật khẩu một lần")
    public ResponseEntity<CouncilAccountDto> createCouncilAccount(
            @Valid @RequestBody CreateCouncilAccountRequest body) {
        CouncilAccountDto created = CouncilAccountDto.from(council.createCouncilAccount(
                body.displayName(), body.loginId(), body.personId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    /**
     * {@code If-Match} → phiên bản. Vắng ⇒ {@code null} (service quyết định: tạo thì không cần,
     * sửa thì {@code 412}). Có mà sai dạng ⇒ {@code 412} ngay — cùng cách {@code PersonController}
     * chấp nhận cả {@code W/"3"} lẫn {@code "3"}.
     */
    private static Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "").trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ClanPreconditionException("If-Match không hợp lệ: " + ifMatch);
        }
    }

    private static String etagOf(ClanProfileView view) {
        return "\"" + view.version() + "\"";
    }
}

package vn.giapha.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.PasswordResetGrant;
import vn.giapha.membership.domain.port.AppUserRepository;
import vn.giapha.membership.domain.port.PasswordRejectedException;
import vn.giapha.membership.domain.port.PasswordResetGrantRepository;
import vn.giapha.membership.domain.port.PasswordResetPort;
import vn.giapha.membership.domain.port.SetPasswordNotAllowedException;

/**
 * Đuôi của luồng cấp lại: người quên mật khẩu bấm liên kết một lần và <b>tự</b> đặt mật khẩu mới.
 *
 * <h2>Lớp này chia chung endpoint với luồng mời, và đó là chủ ý</h2>
 * {@code POST /api/v1/invitations/set-password} nhận <i>cả hai</i> loại token, vì người dùng chỉ có
 * một màn {@code /dat-mat-khau} và khuôn địa chỉ ấy đã nằm trong cấu hình
 * ({@code giapha.keycloak.admin.set-password-url}). Phân biệt hai loại <b>không</b> bằng hình dạng
 * chuỗi mà bằng <b>khoá ký</b>: {@link PasswordResetPort#readGrantRef} chỉ đọc được token do luồng
 * này đúc, và trả rỗng cho mọi token khác — lúc ấy {@code SetPasswordService} chuyển sang luồng đặt
 * mật khẩu lần đầu. Không có quy ước nào để ai đó nhớ nhầm.
 *
 * <h2>ĐỐT TRƯỚC, GHI SAU — và vì sao đó là thứ tự đúng</h2>
 * Thứ tự ngược lại (ghi mật khẩu trước, đánh dấu đã dùng sau) nghe an toàn hơn cho người dùng, và
 * nó mở đúng lỗ hổng mà cả luồng này phải đóng: một sự cố giữa hai bước để lại một liên kết
 * <b>còn dùng được</b> trên một tài khoản mà mật khẩu vừa đổi — tức là người cầm liên kết (chính là
 * Trưởng chi) đặt lại được lần nữa, sau lưng chính chủ.
 *
 * <p>Vì vậy: {@link PasswordResetGrantRepository#claim} trước — một lệnh
 * {@code UPDATE ... WHERE used_at IS NULL} nên nhiều nhất một lượt gọi đi tiếp được — rồi mới gọi
 * Keycloak.</p>
 *
 * <h2>Một ngoại lệ, đúng một, được phép trả liên kết về trạng thái chưa dùng</h2>
 * Realm từ chối mật khẩu vì <b>chính sách</b> ({@link PasswordRejectedException} — HTTP 400 của
 * Keycloak) là lỗi của người gõ, và nó chứng minh <b>không một credential nào bị đổi</b>. Đốt liên
 * kết ở ca ấy là bắt một cụ gõ sai một lần rồi phải đi xin lại liên kết — hỏng đúng nhóm người mà
 * cả luồng sinh ra để phục vụ. Mọi ngoại lệ <i>khác</i> (mạng, 5xx) thì <b>không</b> trả lại: ở đó
 * không biết được realm đã ghi hay chưa, và thiếu dữ kiện thì chọn vế an toàn.
 *
 * <h2>Không {@code @Transactional}, và đó không phải sơ suất</h2>
 * Mỗi lệnh ghi ở đây là một đơn vị độc lập và <b>phải commit ngay</b>: lượt đốt phải có hiệu lực
 * trước khi lời gọi sang Keycloak bắt đầu, không phải sau khi nó kết thúc. Gói cả ba vào một
 * transaction sẽ đúng vẻ ngoài mà sai bản chất — một transaction không bao bọc được lời gọi HTTP,
 * và một lượt đốt chưa commit thì chưa chặn được lượt bấm thứ hai.
 */
@Service
public class PasswordResetRedeemer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRedeemer.class);

    private final PasswordResetGrantRepository grants;
    private final AppUserRepository appUsers;
    private final PasswordResetPort resets;
    private final AuditTrailService audit;

    public PasswordResetRedeemer(PasswordResetGrantRepository grants, AppUserRepository appUsers,
                                 PasswordResetPort resets, AuditTrailService audit) {
        this.grants = grants;
        this.appUsers = appUsers;
        this.resets = resets;
        this.audit = audit;
    }

    /**
     * Đặt mật khẩu nếu {@code token} là token của luồng cấp lại.
     *
     * @return {@code false} khi token <b>không</b> thuộc luồng này — người gọi phải thử luồng đặt
     *         mật khẩu lần đầu. {@code true} khi đã đặt xong.
     * @throws SetPasswordNotAllowedException token đúng chữ ký nhưng lượt cấp đã dùng, đã thu hồi,
     *         quá hạn, hoặc tài khoản đích không còn — tất cả trả về cùng một thông điệp
     */
    public boolean completeIfResetToken(String token, String rawPassword) {
        Optional<UUID> grantRef = resets.readGrantRef(token);
        if (grantRef.isEmpty()) {
            return false;
        }
        UUID grantId = grantRef.get();
        PasswordResetGrant grant = grants.byId(grantId).orElseThrow(
                PasswordResetRedeemer::lienKetKhongDungDuoc);
        AppUser target = appUsers.byId(grant.targetAppUserId()).orElseThrow(
                PasswordResetRedeemer::lienKetKhongDungDuoc);

        // Phep kiem re tien chay truoc de tra loi dung ly do; phep canh THAT la claim() ben duoi.
        // Ai doc dong nay roi ket luan "vay la an toan roi" dang dung lai loi kiem-roi-moi-ghi.
        if (!grant.usableAt(Instant.now())) {
            throw lienKetKhongDungDuoc();
        }
        if (!grants.claim(grantId, Instant.now())) {
            // Thua cuoc dua, hoac vua het han giua hai dong lenh. Ca hai deu la "khong dung duoc".
            throw lienKetKhongDungDuoc();
        }

        try {
            resets.overwritePassword(target.keycloakSub(), rawPassword);
        } catch (PasswordRejectedException ex) {
            // Realm CHUA ghi gi ca — tra lien ket ve trang thai chua dung. Xem javadoc lop.
            grants.release(grantId);
            throw ex;
        }

        // KHONG log token, KHONG log mat khau. Ghi nhat ky sau khi da doi xong: mot dong
        // "da dung" cho mot luot that bai la mot dong noi doi trong bang chi ghi them.
        audit.record(PasswordResetGrantWriter.ENTITY, grantId.toString(), AuditAction.UPDATE,
                null, Map.of("usedAt", String.valueOf(Instant.now()),
                        "targetAppUserId", String.valueOf(grant.targetAppUserId())),
                List.of("usedAt"),
                "Thanh vien da dat lai mat khau qua lien ket duoc cap");
        log.info("Lien ket cap lai {} da duoc dung: app_user {} vua dat lai mat khau", grantId,
                grant.targetAppUserId());
        return true;
    }

    /**
     * Mọi lý do hỏng cho ra <b>cùng một ngoại lệ</b>: đã dùng, đã thu hồi, quá hạn, tài khoản không
     * còn. Phân biệt chúng cho người gọi là nói cho kẻ dò biết mình đang sai ở đâu — và lối đi tiếp
     * thì giống hệt nhau: xin Trưởng chi cấp lại.
     */
    private static SetPasswordNotAllowedException lienKetKhongDungDuoc() {
        return new SetPasswordNotAllowedException(
                "Liên kết đặt mật khẩu không hợp lệ, đã được dùng hoặc đã hết hạn."
                        + " Hãy liên hệ Trưởng chi để được cấp lại.");
    }
}

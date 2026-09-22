package vn.giapha.membership.application;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.PasswordResetGrant;
import vn.giapha.membership.domain.port.PasswordResetGrantRepository;
import vn.giapha.notification.application.LocalizedText;
import vn.giapha.notification.application.NotificationDispatchService;
import vn.giapha.notification.application.SystemNotice;

/**
 * Ghi một lượt cấp lại liên kết đặt mật khẩu: <b>thu hồi liên kết cũ · ghi liên kết mới · ghi nhật
 * ký · báo chính chủ</b> — bốn việc, một transaction.
 *
 * <h2>Vì sao tách khỏi {@link PasswordResetGrantService}</h2>
 * Cùng lý do đã buộc {@code InvitationLinker} ra đời: phần kiểm tra của lệnh cấp phải gọi Keycloak
 * (tra tài khoản theo {@code sub}), và một lời gọi HTTP <b>không được nằm trong</b> transaction
 * Postgres — giữ kết nối CSDL suốt một chuyến đi mạng là cách một sự cố mạng biến thành cạn pool
 * kết nối. Gọi phương thức {@code @Transactional} của chính mình thì proxy không chạy, nên ranh
 * giới ấy phải là một bean khác. Đây là bean ấy.
 *
 * <h2>Bốn việc này cùng sống hoặc cùng chết, và thứ tự trong đó không tuỳ tiện</h2>
 * <ol>
 *   <li><b>Thu hồi liên kết cũ trước.</b> {@code ux_password_reset_grant_open} chỉ cho một liên kết
 *       mở trên mỗi tài khoản; và nếu hai liên kết cùng mở được một tài khoản thì "thu hồi" mất
 *       nghĩa.</li>
 *   <li><b>Ghi liên kết mới.</b></li>
 *   <li><b>Ghi {@code audit_log}.</b> Cùng transaction với (2): một liên kết sống mà không có vết
 *       trong nhật ký là đúng thứ cả thiết kế này sinh ra để không xảy ra.</li>
 *   <li><b>Báo chính chủ.</b> Cũng cùng transaction, và đây là điểm dễ bị "sửa cho gọn" nhất — xem
 *       dưới.</li>
 * </ol>
 *
 * <h2>Vì sao lượt đẩy thông báo nằm TRONG transaction</h2>
 * Đẩy tin lên RabbitMQ trong một transaction Postgres là "hai hệ thống, không có transaction
 * chung", và cách sắp xếp này chọn vế <b>thà thừa một thông báo còn hơn thiếu</b>:
 * <ul>
 *   <li>Broker chết ⇒ ngoại lệ ⇒ transaction rollback ⇒ <b>không có liên kết nào được cấp</b>.
 *       Trưởng chi thấy lỗi và thử lại. Đó là vế đúng: một liên kết sống mà chủ tài khoản không
 *       được báo là một lượt chiếm tài khoản im lặng.</li>
 *   <li>Đẩy xong rồi transaction rollback vì lý do khác ⇒ chính chủ nhận một thông báo về một liên
 *       kết không tồn tại. Vô hại: token trỏ tới một dòng không có, nên nó không mở được gì. Và
 *       người nhận hỏi lại "ai vừa làm việc này" thì đó vẫn là câu hỏi đúng.</li>
 * </ul>
 * Đổi thứ tự — đẩy tin <i>sau</i> khi commit, bằng {@code @TransactionalEventListener} chẳng hạn —
 * nghe sạch hơn nhưng đổi luôn vế: lúc ấy broker chết sẽ cho ra một liên kết sống, không ai biết.
 *
 * <h2>Chính chủ đang không đăng nhập được — thông báo này để làm gì?</h2>
 * Đúng, và nó vẫn là thứ đáng giá nhất ở đây. Hộp thư in-app không mất đi: người ấy đặt lại mật
 * khẩu xong, đăng nhập, và <b>nhìn thấy</b> ai đã cấp liên kết cho mình lúc nào. Web Push thì tới
 * ngay nếu trình duyệt của họ còn đăng ký. Cả hai đều là bằng chứng sau sự việc, và một lượt cấp
 * <i>không chính đáng</i> phải để lại đúng loại bằng chứng ấy ở nơi <b>nạn nhân</b> nhìn thấy, chứ
 * không chỉ ở nơi quản trị viên nhìn thấy.
 */
@Service
public class PasswordResetGrantWriter {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetGrantWriter.class);

    static final String ENTITY = "PasswordResetGrant";

    /** Giờ Việt Nam: thông báo hiện cho người trong họ đọc, không cho máy chủ đọc. */
    private static final DateTimeFormatter GIO_VN = DateTimeFormatter
            .ofPattern("HH:mm 'ngay' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final DateTimeFormatter GIO_EN = DateTimeFormatter
            .ofPattern("HH:mm 'on' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final PasswordResetGrantRepository grants;
    private final AuditTrailService audit;
    private final NotificationDispatchService notifications;

    public PasswordResetGrantWriter(PasswordResetGrantRepository grants, AuditTrailService audit,
                                    NotificationDispatchService notifications) {
        this.grants = grants;
        this.audit = audit;
        this.notifications = notifications;
    }

    /**
     * @param target tài khoản được cấp lại
     * @param issuer tài khoản của người cấp — tên hiển thị của họ đi vào thông báo, vì "ai vừa làm
     *               việc này" là câu hỏi đầu tiên của người nhận. {@code null} thì thông báo nói
     *               "Trưởng chi": một câu nói chung vẫn hơn một câu bỏ trống
     */
    @Transactional
    public PasswordResetGrant write(PasswordResetGrant grant, AppUser target, AppUser issuer) {
        grants.revokeOpenFor(grant.targetAppUserId(), grant.issuedAt(),
                "Cap lai lien ket dat mat khau moi");
        PasswordResetGrant saved = grants.save(grant);

        // KHONG ghi url, KHONG ghi token, KHONG ghi keycloak_sub vao audit: dong nhat ky la vinh
        // vien, con hai thu dau la bi mat mot lan. Nhung gi ghi o day du de tra loi "ai cap, cho
        // ai, luc nao" — dung ba cau hoi cua mot cuoc dieu tra.
        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE, null,
                Map.of("targetAppUserId", String.valueOf(saved.targetAppUserId()),
                        "targetPersonId", String.valueOf(saved.targetPersonId()),
                        "branchId", String.valueOf(saved.branchId()),
                        "issuedBy", String.valueOf(saved.issuedBy()),
                        "expiresAt", String.valueOf(saved.expiresAt())),
                List.of("targetAppUserId", "issuedBy", "expiresAt"),
                "Cap lai lien ket dat mat khau cho thanh vien");

        notifications.dispatchSystemNotice(baoChinhChu(saved, target, issuer));

        log.info("app_user {} cap lai lien ket dat mat khau cho app_user {} (nhan khau {}), han {}",
                saved.issuedBy(), saved.targetAppUserId(), saved.targetPersonId(),
                saved.expiresAt());
        return saved;
    }

    /**
     * Thông báo gửi cho <b>chính chủ</b>.
     *
     * <p>Không một mảnh nào của liên kết nằm trong đây, và cũng không có dữ liệu Tầng 3 nào: thông
     * báo hiện trên màn hình khoá của thiết bị và đi qua hạ tầng của bên thứ ba. Thứ duy nhất nó
     * nói là <i>ai</i> đã cấp và <i>tới bao giờ</i> liên kết còn hiệu lực — vừa đủ để người nhận
     * biết mình đang chờ cái gì, và vừa đủ để họ nhận ra khi mình <b>không</b> chờ gì cả.</p>
     */
    private static SystemNotice baoChinhChu(PasswordResetGrant grant, AppUser target,
                                            AppUser issuer) {
        String nguoiCap = tenNguoiCap(issuer);
        String hanVi = GIO_VN.format(grant.expiresAt());
        String hanEn = GIO_EN.format(grant.expiresAt());
        return new SystemNotice(grant.targetPersonId(), grant.targetAppUserId(), target.locale(),
                LocalizedText.of("Có người vừa cấp liên kết đặt lại mật khẩu cho tài khoản của bạn",
                        "A password reset link was issued for your account"),
                LocalizedText.of(nguoiCap + " vừa cấp một liên kết đặt lại mật khẩu cho tài khoản"
                                + " của bạn. Liên kết có hiệu lực tới " + hanVi
                                + ". Nếu bạn không yêu cầu việc này, hãy báo ngay Hội đồng Tộc biểu"
                                + " và đổi mật khẩu.",
                        nguoiCap + " issued a password reset link for your account. It is valid"
                                + " until " + hanEn + " (GMT+7). If you did not ask for this,"
                                + " tell the clan council and change your password now."),
                // Khong deep link: khong co trang nao de toi, va mot duong dan trong thong bao an
                // ninh la mot thu de bat chuoc trong mot tin nhan gia mao.
                null);
    }

    private static String tenNguoiCap(AppUser issuer) {
        return issuer != null && issuer.displayName() != null && !issuer.displayName().isBlank()
                ? issuer.displayName() : "Trưởng chi";
    }
}

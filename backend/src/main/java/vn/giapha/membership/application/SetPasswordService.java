package vn.giapha.membership.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.IdentityProviderPort;
import vn.giapha.shared.exception.DomainException;

/**
 * Đuôi của luồng mời: người được mời bấm liên kết một lần và <b>tự</b> đặt mật khẩu.
 *
 * <h2>Không có {@code @Transactional}, và không có gì để ghi</h2>
 * Mật khẩu sống ở Keycloak. Lớp này không lưu gì vào Postgres, không lưu gì vào bộ nhớ, và không
 * ghi chuỗi mật khẩu ra log — nó chỉ chuyển tiếp. Vì vậy cũng không có transaction nào ở đây: một
 * transaction không bao bọc được lời gọi HTTP sang Keycloak, và giả vờ ngược lại là tự dối.
 *
 * <h2>Vì sao mật khẩu đi qua backend chứ không đi thẳng tới Keycloak</h2>
 * Cách "đúng sách" là trả về một <i>action token</i> của chính Keycloak, để người dùng đặt mật khẩu
 * trên giao diện của Keycloak và backend không bao giờ nhìn thấy chuỗi ký tự ấy. <b>Keycloak bản
 * thường không có endpoint nào trả ra liên kết ấy</b>: {@code execute-actions-email} <i>gửi thư</i>
 * và trả về thân rỗng, và không có biến thể nào trả về URL. Muốn có thì phải cắm một SPI riêng vào
 * máy chủ Keycloak.
 *
 * <h2>Lớp này phục vụ HAI luồng, không phải một</h2>
 * Từ V21 nó còn là đuôi của luồng <b>cấp lại</b> (Trưởng chi cấp liên kết cho một thành viên đã
 * quên mật khẩu — {@code PasswordResetGrantService}). Hai luồng dùng chung endpoint và chung màn
 * hình vì người dùng chỉ có một màn {@code /dat-mat-khau}; chúng được phân biệt bằng <b>khoá ký của
 * token</b>, không bằng một cờ trong thân yêu cầu và không bằng hình dạng chuỗi — xem
 * {@link PasswordResetRedeemer}.
 *
 * <p>Nên hình dạng hôm nay là: liên kết do backend đúc (ký HMAC, hạn ngắn, một lần), và mật khẩu
 * người dùng chọn đi <i>qua</i> lớp này tới Keycloak — nơi chính sách mật khẩu của realm được áp
 * dụng và nơi nó được băm. Backend không sinh mật khẩu, không lưu mật khẩu, không đọc lại được.
 * <b>Lối nâng cấp đã dọn sẵn</b>: khi có SMTP hoặc một SPI trả action token, chỉ
 * {@code KeycloakIdentityProviderAdapter} đổi — cổng, lớp này và hợp đồng API giữ nguyên.</p>
 */
@Service
public class SetPasswordService {

    private static final Logger log = LoggerFactory.getLogger(SetPasswordService.class);

    private final IdentityProviderPort identityProvider;
    private final PasswordResetRedeemer resetRedeemer;

    public SetPasswordService(IdentityProviderPort identityProvider,
                              PasswordResetRedeemer resetRedeemer) {
        this.identityProvider = identityProvider;
        this.resetRedeemer = resetRedeemer;
    }

    /**
     * Đặt mật khẩu cho tài khoản mà token trỏ tới.
     *
     * <p>Độ mạnh mật khẩu <b>không</b> được kiểm ở đây: luật ấy là của realm, và chép lại là có hai
     * nguồn chân lý sẽ lệch nhau ngay lần đầu ai đó siết chính sách trong giao diện Keycloak. Chỉ
     * hai phép kiểm rẻ tiền chạy ở đây — rỗng và dài quá mức — để không gửi đi một yêu cầu chắc
     * chắn hỏng.</p>
     */
    public void setPassword(String token, String rawPassword) {
        if (token == null || token.isBlank()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Thiếu liên kết đặt mật khẩu");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new DomainException(MembershipProblemCodes.VALIDATION_FAILED,
                    "Mật khẩu không được rỗng");
        }
        // HAI LOAI TOKEN, MOT ENDPOINT. Hoi luong cap lai truoc, va cau hoi ay duoc tra loi bang
        // KHOA KY chu khong bang hinh dang chuoi: readGrantRef chi doc duoc token do luong cap lai
        // duc ra. Token cua luong moi luon cho ra rong o day va di tiep xuong duoi.
        //
        // Thu tu giua hai luong khong anh huong tinh dung — hai khoa ky khac nhau nen khong token
        // nao doc duoc boi ca hai — nhung dat luong cap lai truoc giu cho nhanh duoi nguyen ven
        // dung nhu truoc khi co V21.
        if (resetRedeemer.completeIfResetToken(token, rawPassword)) {
            log.info("Mot thanh vien vua dat lai mat khau qua lien ket do Truong chi cap");
            return;
        }
        if (!identityProvider.isConfigured()) {
            throw new IdentityProviderException(
                    "He thong chua duoc cau hinh de dat mat khau");
        }
        identityProvider.completeSetPassword(token, rawPassword);
        // KHONG log token, KHONG log mat khau. Ca hai deu la bi mat mot lan.
        log.info("Mot nguoi duoc moi vua tu dat mat khau qua lien ket mot lan");
    }
}

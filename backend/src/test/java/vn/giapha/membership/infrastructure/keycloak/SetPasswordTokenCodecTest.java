package vn.giapha.membership.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Token của liên kết đặt mật khẩu: ký HMAC-SHA256, hạn ngắn, <b>không lưu ở đâu cả</b>.
 *
 * <p>Không có bảng nào lưu token, nên mọi phép kiểm phải nằm trong chính token. Bộ test này ghim
 * bốn điều: sửa một ký tự là hỏng · đổi bí mật là hỏng · quá hạn là hỏng · và mọi lý do hỏng trả về
 * <b>cùng một kết quả rỗng</b>, không nói cho người dò biết mình sai ở đâu.</p>
 */
@DisplayName("Token của liên kết đặt mật khẩu")
class SetPasswordTokenCodecTest {

    private static final String BI_MAT = "bi-mat-client-dich-vu-gia-lap";
    private static final String SUBJECT = "0b9f7a4e-1111-2222-3333-444455556666";
    private static final Duration NUA_GIO = Duration.ofMinutes(30);

    private final SetPasswordTokenCodec codec = new SetPasswordTokenCodec(BI_MAT);

    @Test
    @DisplayName("token vừa đúc thì kiểm ra đúng subject")
    void ducRoiKiemRaDungSubject() {
        Instant now = Instant.parse("2026-09-19T07:00:00Z");

        SetPasswordTokenCodec.Minted minted = codec.mint(SUBJECT, NUA_GIO, now);

        assertThat(codec.verify(minted.token(), now)).contains(SUBJECT);
        assertThat(minted.expiresAt()).isEqualTo(now.plus(NUA_GIO));
    }

    @Test
    @DisplayName("sửa một ký tự của token là hỏng — chữ ký bảo vệ cả subject lẫn hạn")
    void suaMotKyTuLaHong() {
        Instant now = Instant.now();
        String token = codec.mint(SUBJECT, NUA_GIO, now).token();
        String suaDoi = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThat(codec.verify(suaDoi, now)).isEmpty();
    }

    @Test
    @DisplayName("token của một bí mật KHÁC không kiểm được — đổi bí mật là thu hồi mọi liên kết")
    void doiBiMatLaThuHoiMoiLienKet() {
        Instant now = Instant.now();
        String token = new SetPasswordTokenCodec("mot-bi-mat-khac").mint(SUBJECT, NUA_GIO, now)
                .token();

        assertThat(codec.verify(token, now)).isEmpty();
    }

    @Test
    @DisplayName("quá hạn thì hỏng, và hỏng ĐÚNG lúc chứ không sớm")
    void quaHanThiHong() {
        Instant now = Instant.parse("2026-09-19T07:00:00Z");
        String token = codec.mint(SUBJECT, NUA_GIO, now).token();

        assertThat(codec.verify(token, now.plus(Duration.ofMinutes(29)))).contains(SUBJECT);
        assertThat(codec.verify(token, now.plus(NUA_GIO))).isEmpty();
    }

    @Test
    @DisplayName("mọi lý do hỏng trả về CÙNG một kết quả rỗng")
    void moiLyDoHongTraCungMotKetQua() {
        Instant now = Instant.now();

        // Sai dinh dang · khong phai base64 · thieu chu ky · rong · null. Phan biet chung cho nguoi
        // goi la noi cho ke do biet minh dang sai o dau.
        assertThat(codec.verify(null, now)).isEmpty();
        assertThat(codec.verify("", now)).isEmpty();
        assertThat(codec.verify("khong-co-dau-cham", now)).isEmpty();
        assertThat(codec.verify(".", now)).isEmpty();
        assertThat(codec.verify("!!!.!!!", now)).isEmpty();
    }

    @Test
    @DisplayName("token KHÔNG mang email, không mang khoá nhân khẩu — nó đi qua thanh địa chỉ")
    void tokenChiMangSubjectVaHan() {
        String token = codec.mint(SUBJECT, NUA_GIO, Instant.now()).token();
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.substring(0, token.indexOf('.'))), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(payload).startsWith(SUBJECT + ":");
        assertThat(payload).doesNotContain("@");
    }

    @Test
    @DisplayName("không dựng được codec khi chưa có bí mật — hỏng lúc khởi động, không lúc 7 giờ sáng")
    void khongCoBiMatThiKhongDungDuoc() {
        assertThatThrownBy(() -> new SetPasswordTokenCodec("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

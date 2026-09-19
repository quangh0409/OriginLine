package vn.giapha.notification.infrastructure.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.notification.infrastructure.webpush.VapidKeyGenerator.CapKhoaVapid;

/**
 * Công cụ sinh khoá phải cho ra thứ mà <b>chính đường gửi thật</b> nhận được.
 *
 * <p>Một công cụ sinh khoá "gần đúng" là ca tệ nhất: nó chạy êm, in ra hai chuỗi trông hợp lệ, và
 * lỗi chỉ lộ ra khi push service trả 401 cho mọi thiết bị — lúc đó không ai nghi ngờ cái lệnh đã
 * chạy xong từ tuần trước. Vì vậy phép kiểm ở đây không dừng ở độ dài chuỗi mà nạp khoá qua đúng
 * {@link VapidKeyMaterial#from(WebPushProperties)} mà ứng dụng dùng lúc khởi động.</p>
 */
class VapidKeyGeneratorTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    @Test
    @DisplayName("Sinh ra cap khoa dung dinh dang VAPID: 65 byte diem khong nen + 32 byte vo huong")
    void dungDinhDang() throws GeneralSecurityException {
        CapKhoaVapid cap = VapidKeyGenerator.sinhCapKhoa();

        byte[] khoaCongKhai = B64.decode(cap.publicKey());
        byte[] khoaRieng = B64.decode(cap.privateKey());

        assertThat(khoaCongKhai).hasSize(65);
        assertThat(khoaCongKhai[0]).as("diem khong nen phai bat dau bang 0x04").isEqualTo((byte) 0x04);
        assertThat(khoaRieng).as("vo huong d phai dung 32 byte, khong 31 cung khong 33").hasSize(32);
    }

    @Test
    @DisplayName("Base64url KHONG dem - PushManager.subscribe tu choi chuoi co dau '='")
    void base64urlKhongDem() throws GeneralSecurityException {
        CapKhoaVapid cap = VapidKeyGenerator.sinhCapKhoa();

        assertThat(cap.publicKey()).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(cap.privateKey()).doesNotContain("=").doesNotContain("+").doesNotContain("/");
    }

    @Test
    @DisplayName("Khoa sinh ra duoc chinh duong khoi dong that chap nhan")
    void duongKhoiDongThatChapNhan() throws GeneralSecurityException {
        CapKhoaVapid cap = VapidKeyGenerator.sinhCapKhoa();
        WebPushProperties cauHinh = new WebPushProperties();
        cauHinh.setPublicKey(cap.publicKey());
        cauHinh.setPrivateKey(cap.privateKey());

        VapidKeyMaterial khoa = VapidKeyMaterial.from(cauHinh);

        assertThat(khoa).isNotNull();
        // Khoá công khai phải khớp TỪNG KÝ TỰ với chuỗi cấu hình: nó đi vào header `k=` và vào
        // `GET /api/v1/push/public-key`; lệch một ký tự là push service từ chối.
        assertThat(khoa.publicKeyBase64Url()).isEqualTo(cap.publicKey());
    }

    @Test
    @DisplayName("Khoa sinh ra ky duoc JWT VAPID that")
    void kyDuocJwtVapid() throws GeneralSecurityException {
        CapKhoaVapid cap = VapidKeyGenerator.sinhCapKhoa();
        WebPushProperties cauHinh = new WebPushProperties();
        cauHinh.setPublicKey(cap.publicKey());
        cauHinh.setPrivateKey(cap.privateKey());

        String header = new VapidSigner().authorizationHeader(VapidKeyMaterial.from(cauHinh),
                "https://fcm.googleapis.com", "mailto:toc-truong@giapha.vn", java.time.Duration.ofHours(12));

        assertThat(header).startsWith("vapid t=").contains(", k=" + cap.publicKey());
    }

    @Test
    @DisplayName("Moi lan goi ra mot cap khoa khac - khong co khoa co dinh nao trong ma nguon")
    void moiLanMotCapKhac() throws GeneralSecurityException {
        assertThat(VapidKeyGenerator.sinhCapKhoa().privateKey())
                .isNotEqualTo(VapidKeyGenerator.sinhCapKhoa().privateKey());
    }

    @Test
    @DisplayName("Dan nham nua cap khoa thi KEU TO ngay luc khoi dong, khong doi toi ngay gio")
    void danNhamNuaCapThiNoNgayLucKhoiDong() throws GeneralSecurityException {
        CapKhoaVapid capMotNoi = VapidKeyGenerator.sinhCapKhoa();
        CapKhoaVapid capNoiKhac = VapidKeyGenerator.sinhCapKhoa();
        WebPushProperties lech = new WebPushProperties();
        lech.setPublicKey(capMotNoi.publicKey());
        lech.setPrivateKey(capNoiKhac.privateKey());

        // Cả hai chuỗi đều đúng độ dài, đúng base64url. Không có phép tự kiểm này thì ứng dụng khởi
        // động sạch, giao diện hiện công tắc bật được, và mọi lượt gửi về sau nhận 401.
        assertThatThrownBy(() -> VapidKeyMaterial.from(lech))
                .isInstanceOf(GeneralSecurityException.class)
                .hasMessageContaining("KHONG cung mot cap");
    }
}

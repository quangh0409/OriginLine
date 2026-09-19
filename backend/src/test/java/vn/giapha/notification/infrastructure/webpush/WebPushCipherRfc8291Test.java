package vn.giapha.notification.infrastructure.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Neo {@link WebPushCipher} vào <b>bộ số mẫu chính thức của RFC 8291 §5 + Phụ lục A</b>.
 *
 * <h2>Vì sao bộ số mẫu, chứ không phải vòng lặp tự mã hoá rồi tự giải mã</h2>
 * Mã hoá Web Push hỏng theo kiểu <b>im lặng tuyệt đối</b>: máy chủ đẩy trả 201, gói tin tới máy
 * người dùng, rồi trình duyệt giải mã thất bại và <i>vứt bỏ</i> — không có sự kiện {@code push},
 * không có lỗi ở client, không có lỗi ở máy chủ. Một phép thử "mã hoá rồi giải mã bằng chính mã
 * này" sẽ xanh xuyên suốt kịch bản đó. Chỉ có con số của chuẩn mới bắt được nó.
 *
 * <p>Đối chiếu ở đây là <b>từng byte của cả thân bản tin</b>. Vì thân bản tin phụ thuộc vào toàn bộ
 * chuỗi dẫn xuất (ECDH → PRK_key → IKM → PRK → CEK/NONCE → AES-GCM) nên một byte khớp cuối cùng
 * đồng nghĩa mọi giá trị trung gian ở Phụ lục A đều khớp.</p>
 */
class WebPushCipherRfc8291Test {

    // ------------------------------------------------------------------------------------
    // RFC 8291 §5 + Phu luc A — da bo xuong dong trinh bay cua ban RFC
    // ------------------------------------------------------------------------------------

    /** "When I grow up, I want to be a watermelon" — bản rõ của ví dụ. */
    private static final String RFC_PLAINTEXT = "When I grow up, I want to be a watermelon";

    private static final String RFC_AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";

    /** Trình duyệt (bên nhận). */
    private static final String RFC_UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String RFC_UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";

    /** Máy chủ ứng dụng (bên gửi) — vai của {@link WebPushCipher}. */
    private static final String RFC_AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String RFC_AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    private static final String RFC_SALT = "DGv6ra1nlYgDCS1FRnbzlw";

    /** Thân bản tin mong đợi, RFC 8291 §5. */
    private static final String RFC_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    /** Header 86 octet của RFC 8291 Phụ lục A: {@code salt(16) || rs(4) || idlen(1) || as_public(65)}. */
    private static final String RFC_HEADER =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();

    private final WebPushCipher cipher = new WebPushCipher();

    @Test
    @DisplayName("RFC 8291 §5: ma hoa ra DUNG TUNG BYTE than ban tin cua bo so mau")
    void khopTungByteVoiBoSoMauRfc() throws Exception {
        KeyPair khoaTamThoiCuaRfc = new KeyPair(
                P256.decodePublicKey(B64.decode(RFC_AS_PUBLIC)),
                P256.decodePrivateKey(B64.decode(RFC_AS_PRIVATE)));

        byte[] than = cipher.encrypt(RFC_PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                RFC_UA_PUBLIC, RFC_AUTH_SECRET, khoaTamThoiCuaRfc, B64.decode(RFC_SALT));

        assertThat(B64E.encodeToString(than))
                .as("Lech mot byte o day nghia la trinh duyet se vut goi tin trong im lang")
                .isEqualTo(RFC_BODY);
    }

    @Test
    @DisplayName("RFC 8291 Phu luc A: header 86 octet dung cau truc salt||rs||idlen||khoa")
    void headerDungCauTrucRfc8188() throws Exception {
        KeyPair khoaTamThoiCuaRfc = new KeyPair(
                P256.decodePublicKey(B64.decode(RFC_AS_PUBLIC)),
                P256.decodePrivateKey(B64.decode(RFC_AS_PRIVATE)));

        byte[] than = cipher.encrypt(RFC_PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                RFC_UA_PUBLIC, RFC_AUTH_SECRET, khoaTamThoiCuaRfc, B64.decode(RFC_SALT));

        byte[] header = new byte[86];
        System.arraycopy(than, 0, header, 0, 86);
        assertThat(B64E.encodeToString(header)).isEqualTo(RFC_HEADER);

        ByteBuffer buffer = ByteBuffer.wrap(than);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertThat(B64E.encodeToString(salt)).isEqualTo(RFC_SALT);
        assertThat(buffer.getInt()).as("rs khai bao").isEqualTo(4096);
        assertThat(Byte.toUnsignedInt(buffer.get())).as("idlen phai la 65").isEqualTo(65);
    }

    @Test
    @DisplayName("Ban giai ma trong test tu no cung dung chuan: mo duoc chinh goi tin mau cua RFC")
    void giaiMaDuocChinhGoiTinMauCuaRfc() throws Exception {
        // Phep neo cua trong tai. Khong co no thi WebPushDecryptor chi la "mot cach lam khac",
        // va viec no doc duoc san pham cua WebPushCipher khong chung minh dieu gi ve chuan.
        WebPushDecryptor trinhDuyet =
                WebPushDecryptor.tuKhoa(RFC_UA_PRIVATE, RFC_UA_PUBLIC, RFC_AUTH_SECRET);

        assertThat(trinhDuyet.giaiMaThanhChuoi(B64.decode(RFC_BODY))).isEqualTo(RFC_PLAINTEXT);
    }

    @Test
    @DisplayName("Khoa client cua RFC duoc suy nguoc dung tu khoa rieng (p256dh khop)")
    void khoaClientKhopVoiRfc() throws Exception {
        WebPushDecryptor trinhDuyet =
                WebPushDecryptor.tuKhoa(RFC_UA_PRIVATE, RFC_UA_PUBLIC, RFC_AUTH_SECRET);

        assertThat(trinhDuyet.p256dh()).isEqualTo(RFC_UA_PUBLIC);
        assertThat(trinhDuyet.auth()).isEqualTo(RFC_AUTH_SECRET);
    }

    @Nested
    @DisplayName("Duong gui that (khoa tam thoi + salt ngau nhien)")
    class DuongGuiThat {

        @Test
        @DisplayName("Trinh duyet giai ma duoc noi dung tieng Viet co dau")
        void giaiMaDuocNoiDungTiengViet() throws Exception {
            WebPushDecryptor trinhDuyet = WebPushDecryptor.thietBiMoi();
            String noiDung = "{\"title\":\"Giỗ cụ Nguyễn Văn Đệ\",\"body\":\"Còn 3 ngày\"}";

            byte[] than = cipher.encrypt(noiDung.getBytes(StandardCharsets.UTF_8),
                    trinhDuyet.p256dh(), trinhDuyet.auth());

            assertThat(trinhDuyet.giaiMaThanhChuoi(than)).isEqualTo(noiDung);
        }

        @Test
        @DisplayName("Moi tin dung mot khoa tam thoi va mot salt MOI (RFC 8291 §3.1)")
        void moiTinMotKhoaTamThoiMoi() throws Exception {
            WebPushDecryptor trinhDuyet = WebPushDecryptor.thietBiMoi();
            byte[] noiDung = "nhac gio".getBytes(StandardCharsets.UTF_8);

            byte[] mot = cipher.encrypt(noiDung, trinhDuyet.p256dh(), trinhDuyet.auth());
            byte[] hai = cipher.encrypt(noiDung, trinhDuyet.p256dh(), trinhDuyet.auth());

            // Dung lai khoa tam thoi lam mat tinh bi mat chuyen tiep giua cac tin — RFC cam.
            assertThat(mot).isNotEqualTo(hai);
            byte[] saltMot = new byte[16];
            byte[] saltHai = new byte[16];
            System.arraycopy(mot, 0, saltMot, 0, 16);
            System.arraycopy(hai, 0, saltHai, 0, 16);
            assertThat(saltMot).isNotEqualTo(saltHai);
            byte[] khoaMot = new byte[65];
            byte[] khoaHai = new byte[65];
            System.arraycopy(mot, 21, khoaMot, 0, 65);
            System.arraycopy(hai, 21, khoaHai, 0, 65);
            assertThat(khoaMot).isNotEqualTo(khoaHai);

            // ...nhung ca hai van mo duoc bang cung mot dang ky.
            assertThat(trinhDuyet.giaiMa(mot)).isEqualTo(noiDung);
            assertThat(trinhDuyet.giaiMa(hai)).isEqualTo(noiDung);
        }

        @Test
        @DisplayName("Payload rong van ra mot ban ghi hop le (chi con byte 0x02)")
        void payloadRongVanHopLe() throws Exception {
            WebPushDecryptor trinhDuyet = WebPushDecryptor.thietBiMoi();

            byte[] than = cipher.encrypt(new byte[0], trinhDuyet.p256dh(), trinhDuyet.auth());

            assertThat(trinhDuyet.giaiMa(than)).isEmpty();
        }

        @Test
        @DisplayName("Thiet bi KHAC khong mo duoc goi tin cua nguoi khac")
        void thietBiKhacKhongMoDuoc() throws Exception {
            WebPushDecryptor cuaToi = WebPushDecryptor.thietBiMoi();
            WebPushDecryptor cuaNguoiKhac = WebPushDecryptor.thietBiMoi();

            byte[] than = cipher.encrypt("bi mat dong ho".getBytes(StandardCharsets.UTF_8),
                    cuaToi.p256dh(), cuaToi.auth());

            assertThatThrownBy(() -> cuaNguoiKhac.giaiMa(than))
                    .isInstanceOf(GeneralSecurityException.class);
        }
    }

    @Nested
    @DisplayName("Khoa client hong")
    class KhoaClientHong {

        @Test
        @DisplayName("p256dh khong phai diem 65 byte -> GeneralSecurityException, khong ra rac")
        void p256dhSaiDoDai() {
            assertThatThrownBy(() -> cipher.encrypt("x".getBytes(StandardCharsets.UTF_8),
                    B64E.encodeToString(new byte[32]), "BTBZMqHH6r4Tts7J_aSIgg"))
                    .isInstanceOf(GeneralSecurityException.class)
                    .hasMessageContaining("65 byte");
        }

        @Test
        @DisplayName("RFC 8291 §7: diem KHONG nam tren duong cong P-256 bi tu choi")
        void diemNgoaiDuongCongBiTuChoi() {
            // "Failure to validate a public key can allow an attacker to extract a private key."
            // JDK tu choi o buoc ECDH (KeyFactory thi KHONG) — ca test nay neu hanh vi do lai,
            // vi neu doi sang mot provider de dai hon thi khoa rieng tam thoi co the bi rut ra.
            byte[] diem = B64.decode(RFC_UA_PUBLIC);
            diem[64] ^= 0x01;

            assertThatThrownBy(() -> cipher.encrypt("x".getBytes(StandardCharsets.UTF_8),
                    B64E.encodeToString(diem), RFC_AUTH_SECRET))
                    .isInstanceOf(GeneralSecurityException.class);
        }

        @Test
        @DisplayName("p256dh khong phai base64 -> IllegalArgumentException (adapter bat va xoa)")
        void p256dhKhongPhaiBase64() {
            assertThatThrownBy(() -> cipher.encrypt("x".getBytes(StandardCharsets.UTF_8),
                    "khong-phai-base64-!!!", RFC_AUTH_SECRET))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

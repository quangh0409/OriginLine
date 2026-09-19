package vn.giapha.notification.infrastructure.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link P256} — lớp mà mọi thứ còn lại của Web Push đứng lên trên.
 *
 * <p>Bộ số mẫu lấy từ <b>RFC 8291 Phụ lục A</b> (cặp khoá client/server) và <b>RFC 8292 §2.4</b>
 * (khoá VAPID kèm toạ độ JWK). Hai nguồn độc lập, cùng một phép mã hoá điểm không nén — đủ để
 * khẳng định định dạng chứ không chỉ khẳng định "mã này nhất quán với chính nó".</p>
 *
 * <p>Cái bẫy được canh gắt nhất ở đây là <b>đệm toạ độ</b>: {@code BigInteger.toByteArray()} trả 31
 * byte khi toạ độ nhỏ và 33 byte khi bit cao bằng 1. Nối thẳng vào là ra một điểm lệch vài byte —
 * ECDH vẫn chạy, vẫn ra một bí mật chung, và phía nhận giải mã ra rác. Không có ngoại lệ nào.</p>
 */
class P256Test {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();

    /** RFC 8291 Phụ lục A — khoá công khai của trình duyệt và của máy chủ ứng dụng. */
    private static final String RFC8291_UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String RFC8291_AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String RFC8291_AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    /** RFC 8292 §2.4 — tham số {@code k=} của header Authorization, cùng JWK tương ứng. */
    private static final String RFC8292_K =
            "BA1Hxzyi1RUM1b5wjxsn7nGxAszw2u61m164i3MrAIxHF6YK5h4SDYic-dRuU_RCPCfA5aq9ojSwk5Y2EmClBPs";
    private static final String RFC8292_JWK_X = "DUfHPKLVFQzVvnCPGyfucbECzPDa7rWbXriLcysAjEc";
    private static final String RFC8292_JWK_Y = "F6YK5h4SDYic-dRuU_RCPCfA5aq9ojSwk5Y2EmClBPs";

    @Test
    @DisplayName("RFC 8291: giai ma roi ma hoa lai khoa cong khai ra dung chuoi ban dau")
    void khoaCongKhaiRfc8291KhopVongTron() throws Exception {
        for (String khoa : new String[] {RFC8291_UA_PUBLIC, RFC8291_AS_PUBLIC}) {
            ECPublicKey decoded = P256.decodePublicKey(B64.decode(khoa));
            assertThat(B64E.encodeToString(P256.encodePublicKey(decoded))).isEqualTo(khoa);
        }
    }

    @Test
    @DisplayName("RFC 8292 §2.4: diem khong nen bang 0x04 || x || y cua JWK")
    void diemKhongNenGhepDungTuJwk() throws Exception {
        ECPublicKey decoded = P256.decodePublicKey(B64.decode(RFC8292_K));

        byte[] point = P256.encodePublicKey(decoded);
        assertThat(point).hasSize(65);
        assertThat(point[0]).isEqualTo((byte) 0x04);

        byte[] x = new byte[32];
        byte[] y = new byte[32];
        System.arraycopy(point, 1, x, 0, 32);
        System.arraycopy(point, 33, y, 0, 32);
        assertThat(B64E.encodeToString(x)).isEqualTo(RFC8292_JWK_X);
        assertThat(B64E.encodeToString(y)).isEqualTo(RFC8292_JWK_Y);
    }

    @Test
    @DisplayName("RFC 8291: khoa rieng 32 byte cua bo so mau la cap voi khoa cong khai cua no")
    void khoaRiengVaKhoaCongKhaiCuaRfcLaMotCap() throws Exception {
        ECPrivateKey khoaRieng = P256.decodePrivateKey(B64.decode(RFC8291_AS_PRIVATE));
        ECPublicKey khoaCongKhai = P256.decodePublicKey(B64.decode(RFC8291_AS_PUBLIC));

        // Ky bang khoa rieng roi kiem bang khoa cong khai: neu decodePrivateKey hieu sai vo huong
        // (vi du doc nham dau, hoac khong dung BigInteger duong) thi phep kiem nay se hong.
        byte[] thongDiep = "kiem tra cap khoa".getBytes(StandardCharsets.US_ASCII);
        Signature ky = Signature.getInstance("SHA256withECDSA");
        ky.initSign(khoaRieng);
        ky.update(thongDiep);
        byte[] chuKy = ky.sign();

        Signature kiem = Signature.getInstance("SHA256withECDSA");
        kiem.initVerify(khoaCongKhai);
        kiem.update(thongDiep);
        assertThat(kiem.verify(chuKy)).isTrue();
    }

    @Test
    @DisplayName("Dem toa do dung ca khi BigInteger tra 33 byte (bit cao = 1) lan khi tra < 32 byte")
    void demToaDoDungOCaHaiTruongHopBien() throws Exception {
        // Sinh khoa that cho toi khi cham duoc ca hai truong hop bien. Bit cao bang 1 xay ra khoang
        // mot nua so lan; toa do < 2^248 (toByteArray tra <= 31 byte) khoang 1/256 so lan.
        boolean chamTruongHop33Byte = false;
        boolean chamTruongHopNgan = false;
        for (int lan = 0; lan < 4000 && !(chamTruongHop33Byte && chamTruongHopNgan); lan++) {
            KeyPair pair = P256.generateEphemeralKeyPair();
            ECPublicKey khoa = (ECPublicKey) pair.getPublic();
            byte[] point = P256.encodePublicKey(khoa);

            assertThat(point).hasSize(65);
            assertThat(point[0]).isEqualTo((byte) 0x04);
            // Vong tron chi khop khi ca hai toa do duoc dem dung 32 byte, can phai.
            assertThat(P256.decodePublicKey(point).getW()).isEqualTo(khoa.getW());

            for (BigInteger toaDo : new BigInteger[] {khoa.getW().getAffineX(), khoa.getW().getAffineY()}) {
                int doDaiTho = toaDo.toByteArray().length;
                chamTruongHop33Byte |= doDaiTho == 33;
                chamTruongHopNgan |= doDaiTho <= 31;
            }
        }

        assertThat(chamTruongHop33Byte)
                .as("Phai cham duoc toa do co byte dau 0x00 cua so duong — nhanh cat byte dau")
                .isTrue();
        assertThat(chamTruongHopNgan)
                .as("Phai cham duoc toa do ngan hon 32 byte — nhanh can phai")
                .isTrue();
    }

    @Test
    @DisplayName("Diem sai do dai bi tu choi kem thong diep noi ro do dai nhan duoc")
    void diemSaiDoDaiBiTuChoi() {
        assertThatThrownBy(() -> P256.decodePublicKey(new byte[64]))
                .isInstanceOf(GeneralSecurityException.class)
                .hasMessageContaining("65 byte")
                .hasMessageContaining("64 byte");
    }

    @Test
    @DisplayName("Diem dang nen (0x02/0x03) bi tu choi — trinh duyet khong bao gio gui dang nay")
    void diemDangNenBiTuChoi() {
        byte[] nen = new byte[65];
        nen[0] = 0x03;

        assertThatThrownBy(() -> P256.decodePublicKey(nen))
                .isInstanceOf(GeneralSecurityException.class)
                .hasMessageContaining("0x04");
    }

    @Test
    @DisplayName("base64url: chap nhan ca padding lan bien the base64 chuan (+ va /)")
    void base64UrlChapNhanMoiBienThe() {
        byte[] mong = B64.decode(RFC8291_UA_PUBLIC);

        assertThat(P256.decodeBase64Url(RFC8291_UA_PUBLIC)).isEqualTo(mong);
        assertThat(P256.decodeBase64Url(RFC8291_UA_PUBLIC + "=")).isEqualTo(mong);
        assertThat(P256.decodeBase64Url("  " + RFC8291_UA_PUBLIC + "  ")).isEqualTo(mong);
        // Mot so client (va nguoi copy tay tu console) gui base64 chuan thay vi base64url.
        assertThat(P256.decodeBase64Url(Base64.getEncoder().encodeToString(mong))).isEqualTo(mong);
    }

    @Test
    @DisplayName("encodeBase64Url khong bao gio kem '=' hay '+' — header VAPID k= khong chiu duoc")
    void khongCoPaddingTrongDauRa() throws Exception {
        for (int lan = 0; lan < 20; lan++) {
            String encoded = P256.encodeBase64Url(P256.encodePublicKey(
                    (ECPublicKey) P256.generateEphemeralKeyPair().getPublic()));
            assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        }
    }
}

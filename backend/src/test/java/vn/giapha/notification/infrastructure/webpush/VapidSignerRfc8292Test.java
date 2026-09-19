package vn.giapha.notification.infrastructure.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link VapidSigner} đối chiếu với <b>RFC 8292 §2.4</b> — ví dụ JWT thật kèm khoá công khai.
 *
 * <h2>Vì sao ví dụ của RFC lại kiểm được gì đó, dù chữ ký ECDSA là ngẫu nhiên</h2>
 * Không tái tạo được chữ ký của RFC (ECDSA có tham số ngẫu nhiên {@code k}), nhưng <b>kiểm</b> được
 * nó. Và phép kiểm ấy chốt đúng cái bẫy nguy hiểm nhất của tầng này: JOSE cần chữ ký
 * {@code R||S} thô 64 byte, còn {@code SHA256withECDSA} mặc định của JDK trả DER. Chọn nhầm thì
 * máy chủ đẩy trả 401 và không một thông điệp lỗi nào nhắc tới định dạng chữ ký. Ở đây token thật
 * của RFC được kiểm bằng đúng thuật toán mà {@code VapidSigner} dùng — và được chứng minh là
 * <i>không</i> kiểm được bằng DER.
 *
 * <p>Ngoài ra header JWT của RFC được so <b>từng byte</b> với chuỗi hằng trong
 * {@code VapidSigner}, và thứ tự claim {@code aud, exp, sub} của ví dụ RFC cũng đúng thứ tự mà
 * signer sinh ra.</p>
 */
class VapidSignerRfc8292Test {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper();

    /** RFC 8292 §2.4, tham số {@code t=} (đã bỏ xuống dòng trình bày). */
    private static final String RFC_JWT =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9"
            + ".eyJhdWQiOiJodHRwczovL3B1c2guZXhhbXBsZS5uZXQiLCJleHAiOjE0NTM1MjM3NjgsInN1YiI6"
            + "Im1haWx0bzpwdXNoQGV4YW1wbGUuY29tIn0"
            + ".i3CYb7t4xfxCDquptFOepC9GAu_HLGkMlMuCGSK2rpiUfnK9ojFwDXb1JrErtmysazNjjvW2L9Ok"
            + "SSHzvoD1oA";

    /** RFC 8292 §2.4, tham số {@code k=}. */
    private static final String RFC_K =
            "BA1Hxzyi1RUM1b5wjxsn7nGxAszw2u61m164i3MrAIxHF6YK5h4SDYic-dRuU_RCPCfA5aq9ojSwk5Y2EmClBPs";

    private final VapidSigner signer = new VapidSigner();

    private VapidKeyMaterial khoa;
    private ECPublicKey khoaCongKhai;

    @BeforeEach
    void sinhCapKhoaVapidThat() throws Exception {
        // Cap khoa VAPID that, sinh moi cho moi ca test. KHONG commit khoa nao vao repo va khong
        // dat vao application.yml — day dung la thu ma WebPushPropertiesTest dang canh.
        KeyPair pair = P256.generateEphemeralKeyPair();
        khoaCongKhai = (ECPublicKey) pair.getPublic();
        khoa = new VapidKeyMaterial((ECPrivateKey) pair.getPrivate(),
                P256.encodeBase64Url(P256.encodePublicKey(khoaCongKhai)));
    }

    // ------------------------------------------------------------------------------------
    // Bo so mau RFC 8292
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("RFC 8292 §2.4: token that cua RFC kiem duoc bang ES256 dinh dang P1363")
    void tokenCuaRfcKiemDuocBangP1363() throws Exception {
        String[] phan = RFC_JWT.split("[.]");
        byte[] chuKy = B64.decode(phan[2]);

        assertThat(chuKy).as("ES256 cho JOSE la R||S tho 64 byte").hasSize(64);

        Signature kiem = Signature.getInstance("SHA256withECDSAinP1363Format");
        kiem.initVerify(P256.decodePublicKey(B64.decode(RFC_K)));
        kiem.update((phan[0] + "." + phan[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(kiem.verify(chuKy)).isTrue();
    }

    @Test
    @DisplayName("Cung token do KHONG kiem duoc bang SHA256withECDSA (DER) — bay 401 kinh dien")
    void tokenCuaRfcKhongKiemDuocBangDer() throws Exception {
        String[] phan = RFC_JWT.split("[.]");

        Signature der = Signature.getInstance("SHA256withECDSA");
        der.initVerify(P256.decodePublicKey(B64.decode(RFC_K)));
        der.update((phan[0] + "." + phan[1]).getBytes(StandardCharsets.US_ASCII));

        // Neu ai do bo hau to `inP1363Format` di, day la ca test giai thich tai sao khong duoc.
        assertThatThrownBy(() -> der.verify(B64.decode(phan[2])))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("Header JWT cua signer trung TUNG BYTE voi header trong vi du RFC 8292")
    void headerJwtTrungTungByteVoiRfc() {
        String header = signer.authorizationHeader(khoa, "https://push.example.net",
                "mailto:push@example.com", Duration.ofHours(12));

        String phanDauCuaTa = jwt(header).split("[.]")[0];
        String phanDauCuaRfc = RFC_JWT.split("[.]")[0];

        assertThat(phanDauCuaTa).isEqualTo(phanDauCuaRfc);
        assertThat(new String(B64.decode(phanDauCuaTa), StandardCharsets.UTF_8))
                .isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
    }

    @Test
    @DisplayName("Thu tu claim aud/exp/sub trung voi vi du RFC 8292")
    void thuTuClaimTrungVoiRfc() throws Exception {
        String header = signer.authorizationHeader(khoa, "https://push.example.net",
                "mailto:push@example.com", Duration.ofHours(12));

        String payloadCuaTa = new String(B64.decode(jwt(header).split("[.]")[1]), StandardCharsets.UTF_8);
        String payloadCuaRfc = new String(B64.decode(RFC_JWT.split("[.]")[1]), StandardCharsets.UTF_8);

        long exp = JSON.readTree(payloadCuaTa).get("exp").asLong();
        assertThat(payloadCuaTa).isEqualTo(payloadCuaRfc.replace("1453523768", Long.toString(exp)));
    }

    // ------------------------------------------------------------------------------------
    // Token do chinh he thong ky
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("Token tu ky kiem duoc bang chinh khoa cong khai trong tham so k=")
    void tokenTuKyKiemDuocBangKhoaTrongHeader() throws Exception {
        String header = signer.authorizationHeader(khoa, "https://fcm.googleapis.com",
                "mailto:admin@giapha.vn", Duration.ofHours(12));

        assertThat(header).startsWith("vapid t=").contains(", k=");
        String k = header.substring(header.indexOf(", k=") + 4);
        assertThat(k).isEqualTo(khoa.publicKeyBase64Url());

        String[] phan = jwt(header).split("[.]");
        assertThat(B64.decode(phan[2])).hasSize(64);

        // Kiem bang khoa doc nguoc tu chinh tham so k= — dung thu ma may chu day se lam.
        Signature kiem = Signature.getInstance("SHA256withECDSAinP1363Format");
        kiem.initVerify(P256.decodePublicKey(P256.decodeBase64Url(k)));
        kiem.update((phan[0] + "." + phan[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(kiem.verify(B64.decode(phan[2]))).isTrue();
    }

    @Test
    @DisplayName("Doi mot byte cua signing input thi chu ky khong con hop le")
    void chuKyGanChatVaoNoiDung() throws Exception {
        String header = signer.authorizationHeader(khoa, "https://fcm.googleapis.com",
                "mailto:admin@giapha.vn", Duration.ofHours(12));
        String[] phan = jwt(header).split("[.]");

        Signature kiem = Signature.getInstance("SHA256withECDSAinP1363Format");
        kiem.initVerify(khoaCongKhai);
        // Doi audience: dung kich ban "ai do sua claim aud tren duong truyen".
        kiem.update((phan[0] + "." + B64E.encodeToString(
                "{\"aud\":\"https://ke-tan-cong.example\",\"exp\":1,\"sub\":\"x\"}"
                        .getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII));

        assertThat(kiem.verify(B64.decode(phan[2]))).isFalse();
    }

    @Test
    @DisplayName("Claim aud la GOC cua endpoint, sub dung nguyen van, exp trong tuong lai va < 24h")
    void baClaimDungChuan() throws Exception {
        Instant truoc = Instant.now();

        String header = signer.authorizationHeader(khoa, "https://updates.push.services.mozilla.com",
                "mailto:admin@giapha.vn", Duration.ofHours(12));

        JsonNode payload = JSON.readTree(B64.decode(jwt(header).split("[.]")[1]));
        assertThat(payload.get("aud").asText()).isEqualTo("https://updates.push.services.mozilla.com");
        assertThat(payload.get("sub").asText()).isEqualTo("mailto:admin@giapha.vn");

        long exp = payload.get("exp").asLong();
        assertThat(exp).isGreaterThan(truoc.getEpochSecond());
        // RFC 8292 §2: exp KHONG duoc qua 24 gio ke tu bay gio, neu khong may chu day tra 400.
        assertThat(exp).isLessThanOrEqualTo(truoc.plus(Duration.ofHours(24)).getEpochSecond());
        assertThat(exp).isCloseTo(truoc.plus(Duration.ofHours(12)).getEpochSecond(),
                org.assertj.core.data.Offset.offset(5L));
    }

    @Test
    @DisplayName("Moi lan goi ky mot JWT MOI (khong dung lai token cu)")
    void moiLanGoiKyTokenMoi() {
        String mot = signer.authorizationHeader(khoa, "https://fcm.googleapis.com", "mailto:a@b.vn",
                Duration.ofHours(12));
        String hai = signer.authorizationHeader(khoa, "https://fcm.googleapis.com", "mailto:a@b.vn",
                Duration.ofHours(12));

        // ECDSA ngau nhien hoa: hai chu ky khac nhau ngay ca khi signing input giong het.
        assertThat(mot).isNotEqualTo(hai);
    }

    @Test
    @DisplayName("Chua cau hinh khoa -> tra null, KHONG ngoai le (kenh Web Push tu tat)")
    void chuaCoKhoaThiTraNull() {
        assertThat(signer.authorizationHeader(null, "https://fcm.googleapis.com", "mailto:a@b.vn",
                Duration.ofHours(12))).isNull();
    }

    @Test
    @DisplayName("Dau nhay kep trong subject bi thoat, JWT van la JSON hop le")
    void thoatChuoiTrongClaim() throws Exception {
        String header = signer.authorizationHeader(khoa, "https://fcm.googleapis.com",
                "mailto:\"admin\"@giapha.vn", Duration.ofHours(12));

        JsonNode payload = JSON.readTree(B64.decode(jwt(header).split("[.]")[1]));
        assertThat(payload.get("sub").asText()).isEqualTo("mailto:\"admin\"@giapha.vn");
    }

    @Test
    @DisplayName("JWT khong chua padding base64 — dau '=' lam hong header Authorization")
    void khongCoPaddingTrongJwt() {
        String header = signer.authorizationHeader(khoa, "https://fcm.googleapis.com",
                "mailto:admin@giapha.vn", Duration.ofHours(12));

        assertThat(header).doesNotContain("=,").doesNotContain("==");
        assertThat(jwt(header)).doesNotContain("=");
    }

    private static String jwt(String authorizationHeader) {
        int k = authorizationHeader.indexOf(", k=");
        return authorizationHeader.substring("vapid t=".length(), k);
    }
}

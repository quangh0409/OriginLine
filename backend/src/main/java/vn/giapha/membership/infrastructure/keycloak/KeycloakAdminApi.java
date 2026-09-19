package vn.giapha.membership.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.PasswordRejectedException;

/**
 * Lớp vỏ mỏng quanh <b>Keycloak Admin REST API</b>: lấy token cho tài khoản dịch vụ rồi gọi đúng
 * năm đường mà luồng mời cần.
 *
 * <h2>Năm đường, không hơn</h2>
 * Tìm người dùng theo email · tạo người dùng · đọc danh sách credential · đặt mật khẩu ·
 * gỡ yêu cầu bắt buộc {@code UPDATE_PASSWORD}. Cả năm đều nằm trong quyền
 * {@code realm-management:manage-users}; không đường nào cần {@code realm-admin}.
 *
 * <h2>Token của tài khoản dịch vụ được nhớ lại, và hết hạn SỚM hơn thật</h2>
 * Xin token mới cho mỗi lời gọi là ba lượt HTTP cho một lần bấm nút. Bộ nhớ đệm ở đây trừ hao
 * {@link #EXPIRY_MARGIN} giây trước hạn thật: một token còn đúng một giây khi rời khỏi lớp này thì
 * gần như chắc chắn đã hết hạn khi Keycloak đọc nó, và triệu chứng là một lỗi 401 ngẫu nhiên rất
 * khó truy.
 *
 * <p><b>Bí mật của client dịch vụ không bao giờ đi vào log.</b> Thân của mọi phản hồi lỗi được ghi
 * lại, nhưng thân của yêu cầu lấy token thì không — nó chứa {@code client_secret}.</p>
 */
public class KeycloakAdminApi {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminApi.class);

    /** Trừ hao trước hạn thật của token, tính bằng giây. */
    private static final long EXPIRY_MARGIN = 30L;

    private static final int HTTP_OK = 200;
    private static final int HTTP_CREATED = 201;
    private static final int HTTP_NO_CONTENT = 204;
    private static final int HTTP_CONFLICT = 409;

    private final KeycloakAdminProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public KeycloakAdminApi(KeycloakAdminProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    // -------------------------------------------------------------------------------------
    // Người dùng
    // -------------------------------------------------------------------------------------

    /**
     * Tìm chính xác một người dùng theo email.
     *
     * <p>Dùng {@code exact=true}: không có nó, Keycloak tìm theo tiền tố và
     * {@code lan@example.com} sẽ khớp cả {@code lan@example.com.vn} — tức ghép nhầm tài khoản của
     * người khác vào một nhân khẩu.</p>
     */
    public Optional<ObjectNode> findUserByEmail(String email) {
        String uri = properties.adminRealmUri() + "/users?exact=true&max=2&email="
                + URLEncoder.encode(email, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(get(uri), "tim nguoi dung theo email");
        requireStatus(response, "tim nguoi dung theo email", HTTP_OK);
        ArrayNode found = (ArrayNode) readTree(response.body());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (found.size() > 1) {
            // realm dat duplicateEmailsAllowed=false nen day la mot trang thai khong the co; neu no
            // xay ra thi realm da bi doi cau hinh va viec ghep tai khoan khong con xac dinh duoc.
            throw new IdentityProviderException(
                    "Realm tra ve nhieu hon mot tai khoan cho cung mot email");
        }
        return Optional.of((ObjectNode) found.get(0));
    }

    public Optional<ObjectNode> findUserById(String userId) {
        HttpResponse<String> response = send(get(properties.adminRealmUri() + "/users/" + userId),
                "doc nguoi dung");
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        requireStatus(response, "doc nguoi dung", HTTP_OK);
        return Optional.of((ObjectNode) readTree(response.body()));
    }

    /**
     * Tạo người dùng <b>không có credential</b>, kèm yêu cầu bắt buộc {@code UPDATE_PASSWORD}.
     *
     * <p>Trả về {@code id} đọc từ header {@code Location}. Keycloak trả {@code 409} khi email hoặc
     * tên đăng nhập trùng — lúc ấy lớp trên đọc lại bằng {@link #findUserByEmail}, đúng như
     * {@code AppUserProvisioningService} xử lý đua tranh ở lần đăng nhập đầu.</p>
     */
    public String createUser(String username, String email, String firstName, String lastName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", username);
        body.put("email", email);
        body.put("enabled", true);
        // emailVerified=false: khong ai chung minh dia chi nay la cua ho. Nguoi duoc moi da duoc
        // Truong chi chi dich danh trong pha, do la bang chung khac va manh hon.
        body.put("emailVerified", false);
        if (firstName != null && !firstName.isBlank()) {
            body.put("firstName", firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            body.put("lastName", lastName);
        }
        ArrayNode actions = body.putArray("requiredActions");
        actions.add(KeycloakRequiredActions.UPDATE_PASSWORD);
        // KHONG co truong "credentials": tai khoan sinh ra khong co mat khau nao. Do la ca dieu
        // kien de phat lien ket dat mat khau, va la cach he thong nay khong bao gio biet mat khau
        // cua ai.

        HttpResponse<String> response = send(
                request(properties.adminRealmUri() + "/users")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(),
                                StandardCharsets.UTF_8)),
                "tao nguoi dung");
        if (response.statusCode() == HTTP_CONFLICT) {
            throw new KeycloakUserAlreadyExistsException(
                    "Realm da co tai khoan trung email hoac ten dang nhap");
        }
        requireStatus(response, "tao nguoi dung", HTTP_CREATED);
        return response.headers().firstValue("Location")
                .map(location -> location.substring(location.lastIndexOf('/') + 1))
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new IdentityProviderException(
                        "Keycloak tao nguoi dung nhung khong tra header Location"));
    }

    // -------------------------------------------------------------------------------------
    // Credential
    // -------------------------------------------------------------------------------------

    /** Tài khoản đã có credential loại {@code password} chưa. */
    public boolean hasPasswordCredential(String userId) {
        HttpResponse<String> response = send(
                get(properties.adminRealmUri() + "/users/" + userId + "/credentials"),
                "doc credential");
        requireStatus(response, "doc credential", HTTP_OK);
        for (JsonNode credential : readTree(response.body())) {
            if ("password".equals(credential.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ghi mật khẩu <b>người dùng tự chọn</b>, {@code temporary: false}.
     *
     * <p>{@code temporary: true} sẽ bắt họ đổi lại ngay ở lần đăng nhập kế tiếp — vô nghĩa với một
     * mật khẩu do chính họ vừa đặt.</p>
     */
    public void setPassword(String userId, String rawPassword) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "password");
        body.put("value", rawPassword);
        body.put("temporary", false);
        HttpResponse<String> response = send(
                request(properties.adminRealmUri() + "/users/" + userId + "/reset-password")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body.toString(),
                                StandardCharsets.UTF_8)),
                "dat mat khau");
        if (response.statusCode() == 400) {
            // Vi pham chinh sach mat khau cua realm. Thong diep cua Keycloak da duoc dich sang
            // ngon ngu nguoi dung o day, nen chuyen nguyen van len tren.
            throw new PasswordRejectedException(errorMessageOf(response.body()));
        }
        requireStatus(response, "dat mat khau", HTTP_NO_CONTENT);
    }

    /**
     * Gỡ {@code UPDATE_PASSWORD} khỏi danh sách yêu cầu bắt buộc.
     *
     * <p>Không gỡ thì Keycloak vẫn chặn người dùng ở màn "đặt lại mật khẩu" ngay sau khi họ vừa đặt
     * mật khẩu xong — một vòng lặp mà người dùng không hiểu nổi.</p>
     */
    public void clearUpdatePasswordAction(String userId) {
        ObjectNode user = findUserById(userId).orElseThrow(() -> new IdentityProviderException(
                "Khong tim thay tai khoan " + userId + " de go yeu cau doi mat khau"));
        ArrayNode remaining = objectMapper.createArrayNode();
        for (JsonNode action : user.path("requiredActions")) {
            if (!KeycloakRequiredActions.UPDATE_PASSWORD.equals(action.asText())) {
                remaining.add(action.asText());
            }
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.set("requiredActions", remaining);
        HttpResponse<String> response = send(
                request(properties.adminRealmUri() + "/users/" + userId)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body.toString(),
                                StandardCharsets.UTF_8)),
                "go yeu cau doi mat khau");
        requireStatus(response, "go yeu cau doi mat khau", HTTP_NO_CONTENT);
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    private HttpRequest.Builder get(String uri) {
        return request(uri).GET();
    }

    private HttpRequest.Builder request(String uri) {
        return HttpRequest.newBuilder(URI.create(uri))
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + serviceAccountToken());
    }

    private HttpResponse<String> send(HttpRequest.Builder builder, String what) {
        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IdentityProviderException("Khong goi duoc Keycloak khi " + what, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IdentityProviderException("Bi ngat khi " + what, ex);
        }
    }

    private void requireStatus(HttpResponse<String> response, String what, int expected) {
        if (response.statusCode() != expected) {
            log.warn("Keycloak tra {} khi {} — than: {}", response.statusCode(), what,
                    response.body());
            throw new IdentityProviderException(
                    "Keycloak tra " + response.statusCode() + " khi " + what);
        }
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IdentityProviderException("Khong doc duoc phan hoi cua Keycloak", ex);
        }
    }

    /**
     * Cau tu choi mat khau, noi RO PHAI SUA GI.
     *
     * <h2>Vi sao doc {@code error} chu khong phai {@code errorMessage}</h2>
     * Keycloak 26 tra ve {@code {"error":"invalidPasswordMinLengthMessage",
     * "error_description":"Invalid password: minimum length 8."}} o endpoint {@code reset-password}
     * — <b>khong co truong {@code errorMessage}</b>. Ban dau lop nay doc {@code errorMessage}, nen
     * no LUON roi ve cau chung chung "Mat khau khong hop le", va nguoi dung khong bao gio biet
     * phai sua gi. Do dung la cau te nhat co the noi voi mot cu 70 tuoi dang lan dau dat mat khau.
     *
     * <p>Khoa {@code error} la <b>on dinh</b> va dich duoc; {@code error_description} la tieng Anh
     * do Keycloak sinh. Nen ta dich theo khoa, va chi dung {@code error_description} lam luoi do
     * cho nhung luat chinh sach chua duoc liet ke o day.</p>
     */
    private String errorMessageOf(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String key = node.path("error").asText("");
            String viet = vietHoaLoiMatKhau(key);
            if (viet != null) {
                return viet;
            }
            String moTa = node.path("error_description").asText(null);
            if (moTa == null || moTa.isBlank()) {
                moTa = node.path("errorMessage").asText(null);
            }
            return moTa == null || moTa.isBlank() ? "Mat khau khong hop le" : moTa;
        } catch (IOException ex) {
            return "Mat khau khong hop le";
        }
    }

    /** {@code null} khi chua biet khoa ay — de ben goi roi ve {@code error_description}. */
    private static String vietHoaLoiMatKhau(String key) {
        return switch (key) {
            case "invalidPasswordMinLengthMessage" ->
                    "Mat khau phai dai it nhat 8 ky tu. Hay them vai chu nua.";
            case "invalidPasswordMinDigitsMessage" -> "Mat khau phai co it nhat mot chu so.";
            case "invalidPasswordMinLowerCaseCharsMessage" -> "Mat khau phai co it nhat mot chu thuong.";
            case "invalidPasswordMinUpperCaseCharsMessage" -> "Mat khau phai co it nhat mot chu hoa.";
            case "invalidPasswordMinSpecialCharsMessage" ->
                    "Mat khau phai co it nhat mot ky tu dac biet, vi du ! hoac #.";
            case "invalidPasswordNotUsernameMessage" ->
                    "Mat khau khong duoc trung voi ten dang nhap. Hay chon chuoi khac.";
            case "invalidPasswordNotEmailMessage" ->
                    "Mat khau khong duoc trung voi dia chi email. Hay chon chuoi khac.";
            case "invalidPasswordHistoryMessage" ->
                    "Mat khau nay da dung truoc day. Hay chon mot mat khau chua tung dung.";
            case "invalidPasswordBlacklistedMessage" ->
                    "Mat khau nay qua pho bien nen de bi doan. Hay chon chuoi khac.";
            case "invalidPasswordRegexPatternMessage" ->
                    "Mat khau chua dung dang ma dong ho quy dinh.";
            default -> null;
        };
    }

    /** Token {@code client_credentials} của tài khoản dịch vụ, có nhớ đệm. */
    private String serviceAccountToken() {
        String token = cachedToken;
        if (token != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return token;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
                return cachedToken;
            }
            String form = "grant_type=client_credentials"
                    + "&client_id=" + URLEncoder.encode(properties.getClientId(),
                            StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(
                            String.valueOf(properties.getClientSecret()), StandardCharsets.UTF_8);
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(properties.tokenUri()))
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response;
            try {
                response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException ex) {
                throw new IdentityProviderException("Khong lay duoc token tai khoan dich vu", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IdentityProviderException("Bi ngat khi lay token tai khoan dich vu", ex);
            }
            if (response.statusCode() != HTTP_OK) {
                // KHONG log than yeu cau: no chua client_secret.
                throw new IdentityProviderException("Keycloak tu choi cap token cho client "
                        + properties.getClientId() + " (HTTP " + response.statusCode() + ")");
            }
            JsonNode body = readTree(response.body());
            String fresh = body.path("access_token").asText(null);
            if (fresh == null || fresh.isBlank()) {
                throw new IdentityProviderException("Phan hoi token khong co access_token");
            }
            long expiresIn = body.path("expires_in").asLong(60L);
            cachedToken = fresh;
            cachedTokenExpiry = Instant.now()
                    .plus(Duration.ofSeconds(Math.max(expiresIn - EXPIRY_MARGIN, 5L)));
            return fresh;
        }
    }
}

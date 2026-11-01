package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.giapha.TestcontainersConfiguration;

/**
 * <b>Header {@code X-Forwarded-For} do client tự bịa không được lách bộ đếm tần suất.</b>
 *
 * <h2>Lỗi mà lớp này ghim</h2>
 * Với {@code server.forward-headers-strategy: framework}, {@code ForwardedHeaderFilter} của Spring
 * tin {@code X-Forwarded-For} của MỌI request và viết đè {@code getRemoteAddr()} — mà
 * {@code getRemoteAddr()} là khoá của {@code InviteThrottle}. Đo trên hệ đang chạy ngày 24/09: sau
 * mười lần gõ sai mã dòng họ, {@code POST /clan-invites/lookup} trả 429; cùng lệnh ấy kèm một
 * {@code X-Forwarded-For} bịa ra trả 404 — kẻ dò mã chỉ cần đổi header sau mỗi lượt.
 *
 * <h2>Vì sao phải là cổng thật ({@code RANDOM_PORT}), không phải MockMvc</h2>
 * Bản sửa là {@code native}: {@code RemoteIpValve} của Tomcat, chỉ tin header khi request đến từ
 * một proxy nằm trong {@code server.tomcat.remoteip.internal-proxies}. Valve chạy trong Tomcat,
 * không chạy trong MockMvc — một bài kiểm MockMvc sẽ xanh với cả cấu hình sai.
 *
 * <p>Ở đây {@code internal-proxies} được đặt RỖNG: bài kiểm gọi từ 127.0.0.1, mà loopback nằm
 * trong danh sách mặc định (đúng cho nginx đặt cùng máy). Rỗng nghĩa là "không proxy nào đáng
 * tin", nên mọi {@code X-Forwarded-For} phải bị bỏ qua. Nếu ai đó đưa chiến lược về
 * {@code framework}, thuộc tính này không còn tác dụng gì, header lại được tin, và lượt thứ mười một
 * trả 404 thay vì 429 — tức bài kiểm đỏ đúng chỗ.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=")
@Import(TestcontainersConfiguration.class)
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
@DisplayName("X-Forwarded-For bịa ra không lách được giới hạn tần suất")
class ForwardedHeaderTrustIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void donBoDem() {
        jdbc.execute("TRUNCATE TABLE invitation_attempt RESTART IDENTITY");
    }

    private int traMaSai(String xForwardedFor) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v1/clan-invites/lookup"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", xForwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"ZZZZZ-ZZZZZ\"}"))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    @DisplayName("mười lần gõ sai, mỗi lần một IP giả → lần thứ mười một vẫn bị chặn 429")
    void doiHeaderKhongLamMoiBoDem() throws Exception {
        // Mac dinh `max-failed-attempts` = 10 trong 60 phut.
        for (int i = 1; i <= 10; i++) {
            assertThat(traMaSai("203.0.113." + i)).as("lan go sai thu %d", i).isEqualTo(404);
        }
        assertThat(traMaSai("198.51.100.99"))
                .as("mot IP gia moi khong duoc mo lai cua — bo dem phai tinh theo dia chi that")
                .isEqualTo(429);
    }
}

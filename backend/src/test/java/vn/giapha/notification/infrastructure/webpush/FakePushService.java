package vn.giapha.notification.infrastructure.webpush;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <b>Máy chủ đẩy giả</b> — một HTTP server thật, nghe trên cổng ngẫu nhiên của localhost.
 *
 * <h2>Vì sao không mock {@code HttpClient}</h2>
 * {@link WebPushAdapter} tự dựng {@code HttpClient} bên trong constructor, nên mock nó đòi hỏi phải
 * chọc thủng thiết kế. Quan trọng hơn: mock chỉ chứng minh được "adapter gọi một hàm nào đó". Thứ
 * cần chứng minh là <b>gói tin đi ra dây có mở được ở phía nhận hay không</b> — và điều đó chỉ kiểm
 * được khi có một bên nhận thật, đọc byte thật từ socket, rồi giải mã.
 *
 * <p>Đây là bằng chứng mạnh nhất có thể có mà không cần một trình duyệt thật: mọi thứ giữa
 * {@code NotificationMessage} và ổ cắm mạng đều là mã sản xuất.</p>
 */
public final class FakePushService implements AutoCloseable {

    /** Một lượt POST mà máy chủ đẩy nhận được. */
    public record LuotNhan(String duongDan, Map<String, String> header, byte[] than) {

        public String headerHoac(String ten, String macDinh) {
            String value = header.get(ten.toLowerCase(Locale.ROOT));
            return value == null ? macDinh : value;
        }
    }

    private final HttpServer server;
    private final List<LuotNhan> daNhan = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> maTheoDuongDan = new HashMap<>();

    private volatile int maMacDinh = 201;
    private volatile String thanPhanHoi = "";

    private FakePushService(HttpServer server) {
        this.server = server;
    }

    public static FakePushService khoiDong() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        FakePushService fake = new FakePushService(server);
        server.createContext("/", fake::xuLy);
        server.setExecutor(null);
        server.start();
        return fake;
    }

    /** Endpoint đúng hình dạng thật của một push service (có đường dẫn định danh riêng). */
    public String endpoint(String dinhDanh) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/push/" + dinhDanh;
    }

    /** Gốc của endpoint — chính là giá trị claim {@code aud} mà JWT VAPID phải mang. */
    public String audience() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void traVe(int ma) {
        this.maMacDinh = ma;
    }

    public void traVe(int ma, String than) {
        this.maMacDinh = ma;
        this.thanPhanHoi = than;
    }

    /** Mã trả riêng cho một endpoint — để dựng ca "một thiết bị chết, một thiết bị sống". */
    public void traVeChoEndpoint(String endpoint, int ma) {
        maTheoDuongDan.put(java.net.URI.create(endpoint).getPath(), ma);
    }

    public List<LuotNhan> daNhan() {
        return List.copyOf(daNhan);
    }

    /** Lượt POST gần nhất — dùng khi một ca gửi nhiều lượt liên tiếp rồi soi từng lượt. */
    public LuotNhan luotCuoi() {
        if (daNhan.isEmpty()) {
            throw new AssertionError("Chua co luot POST nao toi may chu day");
        }
        return daNhan.get(daNhan.size() - 1);
    }

    public LuotNhan luotDuyNhat() {
        if (daNhan.size() != 1) {
            throw new AssertionError("Mong dung mot luot POST toi may chu day, thuc te "
                    + daNhan.size());
        }
        return daNhan.get(0);
    }

    private void xuLy(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] than = in.readAllBytes();
            Map<String, String> header = new HashMap<>();
            exchange.getRequestHeaders().forEach((ten, giaTri) -> {
                if (!giaTri.isEmpty()) {
                    header.put(ten.toLowerCase(Locale.ROOT), giaTri.get(0));
                }
            });
            daNhan.add(new LuotNhan(exchange.getRequestURI().getPath(), Map.copyOf(header), than));

            int ma = maTheoDuongDan.getOrDefault(exchange.getRequestURI().getPath(), maMacDinh);
            byte[] phanHoi = thanPhanHoi.getBytes(StandardCharsets.UTF_8);
            if (phanHoi.length == 0) {
                exchange.sendResponseHeaders(ma, -1);
            } else {
                exchange.sendResponseHeaders(ma, phanHoi.length);
                exchange.getResponseBody().write(phanHoi);
            }
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

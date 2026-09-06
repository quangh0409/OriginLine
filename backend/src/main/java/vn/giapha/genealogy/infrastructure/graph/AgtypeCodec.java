package vn.giapha.genealogy.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Types;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.SqlParameterValue;

/**
 * Đóng gói / mở gói giá trị {@code agtype} của Apache AGE.
 *
 * <h2>Tham số hoá — không đàm phán</h2>
 * AGE nhận tham số qua đối số thứ ba của {@code cypher()} dưới dạng một giá trị {@code agtype} duy
 * nhất chứa map tham số. Đó là <b>cách duy nhất</b> được dùng trong dự án này: không bao giờ nối
 * chuỗi dữ liệu người dùng vào câu Cypher. Cypher injection không khác gì SQL injection, chỉ khó
 * nhận ra hơn vì ít người quen mặt nó.
 *
 * <h2>Đọc kết quả</h2>
 * Cột {@code agtype} về tới JDBC dưới dạng chuỗi, và chuỗi đó có thể mang hậu tố kiểu
 * ({@code ::vertex}, {@code ::edge}). Chuỗi thường thì có dấu nháy kép bao ngoài. Lớp này gỡ cả hai
 * để phần còn lại của adapter chỉ làm việc với giá trị Java.
 */
public final class AgtypeCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgtypeCodec() {
    }

    /**
     * Dựng tham số agtype cho một câu {@code cypher()}.
     *
     * <h3>Vì sao phải là {@code Types.OTHER}, không phải {@code setString}</h3>
     * AGE đòi đối số thứ ba của {@code cypher()} là <b>một tham số kiểu {@code agtype}</b>. Hai lối
     * đi sai đã được thử trên Apache AGE 1.6.0/PG16 và đều hỏng:
     * <ul>
     *   <li>{@code cypher(..., ?::agtype)} ⇒ {@code ERROR: third argument of cypher function must
     *       be a parameter} — ép kiểu biến tham số thành biểu thức, AGE từ chối;</li>
     *   <li>{@code setString} ⇒ driver gửi kèm OID {@code varchar}, và không có phép ép ngầm
     *       {@code text → agtype} nên hàm không khớp chữ ký nào.</li>
     * </ul>
     * Cách đúng là gửi tham số với kiểu <b>chưa xác định</b> để máy chủ tự suy ra {@code agtype} từ
     * chữ ký hàm. {@code SqlParameterValue(Types.OTHER, ...)} dẫn Spring gọi
     * {@code ps.setObject(i, json, Types.OTHER)}, và pgjdbc dịch nó thành
     * {@code bindString(..., Oid.UNSPECIFIED)} — đúng thứ cần.
     */
    public static SqlParameterValue params(Map<String, Object> values) {
        return new SqlParameterValue(Types.OTHER, json(values));
    }

    /** Chuỗi JSON thô của map tham số — tách riêng để test đọc được mà không cần JDBC. */
    public static String json(Map<String, Object> values) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            values.forEach((key, value) -> node.set(key, MAPPER.valueToTree(value)));
            return MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong dung duoc tham so agtype", ex);
        }
    }

    /** Gỡ hậu tố kiểu và dấu nháy của một scalar agtype. {@code null} vào thì {@code null} ra. */
    public static String unquote(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        int marker = value.lastIndexOf("::");
        if (marker > 0) {
            value = value.substring(0, marker).trim();
        }
        if ("null".equals(value)) {
            return null;
        }
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    public static UUID toUuid(String raw) {
        String value = unquote(raw);
        return value == null ? null : UUID.fromString(value);
    }

    public static Integer toInt(String raw) {
        String value = unquote(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return (int) Double.parseDouble(value);
    }

    /** Đọc một thuộc tính của đỉnh/cạnh trả về nguyên khối. */
    public static JsonNode properties(String rawVertexOrEdge) {
        try {
            String json = rawVertexOrEdge;
            int marker = json.lastIndexOf("::");
            if (marker > 0) {
                json = json.substring(0, marker);
            }
            JsonNode node = MAPPER.readTree(json);
            return node.path("properties");
        } catch (Exception ex) {
            throw new IllegalStateException("Khong doc duoc agtype vertex/edge", ex);
        }
    }
}

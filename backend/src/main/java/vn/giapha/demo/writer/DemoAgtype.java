package vn.giapha.demo.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.SqlParameterValue;

/**
 * Đóng gói tham số {@code agtype} cho các câu {@code cypher()} của module demo.
 *
 * <h2>Vì sao module demo có bản riêng thay vì dùng lại {@code AgtypeCodec}</h2>
 * <p>{@code AgtypeCodec} nằm trong {@code genealogy.infrastructure} — ruột của một context khác.
 * Dùng lại nó là vi phạm ranh giới module và {@code ModularityTests} sẽ chặn. Bản này cố ý tối
 * giản: demo chỉ ghi theo lô, không đọc kết quả agtype.</p>
 *
 * <h3>Bắt buộc {@code Types.OTHER}, không phải {@code setString}</h3>
 * <p>AGE đòi đối số thứ ba của {@code cypher()} là <b>một tham số kiểu {@code agtype}</b>. Hai lối
 * đi sai đều đã được thử và đều hỏng: {@code cypher(..., ?::agtype)} cho
 * {@code third argument of cypher function must be a parameter}; còn {@code setString} gửi kèm OID
 * {@code varchar} mà không có phép ép ngầm {@code text → agtype}. Cách đúng là gửi tham số với
 * kiểu <b>chưa xác định</b> để máy chủ tự suy ra {@code agtype} từ chữ ký hàm.</p>
 */
final class DemoAgtype {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DemoAgtype() {
    }

    /** Bọc một lô bản ghi thành tham số {@code $rows} của câu {@code UNWIND $rows AS row}. */
    static SqlParameterValue rows(List<Map<String, Object>> batch) {
        return new SqlParameterValue(Types.OTHER, json(Map.of("rows", batch)));
    }

    static String json(Map<String, Object> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong dung duoc tham so agtype cho du lieu demo", ex);
        }
    }
}

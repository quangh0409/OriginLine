package vn.giapha.kinship.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Tiện ích nhỏ cho việc gọi Cypher của Apache AGE từ JDBC.
 *
 * <p><b>Không bao giờ nối chuỗi dữ liệu người dùng vào câu Cypher.</b> Tham số luôn đi qua một map
 * agtype được truyền bằng tham số JDBC (dấu {@code ?} cuối lời gọi {@code cypher()}), rồi tham
 * chiếu trong câu truy vấn bằng {@code $ten_tham_so}. Chuỗi JSON do Jackson sinh nên việc thoát ký
 * tự là chuyện của thư viện, không phải của người viết truy vấn.</p>
 *
 * <p>Giá trị trả về là {@code agtype}. Chuỗi agtype có kèm dấu nháy kép, số có thể kèm hậu tố
 * {@code ::numeric}; các hàm dưới đây chuẩn hoá lại.</p>
 */
final class AgeCypher {

    static final String GRAPH = "giapha_graph";

    private static final ObjectMapper JSON = new ObjectMapper();

    private AgeCypher() {
    }

    /** Bọc câu Cypher thành một câu SQL hoàn chỉnh, mọi thứ đều qualify để không phụ thuộc search_path. */
    static String sql(String cypher, String resultColumns) {
        return "SELECT * FROM ag_catalog.cypher('" + GRAPH + "', $cypher$ " + cypher
                + " $cypher$, ?) AS (" + resultColumns + ")";
    }

    /** Map tham số dạng JSON để truyền vào đối số thứ ba của {@code cypher()}. */
    static String params(Map<String, ?> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Khong serialize duoc tham so Cypher", ex);
        }
    }

    /** Bỏ dấu nháy kép bao quanh chuỗi agtype; {@code null} và agtype {@code null} đều trả null. */
    static String text(Object agtypeValue) {
        if (agtypeValue == null) {
            return null;
        }
        String raw = agtypeValue.toString().trim();
        if (raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    static Integer integer(Object agtypeValue) {
        String raw = text(agtypeValue);
        if (raw == null) {
            return null;
        }
        int suffix = raw.indexOf("::");
        if (suffix > 0) {
            raw = raw.substring(0, suffix);
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

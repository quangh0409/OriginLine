package vn.giapha.events.infrastructure.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code Map<String,Object>} ⇄ chuỗi JSON cho cột {@code event.lunar_date} ({@code jsonb}).
 *
 * <p>Bản sao có chủ ý của converter cùng chức năng trong {@code genealogy}: hai bounded context
 * không được dùng lớp nội bộ của nhau, và ba mươi dòng chuyển đổi Jackson là cái giá rẻ hơn nhiều so
 * với một phụ thuộc ngang giữa hai context — thứ mà {@code ModularityTests} sẽ chặn ngay.</p>
 *
 * <p>Trả về {@code null} thay vì map rỗng khi không có dữ liệu: {@code lunar_date} là cột
 * <b>nullable</b> và ràng buộc {@code ck_event_date_source} phân biệt "không có ngày âm" với "có
 * ngày âm rỗng". Ghi {@code '{}'} vào đó sẽ làm sự kiện theo âm lịch vi phạm ràng buộc.</p>
 */
@Converter
public class EventJsonbConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong serialize duoc jsonb cua event", ex);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong doc duoc jsonb cua event", ex);
        }
    }
}

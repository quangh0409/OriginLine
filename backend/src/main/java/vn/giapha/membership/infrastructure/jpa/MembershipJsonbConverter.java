package vn.giapha.membership.infrastructure.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code Map<String,Object>} ⇄ chuỗi JSON cho cột {@code change_request.payload}.
 *
 * <p>Bản sao có chủ ý của converter cùng vai trò bên {@code genealogy}: lớp kia là chi tiết nội bộ
 * của context ấy, và kéo nó sang đây sẽ tạo một phụ thuộc {@code membership → genealogy.infrastructure}
 * — đúng thứ mà {@code ModularityTests} tồn tại để chặn. Mười dòng trùng lặp rẻ hơn một ranh giới
 * module bị thủng.</p>
 */
@Converter
public class MembershipJsonbConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong serialize duoc jsonb", ex);
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
            throw new IllegalStateException("Khong doc duoc jsonb", ex);
        }
    }
}

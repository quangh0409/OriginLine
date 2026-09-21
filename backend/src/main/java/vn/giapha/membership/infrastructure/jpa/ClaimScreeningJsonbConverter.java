package vn.giapha.membership.infrastructure.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.giapha.membership.domain.ClaimDuplicateSuspect;

/**
 * {@code person_claim.screening} ⇄ danh sách nghi trùng.
 *
 * <h2>Lưu dạng ĐỐI TƯỢNG bọc ngoài, không phải mảng trần</h2>
 * Cột là {@code JSONB NOT NULL DEFAULT '{}'}, và một mảng JSON trần cũng hợp lệ {@code jsonb} —
 * nhưng lúc đó giá trị mặc định {@code {}} và giá trị do ứng dụng ghi sẽ có <b>hai hình dạng khác
 * nhau</b>, nên mọi truy vấn SQL viết tay trên cột này phải xử lý cả hai. Bọc trong
 * {@code {"duplicates": [...]}} giữ một hình dạng duy nhất, và chừa chỗ cho khoá thứ hai (ví dụ
 * kết quả dò kỵ húy) mà không phải đổi kiểu cột.
 *
 * <h2>Bỏ qua khoá lạ khi đọc</h2>
 * {@code FAIL_ON_UNKNOWN_PROPERTIES = false}: một đơn đã lưu từ phiên bản trước không được làm chết
 * cả màn hàng chờ duyệt chỉ vì ảnh chụp dò trùng có thêm một trường. Ảnh chụp là dữ liệu tham khảo
 * cho người đọc, không phải nguồn chân lý của bất cứ phép tính nào.
 */
@Converter
public class ClaimScreeningJsonbConverter
        implements AttributeConverter<List<ClaimDuplicateSuspect>, String> {

    private static final String KEY = "duplicates";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final TypeReference<Map<String, List<ClaimDuplicateSuspect>>> TYPE =
            new TypeReference<>() {
            };

    @Override
    public String convertToDatabaseColumn(List<ClaimDuplicateSuspect> attribute) {
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put(KEY, attribute == null ? List.of() : attribute);
            return MAPPER.writeValueAsString(wrapper);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong serialize duoc anh chup do trung", ex);
        }
    }

    @Override
    public List<ClaimDuplicateSuspect> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            Map<String, List<ClaimDuplicateSuspect>> wrapper = MAPPER.readValue(dbData, TYPE);
            List<ClaimDuplicateSuspect> found = wrapper.get(KEY);
            return found == null ? List.of() : found;
        } catch (Exception ex) {
            throw new IllegalStateException("Khong doc duoc anh chup do trung", ex);
        }
    }
}

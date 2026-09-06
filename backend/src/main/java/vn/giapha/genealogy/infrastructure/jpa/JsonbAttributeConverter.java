package vn.giapha.genealogy.infrastructure.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code Map<String,Object>} ⇄ chuỗi JSON cho các cột {@code jsonb}.
 *
 * <p>Không dùng thư viện hypersistence hay kiểu tự chế: cột được khai báo
 * {@code @JdbcTypeCode(SqlTypes.JSON)} nên Hibernate 6 tự gửi đúng kiểu {@code jsonb} cho driver;
 * converter này chỉ lo phần chuyển đổi Java.</p>
 */
@Converter
public class JsonbAttributeConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        // null PHAI di ra thanh null, khong duoc bien thanh "{}".
        //
        // Converter nay dung chung cho ba cot co rang buoc khac han nhau: `attributes` la NOT NULL
        // DEFAULT '{}', con `birth_lunar`/`death_lunar` la nullable va co CHECK di kem. Dich null
        // thanh "{}" nghe co ve "an toan" nhung lam nguoi CON SONG khong the them duoc: mot ngay
        // gio rong hoa thanh object rong, va Postgres tra ve
        //   ERROR: violates check constraint "ck_person_birth_lunar"
        //   ERROR: violates check constraint "ck_person_alive_vs_death"
        // Hai cot NOT NULL khong can duoc do o day - truong entity da khoi tao san LinkedHashMap,
        // va neu co ai co tinh gan null thi de rang buoc NOT NULL bao that con hon giau di.
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong serialize duoc jsonb", ex);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        // Doi xung voi chieu ghi. Doc NULL ma tra map rong thi ban ghi vua nap len se ghi nguoc
        // xuong thanh "{}" o lan save ke tiep, dung lai vao rang buoc CHECK cua cot lunar - dung
        // loi cu, chi la di duong vong. Cot NOT NULL khong bao gio roi vao nhanh nay.
        if (dbData == null) {
            return null;
        }
        if (dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong doc duoc jsonb", ex);
        }
    }
}

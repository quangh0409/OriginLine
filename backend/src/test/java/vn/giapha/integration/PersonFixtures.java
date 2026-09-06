package vn.giapha.integration;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.vo.Gender;

/**
 * Gieo nhân khẩu vào <b>cả bảng {@code person} lẫn đỉnh {@code Person} của đồ thị AGE</b> — đúng
 * cặp đôi mà mọi lệnh ghi thật phải giữ đồng bộ.
 *
 * <h2>Vì sao gieo bằng SQL chứ không gọi {@code AddPersonService}</h2>
 * Đi qua use case thật vẫn là cách tốt hơn, nhưng hiện <b>không dùng được</b>: đường ghi JPA đang
 * hỏng vì {@code JsonbAttributeConverter} biến {@code null} thành {@code "{}"} cho hai cột
 * {@code birth_lunar}/{@code death_lunar} (xem {@link PersonJpaWriteIT} để có bản tái hiện tối
 * thiểu và mô tả cách sửa). Hệ quả là <b>không nhân khẩu còn sống nào ghi được qua JPA</b>. Gieo
 * bằng SQL cho phép ba bất biến quan trọng hơn — ghi đôi graph/bảng, phân tầng riêng tư, projection
 * cây — vẫn được kiểm chứng thật ngay bây giờ thay vì cùng chết theo một lỗi ở tầng adapter.
 *
 * <p><b>Khi lỗi kia được sửa</b>, nên chuyển các fixture này sang gọi {@code AddPersonService} để
 * suy luận đời thứ / chi kế thừa / thứ tự sinh cũng nằm trong vùng phủ.</p>
 *
 * <h2>Bố cục dữ liệu phải khớp {@code PersonMapper}</h2>
 * Bảng {@code person} của V2 không có cột cho nghề nghiệp, tiểu sử, ảnh, tỉnh và khối liên hệ; tất
 * cả nằm dưới khoá dành riêng {@code _profile} trong {@code attributes}. Gieo sai bố cục thì
 * {@code PersonMapper.toDomain} trả về hồ sơ rỗng và test phân tầng sẽ "xanh" vì lý do sai.
 */
final class PersonFixtures {

    private PersonFixtures() {
    }

    /** Nhân khẩu còn sống, có đủ dữ liệu Tầng 2 và Tầng 3 để bộ lọc riêng tư có gì mà giấu. */
    static Builder living(String fullName) {
        return new Builder(fullName, true);
    }

    /** Nhân khẩu đã khuất — theo BA v2 §10 là dữ liệu công khai. */
    static Builder deceased(String fullName, int deathYear) {
        return new Builder(fullName, false).deathYear(deathYear);
    }

    static final class Builder {

        private final String fullName;
        private final boolean alive;
        private String tabooName;
        private Gender gender = Gender.MALE;
        private Integer birthYear;
        private Integer deathYear;
        private Integer generation;
        private UUID branchId;
        private PrivacyLevel privacy = PrivacyLevel.DEFAULT;
        private boolean withContact = true;

        private Builder(String fullName, boolean alive) {
            this.fullName = fullName;
            this.alive = alive;
        }

        Builder gender(Gender value) {
            this.gender = value;
            return this;
        }

        Builder birthYear(int value) {
            this.birthYear = value;
            return this;
        }

        Builder deathYear(int value) {
            this.deathYear = value;
            return this;
        }

        Builder generation(int value) {
            this.generation = value;
            return this;
        }

        Builder branch(UUID value) {
            this.branchId = value;
            return this;
        }

        Builder privacy(PrivacyLevel value) {
            this.privacy = value;
            return this;
        }

        /** Thêm lớp tên húy — dữ liệu lễ nghi, chỉ hiện từ Tầng 2 trở lên. */
        Builder tabooName(String value) {
            this.tabooName = value;
            return this;
        }

        Builder withoutContact() {
            this.withContact = false;
            return this;
        }

        UUID seed(JdbcTemplate jdbc, TreeGraphPort graph) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, birth_lunar,"
                            + " death_solar, death_lunar, is_alive, native_place, current_place,"
                            + " primary_branch_id, lineage_status, attributes, privacy_level)"
                            + " VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb), ?, ?, ?, ?,"
                            + " 'NORMAL', CAST(? AS jsonb), ?)",
                    id,
                    gender.name(),
                    generation,
                    birthYear == null ? null : java.sql.Date.valueOf(LocalDate.of(birthYear, 3, 20)),
                    birthYear == null ? null : lunarJson(birthYear, 2, 15),
                    deathYear == null ? null : java.sql.Date.valueOf(LocalDate.of(deathYear, 6, 15)),
                    deathYear == null ? null : lunarJson(deathYear, 5, 10),
                    alive,
                    "Bac Ninh",
                    "So 12 ngo 3 phuong Lang Ha, Ha Noi",
                    branchId,
                    attributesJson(),
                    privacy.dbValue());

            insertName(jdbc, id, "THUONG_GOI", fullName, true);
            if (tabooName != null) {
                insertName(jdbc, id, "HUY", tabooName, false);
            }
            // Đỉnh trong đồ thị: thiếu nó thì mọi phép duyệt phả đồ im lặng bỏ qua người này.
            graph.createPersonNode(id, gender.name(), generation);
            return id;
        }

        private static void insertName(JdbcTemplate jdbc, UUID personId, String type, String name,
                                       boolean primary) {
            jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                    + " VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), personId, type, name, primary);
        }

        /** {@code {"year":…,"month":…,"day":…,"leap":false}} — {@code ck_person_*_lunar} đòi day+month. */
        private static String lunarJson(int year, int month, int day) {
            return "{\"year\":" + year + ",\"month\":" + month + ",\"day\":" + day
                    + ",\"leap\":false}";
        }

        /** Khớp đúng bố cục {@code _profile} mà {@code PersonMapper} đọc lại. */
        private String attributesJson() {
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("occupation", "Giao vien");
            profile.put("biography", "Tieu su chi tiet - du lieu Tang 3");
            profile.put("currentPlaceProvince", "Ha Noi");
            profile.put("avatarKey", "portraits/" + fullName.hashCode() + ".jpg");
            if (withContact) {
                profile.put("contact", Map.of("phone", "0900000001",
                        "email", "nguoi@example.com", "zaloId", "zalo-01"));
            }
            if (birthYear != null) {
                profile.put("birthPrecision", "DAY");
            }
            if (deathYear != null) {
                profile.put("deathPrecision", "DAY");
            }
            return toJson(Map.of("ghi_chu", "du lieu test", "_profile", profile));
        }

        private static String toJson(Object value) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            } catch (Exception ex) {
                throw new IllegalStateException("Khong dung duoc JSON fixture", ex);
            }
        }
    }
}

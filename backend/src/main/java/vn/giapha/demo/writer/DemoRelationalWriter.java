package vn.giapha.demo.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.giapha.demo.model.DemoBranch;
import vn.giapha.demo.model.DemoName;
import vn.giapha.demo.model.DemoPerson;
import vn.giapha.demo.model.DemoRelation;
import vn.giapha.shared.vo.LunarDate;

/**
 * Ghi phần <b>quan hệ</b> của bộ dữ liệu giả: {@code branch} · {@code person} · {@code person_name}
 * · {@code relationship}.
 *
 * <p><b>Không tự mở transaction</b> — ranh giới nằm ở {@link DemoDataWriter}.</p>
 *
 * <h2>Đi thẳng bằng JDBC, không qua application service của {@code genealogy}</h2>
 * <p>Đây là công cụ dựng môi trường chứ không phải nghiệp vụ, và module {@code demo} cố ý không phụ
 * thuộc vào ruột của context khác ({@code ModularityTests} canh điều này). Đổi lại, file này phải
 * tự chịu trách nhiệm tôn trọng mọi ràng buộc của V2 — đọc kỹ phần ghi chú ở từng câu INSERT trước
 * khi sửa.</p>
 */
@Component
@Profile("demo")
class DemoRelationalWriter {

    private static final Logger log = LoggerFactory.getLogger(DemoRelationalWriter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Giờ Việt Nam — mốc thời gian của dữ liệu demo phải tất định, không lấy đồng hồ hệ thống. */
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;

    DemoRelationalWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    long personRowCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM public.person", Long.class);
        return count == null ? 0L : count;
    }

    /**
     * Xoá sạch dữ liệu phả hệ. CHỈ dùng khi nạp lại demo. Thứ tự xoá đi ngược chiều khoá ngoại;
     * {@code branch.head_person_id} phải được gỡ trước, nếu không {@code fk_branch_head_person}
     * sẽ chặn việc xoá {@code person}.
     */
    void deleteAll() {
        jdbc.update("UPDATE public.branch SET head_person_id = NULL WHERE head_person_id IS NOT NULL");
        jdbc.update("DELETE FROM public.relationship");
        jdbc.update("DELETE FROM public.person_name");
        jdbc.update("DELETE FROM public.person");
        jdbc.update("DELETE FROM public.branch");
    }

    // =================================================================================
    // branch
    // =================================================================================

    /**
     * Ghi chi/ngành và <b>đọc ngược</b> {@code path} về {@link DemoBranch}.
     *
     * <p>{@code slug} do PostgreSQL tính bằng {@code vn_slugify()} (V6) chứ không phải Java: cả hệ
     * thống chỉ được có duy nhất một cách bỏ dấu. {@code path} thì ghép từ {@code slug} của tổ tiên
     * — không bao giờ ghép từ {@code name} có dấu, vì nhãn ltree chỉ nhận {@code [a-z0-9_]}.</p>
     *
     * <p>Danh sách vào đã bảo đảm cha đứng trước con nên một lượt duyệt là đủ.</p>
     */
    void writeBranches(List<DemoBranch> branches) {
        for (DemoBranch branch : branches) {
            String slug = jdbc.queryForObject(
                    "SELECT public.vn_slugify(?)", String.class, branch.name());
            String parentPath = branch.parent() == null ? null : branch.parent().path();
            String path = parentPath == null ? slug : parentPath + "." + slug;

            jdbc.update("""
                    INSERT INTO public.branch
                        (id, name, slug, path, parent_id, branch_kind, region,
                         founded_year, note, sort_order, is_deleted)
                    VALUES (?, ?, ?, CAST(? AS public.ltree), ?, ?, ?, ?, ?, ?, FALSE)
                    """,
                    branch.id(), branch.name(), slug, path,
                    branch.parent() == null ? null : branch.parent().id(),
                    branch.kind(), branch.region(), branch.foundedYear(), branch.note(),
                    branch.sortOrder());
            branch.path(path);
        }
        log.info("Da ghi {} chi/nganh, goc = {}", branches.size(),
                branches.isEmpty() ? "-" : branches.get(0).path());
    }

    /**
     * Trưởng tộc / trưởng chi. Chạy <b>sau</b> khi đã ghi {@code person} vì
     * {@code fk_branch_head_person} trỏ sang bảng ấy.
     *
     * <p>Nhắc lại cho người sửa sau: đây là <b>chức danh dòng tộc theo huyết thống</b> (đích tôn),
     * hoàn toàn tách khỏi vai trò kỹ thuật trong bảng {@code role}.</p>
     */
    void writeBranchHeads(List<DemoBranch> branches) {
        int updated = 0;
        for (DemoBranch branch : branches) {
            if (branch.headPersonId() == null) {
                continue;
            }
            updated += jdbc.update("UPDATE public.branch SET head_person_id = ? WHERE id = ?",
                    branch.headPersonId(), branch.id());
        }
        log.info("Da gan {} truong chi/nganh (chuc danh dong toc, khong phai vai tro ky thuat)", updated);
    }

    // =================================================================================
    // person
    // =================================================================================

    /** @param anchorsByPerson id nhân khẩu -> danh sách tên mốc neo, ghi vào {@code demo_anchor} */
    void writePersons(List<DemoPerson> persons, Map<UUID, List<String>> anchorsByPerson,
                      LocalDate referenceDate) {
        OffsetDateTime stamp = referenceDate.atStartOfDay(VN_ZONE).toOffsetDateTime();
        String sql = """
                INSERT INTO public.person
                    (id, gender, generation, birth_order, birth_solar, birth_lunar,
                     death_solar, death_lunar, is_alive, native_place, current_place,
                     primary_branch_id, lineage_status, attributes, privacy_level,
                     is_deleted, deleted_at, is_anonymized, anonymized_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb), ?, ?, ?,
                        ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DemoPerson person = persons.get(i);
                List<String> anchors = anchorsByPerson.getOrDefault(person.id(), List.of());
                ps.setObject(1, person.id());
                ps.setString(2, person.gender());
                ps.setInt(3, person.generation());
                setNullableInt(ps, 4, person.birthOrder());
                ps.setObject(5, person.birthSolar());
                ps.setString(6, lunarJson(person.birthLunar()));
                ps.setObject(7, person.deathSolar());
                ps.setString(8, lunarJson(person.deathLunar()));
                ps.setBoolean(9, person.alive());
                ps.setString(10, person.nativePlace());
                ps.setString(11, DemoPersonAttributes.currentPlaceFull(person));
                ps.setObject(12, person.branchId());
                ps.setString(13, person.lineageStatus());
                ps.setString(14, json(DemoPersonAttributes.toJson(person, anchors)));
                ps.setString(15, person.privacyLevel());
                ps.setBoolean(16, person.deleted());
                // ck_person_deleted_at: da xoa mem thi bat buoc co moc thoi gian.
                ps.setObject(17, person.deleted() ? stamp : null);
                ps.setBoolean(18, person.anonymized());
                ps.setObject(19, person.anonymized() ? stamp : null);
            }

            @Override
            public int getBatchSize() {
                return persons.size();
            }
        });
        log.info("Da ghi {} nhan khau", persons.size());
    }

    void writeNames(List<DemoName> names) {
        String sql = """
                INSERT INTO public.person_name
                    (id, person_id, name_type, full_name, name_hannom, is_primary, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DemoName name = names.get(i);
                ps.setObject(1, name.id());
                ps.setObject(2, name.personId());
                ps.setString(3, name.type());
                ps.setString(4, name.fullName());
                ps.setString(5, name.hannom());
                ps.setBoolean(6, name.primary());
                ps.setString(7, name.note());
            }

            @Override
            public int getBatchSize() {
                return names.size();
            }
        });
        log.info("Da ghi {} lop ten (huy/tu/hieu/thuy/thuong goi/phap danh)", names.size());
    }

    // =================================================================================
    // relationship — bản chiếu của cạnh AGE
    // =================================================================================

    /**
     * Ghi <b>bản chiếu</b> quan hệ. Nguồn chân lý là cạnh trong {@code giapha_graph}; hai bên được
     * ghi trong cùng transaction ở {@link DemoDataWriter}.
     */
    void writeRelations(List<DemoRelation> relations) {
        String sql = """
                INSERT INTO public.relationship
                    (id, from_person_id, to_person_id, rel_type, spouse_order, heir_type,
                     valid_from, valid_to, end_reason, note, attributes, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), FALSE)
                """;
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DemoRelation relation = relations.get(i);
                ps.setObject(1, relation.id());
                ps.setObject(2, relation.from());
                ps.setObject(3, relation.to());
                ps.setString(4, relation.relType());
                setNullableInt(ps, 5, relation.spouseOrder());
                ps.setString(6, relation.heirType());
                ps.setObject(7, relation.validFrom());
                ps.setObject(8, relation.validTo());
                ps.setString(9, relation.endReason());
                ps.setString(10, relation.note());
                ps.setString(11, json(relation.attributes()));
            }

            @Override
            public int getBatchSize() {
                return relations.size();
            }
        });
        log.info("Da ghi {} dong relationship (ban chieu cua canh AGE)", relations.size());
    }

    // =================================================================================
    // Đối soát
    // =================================================================================

    /**
     * Số dòng {@code relationship} còn hiệu lực, <b>đọc lại từ CSDL</b>.
     *
     * <p>{@code DemoDataWriter} đem con số này so với số cạnh đếm trong {@code giapha_graph} ngay
     * trước khi commit. Nhờ vậy bất biến "bảng và đồ thị cùng một transaction" không còn là lời hứa
     * trong javadoc mà thành một điều kiện có thể sai và huỷ cả mẻ dữ liệu.</p>
     */
    long relationRowCount() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM public.relationship WHERE is_deleted = FALSE", Long.class);
        return count == null ? 0L : count;
    }

    /** Thống kê nhanh để đối chiếu với bảng ca biên bắt buộc của plan §10. */
    Map<String, Long> auditCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("person", scalar("SELECT count(*) FROM public.person"));
        counts.put("person_song", scalar("SELECT count(*) FROM public.person WHERE is_alive"));
        counts.put("person_da_khuat", scalar("SELECT count(*) FROM public.person WHERE NOT is_alive"));
        counts.put("branch", scalar("SELECT count(*) FROM public.branch"));
        counts.put("person_name", scalar("SELECT count(*) FROM public.person_name"));
        counts.put("relationship", scalar("SELECT count(*) FROM public.relationship"));
        counts.put("con_nuoi", scalar(
                "SELECT count(*) FROM public.relationship WHERE rel_type = 'PARENT_ADOPT'"));
        counts.put("da_the", scalar("""
                SELECT count(*) FROM (
                    SELECT from_person_id FROM public.relationship
                    WHERE rel_type = 'SPOUSE' GROUP BY from_person_id HAVING count(*) > 1
                ) t"""));
        counts.put("tuyet_tu", scalar(
                "SELECT count(*) FROM public.person WHERE lineage_status = 'TUYET_TU'"));
        counts.put("ke_tu", scalar(
                "SELECT count(*) FROM public.person WHERE lineage_status = 'KE_TU'"));
        counts.put("dich_ton", scalar(
                "SELECT count(*) FROM public.relationship WHERE heir_type = 'DICH_TON'"));
        counts.put("tang3_nguoi_song", scalar("""
                SELECT count(*) FROM public.person
                WHERE is_alive
                  AND attributes #>> '{_profile,contact,phone}' IS NOT NULL
                  AND attributes #>> '{_profile,contact,email}' IS NOT NULL"""));
        return counts;
    }

    private long scalar(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    // =================================================================================

    private static void setNullableInt(PreparedStatement ps, int index, Integer value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** Âm lịch xuống jsonb {@code {year, month, day, leap}} — đúng hình dạng {@code PersonMapper} đọc. */
    private static String lunarJson(LunarDate lunar) {
        if (lunar == null) {
            // Ngay am khuyet la chuyen binh thuong voi gia pha cu; DOAN moi la loi.
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("year", lunar.year());
        json.put("month", lunar.month());
        json.put("day", lunar.day());
        json.put("leap", lunar.leapMonth());
        return json(json);
    }

    private static String json(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong dung duoc jsonb cho du lieu demo", ex);
        }
    }
}

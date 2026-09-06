package vn.giapha.events.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.port.EventSubjectPort;
import vn.giapha.shared.vo.LunarDate;

/**
 * Hiện thực {@link EventSubjectPort}: đọc ảnh chụp gọn của nhân khẩu và chi/ngành.
 *
 * <h2>Chọn tên hiển thị thế nào</h2>
 * Người trong họ có nhiều lớp tên (húy / tự / hiệu / thụy / thường gọi / pháp danh). Thứ tự ưu tiên
 * ở đây là: tên đã đánh dấu <b>chính</b> ({@code is_primary}), rồi tên <b>thường gọi</b>, rồi tên
 * <b>húy</b>. Lý do: thông báo là để người sống nhận ra đang nói về ai, mà trong đời sống họ gọi
 * nhau bằng tên thường gọi chứ không bằng tên khai sinh. Tên húy đứng cuối chứ không đứng đầu — nó
 * là tên kiêng, đọc lên trong một thông báo đại chúng là điều nên tránh khi đã có lựa chọn khác.
 *
 * <h2>Nợ kiến trúc đã biết</h2>
 * {@code person}, {@code person_name}, {@code branch} thuộc sở hữu của {@code genealogy}, chưa có
 * mặt tiền công khai. Đọc thẳng bằng SQL — không có phụ thuộc Java nào sang context khác nên ranh
 * giới module vẫn sạch; khi {@code genealogy} mở application service thì lớp này chuyển sang gọi
 * service và bỏ SQL. Chỉ SELECT, tuyệt đối không ghi.
 *
 * <p><b>Riêng tư:</b> câu truy vấn cố tình chỉ lấy trường Tầng 1 (tên, đời, còn sống hay đã khuất,
 * chi). Không lấy liên hệ, không lấy địa chỉ, không lấy ngày sinh đầy đủ — thông báo hiện trên màn
 * hình khoá thiết bị.</p>
 */
@Repository
public class EventSubjectJdbcAdapter implements EventSubjectPort {

    private static final Logger log = LoggerFactory.getLogger(EventSubjectJdbcAdapter.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private static final String SQL_PERSONS = """
            SELECT p.id,
                   p.generation,
                   p.is_alive,
                   p.death_lunar::text AS death_lunar,
                   b.id   AS branch_id,
                   b.name AS branch_name,
                   b.path::text AS branch_path,
                   b.region AS branch_region,
                   (SELECT pn.full_name
                      FROM person_name pn
                     WHERE pn.person_id = p.id
                     ORDER BY pn.is_primary DESC,
                              CASE pn.name_type WHEN 'THUONG_GOI' THEN 0
                                                WHEN 'TU'         THEN 1
                                                WHEN 'HIEU'       THEN 2
                                                WHEN 'THUY'       THEN 3
                                                WHEN 'PHAP_DANH'  THEN 4
                                                ELSE 5 END,
                              pn.created_at
                     LIMIT 1) AS display_name,
                   (SELECT pn.name_hannom
                      FROM person_name pn
                     WHERE pn.person_id = p.id AND pn.is_primary
                     LIMIT 1) AS name_hannom
              FROM person p
              LEFT JOIN branch b ON b.id = p.primary_branch_id
             WHERE p.id IN (:ids)
            """;

    private static final String SQL_BRANCHES = """
            SELECT b.id, b.name, b.path::text AS path, b.region
              FROM branch b
             WHERE b.id IN (:ids)
            """;

    private static final RowMapper<EventSubject.BranchSnapshot> BRANCH_MAPPER = (rs, rowNum) ->
            new EventSubject.BranchSnapshot(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("path"),
                    rs.getString("region"));

    private final NamedParameterJdbcTemplate jdbc;

    public EventSubjectJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventSubject> findPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(findPersons(List.of(personId)).get(personId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, EventSubject> findPersons(Collection<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EventSubject> result = new LinkedHashMap<>();
        jdbc.query(SQL_PERSONS, new MapSqlParameterSource("ids", personIds), rs -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID branchId = rs.getObject("branch_id", UUID.class);
            EventSubject.BranchSnapshot branch = branchId == null ? null
                    : new EventSubject.BranchSnapshot(branchId, rs.getString("branch_name"),
                            rs.getString("branch_path"), rs.getString("branch_region"));
            result.put(id, new EventSubject(
                    id,
                    rs.getString("display_name"),
                    rs.getString("name_hannom"),
                    (Integer) rs.getObject("generation"),
                    rs.getBoolean("is_alive"),
                    toLunar(id, rs.getString("death_lunar")),
                    branch));
        });
        return Map.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventSubject.BranchSnapshot> findBranch(UUID branchId) {
        if (branchId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(findBranches(List.of(branchId)).get(branchId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, EventSubject.BranchSnapshot> findBranches(Collection<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return Map.of();
        }
        List<EventSubject.BranchSnapshot> rows =
                jdbc.query(SQL_BRANCHES, new MapSqlParameterSource("ids", branchIds), BRANCH_MAPPER);
        Map<UUID, EventSubject.BranchSnapshot> result = new LinkedHashMap<>();
        for (EventSubject.BranchSnapshot row : rows) {
            result.put(row.id(), row);
        }
        return Map.copyOf(result);
    }

    /**
     * {@code person.death_lunar} là {@code jsonb} {@code {year, month, day, leap}}.
     *
     * <p>Dữ liệu hỏng trả {@code null} kèm {@code WARN} thay vì ném lỗi: một hồ sơ sai không được
     * làm sập job sinh lịch nhắc của cả họ, và {@code OccurrenceResolver} đã ghi log riêng khi ngày
     * âm hiệu lực rỗng. Tuyệt đối không đoán bừa một ngày thay thế ở đây.</p>
     */
    private static LunarDate toLunar(UUID personId, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> json = JSON.readValue(rawJson, JSON_MAP);
            Integer day = intOf(json.get("day"));
            Integer month = intOf(json.get("month"));
            if (day == null || month == null) {
                log.warn("person {}: death_lunar thieu day/month -> {}", personId, rawJson);
                return null;
            }
            Integer year = intOf(json.get("year"));
            return new LunarDate(year == null ? 0 : year, month, day,
                    Boolean.TRUE.equals(json.get("leap")));
        } catch (Exception ex) {
            log.warn("person {}: death_lunar khong doc duoc ({}) - {}", personId, rawJson, ex.getMessage());
            return null;
        }
    }

    private static Integer intOf(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}

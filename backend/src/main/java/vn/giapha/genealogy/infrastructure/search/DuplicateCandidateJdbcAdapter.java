package vn.giapha.genealogy.infrastructure.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Hiện thực {@link DuplicateCandidatePort} — cửa lọc ứng viên nghi trùng trên {@code person} +
 * {@code person_name}.
 *
 * <h2>Chỉ lọc theo tên, không chấm điểm</h2>
 * SQL ở đây cố ý "ngu": nó chỉ trả về mọi nhân khẩu chưa xoá mềm có một lớp tên khớp không dấu, kèm
 * các trường cần cho việc chấm điểm. Trọng số và ngưỡng nằm trong {@code DuplicateScorer} bên tầng
 * application, nơi chúng test được mà không cần CSDL và chỉnh được mà không cần migration.
 *
 * <h2>So không dấu — dùng lại V6, không viết mới</h2>
 * Cột {@code person_name.name_unaccented} là {@code GENERATED ALWAYS AS (vn_unaccent(full_name))}
 * nên không bao giờ lệch với {@code full_name}, và chỉ mục {@code ix_person_name_unaccented} phủ
 * đúng phép so đẳng thức mà truy vấn này dùng. Nhờ đó gõ "Nguyen Van Tuan" tìm ra "Nguyễn Văn Tuân".
 *
 * <h2>Ngày giỗ lấy từ jsonb, không lấy từ dương lịch</h2>
 * {@code death_lunar} là nguồn chân lý của ngày giỗ ({@code death_solar} đổi theo từng năm nên vô
 * dụng cho việc so trùng). Cờ {@code leap} được kéo lên nguyên vẹn — bỏ nó là lệch cả một tháng.
 */
@Repository
public class DuplicateCandidateJdbcAdapter implements DuplicateCandidatePort {

    private static final Logger log = LoggerFactory.getLogger(DuplicateCandidateJdbcAdapter.class);

    /** Số tên bỏ dấu trong một câu lệnh — chia lô để không dựng câu SQL dài vô hạn. */
    private static final int LO_BO_DAU = 500;

    /**
     * Hai tầng: truy vấn con chọn <b>id</b> các nhân khẩu trúng cửa lọc tên (có {@code LIMIT}),
     * truy vấn ngoài kéo về <b>mọi</b> lớp tên của đúng những người đó — cần đủ các lớp tên thì
     * mới phân biệt được "trùng có dấu" với "chỉ trùng sau khi bỏ dấu".
     */
    private static final String SQL_UNG_VIEN = """
            WITH ung_vien AS (
                SELECT DISTINCT pn.person_id
                  FROM person_name pn
                  JOIN person p ON p.id = pn.person_id
                 WHERE p.is_deleted = FALSE
                   AND pn.name_unaccented IN (:names)
                 LIMIT :limit
            )
            SELECT p.id                                            AS person_id,
                   p.gender                                        AS gender,
                   p.generation                                    AS generation,
                   p.primary_branch_id                             AS branch_id,
                   COALESCE(EXTRACT(YEAR FROM p.birth_solar)::int,
                            (p.birth_lunar ->> 'year')::int)       AS birth_year,
                   COALESCE(EXTRACT(YEAR FROM p.death_solar)::int,
                            (p.death_lunar ->> 'year')::int)       AS death_year,
                   (p.death_lunar ->> 'month')::int                AS gio_month,
                   (p.death_lunar ->> 'day')::int                  AS gio_day,
                   COALESCE((p.death_lunar ->> 'leap')::boolean, FALSE) AS gio_leap,
                   vn_unaccent(p.native_place)                     AS native_un,
                   pn.name_type                                    AS name_type,
                   pn.full_name                                    AS full_name,
                   pn.name_unaccented                              AS name_unaccented,
                   pn.is_primary                                   AS is_primary
              FROM ung_vien uv
              JOIN person p       ON p.id = uv.person_id
              JOIN person_name pn ON pn.person_id = p.id
             ORDER BY p.id, pn.is_primary DESC, pn.created_at ASC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DuplicateCandidateJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> unaccent(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(rawNames.size());
        for (int from = 0; from < rawNames.size(); from += LO_BO_DAU) {
            int to = Math.min(rawNames.size(), from + LO_BO_DAU);
            result.addAll(unaccentChunk(rawNames.subList(from, to)));
        }
        return result;
    }

    /**
     * Dạng {@code VALUES} thay vì một cột cho mỗi tên: Postgres giới hạn 1664 cột trên một dòng,
     * còn số dòng của {@code VALUES} thì không giới hạn. Tham số vẫn được bind, không nối chuỗi
     * giá trị vào SQL.
     */
    private List<String> unaccentChunk(List<String> names) {
        StringBuilder sql = new StringBuilder("SELECT t.i, vn_unaccent(t.v) AS un FROM (VALUES ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (int i = 0; i < names.size(); i++) {
            sql.append(i == 0 ? "" : ", ").append("(").append(i).append(", CAST(:n").append(i)
                    .append(" AS text))");
            params.addValue("n" + i, names.get(i));
        }
        sql.append(") AS t(i, v) ORDER BY t.i");

        String[] out = new String[names.size()];
        jdbc.query(sql.toString(), params, rs -> {
            out[rs.getInt("i")] = rs.getString("un");
        });
        return java.util.Arrays.asList(out);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonSignature> findByAnyName(Collection<String> unaccentedNames, int limit) {
        if (unaccentedNames == null || unaccentedNames.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("names", unaccentedNames)
                .addValue("limit", Math.max(1, limit));

        Map<UUID, Gom> gom = new LinkedHashMap<>();
        jdbc.query(SQL_UNG_VIEN, params, rs -> {
            UUID id = rs.getObject("person_id", UUID.class);
            Gom hienTai = gom.computeIfAbsent(id, key -> newGom(key, rs));
            hienTai.names.add(new NameKey(NameType.valueOf(rs.getString("name_type")),
                    rs.getString("full_name"), rs.getString("name_unaccented")));
            if (hienTai.displayName == null) {
                hienTai.displayName = rs.getString("full_name");
            }
        });
        List<PersonSignature> result = gom.values().stream().map(Gom::toSignature).toList();
        log.debug("Do trung: cua loc ten tra ve {} ung vien cho {} ten", result.size(),
                unaccentedNames.size());
        return result;
    }

    private static Gom newGom(UUID id, ResultSet rs) {
        try {
            Gom gom = new Gom();
            gom.personId = id;
            gom.gender = Gender.valueOf(rs.getString("gender"));
            gom.generation = (Integer) rs.getObject("generation");
            gom.branchId = rs.getObject("branch_id", UUID.class);
            gom.birthYear = (Integer) rs.getObject("birth_year");
            gom.deathYear = (Integer) rs.getObject("death_year");
            Integer month = (Integer) rs.getObject("gio_month");
            Integer day = (Integer) rs.getObject("gio_day");
            // ck_person_death_lunar bat buoc co ca day lan month, nen chi can mot ve null la coi
            // nhu khong co ngay gio.
            gom.gio = hopLe(month, day) ? new LunarDate(gom.deathYear == null ? 0 : gom.deathYear,
                    month, day, rs.getBoolean("gio_leap")) : null;
            gom.nativeUn = rs.getString("native_un");
            return gom;
        } catch (SQLException ex) {
            throw new IllegalStateException("Khong doc duoc dong ung vien nghi trung", ex);
        }
    }

    /**
     * {@code ck_person_death_lunar} chỉ bắt buộc <b>có</b> khoá {@code day}/{@code month} chứ không
     * kiểm giá trị, nên dữ liệu nhập từ gia phả giấy vẫn có thể lọt tháng 13. Một dòng hỏng không
     * được phép làm chết cả lô 400 người, nên coi như không có ngày giỗ.
     */
    private static boolean hopLe(Integer month, Integer day) {
        return month != null && day != null && month >= 1 && month <= 12 && day >= 1 && day <= 30;
    }

    /** Bộ gom tạm cho các dòng cùng một nhân khẩu — một dòng cho mỗi lớp tên. */
    private static final class Gom {

        private UUID personId;
        private String displayName;
        private final List<NameKey> names = new ArrayList<>();
        private Gender gender;
        private Integer generation;
        private UUID branchId;
        private Integer birthYear;
        private Integer deathYear;
        private LunarDate gio;
        private String nativeUn;

        private PersonSignature toSignature() {
            return new PersonSignature(personId, displayName, names, gender, generation, branchId,
                    birthYear, deathYear, gio, nativeUn);
        }
    }
}

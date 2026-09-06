package vn.giapha.genealogy.infrastructure.search;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.port.PersonSearchPort;
import vn.giapha.shared.vo.BranchPath;

/**
 * Hiện thực {@link PersonSearchPort} bằng full-text search của PostgreSQL — không Elasticsearch,
 * không Redis (BA v2 §9).
 *
 * <h2>Ba lối khớp chạy song song, lấy điểm cao nhất</h2>
 * <ul>
 *   <li><b>Tiền tố không dấu</b> {@code name_unaccented LIKE 'nguyen van%'} — bám chỉ mục
 *       {@code ix_person_name_unaccented} ({@code varchar_pattern_ops}). Đây là lối gõ tự nhiên
 *       nhất và cho điểm cao nhất.</li>
 *   <li><b>Theo từ</b> {@code name_tsv @@ websearch_to_tsquery('public.vi_unaccent', …)} — tìm được
 *       "Tuấn Nguyễn" khi trong sổ ghi "Nguyễn Văn Tuấn". Dùng {@code websearch_to_tsquery} chứ
 *       không {@code to_tsquery}: bản kia <b>ném lỗi cú pháp</b> với chuỗi người dùng gõ tự do
 *       (dấu &amp;, dấu ngoặc, dấu nháy lệch), biến một ô tìm kiếm thành 500.</li>
 *   <li><b>Gần đúng</b> {@code similarity()} qua GIN trigram — chịu được lỗi gõ và thiếu dấu.</li>
 * </ul>
 *
 * <p>Cả ba đều chạy trên cột <b>không dấu</b> hoặc qua cấu hình FTS {@code vi_unaccent}, nên gõ có
 * dấu hay không dấu đều ra cùng kết quả (FR-4.4). Tìm trên <b>mọi lớp tên</b> — húy, tự, hiệu, thụy,
 * thường gọi, pháp danh — vì trong gia phả người ta hay nhớ tên thường gọi hơn tên khai sinh.</p>
 *
 * <h2>Một người, một dòng</h2>
 * Một nhân khẩu có nhiều tên nên có thể khớp nhiều lần. {@code DISTINCT ON (person_id)} giữ lại lớp
 * tên khớp mạnh nhất — nếu không, một trang kết quả 20 dòng có thể chỉ là bốn người lặp lại.
 *
 * <h2>Không lọc riêng tư ở đây</h2>
 * Đúng hợp đồng của cổng: trả <b>id thô</b>. Chỉ tầng application mới biết người gọi là ai để áp
 * phân tầng Nghị định 13/2023. Vì bộ lọc đó sẽ cắt bớt kết quả nên {@code limit} truyền xuống đây
 * phải rộng hơn trang cần hiển thị.
 */
@Repository
public class PersonSearchJdbcAdapter implements PersonSearchPort {

    private static final Logger log = LoggerFactory.getLogger(PersonSearchJdbcAdapter.class);

    /** Trần cứng, để một {@code limit} sai ở tầng trên không kéo cả bảng {@code person_name} về. */
    public static final int MAX_LIMIT = 500;

    private static final String SQL = """
            WITH q AS (
                SELECT vn_unaccent(:query) AS un,
                       websearch_to_tsquery('public.vi_unaccent', :query) AS ts
            ),
            matches AS (
                SELECT pn.person_id AS person_id,
                       pn.name_type AS name_type,
                       GREATEST(
                           CASE WHEN pn.name_unaccented = q.un              THEN 1.0
                                WHEN pn.name_unaccented LIKE (q.un || '%')  THEN 0.9
                                ELSE 0.0 END,
                           CASE WHEN pn.name_tsv @@ q.ts                    THEN 0.85
                                ELSE 0.0 END,
                           similarity(pn.name_unaccented, q.un)::numeric
                       ) AS score
                  FROM person_name pn
                  JOIN person p ON p.id = pn.person_id
                  LEFT JOIN branch b ON b.id = p.primary_branch_id
                  CROSS JOIN q
                 WHERE (CAST(:includeDeleted AS boolean) OR p.is_deleted = FALSE)
                   AND (CAST(:generation AS int) IS NULL OR p.generation = CAST(:generation AS int))
                   AND (CAST(:branchPath AS text) IS NULL OR b.path <@ CAST(:branchPath AS ltree))
                   AND (CAST(:nativePlace AS text) IS NULL
                        OR vn_unaccent(p.native_place) LIKE ('%' || vn_unaccent(CAST(:nativePlace AS text)) || '%'))
                   AND (CAST(:alive AS boolean) IS NULL OR p.is_alive = CAST(:alive AS boolean))
                   AND (pn.name_unaccented LIKE (q.un || '%')
                        OR pn.name_tsv @@ q.ts
                        OR pn.name_unaccented % q.un)
            ),
            best AS (
                SELECT DISTINCT ON (person_id) person_id, name_type, score
                  FROM matches
                 ORDER BY person_id, score DESC
            )
            SELECT person_id, name_type, score
              FROM best
             ORDER BY score DESC, person_id
             LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PersonSearchJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Hit> search(String query, SearchFilter filter, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        SearchFilter effective = filter == null ? SearchFilter.none() : filter;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", query.trim())
                .addValue("generation", effective.generation())
                .addValue("branchPath", validatedBranchPath(effective.branchPathPrefix()))
                .addValue("nativePlace", blankToNull(effective.nativePlace()))
                .addValue("alive", effective.alive())
                .addValue("includeDeleted", effective.includeDeleted())
                .addValue("limit", boundedLimit(limit));

        List<Hit> hits = jdbc.query(SQL, params, (rs, rowNum) -> new Hit(
                rs.getObject("person_id", UUID.class),
                NameType.valueOf(rs.getString("name_type")),
                rs.getDouble("score")));
        log.debug("Tim kiem nhan khau: {} ket qua tho (chua loc rieng tu)", hits.size());
        return hits;
    }

    /**
     * Ép {@code branchPathPrefix} đi qua {@link BranchPath} trước khi vào câu truy vấn.
     *
     * <p>Không phải để chống injection — tham số hoá đã lo việc đó — mà để một chuỗi không hợp lệ
     * {@code ltree} (có dấu tiếng Việt, có khoảng trắng) trả về lỗi nghiệp vụ đọc được, thay vì
     * {@code 22P02: invalid ltree syntax} bật lên từ tận tầng driver.</p>
     */
    private static String validatedBranchPath(String raw) {
        String value = blankToNull(raw);
        return value == null ? null : BranchPath.of(value).value();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int boundedLimit(int requested) {
        if (requested <= 0) {
            return 1;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}

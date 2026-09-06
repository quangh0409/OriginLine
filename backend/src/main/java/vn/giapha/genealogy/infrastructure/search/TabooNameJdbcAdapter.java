package vn.giapha.genealogy.infrastructure.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.TabooConflict;
import vn.giapha.genealogy.domain.TabooMatchKind;
import vn.giapha.genealogy.domain.port.TabooNamePort;

/**
 * Hiện thực {@link TabooNamePort} — tra <b>kỵ húy</b> (FR-1.6) trên {@code person_name}.
 *
 * <h2>Vì sao so ở cột không dấu</h2>
 * Kiều bào gõ "Nguyen Van Tuan" và người trong nước gõ "Nguyễn Văn Tuân" phải cùng bị cảnh báo. Cột
 * {@code name_unaccented} là {@code GENERATED ALWAYS} từ {@code full_name} (V6) nên không bao giờ
 * lệch, và chỉ mục riêng {@code ix_person_name_huy_unaccented} phủ đúng nhánh {@code name_type =
 * 'HUY'} mà truy vấn này lọc.
 *
 * <h2>Ba mức va chạm, xếp theo bậc thang xác định</h2>
 * <ol>
 *   <li>{@link TabooMatchKind#EXACT} — trùng nguyên văn cả dấu (chỉ bỏ qua hoa/thường và khoảng
 *       trắng thừa). Cảnh báo mạnh nhất.</li>
 *   <li>{@link TabooMatchKind#UNACCENTED} — chỉ trùng sau khi bỏ dấu ("Tuân" vs "Tuấn"). Dễ báo
 *       nhầm nên xếp dưới.</li>
 *   <li>{@link TabooMatchKind#GIVEN_NAME} — trùng phần tên chính, bỏ họ và chữ đệm.</li>
 * </ol>
 * Xếp hạng làm ở Java trên các cờ do SQL tính, để định nghĩa "bỏ dấu" chỉ tồn tại ở một nơi duy nhất
 * là hàm {@code vn_unaccent} của CSDL — hai bản hiện thực bỏ dấu khác nhau giữa Java và SQL sẽ sinh
 * ra cảnh báo lúc có lúc không.
 *
 * <h2>Chỉ soi bậc trên</h2>
 * Kỵ húy là kiêng tên của <b>người trên</b>. Biết đời thứ dự kiến thì chỉ quét
 * {@code generation < generationOfNewPerson}; chưa biết thì buộc phải quét toàn dòng họ vì không
 * xác định được ai là bậc trên — thà cảnh báo thừa còn hơn bỏ sót, và đằng nào đây cũng chỉ là cảnh
 * báo có thể ghi đè.
 */
@Repository
public class TabooNameJdbcAdapter implements TabooNamePort {

    private static final Logger log = LoggerFactory.getLogger(TabooNameJdbcAdapter.class);

    /** Trần số dòng trả về: hộp thoại cảnh báo không ai đọc nổi hơn chục dòng. */
    private static final int MAX_CONFLICTS = 50;

    /**
     * Điều kiện lọc gồm hai nhánh, cả hai đều bám được chỉ mục:
     * <ul>
     *   <li>đẳng thức trên {@code name_unaccented} — dùng {@code ix_person_name_huy_unaccented};</li>
     *   <li>{@code LIKE '% ' || <tên chính>} — dùng được GIN trigram
     *       {@code ix_person_name_unaccented_trgm}. Có dấu cách trong mẫu là cố ý: không có nó thì
     *       "an" khớp cả "Tuấn", sinh ra một biển cảnh báo giả.</li>
     * </ul>
     */
    private static final String SQL = """
            WITH q AS (
                SELECT vn_unaccent(:candidate) AS un,
                       regexp_replace(vn_unaccent(:candidate), '^.*[[:space:]]', '') AS given_name
            )
            SELECT pn.person_id                                     AS person_id,
                   pn.full_name                                     AS taboo_name,
                   p.generation                                     AS generation,
                   (lower(btrim(pn.full_name)) = lower(btrim(:candidate))) AS exact_match,
                   (pn.name_unaccented = q.un)                      AS unaccented_match,
                   (SELECT d.full_name
                      FROM person_name d
                     WHERE d.person_id = p.id
                     ORDER BY d.is_primary DESC, d.created_at ASC
                     LIMIT 1)                                       AS display_name
              FROM person_name pn
              JOIN person p ON p.id = pn.person_id
              CROSS JOIN q
             WHERE pn.name_type = 'HUY'
               AND p.is_deleted = FALSE
               AND (CAST(:excludeId AS uuid) IS NULL OR pn.person_id <> CAST(:excludeId AS uuid))
               AND (CAST(:generation AS int) IS NULL
                    OR (p.generation IS NOT NULL AND p.generation < CAST(:generation AS int)))
               AND (pn.name_unaccented = q.un
                    OR pn.name_unaccented = q.given_name
                    OR pn.name_unaccented LIKE ('% ' || q.given_name))
             ORDER BY p.generation ASC NULLS LAST, pn.full_name ASC
             LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TabooNameJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TabooConflict> findConflicts(String candidateFullName, Integer generationOfNewPerson,
                                             UUID excludePersonId) {
        if (candidateFullName == null || candidateFullName.isBlank()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("candidate", candidateFullName.trim())
                .addValue("generation", generationOfNewPerson)
                .addValue("excludeId", excludePersonId)
                .addValue("limit", MAX_CONFLICTS);

        List<TabooConflict> conflicts = jdbc.query(SQL, params,
                (rs, rowNum) -> mapRow(rs, generationOfNewPerson));
        if (!conflicts.isEmpty()) {
            // Khong log ten: canh bao ky huy la du lieu pha he binh thuong, nhung so luong thi du de theo doi.
            log.debug("Ky huy: {} va cham voi bac tren (doi thu {})", conflicts.size(), generationOfNewPerson);
        }
        return conflicts;
    }

    private static TabooConflict mapRow(ResultSet rs, Integer generationOfNewPerson) throws SQLException {
        Integer ancestorGeneration = (Integer) rs.getObject("generation");
        TabooMatchKind kind = classify(rs.getBoolean("exact_match"), rs.getBoolean("unaccented_match"));
        return new TabooConflict(
                rs.getObject("person_id", UUID.class),
                rs.getString("display_name"),
                ancestorGeneration,
                rs.getString("taboo_name"),
                NameType.HUY,
                kind,
                relationHint(ancestorGeneration, generationOfNewPerson));
    }

    private static TabooMatchKind classify(boolean exact, boolean unaccented) {
        if (exact) {
            return TabooMatchKind.EXACT;
        }
        return unaccented ? TabooMatchKind.UNACCENTED : TabooMatchKind.GIVEN_NAME;
    }

    /**
     * Chuỗi chỉ để hiển thị. Cố ý mô tả bằng số đời thay vì đoán tên gọi quan hệ ("cụ", "ông") —
     * suy ra danh xưng là việc của context {@code kinship} với bộ luật cấu hình được, không phải của
     * một adapter tra cứu tên.
     */
    private static String relationHint(Integer ancestorGeneration, Integer newPersonGeneration) {
        if (ancestorGeneration == null) {
            return "Chua xac dinh doi thu";
        }
        if (newPersonGeneration == null) {
            return "Doi thu " + ancestorGeneration;
        }
        int distance = newPersonGeneration - ancestorGeneration;
        return "Doi thu " + ancestorGeneration + " (tren " + distance + " doi)";
    }
}

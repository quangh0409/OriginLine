package vn.giapha.genealogy.infrastructure.search;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.giapha.genealogy.application.ClanRootPort;

/**
 * Hiện thực {@link ClanRootPort} bằng JDBC thuần.
 *
 * <h2>Vì sao JDBC, không phải JPA</h2>
 * {@code BranchJpaEntity} cố ý chỉ đọc — javadoc của trường {@code path} ghi rõ: thêm đường ghi
 * qua JPA thì phải bổ sung phép ép {@code ?::ltree} chiều ghi, và một lỗi ở đó chỉ lộ ra khi đã có
 * dữ liệu thật. Chi được tạo bằng JDBC ở mọi chỗ khác (bộ nạp mẫu, fixture test), nên chỗ này theo
 * đúng đường ấy thay vì mở đường thứ hai.
 *
 * <p>Không có {@code @Transactional}: mọi câu ghi chạy trong transaction của {@code ClanRootService}
 * (và xa hơn là của lượt lưu hồ sơ dòng họ).</p>
 */
@Repository
public class ClanRootJdbcAdapter implements ClanRootPort {

    private static final String SELECT_ROOT = """
            SELECT b.id, b.path::text AS label, b.name, b.region
              FROM branch b
             WHERE nlevel(b.path) = 1
               AND b.is_deleted = FALSE
            """;

    private static final RowMapper<RootBranch> ROOT = (rs, i) -> new RootBranch(
            rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("name"),
            rs.getString("region"));

    private final NamedParameterJdbcTemplate jdbc;

    public ClanRootJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RootBranch> roots() {
        return jdbc.query(SELECT_ROOT + " ORDER BY b.path", new MapSqlParameterSource(), ROOT);
    }

    @Override
    public Optional<RootBranch> rootByLabel(String label) {
        return jdbc.query(SELECT_ROOT + " AND b.path = CAST(:label AS ltree)",
                new MapSqlParameterSource("label", label), ROOT).stream().findFirst();
    }

    @Override
    public boolean pathTaken(String path) {
        Boolean taken = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM branch WHERE path = CAST(:path AS ltree))",
                new MapSqlParameterSource("path", path), Boolean.class);
        return Boolean.TRUE.equals(taken);
    }

    @Override
    public RootBranch createRoot(UUID id, String label, String name, String region) {
        // slug = path = nhan: ck_branch_path_tail_is_slug doi nhan cuoi cua path bang slug, va voi
        // chi goc thi path chi co mot nhan.
        jdbc.update("""
                INSERT INTO branch (id, name, slug, path, parent_id, branch_kind, region)
                VALUES (:id, :name, :label, CAST(:label AS ltree), NULL, 'DONG_HO', :region)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("label", label)
                .addValue("region", region));
        return new RootBranch(id, label, name, region);
    }

    @Override
    public boolean updateRoot(UUID id, String name, String region) {
        // version + 1 bang tay: dong nay khong di qua Hibernate nen @Version khong tu nhich, ma mot
        // BranchJpaEntity dang nam trong bo nho cua transaction khac phai thay duoc la no da cu.
        int rows = jdbc.update("""
                UPDATE branch
                   SET name = :name, region = :region, version = version + 1
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("region", region));
        return rows > 0;
    }

    @Override
    public int relabelRoot(String oldLabel, String newLabel) {
        // subpath(path, 1) KHONG goi duoc tren path mot nhan (Postgres bao "invalid positions"),
        // nen chi goc di nhanh rieng trong CASE. Chi goc doi ca slug (nhan cuoi = slug); chi con
        // giu slug cua minh vi nhan cuoi cua chung khong doi.
        return jdbc.update("""
                UPDATE branch
                   SET path = CASE WHEN nlevel(path) = 1 THEN CAST(:newLabel AS ltree)
                                   ELSE CAST(:newLabel AS ltree) || subpath(path, 1) END,
                       slug = CASE WHEN nlevel(path) = 1 THEN :newLabel ELSE slug END,
                       version = version + 1
                 WHERE path <@ CAST(:oldLabel AS ltree)
                """, new MapSqlParameterSource()
                .addValue("oldLabel", oldLabel)
                .addValue("newLabel", newLabel));
    }

    @Override
    public long countPersons(boolean includeDeleted) {
        String sql = includeDeleted
                ? "SELECT count(*) FROM person"
                : "SELECT count(*) FROM person WHERE is_deleted = FALSE";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public long countBranchesUnder(String label) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM branch
                 WHERE path <@ CAST(:label AS ltree)
                   AND nlevel(path) > 1
                   AND is_deleted = FALSE
                """, new MapSqlParameterSource("label", label), Long.class);
        return count == null ? 0L : count;
    }
}

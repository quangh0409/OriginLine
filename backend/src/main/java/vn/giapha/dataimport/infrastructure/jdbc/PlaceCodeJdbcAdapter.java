package vn.giapha.dataimport.infrastructure.jdbc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.giapha.dataimport.domain.port.PlaceCodePort;

/**
 * Hiện thực {@link PlaceCodePort}: đọc danh mục mã tỉnh/quốc gia.
 *
 * <p>Danh mục nhỏ (vài chục dòng) và gần như không đổi, nhưng <b>không</b> cache trong bộ nhớ ở
 * đây: nó được nạp đúng một lần cho mỗi lần kiểm một lô, tức là vài lần một ngày. Cache một thứ
 * được gọi vài lần một ngày là thêm một nguồn dữ liệu cũ mà không đổi lấy gì.</p>
 */
@Repository
public class PlaceCodeJdbcAdapter implements PlaceCodePort {

    private final JdbcTemplate jdbc;

    public PlaceCodeJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> allCodes() {
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM place_division ORDER BY kind, sort_order, code", String.class);
        return new LinkedHashSet<>(codes);
    }
}

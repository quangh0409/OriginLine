package vn.giapha.demo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tra id của các <b>mốc neo</b> trong bộ dữ liệu giả — cầu nối để test assert trên id cụ thể.
 *
 * <p>Ví dụ tên mốc: {@code THUY_TO}, {@code DEEP_G7}, {@code DICH_TON_1}, {@code CON_NUOI_2},
 * {@code KY_HUY_1} / {@code KY_HUY_ANCESTOR_1}, {@code TUYET_TU_1}, {@code KE_TU_1},
 * {@code TIER3_1}. Danh sách đầy đủ xem các lời gọi {@code anchor(...)} trong
 * {@code ClanTreeGenerator}.</p>
 *
 * <h2>Vì sao đọc từ CSDL chứ không chạy lại generator</h2>
 * <p>Chạy lại generator để lấy id thì con số trả về là <b>id lẽ ra phải có</b>, không phải id đang
 * nằm trong CSDL. Hai thứ đó lệch nhau ngay khi ai đó nạp bộ dữ liệu bằng seed khác — và test sẽ
 * đỏ với thông báo "không tìm thấy nhân khẩu" thay vì thông báo đúng là "dữ liệu demo không khớp".
 * Đọc từ {@code person.attributes -> 'demo_anchor'} thì câu trả lời luôn là sự thật hiện tại.</p>
 */
@Component
@Profile("demo")
public class DemoFixtures {

    private final JdbcTemplate jdbc;

    DemoFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Id nhân khẩu của một mốc neo; rỗng nếu bộ dữ liệu demo chưa được nạp. */
    public Optional<UUID> personId(String anchorName) {
        return jdbc.query("""
                SELECT id FROM public.person
                WHERE attributes -> 'demo_anchor' @> CAST(? AS jsonb)
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.<UUID>empty(),
                "[\"" + requireSafe(anchorName) + "\"]");
    }

    /** Toàn bộ mốc neo hiện có trong CSDL, sắp theo tên. */
    public Map<String, UUID> all() {
        Map<String, UUID> anchors = new LinkedHashMap<>();
        jdbc.query("""
                SELECT anchor.value #>> '{}' AS name, p.id AS person_id
                FROM public.person p,
                     LATERAL jsonb_array_elements(p.attributes -> 'demo_anchor') AS anchor(value)
                ORDER BY 1
                """,
                rs -> {
                    anchors.put(rs.getString("name"), rs.getObject("person_id", UUID.class));
                });
        return anchors;
    }

    /**
     * Tên mốc neo đi vào một literal jsonb nên phải là định danh an toàn, không phải dữ liệu tự do.
     * Chỉ chữ hoa, số và gạch dưới — đúng quy ước đặt tên mốc của generator.
     */
    private static String requireSafe(String anchorName) {
        if (anchorName == null || !anchorName.matches("^[A-Z0-9_]{1,64}$")) {
            throw new IllegalArgumentException("Ten moc neo khong hop le: " + anchorName);
        }
        return anchorName;
    }
}

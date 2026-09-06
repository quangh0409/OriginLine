package vn.giapha.events.api.rest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.giapha.events.domain.EventType;

/**
 * Quy đổi loại sự kiện giữa <b>mã cơ sở dữ liệu</b> và <b>mã hợp đồng</b>.
 *
 * <h2>Hai tập mã này lệch nhau, và đó là một mâu thuẫn có thật giữa hai tài liệu</h2>
 * <table border="1">
 *   <caption>Ánh xạ hiện tại</caption>
 *   <tr><th>{@code ck_event_type} (V4)</th><th>{@code EventType} (OpenAPI)</th></tr>
 *   <tr><td>{@code GIO}</td><td>{@code GIO_THUONG}</td></tr>
 *   <tr><td>{@code GIO_TO}</td><td>{@code GIO_TO}</td></tr>
 *   <tr><td>{@code TE_LE}</td><td>{@code GIO_HO} nếu cấp dòng họ, ngược lại {@code GIO_CHI}</td></tr>
 *   <tr><td>{@code TAO_MO}</td><td>{@code CHAP_MA}</td></tr>
 *   <tr><td>{@code SINH_NHAT}</td><td>{@code MUNG_THO}</td></tr>
 *   <tr><td>{@code KHANH_THANH}, {@code HOP_HO}, {@code CUOI_HOI}, {@code KHAC}</td><td>{@code KHAC}</td></tr>
 * </table>
 *
 * <p><b>Hai điều cần người có thẩm quyền quyết:</b></p>
 * <ol>
 *   <li>{@code TIEU_TUONG} (giỗ đầu) và {@code DAI_TUONG} (giỗ hết) có trong hợp đồng nhưng
 *       <b>không có trong ràng buộc CHECK</b> của bảng, nên hiện <b>không thể</b> phát sinh. Đây là
 *       hai mốc tang lễ có thật và quan trọng trong tập quán Việt; nếu giữ chúng thì migration phải
 *       được sửa. Migration thuộc sở hữu của W1 nên W5 không tự thêm.</li>
 *   <li>Ánh xạ nhiều-thành-một ({@code KHANH_THANH}/{@code HOP_HO}/{@code CUOI_HOI} → {@code KHAC})
 *       là <b>mất mát thông tin</b>: giao diện không phân biệt được lễ khánh thành từ đường với buổi
 *       họp họ. Chấp nhận tạm để không phá hợp đồng đã chốt với frontend.</li>
 * </ol>
 *
 * <p>Chiều ngược lại (tham số lọc {@code eventType} của client) mở rộng thành <b>tập</b> mã cơ sở dữ
 * liệu — {@code KHAC} của hợp đồng khớp bốn giá trị bên dưới.</p>
 */
public final class EventTypeApiMapper {

    private static final Logger log = LoggerFactory.getLogger(EventTypeApiMapper.class);

    private EventTypeApiMapper() {
    }

    /** Mã cơ sở dữ liệu → mã hợp đồng. */
    public static String toApi(EventType type, boolean clanLevel) {
        return switch (type) {
            case GIO -> "GIO_THUONG";
            case GIO_TO -> "GIO_TO";
            case TE_LE -> clanLevel ? "GIO_HO" : "GIO_CHI";
            case TAO_MO -> "CHAP_MA";
            case SINH_NHAT -> "MUNG_THO";
            case KHANH_THANH, HOP_HO, CUOI_HOI, KHAC -> "KHAC";
        };
    }

    /**
     * Mã hợp đồng → tập mã cơ sở dữ liệu.
     *
     * @return tập rỗng nếu không có tham số lọc nào hợp lệ; mã lạ bị bỏ qua kèm log thay vì ném lỗi,
     *         để client cũ gửi mã đã bỏ không làm hỏng cả trang danh sách
     */
    public static List<EventType> toDomain(List<String> apiTypes) {
        if (apiTypes == null || apiTypes.isEmpty()) {
            return List.of();
        }
        Set<EventType> result = new LinkedHashSet<>();
        for (String raw : apiTypes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "GIO_THUONG" -> result.add(EventType.GIO);
                case "GIO_TO" -> result.add(EventType.GIO_TO);
                case "GIO_HO", "GIO_CHI" -> result.add(EventType.TE_LE);
                case "CHAP_MA" -> result.add(EventType.TAO_MO);
                case "MUNG_THO" -> result.add(EventType.SINH_NHAT);
                case "KHAC" -> {
                    result.add(EventType.KHANH_THANH);
                    result.add(EventType.HOP_HO);
                    result.add(EventType.CUOI_HOI);
                    result.add(EventType.KHAC);
                }
                case "TIEU_TUONG", "DAI_TUONG" ->
                    // Có trong hợp đồng nhưng chưa có trong ck_event_type: không dòng nào khớp được.
                        log.info("Bo qua bo loc eventType={}: chua co gia tri tuong ung trong CSDL", raw);
                default -> log.info("Bo qua bo loc eventType khong nhan dang duoc: {}", raw);
            }
        }
        return List.copyOf(result);
    }
}

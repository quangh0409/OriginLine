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
 * <h2>Từ V10 phép quy đổi là song ánh — không còn mã nào bị gộp</h2>
 * <table border="1">
 *   <caption>Ánh xạ hiện tại</caption>
 *   <tr><th>{@code ck_event_type} (V4 + V10)</th><th>Mã hợp đồng</th></tr>
 *   <tr><td>{@code GIO}</td><td>{@code GIO_THUONG}</td></tr>
 *   <tr><td>{@code GIO_TO}</td><td>{@code GIO_TO}</td></tr>
 *   <tr><td>{@code TE_LE}</td><td>{@code GIO_HO} nếu cấp dòng họ, ngược lại {@code GIO_CHI}</td></tr>
 *   <tr><td>{@code TIEU_TUONG}</td><td>{@code TIEU_TUONG}</td></tr>
 *   <tr><td>{@code DAI_TUONG}</td><td>{@code DAI_TUONG}</td></tr>
 *   <tr><td>{@code TAO_MO}</td><td>{@code CHAP_MA}</td></tr>
 *   <tr><td>{@code MUNG_THO}</td><td>{@code MUNG_THO}</td></tr>
 *   <tr><td>{@code SINH_NHAT}</td><td>{@code SINH_NHAT}</td></tr>
 *   <tr><td>{@code KHANH_THANH}</td><td>{@code KHANH_THANH}</td></tr>
 *   <tr><td>{@code HOP_HO}</td><td>{@code HOP_HO}</td></tr>
 *   <tr><td>{@code CUOI_HOI}</td><td>{@code CUOI_HOI}</td></tr>
 *   <tr><td>{@code KHAC}</td><td>{@code KHAC}</td></tr>
 * </table>
 *
 * <h2>Hai khoản nợ đã trả và vì sao chúng là nợ thật</h2>
 * <ol>
 *   <li><b>{@code KHANH_THANH}/{@code HOP_HO}/{@code CUOI_HOI} không còn gộp thành {@code KHAC}.</b>
 *       Gộp là mất thông tin không khôi phục được ở phía client: qua API, một buổi họp họ trông y hệt
 *       một đám cưới, nên màn "lịch việc họ" không lọc nổi đúng thứ nó cần hiển thị.</li>
 *   <li><b>{@code SINH_NHAT} không còn hoá thành {@code MUNG_THO}.</b> Mừng thọ là việc của cả họ với
 *       nghi lễ riêng theo mốc 60/70/80/90 tuổi; sinh nhật là việc của một nhà. V10 tách chúng thành
 *       hai giá trị cơ sở dữ liệu, ở đây chỉ việc chuyển thẳng.</li>
 * </ol>
 *
 * <p>{@code TE_LE} là ngoại lệ <b>duy nhất</b> còn lại của tính song ánh, và là ngoại lệ có lý: một
 * giá trị cơ sở dữ liệu tách làm hai mã hợp đồng theo cờ {@code is_clan_level}, nên thông tin không
 * mất đi đâu cả — nó chỉ đổi chỗ từ một cột sang một cột khác.</p>
 *
 * <p>Chiều ngược lại (tham số lọc {@code eventType} của client) vẫn trả về một <b>tập</b> vì
 * {@code GIO_HO} và {@code GIO_CHI} cùng ánh xạ về {@code TE_LE}.</p>
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
            case TIEU_TUONG -> "TIEU_TUONG";
            case DAI_TUONG -> "DAI_TUONG";
            case TAO_MO -> "CHAP_MA";
            case MUNG_THO -> "MUNG_THO";
            case SINH_NHAT -> "SINH_NHAT";
            case KHANH_THANH -> "KHANH_THANH";
            case HOP_HO -> "HOP_HO";
            case CUOI_HOI -> "CUOI_HOI";
            case KHAC -> "KHAC";
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
                case "TIEU_TUONG" -> result.add(EventType.TIEU_TUONG);
                case "DAI_TUONG" -> result.add(EventType.DAI_TUONG);
                case "CHAP_MA" -> result.add(EventType.TAO_MO);
                case "MUNG_THO" -> result.add(EventType.MUNG_THO);
                case "SINH_NHAT" -> result.add(EventType.SINH_NHAT);
                case "KHANH_THANH" -> result.add(EventType.KHANH_THANH);
                case "HOP_HO" -> result.add(EventType.HOP_HO);
                case "CUOI_HOI" -> result.add(EventType.CUOI_HOI);
                // KHAC nay chi con khop dung KHAC. Truoc V10 no no ra bon gia tri, va chinh viec no
                // ra ay la trieu chung cua phep gop da bi bo: loc "loai khac" ma ra ca dam cuoi.
                case "KHAC" -> result.add(EventType.KHAC);
                default -> log.info("Bo qua bo loc eventType khong nhan dang duoc: {}", raw);
            }
        }
        return List.copyOf(result);
    }
}

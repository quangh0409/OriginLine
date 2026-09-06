package vn.giapha.demo.generator;

import java.time.LocalDate;
import vn.giapha.shared.vo.LunarDate;

/**
 * Cổng quy đổi Dương → Âm cho generator.
 *
 * <h2>Vì sao vẫn giữ interface này khi context {@code calendar} đã xong</h2>
 * <p>Bản cài đặt thật là {@link CalendarLunarDateSource}, gọi
 * {@code vn.giapha.calendar.application.LunarCalendarService} — API công khai của context
 * {@code calendar}. Interface ở lại vì hai lý do:</p>
 * <ul>
 *   <li>{@link ClanTreeGenerator} là POJO thuần, không Spring; nó không tự đi lấy service được.</li>
 *   <li>Đây là <b>điểm duy nhất</b> trong module demo chạm tới âm lịch. Ai muốn đổi chính sách quy
 *       đổi chỉ phải đọc một chỗ.</li>
 * </ul>
 *
 * <p><b>Không</b> gọi thẳng {@code calendar.domain.LunarConverter}: {@code domain} là ruột của
 * context khác, và {@code ModularityTests} sẽ chặn ngay. Ngoài ra thuật toán để trần thì mỗi nơi
 * gọi sẽ tự chọn múi giờ — chính là loại sai làm cả họ giỗ lệch một ngày.</p>
 */
@FunctionalInterface
public interface LunarDateSource {

    /**
     * Quy đổi một ngày dương lịch sang âm lịch Việt Nam.
     *
     * @return {@code null} khi không quy đổi được (trước năm lịch cổ tái lập được, hoặc ngoài dải
     *         thuật toán bảo đảm). Người gọi phải xử lý tường minh — <b>tuyệt đối không đoán</b>.
     */
    LunarDate toLunar(LocalDate solar);
}

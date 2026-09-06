package vn.giapha.genealogy.infrastructure.lunar;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.genealogy.domain.port.LunarCalendarPort;
import vn.giapha.shared.vo.LunarDate;

/**
 * Nối {@link LunarCalendarPort} của {@code genealogy} sang API công khai của context
 * {@code calendar} ({@link LunarCalendarService}, W4).
 *
 * <p>Cảnh báo "hiện thực mặc định của Giai đoạn 1 không quy đổi" trong javadoc của cổng nay đã hết
 * hiệu lực: W4 xong nên adapter này quy đổi thật. Nguyên tắc đằng sau nó thì còn nguyên giá trị —
 * thà trả {@code null} còn hơn đoán — nhưng nguyên tắc ấy được thi hành ở
 * {@code LunarCalendarService}, không phải ở đây.</p>
 *
 * <h2>Vì sao lớp này mỏng đến vậy</h2>
 * Chọn múi giờ theo thời điểm (gia phả chép ngày âm theo cuốn lịch lưu hành lúc đó) và quyết định
 * trả {@code null} ở các ca không quy đổi được là <b>kiến thức của context {@code calendar}</b>.
 * Để chúng ở đây thì {@code events} (W5, sinh lịch nhắc giỗ) sẽ phải chép lại một bản thứ hai, rồi
 * hai bản lệch nhau — triệu chứng là hồ sơ hiển thị một ngày còn thông báo giỗ báo một ngày khác.
 * Adapter này vì vậy chỉ làm đúng việc của một adapter: đổi tên cổng.
 */
@Component
public class LunarCalendarAdapter implements LunarCalendarPort {

    private final LunarCalendarService lunarCalendar;

    public LunarCalendarAdapter(LunarCalendarService lunarCalendar) {
        this.lunarCalendar = Objects.requireNonNull(lunarCalendar);
    }

    @Override
    public LunarDate toLunar(LocalDate solar) {
        return lunarCalendar.toLunar(solar);
    }

    @Override
    public LocalDate toSolar(LunarDate lunar) {
        return lunarCalendar.toSolar(lunar);
    }
}

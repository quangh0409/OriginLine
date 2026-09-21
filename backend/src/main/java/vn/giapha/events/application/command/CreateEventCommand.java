package vn.giapha.events.application.command;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.events.domain.EventType;
import vn.giapha.shared.vo.LunarDate;

/**
 * Yêu cầu tạo một sự kiện dòng họ — {@code POST /api/v1/events}.
 *
 * <h2>Ngày âm là mặc định, và đúng một nguồn ngày được chọn</h2>
 * Việc họ tính theo lịch âm: lễ Tết, chạp mả, giỗ chạp đều là ngày âm. {@code solarDate} có mặt cho
 * số ít việc vốn được ấn định theo dương lịch (một buổi họp mặt chốt ngày 2/9). <b>Chỉ một trong
 * hai được gửi.</b> Nhận cả hai rồi tự chọn một cái để lưu là cách tạo ra hai ngày không bao giờ
 * đồng bộ lại được với nhau — và người dùng sẽ tin vào cái mà giao diện tình cờ hiển thị.
 *
 * @param lunarDate  ngày âm; {@code year} chỉ bắt buộc khi {@code recurringAnnually = false}
 * @param solarDate  ngày dương, thay cho {@code lunarDate}
 * @param recurringAnnually lặp lại hằng năm (chạp mả, giỗ Tổ, lễ Tết) hay xảy ra đúng một lần
 *                          (khánh thành từ đường)
 * @param personId   nhân khẩu chủ thể; <b>bắt buộc</b> với giỗ cá nhân ({@code ck_event_gio_has_person})
 */
public record CreateEventCommand(EventType type,
                                 String title,
                                 String description,
                                 LunarDate lunarDate,
                                 LocalDate solarDate,
                                 boolean recurringAnnually,
                                 EventScope scope,
                                 UUID personId,
                                 String location) {

    public boolean lunarBased() {
        return lunarDate != null;
    }
}

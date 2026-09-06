package vn.giapha.events.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.EventType;
import vn.giapha.shared.vo.LunarDate;

/**
 * Chọn ngày âm <b>hiệu lực</b> của một sự kiện.
 *
 * <h2>{@code person.death_lunar} là nguồn chân lý của ngày giỗ</h2>
 * BA v2 chốt điều này, và {@code V4__events.sql} chép lại nguyên văn trong chú thích bảng
 * {@code event}. Bảng {@code event} vẫn có cột {@code lunar_date} vì sự kiện không phải giỗ
 * (chạp mả, tế lễ, họp họ) cần một ngày âm của riêng nó.
 *
 * <p>Hệ quả: với {@link EventType#GIO}, khi hai giá trị lệch nhau thì <b>hồ sơ nhân khẩu thắng</b>.
 * Người trong họ sửa ngày mất trên hồ sơ và mong cả họ được nhắc theo ngày mới; bắt họ nhớ sửa
 * thêm một bản ghi sự kiện là thiết kế sai. Lệch nhau được ghi {@code WARN} để đội vận hành đồng bộ
 * lại, chứ không sửa ngầm dữ liệu ở đây — sửa dữ liệu trong một job chạy đêm là việc phải có audit
 * và có người duyệt.</p>
 */
final class EffectiveLunarDate {

    private static final Logger log = LoggerFactory.getLogger(EffectiveLunarDate.class);

    private EffectiveLunarDate() {
    }

    /**
     * @param subject ảnh chụp nhân khẩu chủ thể; {@code null} với sự kiện cấp dòng họ không gắn ai
     * @return ngày âm dùng để quy đổi, hoặc {@code null} nếu không có nguồn nào
     */
    static LunarDate of(Event event, EventSubject subject) {
        LunarDate fromEvent = event.lunarDate();
        if (event.type() != EventType.GIO || subject == null) {
            return fromEvent;
        }
        // Người CÒN SỐNG thì không có giỗ - trả null để OccurrenceResolver bỏ qua hẳn.
        //
        // Ca này xảy ra sau một lần đính chính "thật ra cụ còn sống": `person.death_lunar` được gỡ,
        // nhưng ban ghi `event` kieu GIO van giu ngay am cua rieng no. Neu van lui ve `fromEvent`
        // thi ca chi/nganh tiep tuc nhan nhac gio cua mot nguoi dang song - loi khong chi sai ma
        // con rat mat long trong boi canh dong ho.
        if (subject.alive()) {
            if (fromEvent != null) {
                log.warn("Su kien gio {} van con ngay am {} nhung chu the {} da duoc dinh chinh la"
                                + " CON SONG - bo qua, khong sinh lich nhac. Can xoa hoac doi kieu"
                                + " ban ghi su kien nay.",
                        event.id(), fromEvent, subject.personId());
            }
            return null;
        }
        if (subject.deathLunar() == null) {
            // Đã mất nhưng khuyết ngày: gia phả cổ rất hay như vậy. Lúc này ngày âm chép trong bảng
            // `event` là nguồn duy nhất, và nó hợp lệ.
            return fromEvent;
        }
        LunarDate fromPerson = subject.deathLunar();
        if (fromEvent != null && !sameAnniversary(fromEvent, fromPerson)) {
            log.warn("Su kien gio {} ghi ngay am {} nhung person.death_lunar cua {} la {}."
                            + " Lay theo ho so nhan khau (nguon chan ly) - can dong bo lai ban ghi su kien.",
                    event.id(), fromEvent, subject.personId(), fromPerson);
        }
        return fromPerson;
    }

    /** So ngày–tháng–cờ nhuận, bỏ qua năm: giỗ lặp hằng năm nên năm âm gốc không tham gia so sánh. */
    private static boolean sameAnniversary(LunarDate left, LunarDate right) {
        return left.day() == right.day()
                && left.month() == right.month()
                && left.leapMonth() == right.leapMonth();
    }
}

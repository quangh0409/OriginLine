package vn.giapha.events.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.EventType;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.notification.application.LocalizedText;
import vn.giapha.shared.vo.LunarDate;

/**
 * Dựng tiêu đề và nội dung của một lượt nhắc, <b>song ngữ VI/EN</b>.
 *
 * <h2>Có dấu, không phải ASCII (sửa 24/09)</h2>
 * Chuỗi ở đây từng viết KHÔNG DẤU — "Con 7 ngay toi Gio ...", "Ngay 16/09/2026 duong lich" —
 * theo thói quen của dòng log (log trong dự án cố ý ASCII). Nhưng đây là chữ NGƯỜI DÙNG đọc, trong
 * trung tâm thông báo và trên màn hình khoá qua Web Push; cả hai kênh đều hiển thị Unicode đầy
 * đủ, và dự án không có kênh SMS nào cần bảng mã GSM 7-bit. Một lời nhắc giỗ không dấu, đặt cạnh
 * tên cụ có dấu, là thứ đầu tiên người lớn tuổi nhìn thấy mỗi sáng. Nếu mai kia thêm SMS, bỏ dấu ở
 * adapter của kênh ấy, không phải ở đây.
 *
 * <h2>Ba quyết định về nội dung</h2>
 * <ol>
 *   <li><b>Nói rõ khi ngày bị lệch.</b> Nếu ngày âm chép trong gia phả không tồn tại năm nay và
 *       {@link OccurrenceResolver} đã lùi sớm, câu nhắc phải giải thích. Đưa ra một ngày khác với
 *       sổ mà không nói lý do là cách nhanh nhất để cả họ mất tin vào hệ thống.</li>
 *   <li><b>Cả hai lịch trong một câu.</b> Người lớn tuổi nhớ ngày âm, người trẻ và kiều bào sống
 *       theo ngày dương. Bỏ một trong hai là bỏ một nửa người dùng.</li>
 *   <li><b>Không có dữ liệu Tầng 3.</b> Không số điện thoại, không email, không địa chỉ nhà, không
 *       ngày sinh đầy đủ. Thông báo hiện trên màn hình khoá thiết bị và đi qua hạ tầng bên thứ ba —
 *       hợp đồng OpenAPI nói thẳng điều này và Nghị định 13/2023 là lý do.</li>
 * </ol>
 */
@Component
public class ReminderMessageFactory {

    private static final DateTimeFormatter SOLAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OccurrenceResolver occurrences;

    public ReminderMessageFactory(OccurrenceResolver occurrences) {
        this.occurrences = occurrences;
    }

    /** Tiêu đề: "Còn 3 ngày tới giỗ cụ Nguyễn Văn Đức". */
    public LocalizedText title(Event event, EventSubject subject, ReminderJob job) {
        String name = displayName(event, subject);
        return LocalizedText.of(
                countdownVi(job.offsetDays()) + " " + name,
                countdownEn(job.offsetDays()) + " " + name);
    }

    /** Nội dung: ngày song lịch, địa điểm, và lý do lệch ngày nếu có. */
    public LocalizedText body(Event event, EventSubject subject, ReminderJob job) {
        LocalDate due = job.dueSolarDate();
        LunarDate actualLunar = occurrences.lunarDateOf(due);
        LunarDate expectedLunar = EffectiveLunarDate.of(event, subject);

        StringBuilder vi = new StringBuilder();
        StringBuilder en = new StringBuilder();
        vi.append("Ngày ").append(SOLAR.format(due)).append(" dương lịch");
        en.append("On ").append(SOLAR.format(due));
        if (actualLunar != null) {
            vi.append(" (").append(lunarVi(actualLunar)).append(" âm lịch)");
            en.append(" (lunar ").append(actualLunar.day()).append('/').append(actualLunar.month())
                    .append(actualLunar.leapMonth() ? " leap" : "").append(')');
        }
        vi.append('.');
        en.append('.');

        if (event.location() != null && !event.location().isBlank()) {
            vi.append(" Địa điểm: ").append(event.location()).append('.');
            en.append(" Venue: ").append(event.location()).append('.');
        }

        String shift = shiftNote(event, expectedLunar, actualLunar);
        if (shift != null) {
            vi.append(' ').append(shift);
            en.append(" The recorded lunar date does not occur this year; the rite is observed on the"
                    + " nearest earlier day.");
        }
        return LocalizedText.of(vi.toString(), en.toString());
    }

    /** Đường dẫn tương đối trong ứng dụng, dùng chung cho in-app và Web Push. */
    public String deepLink(Event event) {
        return "/events/" + event.id();
    }

    /**
     * Câu giải thích khi ngày âm năm nay khác ngày âm trong sổ.
     *
     * @return {@code null} khi không lệch hoặc khi không quy đổi ngược được (không đoán bừa)
     */
    private static String shiftNote(Event event, LunarDate expected, LunarDate actual) {
        if (!event.isLunarBased() || expected == null || actual == null) {
            return null;
        }
        boolean same = expected.day() == actual.day()
                && expected.month() == actual.month()
                && expected.leapMonth() == actual.leapMonth();
        if (same) {
            return null;
        }
        return "Năm nay không có " + lunarVi(expected) + " nên giỗ được tính vào "
                + lunarVi(actual) + " âm lịch.";
    }

    private static String lunarVi(LunarDate lunar) {
        return "ngày " + lunar.day() + " tháng " + lunar.month() + (lunar.leapMonth() ? " nhuận" : "");
    }

    private static String countdownVi(int offsetDays) {
        return switch (offsetDays) {
            case 0 -> "Hôm nay là";
            case 1 -> "Ngày mai là";
            default -> "Còn " + offsetDays + " ngày tới";
        };
    }

    private static String countdownEn(int offsetDays) {
        return switch (offsetDays) {
            case 0 -> "Today is";
            case 1 -> "Tomorrow is";
            default -> offsetDays + " days until";
        };
    }

    /**
     * Tên hiển thị của sự kiện.
     *
     * <p>Ưu tiên {@code event.title} do người trong họ tự đặt — họ biết cách gọi các cụ đúng hơn bất
     * kỳ mẫu chuỗi nào. Chỉ tự ghép khi trống.</p>
     */
    private static String displayName(Event event, EventSubject subject) {
        if (event.title() != null && !event.title().isBlank()) {
            return event.title().trim();
        }
        String label = typeLabel(event.type());
        if (subject != null && subject.displayName() != null && !subject.displayName().isBlank()) {
            return label + " " + subject.displayName();
        }
        return label;
    }

    private static String typeLabel(EventType type) {
        return switch (type) {
            case GIO -> "giỗ";
            case GIO_TO -> "giỗ Tổ";
            case TE_LE -> "tế lễ";
            case TIEU_TUONG -> "giỗ đầu";
            case DAI_TUONG -> "giỗ hết";
            case TAO_MO -> "chạp mả";
            case KHANH_THANH -> "lễ khánh thành";
            case HOP_HO -> "buổi họp họ";
            case SINH_NHAT -> "sinh nhật";
            case MUNG_THO -> "lễ mừng thọ";
            case CUOI_HOI -> "lễ cưới";
            case KHAC -> "sự kiện dòng họ";
        };
    }
}

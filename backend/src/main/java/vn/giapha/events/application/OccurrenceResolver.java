package vn.giapha.events.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventOccurrence;
import vn.giapha.events.domain.OccurrenceAdjustment;
import vn.giapha.shared.vo.LunarDate;

/**
 * Quy đổi "ngày âm chép trong gia phả" thành "ngày dương của năm nay", <b>kèm chính sách thay thế
 * khi ngày âm ấy không tồn tại</b>.
 *
 * <h2>Phép quy đổi đi qua đâu</h2>
 * Luôn gọi {@link LunarCalendarService} — mặt tiền công khai ({@code @NamedInterface}) của context
 * {@code calendar}. Gọi thẳng {@code calendar.domain.LunarConverter} thì mỗi context sẽ tự chọn múi
 * giờ và tự xử ca lỗi một kiểu, và triệu chứng sẽ là "hồ sơ hiển thị một ngày, thông báo giỗ báo
 * một ngày khác". Ngoài ra {@code LunarConverter} nằm trong package nội bộ của {@code calendar},
 * chạm vào là {@code ModularityTests} đỏ.
 *
 * <h2>Chính sách khi ngày âm không tồn tại: giỗ trước, không giỗ sau</h2>
 * {@code LunarCalendarService.toSolar} trả {@code null} ở hai ca có thật và không hiếm:
 * <ul>
 *   <li><b>Mùng 30 của tháng thiếu.</b> Tháng âm dài 29 hoặc 30 ngày tuỳ năm; cụ mất ngày 30 tháng
 *       Chạp năm đủ thì rất nhiều năm sau đó không có ngày ấy.</li>
 *   <li><b>Tháng nhuận biến mất.</b> Sự kiện chép vào tháng 5 nhuận chỉ gặp lại đúng tháng ấy sau
 *       vài năm.</li>
 * </ul>
 *
 * <p>{@code calendar} cố ý không tự chọn ngày thay thế: đó là <b>chính sách nhắc giỗ</b>, thuộc về
 * context này. Chính sách đã chọn, theo thứ tự ưu tiên:</p>
 * <ol>
 *   <li>tháng nhuận không có ⇒ dùng <b>tháng thường cùng số</b>;</li>
 *   <li>không có ngày 30 ⇒ dùng <b>ngày 29</b> (đúng tập quán: tháng thiếu thì giỗ ngày 29);</li>
 *   <li>vẫn không quy đổi được ⇒ <b>lùi dần về ngày trước đó</b>, tối đa hai ngày.</li>
 * </ol>
 *
 * <p><b>Nguyên tắc xuyên suốt: chỉ được lùi sớm, tuyệt đối không đẩy muộn.</b> Trong tập quán Việt,
 * cúng giỗ sớm một ngày (cáo giỗ, giỗ trước) là chấp nhận được và vẫn hay làm; cúng sau ngày giỗ
 * thì không. Chọn hướng lùi cũng khiến mọi phương án thay thế đều nằm <i>trước</i> ngày gốc, nên
 * nhắc sớm vẫn còn kịp. Ghi chú {@link OccurrenceAdjustment} đi kèm để thông báo nói rõ vì sao
 * lệch — đưa ra một ngày khác với sổ mà không giải thích là cách nhanh nhất để cả họ mất tin vào
 * hệ thống.</p>
 *
 * <p><b>Không bao giờ im lặng bỏ qua.</b> Hết mọi phương án thì ghi {@code WARN} kèm id sự kiện;
 * bỏ qua âm thầm nghĩa là cả họ không được nhắc giỗ mà không ai biết vì sao.</p>
 */
@Component
public class OccurrenceResolver {

    private static final Logger log = LoggerFactory.getLogger(OccurrenceResolver.class);

    /** Số ngày tối đa được phép lùi sớm ở bước cuối. Lùi quá xa thì đó không còn là ngày giỗ ấy nữa. */
    private static final int MAX_BACKWARD_DAYS = 2;

    private final LunarCalendarService lunarCalendar;

    public OccurrenceResolver(LunarCalendarService lunarCalendar) {
        this.lunarCalendar = lunarCalendar;
    }

    /** Năm âm lịch tương ứng một ngày dương; {@code null} khi ngoài dải quy đổi được. */
    public Integer lunarYearOf(LocalDate solar) {
        LunarDate lunar = lunarDateOf(solar);
        return lunar == null ? null : lunar.year();
    }

    /**
     * Ngày âm tương ứng một ngày dương; {@code null} khi không quy đổi được.
     *
     * <p>Dùng lúc dựng nội dung thông báo: so ngày âm <i>thật</i> của ngày giỗ năm nay với ngày âm
     * chép trong sổ để biết có phải nói rõ lý do lệch hay không. Nhờ vậy không phải lưu thêm cột
     * "lý do lệch" vào {@code reminder_job} — bảng thuộc sở hữu của W1.</p>
     */
    public LunarDate lunarDateOf(LocalDate solar) {
        return lunarCalendar.toLunar(solar);
    }

    /**
     * <b>Mọi</b> lần xảy ra của một sự kiện trong tầm nhìn — một chỗ duy nhất biết luật "lặp hằng
     * năm hay xảy ra đúng một lần".
     *
     * <p>Phép chọn năm này trước đây nằm rải ở hai nơi ({@code EventQueryService} và
     * {@code ReminderBatchGenerator}) và <b>cả hai đều sai giống nhau</b>: chúng quét mọi năm trong
     * tầm nhìn cho <i>mọi</i> sự kiện theo âm lịch, kể cả sự kiện một lần. Trước lối ghi thủ công
     * thì không ai thấy, vì bảng chỉ chứa giỗ và giỗ thì lặp; ngay khi Trưởng chi tạo được một lễ
     * khánh thành thì nó hiện trên lịch <b>mỗi năm một lần cho tới vô tận</b>. Gom về đây để câu
     * trả lời chỉ có một bản.</p>
     *
     * @param effective  ngày âm <b>hiệu lực</b> (xem {@link EffectiveLunarDate}); bỏ qua với sự
     *                   kiện theo dương lịch
     * @param lunarYears các năm âm cần xét khi sự kiện lặp hằng năm
     * @param solarYears các năm dương cần xét khi sự kiện theo dương lịch và lặp hằng năm
     */
    public List<EventOccurrence> resolveAll(Event event, LunarDate effective,
                                            List<Integer> lunarYears, List<Integer> solarYears) {
        List<EventOccurrence> found = new ArrayList<>();
        if (event.isLunarBased()) {
            if (event.isRecurring()) {
                for (int lunarYear : lunarYears) {
                    resolveLunar(event, effective, lunarYear).ifPresent(found::add);
                }
            } else if (effective == null || effective.year() <= 0) {
                // ck_event_oneoff_lunar_year (V18) chặn ca này ở CSDL, nhưng dòng cũ ghi trước V18
                // vẫn có thể rơi vào đây. Bỏ qua kèm WARN thay vì đoán một năm nào đó: đoán sai thì
                // cả họ được nhắc một cái lễ không có thật.
                log.warn("Su kien mot lan {} theo am lich nhung ngay am hieu luc khong co nam ({})"
                        + " - khong xac dinh duoc lan xay ra nao", event.id(), effective);
            } else {
                resolveLunar(event, effective, effective.year()).ifPresent(found::add);
            }
            return List.copyOf(found);
        }
        if (!event.isRecurring()) {
            // resolveSolar bo qua tham so nam khi su kien khong lap - tra dung ngay chep trong so.
            resolveSolar(event, event.solarDate() == null ? 0 : event.solarDate().getYear())
                    .ifPresent(found::add);
            return List.copyOf(found);
        }
        for (int solarYear : solarYears) {
            resolveSolar(event, solarYear).ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    /**
     * Lần xảy ra của sự kiện trong một năm âm lịch.
     *
     * @param event     sự kiện
     * @param lunarDate ngày âm <b>hiệu lực</b>: với giỗ, đây là {@code person.death_lunar} chứ không
     *                  phải giá trị chép trong bảng {@code event} (xem {@link EffectiveLunarDate})
     * @param lunarYear năm âm lịch cần quy đổi
     */
    public Optional<EventOccurrence> resolveLunar(Event event, LunarDate lunarDate, int lunarYear) {
        if (lunarDate == null) {
            log.warn("Su kien {} theo am lich nhung khong co ngay am hieu luc - khong sinh duoc lich nhac",
                    event.id());
            return Optional.empty();
        }
        for (Candidate candidate : candidates(lunarDate, lunarYear)) {
            LocalDate solar = lunarCalendar.toSolar(candidate.lunar());
            if (solar != null) {
                if (candidate.adjustment().isAdjusted()) {
                    log.info("Su kien {}: ngay am {} khong ton tai nam {} -> dung {} ({})",
                            event.id(), lunarDate, lunarYear, candidate.lunar(), candidate.adjustment());
                }
                return Optional.of(new EventOccurrence(solar, solar.getYear(), candidate.lunar(),
                        candidate.adjustment()));
            }
        }
        log.warn("Su kien {}: khong quy doi duoc ngay am {} sang duong lich nam am {} sau khi da thu"
                        + " thang thuong, ngay 29 va lui toi {} ngay. KHONG co lich nhac cho lan nay -"
                        + " can nguoi kiem tra lai ngay am cua ho so.",
                event.id(), lunarDate, lunarYear, MAX_BACKWARD_DAYS);
        return Optional.empty();
    }

    /**
     * Lần xảy ra của sự kiện <b>theo dương lịch</b> trong một năm dương.
     *
     * <p>29/02 của năm không nhuận lùi về 28/02 — cùng nguyên tắc chỉ lùi sớm.</p>
     */
    public Optional<EventOccurrence> resolveSolar(Event event, int solarYear) {
        LocalDate source = event.solarDate();
        if (source == null) {
            log.warn("Su kien {} theo duong lich nhung thieu solar_date", event.id());
            return Optional.empty();
        }
        if (!event.isRecurring()) {
            return Optional.of(new EventOccurrence(source, source.getYear(), null, OccurrenceAdjustment.EXACT));
        }
        int day = source.getDayOfMonth();
        int lastDay = LocalDate.of(solarYear, source.getMonth(), 1).lengthOfMonth();
        OccurrenceAdjustment adjustment = day <= lastDay
                ? OccurrenceAdjustment.EXACT : OccurrenceAdjustment.SHIFTED_EARLIER;
        LocalDate solar = LocalDate.of(solarYear, source.getMonth(), Math.min(day, lastDay));
        return Optional.of(new EventOccurrence(solar, solar.getYear(), null, adjustment));
    }

    /**
     * Danh sách phương án theo thứ tự ưu tiên. {@link LinkedHashSet} khử trùng lặp (ví dụ ngày âm
     * vốn đã không nhuận thì phương án bỏ nhuận trùng với phương án gốc) mà vẫn giữ nguyên thứ tự.
     */
    private static List<Candidate> candidates(LunarDate lunar, int lunarYear) {
        Set<LunarDate> seen = new LinkedHashSet<>();
        List<Candidate> result = new ArrayList<>(8);
        // 1. Đúng như sổ chép.
        add(result, seen, new LunarDate(lunarYear, lunar.month(), lunar.day(), lunar.leapMonth()),
                OccurrenceAdjustment.EXACT);
        // 2. Tháng nhuận không có năm nay -> tháng thường cùng số (luôn đứng trước tháng nhuận).
        if (lunar.leapMonth()) {
            add(result, seen, new LunarDate(lunarYear, lunar.month(), lunar.day(), false),
                    OccurrenceAdjustment.LEAP_MONTH_ABSENT);
        }
        // 3. Tháng thiếu, không có ngày 30 -> ngày 29.
        if (lunar.day() == 30) {
            add(result, seen, new LunarDate(lunarYear, lunar.month(), 29, lunar.leapMonth()),
                    OccurrenceAdjustment.SHORT_MONTH);
            add(result, seen, new LunarDate(lunarYear, lunar.month(), 29, false),
                    OccurrenceAdjustment.SHORT_MONTH);
        }
        // 4. Cùng đường: lùi sớm dần, không bao giờ đẩy muộn.
        for (int back = 1; back <= MAX_BACKWARD_DAYS; back++) {
            int day = lunar.day() - back;
            if (day < 1) {
                break;
            }
            add(result, seen, new LunarDate(lunarYear, lunar.month(), day, lunar.leapMonth()),
                    OccurrenceAdjustment.SHIFTED_EARLIER);
            add(result, seen, new LunarDate(lunarYear, lunar.month(), day, false),
                    OccurrenceAdjustment.SHIFTED_EARLIER);
        }
        return result;
    }

    private static void add(List<Candidate> target, Set<LunarDate> seen, LunarDate lunar,
                            OccurrenceAdjustment adjustment) {
        if (seen.add(lunar)) {
            target.add(new Candidate(lunar, adjustment));
        }
    }

    private record Candidate(LunarDate lunar, OccurrenceAdjustment adjustment) {
    }
}

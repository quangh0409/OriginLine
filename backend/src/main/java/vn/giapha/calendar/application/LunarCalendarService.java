package vn.giapha.calendar.application;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.calendar.domain.LunarConverter;
import vn.giapha.calendar.domain.NoSuchLunarDateException;
import vn.giapha.calendar.domain.VietnamLunarZone;
import vn.giapha.shared.vo.LunarDate;

/**
 * <b>API công khai của context {@code calendar}</b>: quy đổi Âm ↔ Dương cho các context khác.
 *
 * <p>Thuật toán Hồ Ngọc Đức nằm ở {@code calendar.domain} và là POJO tĩnh, zero-dependency. Lớp này
 * không tính toán gì thêm — nó <b>đóng gói chính sách</b> quanh thuật toán đó, và đó là lý do nó
 * tồn tại thay vì để mỗi context tự gọi {@code LunarConverter}:</p>
 *
 * <ul>
 *   <li>chọn múi giờ theo thời điểm (xem bên dưới) — sai một lần là sai ở mọi context;</li>
 *   <li>quyết định trả {@code null} thay vì ném lỗi ở các ca không quy đổi được;</li>
 *   <li>ghi log cảnh báo giai đoạn hai miền dùng hai lịch.</li>
 * </ul>
 *
 * <p>Cả {@code genealogy} (điền vế còn thiếu của mốc song lịch) lẫn {@code events} (sinh lịch nhắc
 * giỗ, W5) đều cần đúng ba chính sách này. Nhân bản chúng ra hai nơi thì sớm muộn hai nơi lệch
 * nhau, và triệu chứng sẽ là "hồ sơ hiển thị một ngày, thông báo giỗ báo một ngày khác".</p>
 *
 * <h2>Múi giờ đi theo thời điểm, không phải theo hằng số</h2>
 * Không gọi {@link LunarConverter#toLunar(LocalDate)} (cố định GMT+7) mà lấy múi giờ từ
 * {@link VietnamLunarZone}. Lý do là nghiệp vụ gia phả chứ không phải kỹ thuật: ngày âm chép trong
 * gia phả là ngày của <b>cuốn lịch đang lưu hành lúc đó</b>. Cụ mất năm 1965 thì ngày âm ấy tính
 * theo múi giờ thứ 8; quy đổi lại bằng GMT+7 ra một ngày khác với ngày trong sổ, và từ đó cả họ
 * giỗ lệch. Đây là loại sai không bao giờ tự lộ ra.
 *
 * <h2>Vì sao trả {@code null} chứ không ném lỗi</h2>
 * Ngày sinh/mất trong gia phả cũ vốn khuyết và mơ hồ; một hồ sơ không quy đổi được là chuyện
 * thường, không phải sự cố. Nhưng <b>đoán</b> thì tuyệt đối không: ngày giỗ sai không ném lỗi,
 * không ghi log, chỉ lặng lẽ khiến cả họ đi giỗ nhầm ngày. Trả {@code null} ở:
 * <ul>
 *   <li>trước năm {@value VietnamLunarZone#FIRST_RECONSTRUCTIBLE_YEAR} — lịch cổ Việt Nam không
 *       tái lập được bằng công thức thiên văn;</li>
 *   <li>ngoài dải {@value LunarConverter#MIN_SUPPORTED_YEAR}–{@value LunarConverter#MAX_SUPPORTED_YEAR}
 *       mà {@code LunarConverter} bảo đảm;</li>
 *   <li>ngày âm không tồn tại trong năm đó — mùng 30 của tháng thiếu, hoặc tháng nhuận của một năm
 *       không nhuận tháng ấy. Chọn ngày thay thế là một <b>chính sách nhắc giỗ</b> và thuộc về
 *       {@code events}, không thuộc về phép quy đổi.</li>
 * </ul>
 */
@Service
public class LunarCalendarService {

    private static final Logger log = LoggerFactory.getLogger(LunarCalendarService.class);

    /** Âm lịch tương ứng một ngày dương; {@code null} khi không quy đổi được. */
    public LunarDate toLunar(LocalDate solar) {
        if (solar == null) {
            return null;
        }
        if (!VietnamLunarZone.isReconstructible(solar)) {
            log.debug("Bo qua quy doi am lich cho {}: truoc {} phai tra bang lich co",
                    solar, VietnamLunarZone.FIRST_RECONSTRUCTIBLE_YEAR);
            return null;
        }
        warnIfTwoCalendars(solar);
        try {
            return LunarConverter.toLunar(solar, VietnamLunarZone.offsetHoursFor(solar));
        } catch (RuntimeException ex) {
            log.warn("Khong quy doi duoc ngay duong {} sang am lich: {}", solar, ex.getMessage());
            return null;
        }
    }

    /** Ngày dương tương ứng một ngày âm; {@code null} khi không quy đổi được. */
    public LocalDate toSolar(LunarDate lunar) {
        if (lunar == null) {
            return null;
        }
        if (lunar.year() < VietnamLunarZone.FIRST_RECONSTRUCTIBLE_YEAR) {
            log.debug("Bo qua quy doi duong lich cho nam am {}: truoc {} phai tra bang lich co",
                    lunar.year(), VietnamLunarZone.FIRST_RECONSTRUCTIBLE_YEAR);
            return null;
        }
        try {
            // Nam am va nam duong lech nhau toi da khoang mot thang ruoi, khong du de nhay qua moc
            // doi mui gio 1968 tru dung vai ngay giap Tet — sai lech con lai la mot ngay, chap nhan
            // duoc de doi lay viec khong phai quy doi hai lan chi de biet nam duong.
            LocalDate solar =
                    LunarConverter.toSolar(lunar, VietnamLunarZone.offsetHoursForYear(lunar.year()));
            warnIfTwoCalendars(solar);
            return solar;
        } catch (NoSuchLunarDateException ex) {
            log.debug("Ngay am {} khong ton tai trong nam do: {}", lunar, ex.getMessage());
            return null;
        } catch (RuntimeException ex) {
            log.warn("Khong quy doi duoc ngay am {} sang duong lich: {}", lunar, ex.getMessage());
            return null;
        }
    }

    /**
     * 1968–1975 hai miền dùng hai lịch chính thức khác nhau. Kết quả trả về theo lịch miền Bắc.
     *
     * <p><b>Vì sao DEBUG chứ không phải INFO:</b> đây là thuộc tính của <i>dữ liệu</i>, không phải
     * một sự kiện bất thường của hệ thống. Một dòng họ có vài chục người mất trong giai đoạn đó thì
     * mỗi lần nạp danh sách hay dựng cây sẽ đẩy ra hàng trăm dòng log giống hệt nhau — nạp bộ dữ
     * liệu demo một lần đã sinh ~150 dòng. Log ồn tới mức đó thì không ai đọc nữa, và nó che mất
     * những dòng thật sự đáng chú ý.</p>
     *
     * <p>Vẫn giữ lại chứ không xoá: khi dòng họ khiếu nại "ngày giỗ sai một ngày", bật
     * {@code logging.level.vn.giapha.calendar=DEBUG} là thấy ngay có phải ca này không.</p>
     */
    private static void warnIfTwoCalendars(LocalDate solar) {
        if (log.isDebugEnabled() && VietnamLunarZone.hasTwoOfficialCalendars(solar)) {
            log.debug("Ngay {} roi vao giai doan hai mien dung hai lich chinh thuc khac nhau"
                    + " (1968-1975); ket qua theo lich mien Bac, nen hoi lai gia dinh", solar);
        }
    }
}

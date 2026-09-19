package vn.giapha.dataimport.infrastructure.lunar;

import java.time.LocalDate;
import org.springframework.stereotype.Component;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.dataimport.domain.port.LunarDatePort;
import vn.giapha.shared.vo.LunarDate;

/**
 * Hiện thực {@link LunarDatePort} bằng <b>mặt tiền công khai</b> của context lịch.
 *
 * <h2>Không tự chép một thuật toán âm lịch thứ hai</h2>
 * Cám dỗ ở đây rất thật: câu hỏi "tháng 8 năm Ất Dậu có 29 hay 30 ngày" nghe như một hàm ba dòng.
 * Nhưng nếu đường ống nhập liệu có bản tính riêng thì nó sẽ lệch với bản mà bộ nhắc giỗ dùng, và
 * triệu chứng là bộ kiểm bảo ngày hợp lệ còn thông báo giỗ bắn vào một ngày khác — không ai truy
 * ra được vì sao. Gọi đúng service đã có còn biến mỗi lần nhập liệu thành một bài kiểm chéo cho
 * bộ quy đổi.
 *
 * <h2>Cách hỏi: quy đổi ngược</h2>
 * {@code LunarCalendarService.toSolar} trả {@code null} khi ngày âm ấy <b>không tồn tại</b> trong
 * năm ấy — mùng 30 của tháng thiếu, hoặc tháng nhuận của một năm không nhuận tháng đó. Đó chính là
 * câu trả lời ta cần, nên không cần thêm API nào.
 *
 * <p><b>Bẫy:</b> {@code toSolar} cũng trả {@code null} cho năm trước 1813, vì lịch cổ Việt Nam
 * không tái lập được bằng công thức thiên văn. Hai cái {@code null} ấy mang hai nghĩa hoàn toàn
 * khác nhau, và gộp chúng lại sẽ báo "ngày không tồn tại" cho mọi cụ tổ đời đầu — đúng những người
 * mà dòng họ giỗ trọng thể nhất. Vì vậy {@link #kiemDuoc(int)} tồn tại và phải được gọi trước.</p>
 */
@Component
public class LunarDateAdapter implements LunarDatePort {

    /**
     * Năm âm sớm nhất còn tái lập được. Khớp {@code VietnamLunarZone.FIRST_RECONSTRUCTIBLE_YEAR}
     * — chép hằng số thay vì import, vì {@code calendar.domain} không phải mặt tiền công khai.
     */
    private static final int NAM_SOM_NHAT = 1813;

    /** Trần trên của bộ quy đổi. Khớp {@code LunarConverter.MAX_SUPPORTED_YEAR}. */
    private static final int NAM_MUON_NHAT = 2199;

    private final LunarCalendarService lunar;

    public LunarDateAdapter(LunarCalendarService lunar) {
        this.lunar = lunar;
    }

    @Override
    public boolean kiemDuoc(int lunarYear) {
        return lunarYear >= NAM_SOM_NHAT && lunarYear <= NAM_MUON_NHAT;
    }

    @Override
    public boolean tonTai(int lunarYear, int month, int day, boolean leap) {
        if (!kiemDuoc(lunarYear)) {
            // Khong kiem duoc thi KHONG duoc phep ket luan la sai. Im lang la cau tra loi trung
            // thuc duy nhat; goi ham nay khi kiemDuoc() sai la loi cua ben goi.
            return true;
        }
        LocalDate solar = lunar.toSolar(new LunarDate(lunarYear, month, day, leap));
        return solar != null;
    }
}

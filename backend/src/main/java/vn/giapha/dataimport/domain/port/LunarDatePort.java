package vn.giapha.dataimport.domain.port;

/**
 * Cổng hỏi lịch âm: <b>ngày này có tồn tại không</b>.
 *
 * <h2>Vì sao câu hỏi lại quan trọng đến thế</h2>
 * Ngày giỗ sai thì <b>nhắc giỗ sai</b> — mà nhắc giỗ là lý do dòng họ mở ứng dụng. Và ngày âm có
 * hai cách không tồn tại mà người nhập không cách nào biết trước: mùng 30 của một tháng thiếu, và
 * tháng nhuận của một năm không nhuận tháng ấy. Cả hai đều trông hoàn toàn bình thường trên giấy.
 *
 * <p>Hiện thực gọi {@code vn.giapha.calendar.application.LunarCalendarService} — mặt tiền công
 * khai của context lịch. <b>Không</b> chạm {@code calendar.domain}: tự chép một thuật toán âm lịch
 * thứ hai thì sớm muộn hai bản lệch nhau, và triệu chứng sẽ là hồ sơ hiển thị một ngày còn thông
 * báo giỗ báo một ngày khác.</p>
 */
public interface LunarDatePort {

    /**
     * Năm âm này có kiểm chứng được bằng thuật toán không.
     *
     * <p>Trước 1813 lịch cổ Việt Nam không tái lập được bằng công thức thiên văn, nên câu trả lời
     * duy nhất trung thực là không biết — và một cụ tổ đời thứ nhất rất hay rơi vào khoảng đó.
     * Trả về false thì luật ngày âm <b>bỏ qua</b> dòng ấy, thay vì báo sai là ngày không tồn tại.</p>
     */
    boolean kiemDuoc(int lunarYear);

    /** True nếu ngày âm ấy thật sự có trong năm ấy. */
    boolean tonTai(int lunarYear, int month, int day, boolean leap);
}

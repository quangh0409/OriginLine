package vn.giapha.clan.domain;

/**
 * Ngày giỗ tổ theo <b>âm lịch</b> — ngày và tháng, không năm (giỗ lặp lại hằng năm).
 *
 * <p>Không mang cờ tháng nhuận: giỗ tổ được làm theo tháng thường, năm có tháng nhuận trùng tháng
 * ấy thì vẫn làm ở tháng thường. Ngày 30 hợp lệ ở đây dù tháng âm có thể chỉ 29 ngày: việc dời
 * sang ngày 29 là việc của bộ lập lịch nhắc, không phải của hồ sơ — hồ sơ ghi đúng điều dòng họ
 * nói.</p>
 */
public record GioTo(int lunarDay, int lunarMonth) {

    public GioTo {
        if (lunarDay < 1 || lunarDay > 30) {
            throw new IllegalArgumentException("gioTo.lunarDay phải từ 1 đến 30");
        }
        if (lunarMonth < 1 || lunarMonth > 12) {
            throw new IllegalArgumentException("gioTo.lunarMonth phải từ 1 đến 12");
        }
    }
}

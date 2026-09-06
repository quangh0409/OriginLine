package vn.giapha.calendar.domain;

import vn.giapha.shared.exception.DomainException;

/**
 * Ngày âm lịch được yêu cầu <b>không tồn tại</b> trong năm âm lịch đó.
 *
 * <p>Hai kịch bản thật hay gặp khi tính giỗ:</p>
 * <ul>
 *   <li>ngày giỗ ghi ở một <b>tháng nhuận</b>, nhưng năm cần tính lại không nhuận tháng ấy —
 *       phần lớn các năm đều như vậy, vì một tháng nhuận trung bình 19 năm mới lặp lại;</li>
 *   <li>ngày giỗ là <b>30</b> nhưng năm cần tính tháng đó chỉ có 29 ngày (tháng thiếu).</li>
 * </ul>
 *
 * <p>Ném lỗi thay vì âm thầm trả về ngày của tháng kế tiếp là quyết định có chủ đích: sai một tháng
 * nghĩa là cả dòng họ đi giỗ nhầm ngày mà không ai biết. Bên gọi phải chọn cách lùi một cách tường
 * minh — {@link LunarAnniversary} làm đúng việc đó với chính sách được ghi rõ.</p>
 */
public class NoSuchLunarDateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public NoSuchLunarDateException(String message) {
        super("LUNAR_CONVERSION_FAILED", message);
    }
}

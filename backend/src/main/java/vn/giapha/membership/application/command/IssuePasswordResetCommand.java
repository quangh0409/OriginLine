package vn.giapha.membership.application.command;

import java.util.UUID;

/**
 * Lệnh: cấp lại liên kết đặt mật khẩu cho một thành viên.
 *
 * <h2>Khoá là NHÂN KHẨU, không phải tài khoản</h2>
 * Trưởng chi làm việc trên phả đồ: họ chọn <i>cụ Bảy ở Chi Giáp</i>, không chọn một dòng
 * {@code app_user} nào cả. Nhận {@code appUserId} ở đây sẽ buộc giao diện phải tra ngược
 * nhân khẩu → tài khoản, tức là mở thêm một endpoint chỉ để làm việc đó — và endpoint ấy
 * liệt kê được tài khoản của người khác.
 *
 * <p>Quan trọng hơn: phép kiểm phạm vi chi đọc {@code ltree} của <b>nhân khẩu</b>. Khoá vào bằng
 * nhân khẩu thì đối tượng của phép kiểm và đối tượng của lệnh là một; khoá vào bằng tài khoản thì
 * giữa chúng có một phép tra, và mọi phép tra ở giữa là một chỗ để lệch.</p>
 *
 * @param personId nhân khẩu của người quên mật khẩu
 * @param note     ghi chú của người cấp (ví dụ "cụ gọi điện sáng 12/3"), vào {@code audit_log}.
 *                 <b>Không</b> ghi số điện thoại hay địa chỉ vào đây — đó là dữ liệu Tầng 3 và
 *                 {@code audit_log} là bảng chỉ ghi thêm.
 */
public record IssuePasswordResetCommand(UUID personId, String note) {

    public IssuePasswordResetCommand(UUID personId) {
        this(personId, null);
    }
}

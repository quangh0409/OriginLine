package vn.giapha.membership.application.command;

import java.util.UUID;

/**
 * Yêu cầu phát một lời mời.
 *
 * <p>Chỉ có {@code personId}: Trưởng chi đã chọn đích danh người mình mời trong phả, và hệ thống
 * <b>không nên bắt họ nói điều đó hai lần</b> (không hỏi lại tên, không hỏi lại chi, không hỏi lại
 * số điện thoại — những thứ ấy đã nằm trong hồ sơ).</p>
 *
 * <p><b>Cố ý không có trường vai trò.</b> Nhận lời mời cho ra một tài khoản đã gắn nhân khẩu, ở vai
 * Thành viên; cấp vai {@code BRANCH_HEAD}/{@code COUNCIL} vẫn là việc của
 * {@code RoleAssignmentService}. Nếu lời mời chở theo vai trò thì một Trưởng chi phát được lời mời
 * mang vai Hội đồng, và toàn bộ luật "chỉ ADMIN mới cấp được ADMIN" bị đi vòng qua một cửa sau.</p>
 *
 * @param personId  nhân khẩu sẽ được ghép khi lời mời được nhận
 * @param ttlDays   số ngày hiệu lực; {@code null} lấy mặc định của hệ thống (7 ngày)
 * @param note      ghi chú của người mời, chỉ hiện trong danh sách quản trị
 */
public record IssueInvitationCommand(UUID personId, Integer ttlDays, String note) {

    public IssueInvitationCommand(UUID personId) {
        this(personId, null, null);
    }
}

package vn.giapha.genealogy.application.view;

import java.util.UUID;

/**
 * Một dòng <b>danh bạ dòng họ</b>: người còn sống đã tự mở ít nhất một nhóm trường cho người đang
 * xem.
 *
 * <h2>Trường vắng mặt là câu trả lời cuối cùng</h2>
 * {@code occupation} / {@code currentPlaceProvince} / {@code avatarKey} là {@code null} khi chủ thể
 * <b>chưa mở</b> nhóm tương ứng <i>cho đúng người gọi này</i> — và cũng {@code null} khi họ đã mở
 * nhưng không điền gì. Hai nguyên nhân ấy cố ý <b>không phân biệt được</b>: thêm bất kỳ cờ nào cho
 * phép phân biệt là phá đúng điều mà bộ lọc riêng tư đang bảo vệ. Giao diện không được vẽ ô trống
 * thay cho trường vắng.
 *
 * <p>Ba trường đầu ({@code displayName}, {@code generation}, {@code primaryBranch}) là dữ liệu phả
 * hệ Tầng 1, không thuộc nhóm nào và không có công tắc — mọi thành viên đã đăng nhập đều thấy. Nếu
 * một người xuất hiện trong danh bạ thì ba trường ấy luôn có; thứ họ điều khiển là ba trường sau.
 *
 * @param avatarKey khoá đối tượng trong MinIO, <b>không</b> phải URL đã ký. Đặt tên theo đúng thứ
 *                  nó chứa; việc dựng URL là của tầng phát media.
 */
public record DirectoryEntryView(UUID personId,
                                 String displayName,
                                 Integer generation,
                                 BranchRef primaryBranch,
                                 String occupation,
                                 String currentPlaceProvince,
                                 String avatarKey) {
}

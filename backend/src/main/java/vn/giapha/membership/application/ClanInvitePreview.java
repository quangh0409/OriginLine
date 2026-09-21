package vn.giapha.membership.application;

import java.time.Instant;

/**
 * Nội dung màn "mã này của dòng họ nào" — trả về cho người <b>chưa có tài khoản</b>.
 *
 * <h2>Hai trường, và không có trường thứ ba</h2>
 * Đây là khác biệt lớn nhất giữa mã dòng họ và mã cá nhân. {@code InvitationPreview} trả về
 * <b>tên một người đang sống</b>, và đó là một ngoại lệ có chủ ý của BA v2 §10, trả giá bằng ba lớp
 * chống đỡ. Ở đây thì không có gì để trả: mã dòng họ không trỏ vào ai. Nên nó chỉ nói tên dòng họ
 * (dữ liệu công khai, đã nằm trong {@code BranchRef} mà Khách vẫn đọc được) và hạn dùng.
 *
 * <p><b>Cố ý không trả bộ đếm.</b> {@code useCount} và {@code remainingUses} là công cụ giám sát
 * của Hội đồng, không phải thông tin cho người cầm mã. Nói "mã này còn 3 lượt" cho một người chưa
 * đăng nhập là nói cho kẻ dò biết mình đang ở đâu, và nói "đã dùng 400 lượt" là xác nhận giúp họ
 * rằng mã đang lan.</p>
 *
 * <p><b>Và cố ý không trả nhãn mã.</b> {@code label} ("Nhóm Zalo họ Nguyễn 2026") là ghi chú nội
 * bộ để Hội đồng nhận ra kênh phát; đưa ra ngoài là nói cho người cầm mã biết hệ thống đang theo
 * dõi mã đi đường nào.</p>
 *
 * @param clanName  tên gốc của dòng họ, để người nhận mã qua một tin nhắn chuyển tiếp biết mã của
 *                  họ nào; {@code null} khi dòng họ chưa được khởi tạo
 * @param expiresAt hạn dùng — người ta cần biết còn kịp đăng ký không
 */
public record ClanInvitePreview(String clanName, Instant expiresAt) {
}

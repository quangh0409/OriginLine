package vn.giapha.content.application.command;

import vn.giapha.content.domain.ContentStatus;

/**
 * Tham số truy vấn danh sách bài.
 *
 * @param status {@code null} = mọi trạng thái mà người gọi được thấy. Lưu ý đây <b>không</b> phải
 *        "mọi trạng thái": luật ai thấy gì chạy trong SQL, xem {@code PostRepository.search}
 * @param mine chỉ bài do <b>chính người đang đăng nhập</b> viết — màn "Bài của tôi".
 *        <p>Lọc <b>trong SQL</b>, không ở client. Không có tham số này thì giao diện buộc phải bắn
 *        bốn lượt gọi song song (một cho mỗi trạng thái) rồi lọc tiếp, giới hạn 50 mỗi loại — và
 *        một người viết nhiều sẽ <b>mất bài cũ mà không có gì báo</b>, vì phân trang của máy chủ
 *        đếm trên toàn bộ tập còn phép lọc của client chỉ thấy trang đầu.</p>
 *        <p>"Tôi" ở đây là <b>tài khoản</b> ({@code author_user_id}), không phải nhân khẩu: câu
 *        hỏi của màn ấy là "tôi đã viết gì", và người hỏi là người đang đăng nhập.</p>
 */
public record PostQuery(ContentStatus status, boolean mine, int page, int size) {
}

package vn.giapha.media.application.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phiếu tải lên: trả lời của {@code POST /api/v1/media/upload-tickets}.
 *
 * <h2>Vì sao phiếu chở luôn CÁC TRẦN ĐANG ÁP DỤNG</h2>
 * Để giao diện không phải chép lại con số nào. Một trần được viết cứng trong JavaScript sẽ lệch
 * khỏi {@code MediaLimits} đúng vào ngày ai đó chỉnh một bên — và triệu chứng là thứ tệ nhất:
 * giao diện nhận tệp, người dùng chờ hết 100 MiB tải lên, rồi bước xác nhận mới từ chối. Chở con
 * số xuống nghĩa là màn hình chặn được <b>trước khi</b> tốn băng thông, mà vẫn chỉ có một nguồn
 * chân lý.
 *
 * @param uploadUrl URL {@code PUT} đã ký. Client tải <b>thẳng lên MinIO</b>, không qua backend
 * @param mediaId khoá của phiếu; dùng cho bước xác nhận và cho việc gắn tệp vào bài
 * @param mediaKey khoá đối tượng trên kho. Trả xuống để đối chiếu khi đi truy sự cố; client
 *        <b>không</b> cần nó cho luồng thường, và cầm nó cũng không đọc được gì — bucket để
 *        private, lối đọc duy nhất là một URL ký lại sau khi kiểm quyền
 * @param maxBytes trần dung lượng áp cho loại tệp này
 * @param maxDurationSeconds trần thời lượng; {@code null} với ảnh
 * @param acceptedContentTypes danh sách kiểu MIME được nhận — để giao diện đặt thuộc tính
 *        {@code accept} của ô chọn tệp. Đây là <b>gợi ý cho người dùng</b>, không phải phép kiểm:
 *        phép kiểm thật là chữ ký byte đọc ngược về từ kho
 */
public record MediaTicketView(String uploadUrl,
                              UUID mediaId,
                              String mediaKey,
                              Instant expiresAt,
                              long maxBytes,
                              Integer maxDurationSeconds,
                              List<String> acceptedContentTypes) {
}

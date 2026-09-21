package vn.giapha.content.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.content.domain.ContentStatus;

/**
 * Bài viết trên dây — <b>hợp đồng đã chốt với frontend, không đổi hình dạng</b>.
 *
 * @param authorDisplayName tên tác giả <b>đã qua bộ lọc riêng tư của người đọc</b>; vắng mặt khi
 *        người đọc không được biết tác giả tồn tại (hồ sơ đã xoá mềm). Giao diện phải chịu được
 *        trường này rỗng — bài viết vẫn ở lại vì nó là tiếng nói của dòng họ, không phải tài sản
 *        riêng của một hồ sơ
 * @param canReview người đọc hiện tại có duyệt được bài này không. <b>Nói về người đọc</b>, không
 *        nói về bài; có nó thì giao diện không phải chép lại luật phạm vi {@code ltree} bằng
 *        JavaScript để quyết định vẽ nút "Duyệt"
 * @param version dùng cho {@code If-Match}. Cũng là giá trị của {@code ETag}
 * @param reviewedBy <b>khóa tài khoản</b> ({@code app_user.id}) của người duyệt, không phải
 *        khóa nhân khẩu — cùng quy ước với {@code change_request.reviewer_id}. Dùng để đối
 *        chiếu nhật ký, không dùng để hiển thị
 * @param reviewedByDisplayName tên người duyệt, <b>đã qua đúng bộ lọc riêng tư của
 *        người đọc</b>. Checklist §2 đòi ghi rõ "ai duyệt và lúc nào", mà {@code reviewedBy} một
 *        mình chỉ là một khóa tài khoản nên màn hình không bao giờ hiện nổi cái tên.
 *        {@code null} khi người đọc không được biết người ấy tồn tại, hoặc khi tài khoản
 *        duyệt chưa gắn nhân khẩu nào. Giao diện hiện "đã duyệt" trơn ở ca đó — <b>tuyệt
 *        đối không bịa một chuỗi thay thế</b>, vì "một ai đó" và "bác Ba" là hai thông điệp
 *        khác nhau về trách nhiệm
 *
 * @param media <b>danh sách</b> tệp đính kèm, đúng thứ tự, kiểu {@code IMAGE}/{@code VIDEO}, mỗi
 *        phần tử kèm một URL đã ký hạn 10 phút. Rỗng khi bài không có tệp nào — không bao giờ
 *        vắng mặt
 *
 * <h2>Vì sao là một DANH SÁCH, không phải một {@code coverImageKey}</h2>
 * <p>Bản nháp đầu của hợp đồng này (V17) có một trường {@code coverImageKey} và nó đã bị gỡ với
 * một lý do đo được: backend khi ấy không có SDK S3/MinIO nào, nên trường ấy chỉ nhận được giá trị
 * do client tự bịa. Điều kiện tiên quyết mà V17 nêu đích danh — "SDK MinIO + bucket + chính sách
 * URL đã ký" — <b>đã xong</b> ở đợt này, nên trường ảnh quay lại. Nhưng nó quay lại ở đúng hình
 * dạng của nghiệp vụ: một bài kể chuyện lễ giỗ có <i>nhiều</i> tấm ảnh, và thứ tự của chúng là một
 * phần của câu chuyện. Một trường "ảnh bìa" sẽ buộc giao diện phải bịa ra một quy ước ("tấm đầu
 * tiên là bìa") ngay trong tuần đầu.</p>
 *
 * <p><b>Hệ quả về bộ nhớ đệm:</b> {@code media[].url} là URL <i>đã ký</i>, nên
 * {@code Cache-Control: no-store} + {@code Vary: Authorization} mà {@code PostController} đặt sẵn
 * không còn chỉ là chuyện của {@code authorDisplayName} nữa — thiếu chúng là một tệp riêng tư được
 * phục vụ lại cho người dùng sau trên cùng một máy.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostDto(UUID id,
                      String title,
                      String body,
                      ContentStatus status,
                      UUID authorPersonId,
                      String authorDisplayName,
                      UUID branchId,
                      String branchName,
                      Instant publishedAt,
                      UUID reviewedBy,
                      String reviewedByDisplayName,
                      Instant reviewedAt,
                      String rejectReason,
                      Instant createdAt,
                      Instant updatedAt,
                      long version,
                      boolean canReview,
                      boolean canEdit,
                      List<PostMediaDto> media) {
}

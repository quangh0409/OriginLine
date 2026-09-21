package vn.giapha.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.media.application.view.MediaAssetView;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Post;

/**
 * Bài viết ở dạng tầng {@code api} tiêu thụ được — aggregate đã ghép với <b>tên tác giả đã qua bộ
 * lọc riêng tư</b> và tên chi.
 *
 * <p>Ghép ở tầng application chứ không ở api: tên tác giả là kết quả của một lời gọi sang
 * {@code genealogy}, và nó phải giống nhau cho mọi lối ra (REST hôm nay, GraphQL hay bản tin ngày
 * mai). Tầng api chỉ đổi kiểu record này sang JSON.</p>
 *
 * @param authorDisplayName tên tác giả <b>đã lọc theo người đọc</b>; {@code null} khi người đọc
 *        không được biết tác giả tồn tại (hồ sơ đã xoá mềm). Bài viết vẫn ở lại — nó là tiếng nói
 *        của dòng họ, không phải tài sản riêng của một hồ sơ
 * @param canReview người đọc hiện tại có duyệt được bài này không — <b>nói về người đọc</b>, không
 *        nói về bài. Có nó thì giao diện không phải đoán xem có vẽ nút "Duyệt" hay không, và không
 *        phải tự chép lại luật phạm vi {@code ltree} bằng JavaScript
 * @param canEdit  người đọc có sửa được không (là tác giả, và bài đang ở {@code DRAFT})
 * @param media    <b>danh sách</b> tệp đính kèm, đúng thứ tự, mỗi phần tử đã kèm URL đã ký. Là
 *        một danh sách chứ không phải một trường "ảnh bìa": một bài kể chuyện lễ giỗ có nhiều
 *        tấm ảnh và thứ tự của chúng là một phần của câu chuyện. Rỗng khi bài không có tệp nào —
 *        <b>không bao giờ {@code null}</b>, để giao diện khỏi viết nhánh dự phòng
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
 */
public record PostView(UUID id,
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
                       List<MediaAssetView> media) {

    public PostView {
        media = media == null ? List.of() : List.copyOf(media);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public static PostView of(Post post, String authorDisplayName, String reviewerDisplayName,
                              String branchName, boolean canReview, boolean canEdit,
                              List<MediaAssetView> media) {
        return new PostView(post.id(), post.title(), post.body(),
                post.status(), post.authorPersonId(), authorDisplayName, post.branchId(),
                branchName, post.publishedAt(), post.reviewedBy(), reviewerDisplayName,
                post.reviewedAt(), post.rejectReason(), post.createdAt(), post.updatedAt(),
                post.version(), canReview, canEdit, media);
    }
}

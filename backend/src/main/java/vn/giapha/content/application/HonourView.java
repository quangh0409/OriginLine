package vn.giapha.content.application;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Honour;
import vn.giapha.content.domain.HonourKind;

/**
 * Một bản ghi vinh danh ở dạng tầng {@code api} tiêu thụ được.
 *
 * <p><b>Bản ghi nào lọt tới đây thì người đọc đã được phép xem</b> — phép lọc theo nhóm trường
 * riêng tư thứ sáu ({@code PrivacyFieldGroup.HONOUR}) chạy ở {@code HonourService} <i>trước</i> khi
 * view được dựng. Không có trạng thái "view đã dựng nhưng chưa lọc", nên không có chỗ nào để quên
 * gọi bộ lọc.</p>
 *
 * @param personDisplayName tên chủ thể, <b>đã lọc theo người đọc</b>; {@code null} khi người đọc
 *        không được biết nhân khẩu ấy tồn tại
 * @param canReview người đọc có duyệt được bản ghi này không — nói về người đọc, không nói về bản ghi
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
public record HonourView(UUID id,
                         UUID personId,
                         String personDisplayName,
                         HonourKind kind,
                         String title,
                         Integer year,
                         String issuer,
                         String description,
                         ContentStatus status,
                         UUID branchId,
                         String branchName,
                         UUID reviewedBy,
                         String reviewedByDisplayName,
                         Instant reviewedAt,
                         String rejectReason,
                         Instant createdAt,
                         Instant updatedAt,
                         long version,
                         boolean canReview) {

    public static HonourView of(Honour honour, String personDisplayName,
                                String reviewerDisplayName, UUID branchId, String branchName,
                                boolean canReview) {
        return new HonourView(honour.id(), honour.personId(), personDisplayName, honour.kind(),
                honour.title(), honour.year(), honour.issuer(), honour.description(),
                honour.status(), branchId, branchName, honour.reviewedBy(), reviewerDisplayName,
                honour.reviewedAt(), honour.rejectReason(), honour.createdAt(),
                honour.updatedAt(), honour.version(), canReview);
    }
}

package vn.giapha.content.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.HonourKind;

/**
 * Một bản ghi vinh danh trên dây — <b>hợp đồng đã chốt với frontend</b>.
 *
 * <p>Bản ghi nào ra tới đây thì người đọc <b>đã</b> được phép xem: bộ lọc nhóm trường riêng tư thứ
 * sáu chạy ở {@code HonourService} trước khi DTO được dựng. Không có cờ nào cho phép phân biệt
 * "bị giấu" với "không có" — đó là nguyên tắc của BA v2 §10 và nó áp cho cả context này.</p>
 *
 * @param personDisplayName tên chủ thể, đã lọc theo người đọc
 * @param branchId chi <b>hiện tại</b> của chủ thể, suy lúc đọc — không phải một giá trị chụp lại.
 *        Xem javadoc của {@code Honour}
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HonourDto(UUID id,
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
}

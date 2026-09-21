package vn.giapha.membership.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.membership.application.PersonClaimView;
import vn.giapha.membership.domain.ClaimDuplicateSuspect;
import vn.giapha.shared.vo.Gender;

/**
 * Một đơn tự nhận, nhìn từ màn của người gửi và từ hàng chờ của Trưởng chi.
 *
 * <h2>{@code phone} và {@code introduction} chỉ đi tới hai loại người</h2>
 * Người gửi (xem đơn của chính mình) và người duyệt <b>đúng phạm vi chi</b>. Không có lối thứ ba —
 * mở cho "mọi thành viên đã đăng nhập" là biến hàng chờ duyệt thành một danh bạ.
 *
 * <h2>Không mang tên hay năm sinh của nhân khẩu được nhận</h2>
 * Chỉ {@code personId}. Nhân khẩu ấy có thể là một người <b>còn sống</b>, và ai được xem gì về họ
 * là câu hỏi của bộ lọc phân tầng riêng tư — thứ chạy ở
 * {@code GET /api/v1/persons/&#123;id&#125;}, không phải ở đây. Giao diện cầm khoá rồi gọi sang,
 * <b>dưới phiên của chính người đang đọc</b>: một cái tên chép sẵn vào đây sẽ là tên đã lọc theo
 * quyền của người <i>gửi đơn</i>, không phải người duyệt.
 */
@Schema(description = "Đơn tự nhận mình trong phả")
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("java:S107")
public record PersonClaimDto(
        UUID id,

        @Schema(description = "EXISTING = nhận một nhân khẩu đã có; NEW_PERSON = xin được thêm vào phả",
                allowableValues = {"EXISTING", "NEW_PERSON"})
        String kind,

        @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"})
        String status,

        UUID requestedBy,

        @Schema(description = "Tên hiển thị của **tài khoản** gửi đơn (tự khai lúc đăng ký), để"
                + " Trưởng chi đối chiếu với phần tự giới thiệu. Không phải tên trong phả.")
        String requesterDisplayName,

        @Schema(description = "Đơn thứ mấy của người này. Lần thứ nhất là chuyện thường; lần thứ tư"
                + " trên bốn nhân khẩu khác nhau là một tín hiệu hoàn toàn khác.",
                example = "1")
        int attemptNo,

        @Schema(description = "Nhân khẩu được nhận. Chỉ đơn EXISTING. Giao diện gọi"
                + " GET /persons/{id} **dưới phiên của chính người đang đọc** để lấy chi tiết.")
        UUID personId,

        @Schema(description = "Người thân đã có trong phả. Chỉ đơn NEW_PERSON.")
        UUID relativePersonId,

        @Schema(allowableValues = {"FATHER", "MOTHER", "SPOUSE"})
        String relativeKind,

        @Schema(description = "Họ tên tự khai. Chỉ đơn NEW_PERSON.")
        String declaredName,

        @Schema(description = "Năm sinh tự khai. Chỉ đơn NEW_PERSON.")
        Integer declaredBirthYear,

        @Schema(description = "Giới tính tự khai. Chỉ đơn NEW_PERSON.")
        Gender declaredGender,

        @Schema(description = "Chi đích — căn cứ để so phạm vi ltree khi duyệt")
        UUID targetBranchId,

        @Schema(description = "Chi đích ở dạng đọc được — trả lời \"đang chờ ai\"")
        ClaimBranchDto targetBranch,

        @Schema(description = "Số điện thoại người khai, để gọi kiểm chứng", example = "0912345678")
        String phone,

        @Schema(description = "Vài dòng tự giới thiệu — thứ Trưởng chi thật sự dùng để đối chiếu")
        String introduction,

        @Schema(description = "Ảnh chụp kết quả dò trùng, chụp **lúc gửi**. **CHỈ có trên bản đọc"
                + " của người duyệt** — với người gửi đơn thì trường này luôn vắng mặt, vì các"
                + " tín hiệu (NAM_SINH_KHOP, CUNG_CHI, CUNG_NGUYEN_QUAN, DOI_*) nói ra dữ liệu"
                + " Tầng 2 của một người đang sống. Trên màn duyệt, ba trạng thái là ba câu khác"
                + " nhau: **vắng mặt** = chưa quét bao giờ (đơn EXISTING không cần quét) · **mảng"
                + " rỗng** = đã quét, không nghi ai · **có phần tử** = đã quét, đây. Mỗi phần tử"
                + " chỉ có khoá + điểm + tín hiệu — **không** có tên hay năm sinh đọc từ phả.")
        List<ClaimDuplicateSuspect> duplicateSuspects,

        @Schema(description = "Các đơn **khác đang chờ** cùng trỏ vào nhân khẩu này. **Chỉ có"
                + " trên bản đọc của người duyệt**; người gửi luôn nhận mảng rỗng. Rỗng là"
                + " thường. Khác rỗng thì màn duyệt **phải** nói ra: quyết định đã chốt là Trưởng"
                + " chi thấy cả hai rồi chọn, nên một đơn đối thủ nằm ở trang 2 mà không được đánh"
                + " dấu sẽ âm thầm biến thành luật \"ai gửi trước thắng\". Chỉ trả khoá — đơn kia"
                + " chở số điện thoại của một người thứ ba.")
        List<UUID> competingClaimIds,

        UUID reviewerId,
        String reviewNote,
        Instant reviewedAt,

        @Schema(description = "Nhân khẩu được tạo **lúc duyệt** một đơn NEW_PERSON. Rỗng ở mọi đơn"
                + " bị từ chối — đó là bằng chứng không có node ma nào trong phả.")
        UUID createdPersonId,

        Instant createdAt) {

    public static PersonClaimDto from(PersonClaimView view) {
        return new PersonClaimDto(view.id(), view.kind(), view.status(), view.requestedBy(),
                view.requesterDisplayName(), view.attemptNo(), view.personId(),
                view.relativePersonId(), view.relativeKind(), view.declaredName(),
                view.declaredBirthYear(), view.declaredGender(), view.targetBranchId(),
                ClaimBranchDto.from(view.targetBranch()), view.phone(), view.introduction(),
                view.duplicateSuspects(), view.competingClaimIds(), view.reviewerId(),
                view.reviewNote(), view.reviewedAt(), view.createdPersonId(), view.createdAt());
    }
}

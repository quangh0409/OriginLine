package vn.giapha.membership.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.application.InvitationPreview;

/**
 * Màn "Lời mời này dành cho ai" — thứ <b>người chưa đăng nhập</b> nhìn thấy.
 *
 * <h2>Đây là một ngoại lệ riêng tư có chủ ý, đã được chốt</h2>
 * {@code invitee.displayName} là dữ liệu Tầng 1 của một người <i>đang sống</i> (BA v2 §10), hiện ra
 * cho bất kỳ ai cầm mã. Ngoại lệ được chọn vì không hiện tên thì người nhận không xác nhận được
 * "đúng là tôi", nút "Không phải tôi" mất nghĩa, và cả luồng vô nghĩa (design 06 §5.3, phương án a).
 *
 * <p>Cái giá được trả bằng ba lớp: mã <b>dùng một lần</b> · <b>hạn ngắn</b> · <b>giới hạn tần
 * suất</b>. Không lớp nào thay được lớp nào.</p>
 *
 * <h2>Không có khoá nhân khẩu ở đây</h2>
 * {@code personId} là khoá tra cứu ở mọi endpoint khác — một khoá nối bền vững trao cho người chưa
 * xác thực, trong khi màn hình không dùng tới nó. Nó xuất hiện ở {@link AcceptedInvitationDto},
 * tức sau khi người gọi đã có token.
 *
 * <h2>Trường vắng mặt, không phải null</h2>
 * {@code @JsonInclude(NON_NULL)}: một trường không có dữ liệu bị <b>loại khỏi JSON</b>, đúng quy
 * ước riêng tư của toàn hệ thống — "ẩn vì thiếu quyền" và "ẩn vì không có dữ liệu" cố ý không phân
 * biệt được.
 */
@Schema(description = "Nội dung màn nhận lời mời; không cần token")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitationPreviewDto(

        @Schema(description = "Tên cả dòng họ, lấy từ gốc cây chi", example = "Dòng họ Nguyễn — Đại Lan")
        String clanName,

        InviterDto inviter,

        InviteeDto invitee,

        @Schema(description = "Thời điểm mã hết hạn (GMT+7)")
        Instant expiresAt) {

    /**
     * Người phát lời mời — <b>một con người trong họ, không phải hệ thống</b>.
     *
     * <p>Một tin nhắn từ đầu số lạ kèm đường dẫn đúng là hình dạng của tin lừa đảo mà người lớn
     * tuổi được dặn phải xoá; tên người mời là thứ phân biệt.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InviterDto(

            @Schema(description = """
                    Tên **trần**, KHÔNG kính ngữ. Kính ngữ ("ông", "bà") phụ thuộc quan hệ họ hàng \
                    giữa người mời và người đọc, tức thuộc bộ luật danh xưng — thứ endpoint này cố ý \
                    không gọi tới. Giao diện cũng không được tự thêm: đoán sai một chữ "ông" cho một \
                    người phụ nữ là đúng loại lỗi mà quy tắc "quan hệ là việc của máy chủ" sinh ra \
                    để chặn. Sự tôn kính trên màn này do `clanTitle` gánh.""",
                    example = "Nguyễn Văn Bốn")
            String displayName,

            @Schema(description = """
                    Chức danh **dòng tộc**, suy từ `branch.head_person_id`. KHÔNG phải vai kỹ thuật \
                    `BRANCH_HEAD` — không ai trong họ tự giới thiệu mình bằng một mã vai trò. \
                    Vắng mặt với phần lớn thành viên, và đó là câu trả lời đúng.""",
                    example = "Trưởng Chi Giáp")
            String clanTitle) {
    }

    /** Người được mời — mức tối thiểu để tự nhận ra mình, không hơn. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InviteeDto(

            @Schema(description = "Tên người sẽ được gắn với tài khoản", example = "Nguyễn Thị Lan")
            String displayName,

            @Schema(description = "Đời thứ trong phả; thuỷ tổ = 1", example = "7")
            Integer generation,

            @Schema(description = """
                    Chi/ngành. Khối này KHÔNG mở thêm gì: `PublicPersonDto` của cổng công khai vốn \
                    đã chở nguyên một `BranchRef` cho Khách.""")
            BranchRefDto branch) {
    }

    /** Khớp đúng {@code BranchRef} đã có trong hợp đồng. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BranchRefDto(UUID id, String name, String path, String region) {
    }

    public static InvitationPreviewDto from(InvitationPreview preview) {
        InvitationPreview.Inviter inviter = preview.inviter();
        InvitationPreview.Invitee invitee = preview.invitee();
        return new InvitationPreviewDto(
                preview.clanName(),
                new InviterDto(inviter.displayName(), inviter.clanTitle()),
                new InviteeDto(invitee.displayName(), invitee.generation(),
                        branchOf(invitee.branch())),
                preview.expiresAt());
    }

    private static BranchRefDto branchOf(InvitationPreview.Branch branch) {
        if (branch == null) {
            return null;
        }
        return new BranchRefDto(branch.id(), branch.name(),
                branch.path() == null ? null : branch.path().value(), branch.region());
    }
}

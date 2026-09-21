package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Phát một mã mời cho cả dòng họ. Chỉ Hội đồng Tộc biểu hoặc Quản trị hệ thống. */
@Schema(description = "Phát mã mời dùng nhiều lần cho cả dòng họ")
public record IssueClanInviteRequest(

        @Size(max = 160)
        @Schema(description = """
                Nhãn để nhận ra mã này phát cho kênh nào. Nó là thứ biến bộ đếm thành thông tin \
                dùng được: "mã dán nhóm Zalo đã dùng 400 lần" nói được điều gì đó, "mã thứ ba đã \
                dùng 400 lần" thì không. KHÔNG phải bí mật, nhưng cũng KHÔNG đi ra tới người cầm mã.""",
                example = "Nhóm Zalo họ Nguyễn 2026")
        String label,

        @Min(1)
        @Max(90)
        @Schema(description = """
                Số ngày hiệu lực; bỏ trống dùng mặc định 30 ngày. **Không có giá trị nào nghĩa là \
                vô hạn** — một mã không hạn là một mã vĩnh viễn. Trần 90 ngày dài hơn mã cá nhân \
                (30) vì mã dòng họ phát ra ở một dịp có thật và người ở xa cần vài tuần mới ngồi \
                xuống đăng ký.""",
                example = "30")
        Integer ttlDays,

        @Min(1)
        @Schema(description = """
                Trần lượt dùng. Bỏ trống = không giới hạn *số lượt* (vẫn có hạn thời gian, vẫn thu \
                hồi được, vẫn có bộ đếm). Hội đồng biết chi mình có bao nhiêu người thì nên đặt \
                theo con số ấy.""",
                example = "600")
        Integer maxUses,

        @Size(max = 500)
        @Schema(description = "Ghi chú nội bộ, chỉ hiện trong danh sách quản trị")
        String note) {
}

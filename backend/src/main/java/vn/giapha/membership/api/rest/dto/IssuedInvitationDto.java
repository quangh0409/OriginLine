package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.membership.application.IssuedInvitation;

/**
 * Phản hồi của lệnh phát mã — <b>lần duy nhất mã thô rời máy chủ</b>.
 *
 * <h2>Đọc kỹ trước khi dựng giao diện quanh kiểu này</h2>
 * {@link #code()} không đọc lại được ở bất cứ endpoint nào. Trong CSDL chỉ có băm. Màn hình phát mã
 * vì vậy phải cho Trưởng chi <b>chép hoặc in ngay</b>, và phải nói rõ rằng đóng màn là mất mã.
 *
 * <p>Mất mã thì <b>phát lại</b>; thao tác phát lại tự thu hồi mã cũ. Đó không phải hạn chế của
 * hiện thực mà là chính điều kiện khiến "lưu băm chứ không lưu mã thô" có nghĩa.</p>
 */
@Schema(description = "Mã mời vừa phát. Mã chỉ xuất hiện đúng một lần, không tra lại được.")
public record IssuedInvitationDto(

        @Schema(description = "Mã thô, đã chia nhóm cho dễ đọc trên phiếu giấy", example = "K7M2Q-D9HFX")
        String code,

        InvitationDto invitation) {

    public static IssuedInvitationDto from(IssuedInvitation issued) {
        return new IssuedInvitationDto(issued.code(), InvitationDto.from(issued.invitation()));
    }
}

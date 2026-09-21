package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.membership.application.IssuedClanInvite;

/** Mã dòng họ vừa phát. Mã chỉ xuất hiện đúng một lần và không tra lại được. */
@Schema(description = "Mã mời dòng họ vừa phát. Mã chỉ xuất hiện đúng một lần.")
public record IssuedClanInviteDto(

        @Schema(description = "Mã thô, đã chia nhóm cho dễ đọc qua điện thoại",
                example = "K7M2Q-D9HFX")
        String code,

        ClanInviteDto invite) {

    public static IssuedClanInviteDto from(IssuedClanInvite issued) {
        return new IssuedClanInviteDto(issued.code(), ClanInviteDto.from(issued.invite()));
    }
}

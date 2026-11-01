package vn.giapha.clan.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import vn.giapha.clan.application.ClanProfileView;

/** Từ đường — dùng chung cho thân ghi và thân đọc. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AncestralHallDto(@Size(max = 200) String name, @Size(max = 500) String address) {

    static AncestralHallDto of(ClanProfileView v) {
        return v.ancestralHallName() == null ? null
                : new AncestralHallDto(v.ancestralHallName(), v.ancestralHallAddress());
    }
}

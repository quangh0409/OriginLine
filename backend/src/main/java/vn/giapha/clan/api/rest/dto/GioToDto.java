package vn.giapha.clan.api.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import vn.giapha.clan.application.ClanProfileView;

/** Ngày giỗ tổ theo âm lịch — dùng chung cho thân ghi và thân đọc. */
public record GioToDto(@Min(1) @Max(30) Integer lunarDay, @Min(1) @Max(12) Integer lunarMonth) {

    static GioToDto of(ClanProfileView v) {
        return v.gioToLunarDay() == null ? null
                : new GioToDto(v.gioToLunarDay(), v.gioToLunarMonth());
    }
}

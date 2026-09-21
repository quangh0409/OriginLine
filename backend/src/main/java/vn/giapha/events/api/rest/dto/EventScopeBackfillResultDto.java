package vn.giapha.events.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.events.application.EventScopeBackfillResult;

/**
 * Kết quả lượt dọn phạm vi sự kiện cũ.
 *
 * <p>{@code changed} tách khỏi {@code scanned} vì một lượt {@code dryRun} quét thì có mà sửa thì
 * không — gộp làm một sẽ khiến người vận hành tin rằng mình đã sửa xong.</p>
 */
@Schema(description = "Ket qua don pham vi su kien cu")
public record EventScopeBackfillResultDto(

        @Schema(description = "So su kien con hieu luc khong co chi lan co cap dong ho")
        int scanned,

        @Schema(description = "So dong duoc gan chi/nganh lay tu ho so nhan khau")
        int assignedBranch,

        @Schema(description = "So dong duoc danh dau tuong minh la cap dong ho")
        int markedClanWide,

        @Schema(description = "So dong THAT SU bi sua; luon 0 khi dryRun")
        int changed,

        @Schema(description = "Luot chay nay chi dem, khong ghi")
        boolean dryRun) {

    public static EventScopeBackfillResultDto from(EventScopeBackfillResult result) {
        return new EventScopeBackfillResultDto(result.scanned(), result.assignedBranch(),
                result.markedClanWide(), result.changed(), result.dryRun());
    }
}

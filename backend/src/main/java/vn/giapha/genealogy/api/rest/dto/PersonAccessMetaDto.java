package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import vn.giapha.genealogy.application.CallerRole;
import vn.giapha.genealogy.application.VisibleTier;

/**
 * Ngữ cảnh truy cập: <b>người gọi</b> được làm gì với hồ sơ này.
 *
 * <p>Cho giao diện render đúng affordance thay vì bấm thử rồi ăn 403. {@code visibleTier} nói
 * người gọi đang ở tầng nào, <b>không</b> nói trường nào đang bị giấu - hai điều đó khác nhau, và
 * chỉ điều thứ nhất là an toàn để tiết lộ.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonAccessMetaDto(VisibleTier visibleTier,
                                  boolean canEdit,
                                  boolean canDelete,
                                  boolean canRequestCorrection,
                                  @JsonProperty("isSelf") boolean isSelf,
                                  CallerRole callerRole) {
}

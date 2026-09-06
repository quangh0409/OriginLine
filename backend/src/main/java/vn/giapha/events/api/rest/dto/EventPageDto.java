package vn.giapha.events.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Trang danh sách sự kiện, khớp schema {@code EventPage}. */
@Schema(name = "EventPage", description = "Trang danh sach su kien")
public record EventPageDto(List<EventDto> items, PageMetaDto page) {
}

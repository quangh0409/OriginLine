package vn.giapha.notification.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.giapha.notification.application.view.PageMetaView;

/** Khối phân trang, khớp schema {@code PageMeta}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PageMeta", description = "Thong tin phan trang")
public record PageMetaDto(int page, int size, long totalElements, int totalPages, boolean hasNext,
                          String sort) {

    public static PageMetaDto from(PageMetaView view) {
        return new PageMetaDto(view.page(), view.size(), view.totalElements(), view.totalPages(),
                view.hasNext(), view.sort());
    }
}

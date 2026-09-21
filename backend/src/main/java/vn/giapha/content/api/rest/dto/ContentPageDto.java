package vn.giapha.content.api.rest.dto;

import java.util.List;
import vn.giapha.content.application.ContentPage;

/**
 * Bọc phân trang {@code { items, page }} — cùng hình dạng với {@code PageDto} của
 * {@code genealogy}, để giao diện chỉ phải biết <b>một</b> kiểu phân trang.
 *
 * @param page siêu dữ liệu. Lưu ý {@code totalElements} của vinh danh đếm <i>trước</i> bộ lọc nhóm
 *        trường riêng tư (xem {@code HonourService.search}), nên giao diện phải dựa vào
 *        {@code hasNext} chứ không dựa vào phép nhân {@code size × totalPages}
 */
public record ContentPageDto<T>(List<T> items, PageMetaDto page) {

    public record PageMetaDto(int page, int size, long totalElements, int totalPages,
                              boolean hasNext) {
    }

    public static <S, T> ContentPageDto<T> of(ContentPage<S> source, List<T> items) {
        return new ContentPageDto<>(items, new PageMetaDto(source.page(), source.size(),
                source.totalElements(), source.totalPages(), source.hasNext()));
    }
}

package vn.giapha.genealogy.application.view;

import java.util.List;

/**
 * Một trang kết quả, thống nhất kiểu offset ({@code page} 0-based + {@code size}) cho cả REST lẫn
 * GraphQL.
 *
 * <p>Cố ý không dùng cursor: quy mô hàng vạn nhân khẩu chưa cần tới, và giữ một kiểu phân trang
 * duy nhất thì giao diện đỡ phải viết hai lớp xử lý.</p>
 */
public record PageView<T>(List<T> items, PageMetaView page) {

    public static <T> PageView<T> of(List<T> items, int page, int size, long totalElements, String sort) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean hasNext = (long) (page + 1) * size < totalElements;
        return new PageView<>(items, new PageMetaView(page, size, totalElements, totalPages, hasNext, sort));
    }
}

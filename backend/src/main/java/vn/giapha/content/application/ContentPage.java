package vn.giapha.content.application;

import java.util.List;

/**
 * Một trang kết quả: phần tử + siêu dữ liệu phân trang.
 *
 * <p>Kiểu offset ({@code page} 0-based + {@code size}) chứ không cursor — giữ đúng một kiểu phân
 * trang cho cả sản phẩm, khớp {@code PageDto} của {@code genealogy}.</p>
 *
 * @param totalElements tổng số <b>sau khi đã áp luật ai thấy gì</b>. Con số này cố ý không phản
 *        ánh dữ liệu người gọi không được thấy: chỉ cần so hai tổng là biết đang có bao nhiêu bài
 *        bị giấu, và đó cũng là một mẩu thông tin
 */
public record ContentPage<T>(List<T> items, int page, int size, long totalElements) {

    public ContentPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }
}

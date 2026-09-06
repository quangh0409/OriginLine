package vn.giapha.genealogy.application.view;

/**
 * Khối phân trang.
 *
 * @param totalElements tổng số phần tử <b>sau khi đã lọc riêng tư</b>. Con số này cố ý không phản
 *                      ánh dữ liệu người gọi không được thấy — nếu không thì chỉ cần so hai tổng
 *                      là biết dòng họ có bao nhiêu người còn sống bị ẩn.
 */
public record PageMetaView(int page, int size, long totalElements, int totalPages, boolean hasNext,
                           String sort) {
}

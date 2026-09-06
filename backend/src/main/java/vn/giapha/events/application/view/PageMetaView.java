package vn.giapha.events.application.view;

/**
 * Khối phân trang, khớp schema {@code PageMeta} của {@code contracts/openapi.yaml}.
 *
 * <p>Định nghĩa riêng cho context này thay vì dùng chung với {@code genealogy}: hai context không
 * chạm vào lớp của nhau, và một record năm trường không đáng để kéo một phụ thuộc ngang. Hợp đồng
 * là hình dạng JSON, không phải lớp Java.</p>
 */
public record PageMetaView(int page, int size, long totalElements, int totalPages, boolean hasNext,
                           String sort) {

    public static PageMetaView of(int page, int size, long totalElements, String sort) {
        int safeSize = Math.max(1, size);
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        boolean hasNext = (long) (page + 1) * safeSize < totalElements;
        return new PageMetaView(page, safeSize, totalElements, totalPages, hasNext, sort);
    }
}

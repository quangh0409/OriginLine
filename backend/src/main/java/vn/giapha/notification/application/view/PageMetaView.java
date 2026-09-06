package vn.giapha.notification.application.view;

/**
 * Khối phân trang dùng chung cho các endpoint trả danh sách của context này — khớp schema
 * {@code PageMeta} của {@code contracts/openapi.yaml}.
 *
 * <p>Định nghĩa lại ở đây thay vì dùng chung với {@code genealogy} là có chủ ý: hai context không
 * được phép chạm vào lớp của nhau, và một record năm trường không đáng để kéo cả một phụ thuộc
 * ngang. Hình dạng JSON mới là hợp đồng, không phải lớp Java.</p>
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

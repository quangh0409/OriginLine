package vn.giapha.kinship.api.dto;

import java.util.List;

/**
 * Khối phân trang dùng chung — hợp đồng {@code PageMeta} của {@code contracts/openapi.yaml}.
 *
 * <p>Kiểu offset ({@code page}/{@code size}), không phải cursor: đủ cho quy mô hàng vạn nhân khẩu
 * và cho phép nhảy trang trong giao diện quản trị.</p>
 *
 * <p><b>TODO:</b> {@code PageMeta} là khối dùng chung cho mọi endpoint trả danh sách. Khi shared
 * kernel có một bản chính thức thì bỏ bản này đi — hiện chưa có, và context {@code kinship} không
 * được tự ý thêm lớp vào {@code shared/}.</p>
 */
public record PageMetaDto(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static PageMetaDto of(int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageMetaDto(page, size, totalElements, totalPages, (long) (page + 1) * size < totalElements);
    }

    /** Trang đầy đủ: danh sách phần tử + siêu dữ liệu phân trang. */
    public record Page<T>(List<T> items, PageMetaDto page) {
    }
}

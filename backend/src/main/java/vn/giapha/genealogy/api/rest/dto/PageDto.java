package vn.giapha.genealogy.api.rest.dto;

import java.util.List;

/**
 * Bọc phân trang dùng chung cho mọi endpoint trả danh sách: {@code { items, page }}.
 *
 * <p>Kiểu offset ({@code page} 0-based + {@code size}) chứ không phải cursor - đủ cho quy mô hàng
 * vạn nhân khẩu, cho phép nhảy trang trong giao diện quản trị, và giữ một kiểu phân trang duy nhất
 * cho cả REST lẫn GraphQL.</p>
 */
public record PageDto<T>(List<T> items, PageMetaDto page) {

    /**
     * @param totalElements tổng số phần tử <b>sau khi đã lọc phân tầng riêng tư</b>. Con số này cố
     *        ý không phản ánh dữ liệu người gọi không được thấy: chỉ cần so hai tổng là biết dòng
     *        họ đang giấu bao nhiêu người.
     */
    public record PageMetaDto(int page, int size, long totalElements, int totalPages,
                              boolean hasNext, String sort) {
    }
}

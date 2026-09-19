package vn.giapha.genealogy.api.rest.public_.dto;

import java.util.List;

/**
 * Trang kết quả công khai.
 *
 * <p><b>Không có {@code totalElements}.</b> Bản dành cho thành viên trả tổng đã lọc; ở đây ngay cả
 * con số đã lọc cũng bị bỏ, vì với Khách nó là <i>tổng số người đã khuất khớp truy vấn</i> — tức
 * một phép đếm dân số dòng họ mà kẻ quét có thể dò dần bằng cách đổi từ khoá. Client phân trang
 * bằng {@code hasNext}, đúng kiểu con trỏ.</p>
 *
 * @param page thứ tự trang (0-based); trần trang của bề mặt công khai nằm ở
 *             {@code PublicPortalProperties.maxSearchPage}
 */
public record PublicPageDto<T>(List<T> items, PublicPageMetaDto page) {

    /** @param hasNext còn trang sau hay không — thay cho tổng số phần tử. */
    public record PublicPageMetaDto(int page, int size, boolean hasNext) {
    }
}

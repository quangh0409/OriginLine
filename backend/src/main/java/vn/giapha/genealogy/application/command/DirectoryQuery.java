package vn.giapha.genealogy.application.command;

import java.util.UUID;

/**
 * Tham số một lượt xem <b>danh bạ dòng họ</b>.
 *
 * <p>Ba bộ lọc {@code province} / {@code occupation} / {@code branchId} so <b>khớp đúng</b> với các
 * giá trị mà chính người gọi đang nhìn thấy — chúng được lấy từ {@code facets} của phản hồi trước
 * chứ không phải do người dùng gõ tay. Đó là lý do danh bạ có {@code facets}: một ô nhập tự do rồi
 * để người dùng gõ "Hà Nội" ra không kết quả là cách nhanh nhất khiến họ kết luận hệ thống hỏng.
 *
 * @param q          tìm theo tên, <b>không dấu cũng khớp</b>; {@code null}/rỗng là không lọc
 * @param province   tỉnh/thành nơi ở hiện tại
 * @param occupation nghề nghiệp
 * @param branchId   chi/ngành, lấy cả cây con theo {@code ltree}
 * @param page       số trang, đếm từ 0
 * @param size       số dòng mỗi trang
 * @param sort       {@code name} (mặc định) hoặc {@code generation}; cùng từ vựng với
 *                   {@code /persons/search}
 */
public record DirectoryQuery(String q,
                             String province,
                             String occupation,
                             UUID branchId,
                             int page,
                             int size,
                             String sort) {

    /** Từ vựng sắp xếp mặc định — trùng với thứ vựng của {@code /persons/search}. */
    public static final String SORT_NAME = "name";

    public static final String SORT_GENERATION = "generation";

    public DirectoryQuery {
        page = Math.max(0, page);
        size = size <= 0 ? 20 : size;
        sort = sort == null || sort.isBlank() ? SORT_NAME : sort.trim();
        q = blankToNull(q);
        province = blankToNull(province);
        occupation = blankToNull(occupation);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** {@code true} khi không có bộ lọc nào — tập kết quả bằng đúng tập dùng để tính {@code facets}. */
    public boolean unfiltered() {
        return q == null && province == null && occupation == null && branchId == null;
    }
}

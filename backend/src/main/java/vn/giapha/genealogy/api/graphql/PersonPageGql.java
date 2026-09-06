package vn.giapha.genealogy.api.graphql;

import java.util.List;
import vn.giapha.genealogy.api.rest.dto.PersonDto;

/**
 * Trang nhân khẩu cho GraphQL.
 *
 * <p>Tách khỏi {@code PageDto} của REST vì hai bên trả <b>kiểu phần tử khác nhau</b>: REST trả
 * dạng rút gọn cho danh sách, còn GraphQL trả {@code Person} đầy đủ để client tự chọn hình dạng
 * bằng chính truy vấn của mình - đó là lý do tồn tại của cổng GraphQL.</p>
 *
 * <p>{@code totalElements} là {@code Int} 32-bit theo schema, nên tổng được thu hẹp từ
 * {@code long}; quy mô hàng vạn nhân khẩu không chạm tới giới hạn đó.</p>
 */
public record PersonPageGql(List<PersonDto> items, PageInfoGql page) {

    /** Khối phân trang, thống nhất kiểu offset với REST. */
    public record PageInfoGql(int page, int size, int totalElements, int totalPages, boolean hasNext,
                              String sort) {
    }
}

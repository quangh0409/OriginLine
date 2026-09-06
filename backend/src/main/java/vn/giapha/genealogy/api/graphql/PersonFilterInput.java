package vn.giapha.genealogy.api.graphql;

import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Bộ lọc nhân khẩu của GraphQL, ánh xạ input {@code PersonFilter}.
 *
 * <p>{@code gender} có trong hợp đồng nhưng cổng tìm kiếm Giai đoạn 1 chưa nhận tham số này, nên
 * hiện bị bỏ qua thay vì lọc sai một cách âm thầm - xem báo cáo bàn giao.</p>
 *
 * @param isAlive Khách gửi {@code true} luôn nhận danh sách rỗng, vì họ không được thấy người còn sống
 * @param includeDeleted chỉ {@code ADMIN}/{@code COUNCIL}; vai khác gửi {@code true} sẽ nhận lỗi
 */
public record PersonFilterInput(Integer generation, UUID branchId, Gender gender, Boolean isAlive,
                                String nativePlace, Boolean includeDeleted) {
}

package vn.giapha.genealogy.application.command;

import java.util.UUID;

/**
 * Tham số tìm kiếm nhân khẩu theo tên, <b>có dấu hoặc không dấu</b> (FR-4.4).
 *
 * @param sort dạng {@code field,asc|desc}; {@code field} thuộc {@code relevance},
 *        {@code generation}, {@code birthYear}, {@code name}. Sắp xếp chạy <b>sau</b> bộ lọc
 *        riêng tư, nếu không thì thứ tự trang sẽ nhảy loạn giữa các vai.
 * @param includeDeleted kèm bản ghi đã xoá mềm; chỉ {@code ADMIN} và {@code COUNCIL}
 */
public record PersonSearchQuery(String q, Integer generation, UUID branchId, String nativePlace,
                                Boolean alive, boolean includeDeleted, int page, int size, String sort) {

    public PersonSearchQuery {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(size, 100));
    }
}

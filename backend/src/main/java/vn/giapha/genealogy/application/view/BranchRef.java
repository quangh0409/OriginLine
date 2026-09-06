package vn.giapha.genealogy.application.view;

import java.util.UUID;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.Region;

/**
 * Dạng rút gọn của chi/ngành/cành/nhánh cho các phản hồi đọc.
 *
 * @param id     định danh chi
 * @param name   tên hiển thị <b>có dấu</b> ("Chi Nhất — Ngành Trưởng")
 * @param path   đường dẫn {@code ltree} sinh từ slug không dấu; cũng là phạm vi RBAC
 * @param region vùng miền, quyết định bộ quy tắc danh xưng cấp {@code REGION}
 */
public record BranchRef(UUID id, String name, String path, Region region) {

    public static BranchRef of(Branch branch) {
        if (branch == null) {
            return null;
        }
        return new BranchRef(branch.id(), branch.name(),
                branch.path() == null ? null : branch.path().value(), branch.region());
    }
}

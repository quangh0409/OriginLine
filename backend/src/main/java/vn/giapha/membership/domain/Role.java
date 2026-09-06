package vn.giapha.membership.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Một dòng của <b>danh mục</b> vai trò ({@code role}), do V5 seed sẵn năm bản ghi.
 *
 * <h2>Vì sao tách khỏi {@link RoleCode}</h2>
 * {@link RoleCode} là <i>luật</i> — thứ mã nguồn phân nhánh theo, và là danh sách đóng khớp
 * {@code ck_role_code}. {@link Role} là <i>dữ liệu hiển thị</i>: tên tiếng Việt và mô tả quyền để
 * màn hình phân quyền của Hội đồng Tộc biểu đọc lên được mà không phải hard-code chuỗi trong
 * frontend. Trộn hai thứ lại thì đổi một nhãn hiển thị sẽ phải sửa mã nguồn.
 *
 * <p>Không có phương thức ghi: thêm một vai mới đồng nghĩa với sửa {@code ck_role_code}, tức là một
 * migration, chứ không phải một câu INSERT lúc chạy.</p>
 *
 * @param id          khoá của dòng danh mục
 * @param code        mã vai trò
 * @param name        tên hiển thị tiếng Việt
 * @param description mô tả quyền, hiện trên màn hình phân quyền
 * @param rank        thứ bậc để so sánh nhanh; <b>không</b> thay cho kiểm tra phạm vi chi/ngành
 */
public record Role(UUID id, RoleCode code, String name, String description, int rank) {

    public Role {
        Objects.requireNonNull(id, "Role.id khong duoc null");
        Objects.requireNonNull(code, "Role.code khong duoc null");
    }
}

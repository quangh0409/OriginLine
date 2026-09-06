package vn.giapha.genealogy.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.giapha.genealogy.domain.NameType;

/**
 * Dữ liệu tạo/sửa một lớp tên.
 *
 * @param isPrimary nếu cả danh sách không có tên nào đặt {@code true}, backend chọn tên đầu tiên
 *        làm tên chính - bất biến "đúng một tên chính" do domain tự bảo vệ
 */
public record PersonNameInput(@NotNull NameType nameType,
                              @NotBlank @Size(max = 200) String fullName,
                              @Size(max = 200) String nameHanNom,
                              Boolean isPrimary,
                              @Size(max = 500) String note) {
}

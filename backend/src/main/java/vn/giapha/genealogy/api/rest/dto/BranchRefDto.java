package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import vn.giapha.genealogy.domain.Region;

/**
 * Tham chiếu chi/ngành/cành/nhánh.
 *
 * <p>{@code name} là tên hiển thị <b>có dấu</b>; {@code path} là đường dẫn {@code ltree} sinh từ
 * slug không dấu, vì nhãn {@code ltree} không nhận dấu tiếng Việt. Path cũng chính là phạm vi
 * phân quyền, nên đừng dựng lại nó ở client từ {@code name}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchRefDto(UUID id, String name, String path, Region region) {
}

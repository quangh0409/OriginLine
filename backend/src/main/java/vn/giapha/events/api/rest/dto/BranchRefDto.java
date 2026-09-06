package vn.giapha.events.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.giapha.events.domain.EventSubject;

/**
 * Chi/ngành dạng rút gọn, khớp schema {@code BranchRef}.
 *
 * @param path đường dẫn {@code ltree}; nhãn {@code ltree} không nhận dấu tiếng Việt nên path sinh từ
 *             slug không dấu, còn tên có dấu nằm ở {@code name}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "BranchRef", description = "Tham chieu chi/nganh")
public record BranchRefDto(UUID id, String name, String path, String region) {

    public static BranchRefDto from(EventSubject.BranchSnapshot branch) {
        return branch == null ? null
                : new BranchRefDto(branch.id(), branch.name(), branch.path(), branch.region());
    }
}

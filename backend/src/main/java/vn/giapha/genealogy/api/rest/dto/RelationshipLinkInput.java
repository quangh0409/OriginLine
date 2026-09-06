package vn.giapha.genealogy.api.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Nối nhân khẩu <b>đang được tạo</b> vào cây.
 *
 * <p>Id của người mới chưa tồn tại lúc gửi request, nên cạnh được mô tả bằng "người kia là ai" và
 * "người kia đứng ở đầu nào của cạnh": {@code SOURCE} nghĩa là người kia ở đầu {@code from},
 * {@code TARGET} nghĩa là người mới ở đầu {@code from}.</p>
 *
 * <p>Con nuôi dùng {@code PARENT_ADOPT} - con nuôi và con ruột là <b>hai loại cạnh khác nhau</b>,
 * không phải một cờ trên cùng cạnh, vì suy luận danh xưng đối xử khác nhau với hai loại.</p>
 */
public record RelationshipLinkInput(@NotNull RelType relType,
                                    @NotNull UUID otherPersonId,
                                    @NotNull OtherPersonRole otherPersonRole,
                                    HeirKind heirKind,
                                    @Min(1) Integer spouseOrder,
                                    LocalDate validFrom,
                                    LocalDate validTo,
                                    @Size(max = 500) String note) {

    /** Vị trí của người kia trên cạnh {@code from} tới {@code to}. */
    public enum OtherPersonRole {
        SOURCE,
        TARGET
    }

    public boolean otherIsSource() {
        return otherPersonRole == OtherPersonRole.SOURCE;
    }
}

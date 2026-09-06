package vn.giapha.genealogy.application.command;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Nối nhân khẩu <b>đang được tạo</b> vào cây.
 *
 * <p>Vì id của người mới chưa tồn tại lúc client gửi request, cạnh được mô tả bằng "người kia là
 * ai" và "người kia đứng ở đầu nào của cạnh {@code from} tới {@code to}":</p>
 * <ul>
 *   <li>thêm <b>con</b> của ông X: {@code PARENT_BIO}, {@code otherPersonId = X},
 *       {@code otherIsSource = true} (X là cha nên X ở đầu {@code from});</li>
 *   <li>thêm <b>cha</b> của anh Y: {@code PARENT_BIO}, {@code otherPersonId = Y},
 *       {@code otherIsSource = false} (người mới là cha nên đứng ở đầu {@code from});</li>
 *   <li>thêm <b>vợ thứ hai</b> của ông Z: {@code SPOUSE}, {@code otherIsSource = true},
 *       {@code spouseOrder = 2}.</li>
 * </ul>
 *
 * @param otherIsSource {@code true} nghĩa là người kia ở đầu {@code from}; {@code false} nghĩa là
 *        nhân khẩu mới ở đầu {@code from}
 */
public record RelationshipLinkCommand(RelType relType, UUID otherPersonId, boolean otherIsSource,
                                      HeirKind heirKind, Integer spouseOrder, LocalDate validFrom,
                                      LocalDate validTo, String note) {

    /** {@code true} khi người kia là cha/mẹ của nhân khẩu mới - căn cứ suy ra đời thứ và chi. */
    public boolean otherIsParentOfNewPerson() {
        return relType != null && relType.isParentEdge() && otherIsSource;
    }

    /** {@code true} khi nhân khẩu mới là cha/mẹ của người kia. */
    public boolean newPersonIsParentOfOther() {
        return relType != null && relType.isParentEdge() && !otherIsSource;
    }
}

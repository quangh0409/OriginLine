package vn.giapha.genealogy.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.application.view.PersonBadge;
import vn.giapha.genealogy.domain.LineageStatus;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.shared.vo.Gender;

/**
 * Tính các nhãn nghiệp vụ mà canvas phả đồ vẽ lên node.
 *
 * <p>Tính ở backend chứ không để giao diện tự suy: mấy nhãn này không đọc được từ một cột nào cả.
 * <b>Dâu và rể không phải một loại cạnh</b> - chúng được suy ra từ {@code SPOUSE} cộng huyết
 * thống (vợ của một người con trai trong dòng họ là con dâu). Bắt frontend tự luận ra là bảo nó
 * cài lại một phần rule engine danh xưng bằng JavaScript, và bản đó chắc chắn sẽ lệch với bản
 * chính.</p>
 */
final class TreeBadgeResolver {

    private TreeBadgeResolver() {
    }

    /**
     * @param bloodline {@code true} nếu node này nằm trên dòng huyết thống của phép duyệt;
     *        {@code false} nghĩa là node được kéo vào chỉ vì kết hôn với một người trong cây -
     *        đó chính là điều kiện để gắn nhãn dâu/rể
     */
    static List<PersonBadge> badgesOf(Person person, Collection<Relationship> edges,
                                      BranchDirectory branches, boolean bloodline) {
        List<PersonBadge> badges = new ArrayList<>();
        UUID id = person.rawId();

        if (!person.isAlive()) {
            badges.add(PersonBadge.DECEASED);
        }
        if (person.lineageStatus() == LineageStatus.TUYET_TU) {
            badges.add(PersonBadge.TUYET_TU);
        }
        if (id.equals(branches.headPersonOf(person.primaryBranchId()))) {
            badges.add(PersonBadge.TRUONG_CHI);
        }

        boolean marriedIn = false;
        for (Relationship edge : edges) {
            if (edge.relType() == RelType.PARENT_ADOPT && edge.toPersonId().equals(id)) {
                badges.add(PersonBadge.CON_NUOI);
            }
            if (edge.relType() == RelType.HEIR && edge.toPersonId().equals(id) && edge.heirKind() != null) {
                badges.add(switch (edge.heirKind()) {
                    case DICH_TON -> PersonBadge.DICH_TON;
                    case THUA_TU -> PersonBadge.THUA_TU;
                    case KE_TU -> PersonBadge.KE_TU;
                });
            }
            if (edge.relType() == RelType.SPOUSE && edge.otherEnd(id) != null) {
                marriedIn = true;
            }
        }
        if (!bloodline && marriedIn) {
            if (person.gender() == Gender.FEMALE) {
                badges.add(PersonBadge.DAU);
            } else if (person.gender() == Gender.MALE) {
                badges.add(PersonBadge.RE);
            }
        }
        return List.copyOf(badges);
    }
}

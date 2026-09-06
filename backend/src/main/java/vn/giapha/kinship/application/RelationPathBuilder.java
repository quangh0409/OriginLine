package vn.giapha.kinship.application;

import java.util.ArrayList;
import java.util.List;
import vn.giapha.kinship.domain.DirectLink;
import vn.giapha.kinship.domain.DirectLinkType;
import vn.giapha.kinship.domain.InLawDirection;
import vn.giapha.kinship.domain.InLawLink;
import vn.giapha.kinship.domain.LcaResult;
import vn.giapha.kinship.domain.PersonView;
import vn.giapha.kinship.domain.RelationContext;
import vn.giapha.kinship.domain.RelationFacts;
import vn.giapha.kinship.domain.RelationSide;
import vn.giapha.shared.vo.PersonId;

/**
 * Dựng <b>đường quan hệ</b> đọc được cho giao diện: từ ego đi lên tổ chung gần nhất rồi đi xuống
 * alter, hoặc vòng qua người nối nếu là quan hệ dâu/rể.
 *
 * <p>Đây là thứ tạo niềm tin cho tính năng danh xưng: dòng họ nhìn đường đi là biết ngay hệ thống
 * suy từ đâu, và khi báo "gọi sai" thì chỉ đúng được chỗ dữ liệu sai.</p>
 *
 * <p><b>Chọn đường nào để vẽ là do domain quyết, không phải do lớp này.</b> Khi một người vừa là họ
 * hàng xa vừa là dâu/rể trong họ, {@code RelationFactsFactory} đã chọn đường gần hơn; lớp này chỉ
 * đọc lại quyết định đó qua {@link RelationFacts#side()} để hai thứ không bao giờ lệch nhau.</p>
 */
final class RelationPathBuilder {

    private RelationPathBuilder() {
    }

    static List<RelationPathStep> build(RelationContext context, RelationFacts facts) {
        List<RelationPathStep> steps = new ArrayList<>();
        if (context.isSelf()) {
            steps.add(step(context, context.ego().id(), PathDirection.SELF, null));
            return steps;
        }

        InLawLink inLaw = context.inLaw();
        boolean renderInLaw = inLaw != null && facts != null && facts.side() == RelationSide.IN_LAW;

        if (renderInLaw) {
            appendInLaw(context, inLaw, steps);
            return steps;
        }
        if (context.lca() != null) {
            appendBlood(context, context.lca(), steps, true);
            return steps;
        }
        appendDirect(context, steps);
        return steps;
    }

    /**
     * Chặng phả hệ: lên từ {@code path.egoPathUp()} tới LCA rồi xuống theo {@code path.alterPathUp()}.
     * LCA chỉ xuất hiện <b>một lần</b>, ở cuối đoạn đi lên.
     */
    private static void appendBlood(RelationContext context, LcaResult path,
            List<RelationPathStep> steps, boolean includeStart) {
        List<PersonId> up = path.egoPathUp();
        List<PersonId> down = path.alterPathUp();

        for (int i = 0; i < up.size(); i++) {
            if (i == 0) {
                if (includeStart) {
                    steps.add(step(context, up.get(i), PathDirection.SELF, null));
                }
                continue;
            }
            steps.add(step(context, up.get(i), PathDirection.UP, parentEdgeType(path)));
        }
        for (int i = down.size() - 2; i >= 0; i--) {
            steps.add(step(context, down.get(i), PathDirection.DOWN, parentEdgeType(path)));
        }
    }

    /**
     * Quan hệ dâu/rể đi vòng qua NGƯỜI NỐI.
     *
     * <ul>
     *   <li>{@link InLawDirection#ALTER_IS_SPOUSE} — ego đi phả hệ tới người nối (ruột thịt của
     *       ego), rồi sang ngang một bước hôn nhân tới alter. Ví dụ "thím": ego → bố → chú → thím.</li>
     *   <li>{@link InLawDirection#EGO_IS_SPOUSE} — ego sang ngang tới vợ/chồng mình trước, rồi mới
     *       đi phả hệ tới alter. Ví dụ "bố chồng": ego → chồng → bố chồng.</li>
     * </ul>
     */
    private static void appendInLaw(RelationContext context, InLawLink inLaw,
            List<RelationPathStep> steps) {
        if (inLaw.direction() == InLawDirection.ALTER_IS_SPOUSE) {
            appendBlood(context, inLaw.bloodPath(), steps, true);
            steps.add(step(context, context.alter().id(), PathDirection.ACROSS, DirectLinkType.SPOUSE));
            return;
        }
        steps.add(step(context, context.ego().id(), PathDirection.SELF, null));
        steps.add(step(context, inLaw.linkPerson(), PathDirection.ACROSS, DirectLinkType.SPOUSE));
        // bloodPath của chiều này nối NGƯỜI NỐI với alter, và người nối vừa được thêm ở trên.
        appendBlood(context, inLaw.bloodPath(), steps, false);
    }

    /** Không có tổ chung: chỉ còn một cạnh trực tiếp (vợ/chồng, con nuôi ngoài họ, thừa tự). */
    private static void appendDirect(RelationContext context, List<RelationPathStep> steps) {
        DirectLink link = context.directLinks().stream().filter(DirectLink::active).findFirst().orElse(null);
        if (link == null) {
            return;
        }
        steps.add(step(context, context.ego().id(), PathDirection.SELF, null));
        steps.add(step(context, context.alter().id(), directionOf(link), link.type()));
    }

    private static PathDirection directionOf(DirectLink link) {
        return switch (link.type()) {
            case SPOUSE, HEIR -> PathDirection.ACROSS;
            // reversed = cạnh đi từ alter sang ego, tức alter là cha/mẹ của ego.
            case PARENT_BIO, PARENT_ADOPT -> link.reversed() ? PathDirection.UP : PathDirection.DOWN;
        };
    }

    /**
     * {@code LcaResult} chỉ mang <b>một cờ tổng</b> {@code viaAdoption} cho cả hai đường đi, không
     * cho biết cạnh nào là cạnh nuôi. Khi đường đi có dính nhận nuôi thì để {@code null} còn hơn
     * gán bừa {@code PARENT_BIO} cho mọi chặng — nhãn sai còn tệ hơn nhãn trống.
     */
    private static DirectLinkType parentEdgeType(LcaResult path) {
        return path.viaAdoption() ? null : DirectLinkType.PARENT_BIO;
    }

    private static RelationPathStep step(RelationContext context, PersonId id,
            PathDirection direction, DirectLinkType viaRelType) {
        PersonView view = context.view(id);
        return new RelationPathStep(id.value(), view.displayName(), view.generation(), direction,
                viaRelType);
    }
}

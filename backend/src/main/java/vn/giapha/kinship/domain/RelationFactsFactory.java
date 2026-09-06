package vn.giapha.kinship.domain;

import java.time.LocalDate;
import java.util.List;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * Biến bằng chứng đồ thị ({@link RelationContext}) thành dữ kiện so khớp ({@link RelationFacts}).
 *
 * <p>Đây là nơi ba định nghĩa dễ làm sai nhất của migration V3 được hiện thực — sai một trong ba
 * là lệch cả 111 luật:</p>
 *
 * <ol>
 *   <li><b>{@code collateral_degree = min(dist_a, dist_b)}</b> — bậc bàng hệ, chiều duy nhất phân
 *       biệt "bác ruột" với "bác họ" và "anh ruột" với "anh họ".</li>
 *   <li><b>{@code side}</b> — {@code genDelta >= 0} thì lấy theo bước đi lên ĐẦU TIÊN của ego (qua
 *       cha là nội, qua mẹ là ngoại); {@code genDelta < 0} thì lấy theo nhánh nối vào ego, tức là
 *       con của LCA nằm trên đường đi của alter (con trai là cháu nội, con gái là cháu ngoại).</li>
 *   <li><b>{@code is_elder}</b> — so <b>hai nút ở đời ngay dưới LCA</b>, không phải so tuổi hai
 *       người. Anh của bố là bác dù ít tuổi hơn ta; em của bố là chú. Thiếu
 *       {@code birth_order} thì trả {@code null} để rơi vào luật "chưa rõ vai".</li>
 * </ol>
 *
 * <p>POJO thuần, không trạng thái, không Spring, không CSDL.</p>
 */
public final class RelationFactsFactory {

    public RelationFacts from(RelationContext context) {
        List<DirectLink> links = context.directLinks().stream().filter(DirectLink::active).toList();
        boolean marriage = context.inLaw() != null
                || links.stream().anyMatch(link -> link.type() == DirectLinkType.SPOUSE);

        RelationFacts.Builder builder = RelationFacts.builder()
                .targetGender(context.alter().gender())
                .directLinks(links)
                .throughMarriage(marriage);

        LcaResult blood = context.lca();
        InLawLink inLaw = context.inLaw();

        if (blood != null && preferBlood(blood, inLaw)) {
            return builder
                    .genDelta(blood.genDelta())
                    .collateralDegree(blood.collateralDegree())
                    .side(sideOf(blood, context))
                    .isElder(elderOf(blood, context, context.ego(), context.alter()))
                    .throughAdoption(blood.viaAdoption())
                    .build();
        }

        if (inLaw != null) {
            LcaResult path = inLaw.bloodPath();
            PersonView link = context.view(inLaw.linkPerson());
            // Đường huyết thống của quan hệ dâu/rể nối ego với NGƯỜI NỐI (ALTER_IS_SPOUSE) hoặc
            // nối NGƯỜI NỐI với alter (EGO_IS_SPOUSE) — hai đầu của phép so vai phải theo đúng đó.
            boolean alterIsSpouse = inLaw.direction() == InLawDirection.ALTER_IS_SPOUSE;
            PersonView pathEgo = alterIsSpouse ? context.ego() : link;
            PersonView pathAlter = alterIsSpouse ? link : context.alter();
            return builder
                    .genDelta(path.genDelta())
                    .collateralDegree(path.collateralDegree())
                    .side(RelationSide.IN_LAW)
                    .isElder(elderOf(path, context, pathEgo, pathAlter))
                    .linkSide(sideOf(path, context))
                    .linkGender(link.gender())
                    .inLawDirection(inLaw.direction())
                    .throughAdoption(path.viaAdoption())
                    .build();
        }

        if (!links.isEmpty()) {
            // Không có tổ chung nhưng vẫn có cạnh nối: con nuôi từ ngoài họ, thừa tự, vợ/chồng.
            // Bên quan hệ suy từ chính loại cạnh, nếu không thì luật ghi side='BLOOD' sẽ trượt.
            boolean onlyMarriage = links.stream().allMatch(link -> link.type() == DirectLinkType.SPOUSE);
            return builder.side(onlyMarriage ? RelationSide.IN_LAW : RelationSide.BLOOD).build();
        }

        return builder.build();
    }

    /**
     * Khi một người vừa là họ hàng xa vừa là dâu/rể trong họ (rất thường gặp ở làng xã), lấy đường
     * <b>gần hơn</b>. Ví dụ vợ của chú mà cũng là chị họ đời thứ tư: mọi người vẫn gọi là "thím".
     * Hoà nhau thì ưu tiên huyết thống.
     */
    private boolean preferBlood(LcaResult blood, InLawLink inLaw) {
        if (inLaw == null) {
            return true;
        }
        int bloodDistance = blood.distEgo() + blood.distAlter();
        return bloodDistance <= inLaw.distance();
    }

    /**
     * Bên nội/ngoại. Xem quy tắc ở javadoc lớp.
     *
     * <p>Không xác định được giới tính của người nối (gia phả cũ hay thiếu) thì trả
     * {@link RelationSide#BLOOD} — vẫn khớp được các luật ghi {@code BLOOD}, chỉ mất phần phân biệt
     * nội/ngoại.</p>
     */
    RelationSide sideOf(LcaResult lca, RelationContext context) {
        if (lca.genDelta() >= 0) {
            PersonId firstParent = lca.egoFirstParent();
            if (firstParent == null) {
                return RelationSide.BLOOD;
            }
            return RelationSide.fromParentGender(context.view(firstParent).gender());
        }
        PersonId branchNode = lca.alterLineNode();
        if (branchNode == null) {
            return RelationSide.BLOOD;
        }
        return RelationSide.fromParentGender(context.view(branchNode).gender());
    }

    /**
     * Vai trên/vai dưới. So hai nút ở đời ngay dưới LCA (chúng là anh chị em ruột với nhau), theo
     * thứ tự: {@code birth_order} trước, {@code birth_solar} sau. Cùng đời mà vẫn chưa phân định
     * được thì so ngày sinh của chính hai người.
     *
     * @return {@code null} khi không kết luận được — đúng ý đồ của các luật "chưa rõ vai"
     */
    Boolean elderOf(LcaResult lca, RelationContext context, PersonView pathEgo, PersonView pathAlter) {
        PersonId egoLineId = lca.egoLineNode();
        PersonId alterLineId = lca.alterLineNode();
        if (egoLineId == null || alterLineId == null) {
            // Trực hệ: không có khái niệm vai trên/vai dưới trong cùng một đời.
            return null;
        }
        PersonView egoLine = context.view(egoLineId);
        PersonView alterLine = context.view(alterLineId);

        Boolean byOrder = compare(alterLine.birthOrder(), egoLine.birthOrder());
        if (byOrder != null) {
            return byOrder;
        }
        Boolean byBirthDate = compare(alterLine.birthSolar(), egoLine.birthSolar());
        if (byBirthDate != null) {
            return byBirthDate;
        }
        if (lca.genDelta() == 0) {
            return compare(pathAlter.birthSolar(), pathEgo.birthSolar());
        }
        return null;
    }

    private static Boolean compare(Integer alterValue, Integer egoValue) {
        if (alterValue == null || egoValue == null || alterValue.equals(egoValue)) {
            return null;
        }
        return alterValue < egoValue;
    }

    private static Boolean compare(LocalDate alterValue, LocalDate egoValue) {
        if (alterValue == null || egoValue == null || alterValue.isEqual(egoValue)) {
            return null;
        }
        return alterValue.isBefore(egoValue);
    }

    /** Giới tính alter — tách hàm cho dễ đọc ở nơi gọi. */
    static Gender targetGender(RelationContext context) {
        return context.alter().gender();
    }
}

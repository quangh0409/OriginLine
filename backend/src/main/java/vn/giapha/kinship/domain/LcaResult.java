package vn.giapha.kinship.domain;

import java.util.List;
import java.util.Objects;
import vn.giapha.shared.vo.PersonId;

/**
 * Kết quả truy vấn tổ chung gần nhất (LCA) — đầu vào của mọi suy luận danh xưng huyết thống.
 *
 * <p>Giữ nguyên <b>cả hai đường đi</b> chứ không chỉ hai khoảng cách, vì ba chiều so khớp quan
 * trọng nhất đều cần các nút trung gian:</p>
 * <ul>
 *   <li>{@code side} — cần bước đi lên đầu tiên của ego (qua cha hay qua mẹ);</li>
 *   <li>{@code is_elder} — cần hai nút ở <b>đời ngay dưới LCA</b> để so thứ tự sinh;</li>
 *   <li>đường quan hệ trả cho giao diện để người dùng tự kiểm chứng.</li>
 * </ul>
 *
 * @param egoPathUp   đường đi lên từ ego tới LCA; phần tử đầu là chính ego, phần tử cuối là LCA
 * @param alterPathUp đường đi lên từ alter tới LCA, cùng quy ước
 * @param viaAdoption có ít nhất một cạnh nuôi ({@code PARENT type=ADOPT}) trên hai đường đi
 */
public record LcaResult(List<PersonId> egoPathUp, List<PersonId> alterPathUp, boolean viaAdoption) {

    public LcaResult {
        Objects.requireNonNull(egoPathUp, "egoPathUp khong duoc null");
        Objects.requireNonNull(alterPathUp, "alterPathUp khong duoc null");
        if (egoPathUp.isEmpty() || alterPathUp.isEmpty()) {
            throw new IllegalArgumentException("Duong di toi LCA khong duoc rong");
        }
        PersonId lcaOfEgo = egoPathUp.get(egoPathUp.size() - 1);
        PersonId lcaOfAlter = alterPathUp.get(alterPathUp.size() - 1);
        if (!lcaOfEgo.equals(lcaOfAlter)) {
            throw new IllegalArgumentException(
                    "Hai duong di phai ket thuc o cung mot LCA: " + lcaOfEgo + " vs " + lcaOfAlter);
        }
        egoPathUp = List.copyOf(egoPathUp);
        alterPathUp = List.copyOf(alterPathUp);
    }

    public PersonId lca() {
        return egoPathUp.get(egoPathUp.size() - 1);
    }

    /** {@code dist_a} — số đời từ ego đi lên tới LCA. */
    public int distEgo() {
        return egoPathUp.size() - 1;
    }

    /** {@code dist_b} — số đời từ alter đi lên tới LCA. */
    public int distAlter() {
        return alterPathUp.size() - 1;
    }

    /**
     * {@code gen_delta = dist_a - dist_b}. Dương: alter thuộc đời TRÊN ego. Âm: đời DƯỚI.
     * <b>Đây là quy ước của migration V3 và của cả 111 luật đã seed.</b>
     */
    public int genDelta() {
        return distEgo() - distAlter();
    }

    /**
     * {@code collateral_degree = min(dist_a, dist_b)} — bậc bàng hệ.
     * 0 = trực hệ · 1 = ruột · 2 = họ (con chú con bác) · từ 3 trở lên = họ xa.
     */
    public int collateralDegree() {
        return Math.min(distEgo(), distAlter());
    }

    /** Cha/mẹ của ego trên đường đi lên LCA — căn cứ xác định bên nội/ngoại khi genDelta &gt;= 0. */
    public PersonId egoFirstParent() {
        return distEgo() >= 1 ? egoPathUp.get(1) : null;
    }

    /** Cha/mẹ của alter trên đường đi lên LCA (dùng khi tra danh xưng chiều ngược lại). */
    public PersonId alterFirstParent() {
        return distAlter() >= 1 ? alterPathUp.get(1) : null;
    }

    /** Con của LCA nằm trên đường đi của ego — một vế của phép so vai trên/vai dưới. */
    public PersonId egoLineNode() {
        return distEgo() >= 1 ? egoPathUp.get(distEgo() - 1) : null;
    }

    /** Con của LCA nằm trên đường đi của alter — vế còn lại của phép so vai. */
    public PersonId alterLineNode() {
        return distAlter() >= 1 ? alterPathUp.get(distAlter() - 1) : null;
    }

    /** Đảo vai ego/alter — tính danh xưng chiều ngược mà không phải hỏi lại đồ thị. */
    public LcaResult reversed() {
        return new LcaResult(alterPathUp, egoPathUp, viaAdoption);
    }
}

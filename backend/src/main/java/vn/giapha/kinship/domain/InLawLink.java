package vn.giapha.kinship.domain;

import java.util.Objects;
import vn.giapha.shared.vo.PersonId;

/**
 * Quan hệ hôn nhân <b>gián tiếp</b>, tính qua NGƯỜI NỐI (V3 §link_side/link_gender).
 *
 * <p>Danh xưng dâu/rể không suy thẳng từ LCA giữa hai người — hai người có thể chẳng có tổ chung
 * nào. Trước hết phải giải quan hệ huyết thống tới người nối, rồi mới ánh xạ sang danh xưng:</p>
 *
 * <ul>
 *   <li>{@link InLawDirection#ALTER_IS_SPOUSE} — người nối là <b>ruột thịt của ego</b> và là
 *       vợ/chồng của alter. {@code bloodPath} là LCA(ego, người nối).
 *       Ví dụ "thím": người nối = em trai của bố.</li>
 *   <li>{@link InLawDirection#EGO_IS_SPOUSE} — người nối là <b>vợ/chồng của ego</b> và là ruột thịt
 *       của alter. {@code bloodPath} là LCA(người nối, alter).
 *       Ví dụ "bố chồng": người nối = chồng của ego.</li>
 * </ul>
 *
 * @param linkPerson  người nối
 * @param direction   chiều của quan hệ hôn nhân
 * @param bloodPath   đường huyết thống đã mô tả ở trên; {@code ego} của nó tuỳ theo {@code direction}
 * @param spouseOrder thứ tự vợ/chồng của cạnh hôn nhân đã dùng (đa thê)
 */
public record InLawLink(
        PersonId linkPerson,
        InLawDirection direction,
        LcaResult bloodPath,
        Integer spouseOrder) {

    public InLawLink {
        Objects.requireNonNull(linkPerson, "InLawLink.linkPerson khong duoc null");
        Objects.requireNonNull(direction, "InLawLink.direction khong duoc null");
        Objects.requireNonNull(bloodPath, "InLawLink.bloodPath khong duoc null");
    }

    /**
     * Tổng số bậc của đường huyết thống tới người nối — dùng để chọn người nối GẦN NHẤT khi một
     * người có nhiều đường nối (ví dụ hai họ đã có sẵn quan hệ rồi lại kết thông gia).
     */
    public int distance() {
        return bloodPath.distEgo() + bloodPath.distAlter();
    }
}

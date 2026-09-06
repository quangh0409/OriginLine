package vn.giapha.kinship.domain;

import java.util.Objects;
import vn.giapha.shared.vo.PersonId;

/**
 * Một cạnh nối <b>trực tiếp</b> hai người trong đồ thị, dùng cho các luật có {@code direct_link}.
 *
 * <p>Luật khớp theo cạnh luôn được xét trước luật suy từ LCA (priority nhỏ hơn), vì có những quan
 * hệ mà LCA hoặc vô nghĩa (vợ/chồng) hoặc cho câu trả lời sai (bố nuôi vẫn là "bố nuôi" chứ không
 * phải "bố", dù cạnh nuôi vẫn nằm trên đường đi phả hệ).</p>
 *
 * @param type     loại cạnh
 * @param subtype  phân loại con: {@code HEIR} có DICH_TON/THUA_TU/KE_TU; {@code PARENT_*} có BIO/ADOPT
 * @param reversed {@code true} nghĩa là cạnh đi từ alter sang ego (ví dụ bố nuôi: cạnh từ B sang A).
 *                 Cạnh {@code SPOUSE} được chuẩn hoá thành vô hướng nên luôn {@code false}
 * @param active   cạnh còn hiệu lực ({@code valid_to IS NULL} và chưa xoá mềm). Ly hôn hoặc quan hệ
 *                 đã kết thúc thì {@code false} và <b>không</b> sinh danh xưng
 */
public record DirectLink(
        DirectLinkType type,
        String subtype,
        boolean reversed,
        boolean active,
        PersonId from,
        PersonId to) {

    public DirectLink {
        Objects.requireNonNull(type, "DirectLink.type khong duoc null");
    }

    public static DirectLink of(DirectLinkType type, boolean reversed) {
        return new DirectLink(type, null, reversed, true, null, null);
    }

    public static DirectLink of(DirectLinkType type, String subtype, boolean reversed) {
        return new DirectLink(type, subtype, reversed, true, null, null);
    }

    /** {@code true} nếu cạnh này khớp yêu cầu của một luật. {@code subtype} null trên luật = mọi phân loại. */
    public boolean satisfies(DirectLinkType wantedType, String wantedSubtype, boolean wantedReversed) {
        if (!active) {
            return false;
        }
        if (type != wantedType || reversed != wantedReversed) {
            return false;
        }
        return wantedSubtype == null || wantedSubtype.equalsIgnoreCase(subtype);
    }

    /** Cạnh đảo chiều — dùng khi tra danh xưng chiều ngược lại. */
    public DirectLink reverse() {
        boolean flipped = type == DirectLinkType.SPOUSE ? reversed : !reversed;
        return new DirectLink(type, subtype, flipped, active, to, from);
    }
}

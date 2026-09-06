package vn.giapha.kinship.domain;

import java.util.Objects;
import vn.giapha.shared.vo.PersonId;

/**
 * Một cạnh {@code PARENT} của đồ thị, đọc theo đúng chiều đã lưu: <b>cha/mẹ → con</b>.
 *
 * <p>Nhắc lại cho khỏi lặp lại lỗi cũ: cạnh đi cha → con, nên muốn đi lên tổ tiên phải dùng mũi tên
 * ngược {@code <-[:PARENT*0..]-}. TDD v1.0 viết xuôi chiều và truy vấn LCA trả về 0 dòng với mọi
 * cặp người; V7 §7.5(c) là bản đúng.</p>
 *
 * @param adopt cạnh nhận nuôi ({@code type = 'ADOPT'}). Cạnh nuôi <b>vẫn</b> nằm trên đường đi phả
 *              hệ — con nuôi được ghi nhận đầy đủ; muốn danh xưng khác thì lọc ở tầng luật, không
 *              bỏ cạnh khỏi đồ thị
 */
public record ParentEdge(PersonId parent, PersonId child, boolean adopt) {

    public ParentEdge {
        Objects.requireNonNull(parent, "parent khong duoc null");
        Objects.requireNonNull(child, "child khong duoc null");
    }

    public static ParentEdge bio(PersonId parent, PersonId child) {
        return new ParentEdge(parent, child, false);
    }

    public static ParentEdge adopt(PersonId parent, PersonId child) {
        return new ParentEdge(parent, child, true);
    }
}

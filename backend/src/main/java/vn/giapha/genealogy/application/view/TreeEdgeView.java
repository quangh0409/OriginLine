package vn.giapha.genealogy.application.view;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.RelType;

/**
 * Một cạnh trên phả đồ.
 *
 * <p>{@code id} phải <b>ổn định giữa các lần gọi</b> vì giao diện gọi nhiều nhánh rồi ghép vào
 * cùng một đồ thị và khử trùng theo id; sinh id ngẫu nhiên là tạo ra cạnh trùng mỗi lần mở rộng
 * một nhánh.</p>
 *
 * @param validTo có giá trị ⇒ quan hệ đã kết thúc (ly hôn hoặc một bên mất) — canvas vẽ nét đứt
 */
public record TreeEdgeView(String id, UUID source, UUID target, RelType relType, HeirKind heirKind,
                           Integer spouseOrder, LocalDate validTo) {
}

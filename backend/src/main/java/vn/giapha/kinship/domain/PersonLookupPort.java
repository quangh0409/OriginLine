package vn.giapha.kinship.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import vn.giapha.shared.vo.PersonId;

/**
 * Port đọc <b>bản chiếu chỉ-đọc</b> của nhân khẩu phục vụ suy luận danh xưng.
 *
 * <p>Vì sao cần port riêng thay vì hỏi context {@code genealogy}: rule engine cần
 * {@code birth_order} và {@code birth_solar} để quyết định vai trên/vai dưới (bác hay chú), mà node
 * trong đồ thị AGE chỉ mang {@code id · gender · generation · is_deleted} (V7 quy định). Hai thuộc
 * tính kia nằm ở bảng {@code person}.</p>
 *
 * <p>Hiện thực đọc thẳng bảng {@code person} bằng SQL chỉ-đọc, <b>không</b> import lớp nào của
 * context {@code genealogy} — ranh giới context được giữ ở mức mã nguồn. Khi
 * {@code genealogy} công bố application service tra cứu, adapter đổi sang gọi service đó mà không
 * ảnh hưởng domain.</p>
 */
public interface PersonLookupPort {

    Optional<PersonView> byId(PersonId id);

    /** Nạp theo lô — một lần tra danh xưng chạm tới toàn bộ hai đường đi lên LCA. */
    Map<PersonId, PersonView> byIds(Collection<PersonId> ids);
}

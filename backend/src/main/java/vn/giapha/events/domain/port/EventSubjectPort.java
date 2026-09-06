package vn.giapha.events.domain.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.events.domain.EventSubject;

/**
 * Cổng đọc <b>ảnh chụp gọn</b> của nhân khẩu chủ thể và chi/ngành đích của sự kiện.
 *
 * <h2>Nợ kiến trúc đã biết — cùng loại với {@code CallerIdentityJdbcAdapter} của W2</h2>
 * {@code person}, {@code person_name} và {@code branch} thuộc sở hữu của context {@code genealogy},
 * nhưng {@code genealogy.application} <b>không</b> phải {@code @NamedInterface} nên context khác
 * không gọi được {@code PersonQueryService}. Hiện thực Giai đoạn 1 đọc thẳng ba bảng ấy bằng SQL:
 * ở cấp Java không có phụ thuộc nào sang {@code genealogy} nên ranh giới module vẫn sạch, nhưng khi
 * {@code genealogy} mở mặt tiền công khai thì adapter phải chuyển sang gọi service đó và bỏ SQL.
 *
 * <p><b>Riêng tư:</b> cổng này trả dữ liệu thô. Việc lọc theo phân tầng (Khách không thấy người còn
 * sống) là trách nhiệm của tầng application/api, không phải của cổng.</p>
 */
public interface EventSubjectPort {

    Optional<EventSubject> findPerson(UUID personId);

    /** Nạp hàng loạt để danh sách sự kiện không sinh truy vấn N+1. */
    Map<UUID, EventSubject> findPersons(Collection<UUID> personIds);

    Optional<EventSubject.BranchSnapshot> findBranch(UUID branchId);

    Map<UUID, EventSubject.BranchSnapshot> findBranches(Collection<UUID> branchIds);
}

package vn.giapha.content.domain.port;

import java.util.Optional;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Tra chi/ngành: {@code branch.id → (tên, ltree path)} và {@code person.id → chi chính}.
 *
 * <h2>Vì sao context này tự khai một cổng thay vì gọi {@code genealogy}</h2>
 * Bảng {@code branch} thuộc {@code genealogy}, nhưng {@code genealogy.application} <b>không</b> là
 * {@code @NamedInterface} — mở cả gói ấy nghĩa là mọi module đều với tới được {@code Person},
 * {@code PersonRepository}, {@code PrivacyTierService}, tức xoá gần hết ranh giới mà
 * {@code ModularityTests} sinh ra để giữ (xem {@code genealogy/package-info.java}). Ba context đã
 * đi đúng lối này trước: {@code membership.infrastructure.branch.BranchLookupJdbcAdapter},
 * {@code events.infrastructure.jdbc.BranchScopeJdbcAdapter} và
 * {@code dataimport.api.support.ImportBranchDirectory}. Ở cấp Java không có phụ thuộc nào sang
 * {@code genealogy}, nên ranh giới module vẫn sạch.
 *
 * <h2>Cổng này chỉ đọc CHI, không bao giờ đọc dữ liệu NHÂN KHẨU</h2>
 * {@link #branchOfPerson} trả về một {@code ltree path}, không trả tên, không trả ngày sinh, không
 * trả gì của con người. Dữ liệu nhân khẩu chỉ ra khỏi hệ thống qua bộ lọc phân tầng riêng tư của
 * {@code genealogy} — ở context này là {@code AuthorDirectory}. Một cổng "tiện thể lấy luôn cái
 * tên" là cách tên người còn sống rò ra khỏi bộ lọc mà không ai thấy.
 */
public interface BranchLocatorPort {

    /** {@code ltree} path của một chi; rỗng nếu chi không tồn tại hoặc đã xoá mềm. */
    Optional<BranchPath> pathOfBranch(UUID branchId);

    /** Tên hiển thị của một chi — dữ liệu công khai của dòng họ, không phải dữ liệu cá nhân. */
    Optional<String> nameOfBranch(UUID branchId);

    /** Chi chính của một nhân khẩu, dạng {@code ltree}; rỗng khi chưa gắn chi nào. */
    Optional<BranchPath> branchOfPerson(UUID personId);

    /** Khoá chi chính của một nhân khẩu — để chụp vào {@code post.branch_id} lúc tạo nháp. */
    Optional<UUID> branchIdOfPerson(UUID personId);
}

package vn.giapha.membership.domain.port;

import java.util.Optional;
import java.util.UUID;
import vn.giapha.shared.vo.BranchPath;

/**
 * Tra {@code ltree} path của một chi, và chi chính của một nhân khẩu.
 *
 * <h2>Nợ kiến trúc đã biết — đối xứng với nợ ở phía genealogy</h2>
 * {@code branch} và {@code person} thuộc context {@code genealogy}. Phân quyền theo phạm vi
 * <b>bắt buộc</b> phải biết path của đối tượng, nhưng {@code genealogy.application} không được
 * đánh dấu {@code @NamedInterface} nên {@code membership} không gọi sang được ở cấp Java mà không
 * làm {@code ModularityTests} đỏ.
 *
 * <p>Adapter Giai đoạn 1 vì vậy đọc <b>chỉ đọc</b> hai bảng đó bằng SQL. Ở cấp Java không có phụ
 * thuộc nào sang {@code genealogy} nên ranh giới module vẫn sạch. Khi {@code genealogy} công bố một
 * mặt tiền tra cứu chi (ví dụ {@code BranchLookupService} kèm {@code @NamedInterface}), adapter này
 * chuyển sang gọi service đó và bỏ SQL — chỗ phải sửa chỉ có <b>một</b>.</p>
 *
 * <p>Đây đúng là mặt gương của ghi chú trên {@code CallerIdentityPort} bên {@code genealogy}, vốn
 * đang đọc thẳng {@code app_user}/{@code branch_assignment} của {@code membership} vì lý do y hệt.</p>
 */
public interface BranchLookupPort {

    /** Path của một chi; rỗng khi chi không tồn tại hoặc đã bị xoá mềm. */
    Optional<BranchPath> pathOfBranch(UUID branchId);

    /** Chi chính của một nhân khẩu; rỗng khi nhân khẩu chưa được gắn chi nào. */
    Optional<BranchPath> branchOfPerson(UUID personId);

    /**
     * Khoá của chi chính của một nhân khẩu.
     *
     * <p>Tách khỏi {@link #branchOfPerson} vì hai lúc dùng khác nhau: lúc <b>gửi</b> yêu cầu cần
     * khoá để lưu vào {@code change_request.target_branch_id}, còn lúc <b>duyệt</b> cần path để so
     * {@code ltree}.</p>
     */
    Optional<UUID> branchIdOfPerson(UUID personId);

    /** Chi tồn tại và chưa bị xoá mềm. */
    boolean branchExists(UUID branchId);

    /**
     * {@code person.version} hiện tại — mốc khoá lạc quan mà một đề nghị đính chính dựa trên.
     *
     * <p><b>Vì sao membership cần biết con số này.</b> Một đề nghị nằm chờ Trưởng chi cả tuần rồi
     * mới được áp dụng. Không có mốc phiên bản đóng dấu <i>lúc gửi</i> thì lệnh ghi lúc duyệt là
     * một cú <b>ghi đè mù</b> lên mọi thay đổi đã xảy ra trong lúc chờ. Xem
     * {@code CorrectionPayload#BASE_VERSION_KEY}.</p>
     *
     * <p>Rỗng khi nhân khẩu không tồn tại. Người gửi vẫn có lối đi: tự gửi kèm
     * {@code _baseVersion} lấy từ {@code ETag} của lần {@code GET} hồ sơ gần nhất — và đó mới là
     * giá trị <i>đúng nhất</i>, vì nó là phiên bản họ thật sự nhìn thấy.</p>
     */
    Optional<Long> versionOfPerson(UUID personId);
}

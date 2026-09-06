package vn.giapha.membership.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.membership.domain.MemberScope;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Kiểm quyền <b>hai chiều</b>: vai trò <i>và</i> phạm vi chi/ngành theo {@code ltree}.
 *
 * <h2>Chiều thứ hai là chiều dễ bỏ sót</h2>
 * Có vai {@code BRANCH_HEAD} trong token <b>không</b> đồng nghĩa được duyệt mọi thứ trong họ:
 * Trưởng Chi chỉ thao tác được trên cây con nằm dưới path được giao. Vì thế mọi phương thức ở đây
 * nhận path của <i>đối tượng</i>, không chỉ nhận vai của người gọi. Một hàm kiểm quyền không có
 * tham số {@link BranchPath} là một hàm kiểm quyền thiếu một nửa.
 *
 * <h2>Chi rỗng không phải là chi công cộng</h2>
 * Đối tượng chưa gắn chi ({@code target == null}) chỉ vai toàn dòng họ được đụng. Coi "không có
 * chi" là "thuộc mọi chi" sẽ biến mỗi bản ghi thiếu dữ liệu thành một lỗ hổng phân quyền — và bản
 * ghi thiếu dữ liệu thì phả hệ nào cũng có.
 *
 * <h2>Vì sao mã lỗi tách làm hai</h2>
 * {@code FORBIDDEN} = sai vai. {@code BRANCH_SCOPE_VIOLATION} = đúng vai, sai nhánh. Người dùng cần
 * phân biệt: "bạn không có quyền này" và "bạn có quyền này nhưng không phải ở chi đó" dẫn tới hai
 * hành động hoàn toàn khác nhau.
 */
@Service
public class BranchScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(BranchScopeGuard.class);

    /**
     * Bắt buộc người gọi được ghi trên dữ liệu thuộc chi {@code target}.
     *
     * @throws ForbiddenException {@code BRANCH_SCOPE_VIOLATION} khi đủ vai nhưng sai phạm vi;
     *         {@code FORBIDDEN} khi không đủ vai
     */
    public void requireWriteAccess(MemberScope caller, BranchPath target) {
        if (caller.isClanWide()) {
            return;
        }
        if (caller.role() == RoleCode.BRANCH_HEAD) {
            if (caller.managesBranch(target)) {
                return;
            }
            log.warn("Tu choi ghi ngoai pham vi: app_user {} nham chi {}",
                    caller.appUserId(), target);
            throw new ForbiddenException(MembershipProblemCodes.BRANCH_SCOPE_VIOLATION,
                    "Tai khoan khong co quyen tren pham vi chi/nganh cua doi tuong nay");
        }
        throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN,
                "Chi Truong chi (trong pham vi duoc giao), Hoi dong Toc bieu hoac Quan tri he thong "
                        + "duoc thao tac tren du lieu nay");
    }

    /**
     * Bắt buộc quyền <b>duyệt</b> một yêu cầu đính chính nhắm vào chi {@code target}.
     *
     * <p>Cùng luật với ghi trực tiếp. Nới lỏng ở đây — ví dụ cho phép duyệt vì "dù sao cũng có
     * người thứ hai xem lại" — là mở đúng lối mà luồng duyệt sinh ra để đóng: một Trưởng chi không
     * được chạm vào chi khác, kể cả qua tay người gửi.</p>
     */
    public void requireReviewAccess(MemberScope caller, BranchPath target) {
        if (!caller.role().canReview()) {
            throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN,
                    "Chi Truong chi, Hoi dong Toc bieu hoac Quan tri he thong duoc duyet yeu cau dinh chinh");
        }
        requireWriteAccess(caller, target);
    }

    /** Bắt buộc vai có phạm vi toàn dòng họ — cấp/thu hồi vai trò, xem nhật ký, khôi phục bản ghi. */
    public void requireClanWide(MemberScope caller, String what) {
        if (!caller.isClanWide()) {
            throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN,
                    "Chi Hoi dong Toc bieu hoac Quan tri he thong duoc " + what);
        }
    }

    /**
     * Bắt buộc là <b>vai kỹ thuật</b> {@code ADMIN}.
     *
     * <p>Tách khỏi {@link #requireClanWide}: Hội đồng Tộc biểu là thẩm quyền <i>nội dung</i> của
     * dòng họ, còn quản trị tài khoản và phân quyền là thẩm quyền <i>kỹ thuật</i>. Trộn hai thứ lại
     * là cách một tranh chấp nội bộ trong họ biến thành một sự cố an ninh hệ thống.</p>
     */
    public void requireSystemAdmin(MemberScope caller, String what) {
        if (!caller.isSystemAdmin()) {
            throw new ForbiddenException(MembershipProblemCodes.FORBIDDEN,
                    "Chi Quan tri he thong duoc " + what);
        }
    }

    /** Bắt buộc người gọi đã có tài khoản trong hệ thống (không phải khách, không phải token lạ). */
    public void requireProvisionedAccount(MemberScope caller) {
        if (caller.isGuest() || caller.appUserId() == null) {
            throw new ForbiddenException(MembershipProblemCodes.ACCOUNT_NOT_PROVISIONED,
                    "Tai khoan chua duoc khoi tao trong he thong, hay dang nhap lai");
        }
    }

    /** Cho phép <b>chính chủ</b> thao tác trên hồ sơ của mình, ngoài ra thì theo luật ghi thường. */
    public void requireSelfOrWriteAccess(MemberScope caller, BranchPath target,
                                         java.util.UUID targetPersonId) {
        if (caller.isSelf(targetPersonId)) {
            return;
        }
        requireWriteAccess(caller, target);
    }
}

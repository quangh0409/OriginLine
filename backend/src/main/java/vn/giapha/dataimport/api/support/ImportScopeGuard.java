package vn.giapha.dataimport.api.support;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Cổng phân quyền của đường ống nhập liệu: <b>vai trò × phạm vi {@code ltree}</b>.
 *
 * <h2>Vì sao phép kiểm nằm ở đây chứ không ở {@code @PreAuthorize}</h2>
 * Quyền nhập liệu phụ thuộc vào <i>chi của lô</i>, mà controller chưa nạp lô lên thì chưa biết chi
 * ấy là gì. Một {@code @PreAuthorize("hasRole('BRANCH_HEAD')")} sẽ cho Trưởng chi Ất đi qua cửa rồi
 * mới chặn ở trong — và tệ hơn, nó tạo cảm giác đã kiểm quyền xong.
 *
 * <h2>Hai mã lỗi, vì hai tình huống dẫn tới hai hành động khác nhau</h2>
 * {@code FORBIDDEN} = sai vai ("bạn không có quyền này"). {@code BRANCH_SCOPE_VIOLATION} = đúng
 * vai, sai nhánh ("bạn có quyền này nhưng không phải ở chi đó"). Gộp làm một là bắt người dùng đoán.
 *
 * <h2>Khoản nợ đã biết: một dòng luật bị chép lại</h2>
 * {@code membership.application.BranchScopeGuard} đã có đúng phép kiểm này, nhưng nó nhận
 * {@code MemberScope} — kiểu của {@code membership.domain}, tức <b>không</b> nằm trong
 * {@code @NamedInterface("application")}, nên gọi được nó là làm {@code ModularityTests} đỏ.
 * Vì vậy ở đây dựng lại phép kiểm trên {@code MemberScopeView}, là kiểu <i>có</i> được công bố.
 * Cách sửa đúng là {@code BranchScopeGuard} thêm một chồng hàm nhận {@code MemberScopeView};
 * lúc đó xoá lớp này đi. Xem báo cáo bàn giao.
 */
@Component
public class ImportScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(ImportScopeGuard.class);

    /** Vai kỹ thuật Trưởng Chi/Ngành, dạng chuỗi vì {@code MemberScopeView.role()} là chuỗi. */
    private static final String BRANCH_HEAD = "BRANCH_HEAD";

    private final MemberScopeService scopes;
    private final ImportBranchDirectory branches;

    public ImportScopeGuard(MemberScopeService scopes, ImportBranchDirectory branches) {
        this.scopes = scopes;
        this.branches = branches;
    }

    public MemberScopeView caller() {
        return scopes.currentScope();
    }

    /**
     * Bắt buộc người gọi đã có tài khoản trong hệ thống.
     *
     * <p>Khách vãng lai và token chưa có {@code app_user} <b>không</b> nhìn thấy đường ống nhập
     * liệu dưới bất kỳ hình thức nào — kể cả danh sách chi rỗng. Trả một danh sách rỗng cho khách
     * là nói với họ rằng màn này tồn tại.</p>
     */
    public MemberScopeView requireProvisioned() {
        MemberScopeView caller = caller();
        if (caller.isGuest() || caller.appUserId() == null) {
            throw new ForbiddenException(ImportProblemCodes.ACCOUNT_NOT_PROVISIONED,
                    "Tai khoan chua duoc khoi tao trong he thong, hay dang nhap lai");
        }
        return caller;
    }

    /**
     * Bắt buộc quyền <b>ghi</b> trên dữ liệu nhập liệu của chi {@code branchId}.
     *
     * <p>Chi không tồn tại (hoặc đã xoá mềm) trả {@code 404}, không phải {@code 403}: trả 403 cho
     * một khoá bịa ra là xác nhận cho người hỏi biết khoá nào có thật.</p>
     *
     * @return chi đã phân giải, để bên gọi không phải tra lại
     */
    public ImportBranchDirectory.Chi requireWriteAccess(UUID branchId) {
        MemberScopeView caller = requireProvisioned();
        ImportBranchDirectory.Chi chi = branches.byId(branchId)
                .orElseThrow(() -> NotFoundException.of("Branch", branchId));
        if (!canWriteOn(caller, chi.path())) {
            log.warn("Tu choi nhap lieu ngoai pham vi: app_user {} (vai {}) nham chi {}",
                    caller.appUserId(), caller.role(), chi.path());
            throw scopeViolation(caller);
        }
        return chi;
    }

    /**
     * {@code true} nếu người gọi được ghi lên dữ liệu thuộc chi {@code target}.
     *
     * <p>Chép nguyên ngữ nghĩa của {@code MemberScope#canWriteOn}: toàn dòng họ, hoặc vai
     * {@code BRANCH_HEAD} <b>và</b> đúng phạm vi. Thành viên thường luôn {@code false} — muốn sửa
     * phả thì gửi yêu cầu đính chính, không nộp cả một tệp.</p>
     */
    public boolean canWriteOn(MemberScopeView caller, BranchPath target) {
        if (caller.clanWide()) {
            return true;
        }
        return BRANCH_HEAD.equals(caller.role()) && caller.managesBranch(target);
    }

    /** Sai vai thì {@code FORBIDDEN}; đúng vai sai nhánh thì {@code BRANCH_SCOPE_VIOLATION}. */
    private ForbiddenException scopeViolation(MemberScopeView caller) {
        if (BRANCH_HEAD.equals(caller.role())) {
            return new ForbiddenException(ImportProblemCodes.BRANCH_SCOPE_VIOLATION,
                    "Tai khoan khong co quyen nhap lieu tren pham vi chi/nganh cua lo nay");
        }
        return new ForbiddenException(ImportProblemCodes.FORBIDDEN,
                "Chi Truong chi (trong pham vi duoc giao), Hoi dong Toc bieu hoac Quan tri he thong"
                        + " duoc nhap lieu hang loat");
    }
}

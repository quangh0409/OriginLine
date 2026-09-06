package vn.giapha.genealogy.application;

import org.springframework.stereotype.Service;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.BranchPath;

/**
 * Kiểm quyền ghi trên dữ liệu phả hệ - <b>hai chiều</b>: vai trò <i>và</i> phạm vi chi/ngành theo
 * {@code ltree}.
 *
 * <p>Chiều thứ hai mới là chiều dễ bỏ sót. Có vai {@code BRANCH_HEAD} trong token <b>không</b>
 * đồng nghĩa được sửa mọi người trong họ: Trưởng Chi chỉ thao tác được trên cây con nằm dưới path
 * được giao. Vì thế mọi lệnh ghi phải đi qua đây với path của <i>đối tượng</i>, không phải chỉ với
 * vai của người gọi.</p>
 *
 * <p><b>Nhân khẩu chưa gắn chi</b> ({@code path == null}) chỉ {@code ADMIN}/{@code COUNCIL} được
 * đụng tới. Coi "không có chi" là "thuộc mọi chi" sẽ biến mỗi bản ghi thiếu dữ liệu thành một lỗ
 * hổng phân quyền.</p>
 */
@Service
public class GenealogyAccessGuard {

    /**
     * Bắt buộc người gọi được ghi trên nhân khẩu thuộc chi {@code target}.
     *
     * @throws ForbiddenException mã {@code BRANCH_SCOPE_VIOLATION} khi đủ vai nhưng sai phạm vi,
     *         mã {@code FORBIDDEN} khi không đủ vai
     */
    public void requireWriteAccess(CallerContext caller, BranchPath target) {
        if (caller.role().isClanWide()) {
            return;
        }
        if (caller.role() == CallerRole.BRANCH_HEAD) {
            if (caller.managesBranch(target)) {
                return;
            }
            throw new ForbiddenException(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION,
                    "Tai khoan khong co quyen tren pham vi chi/nganh cua nhan khau nay");
        }
        throw new ForbiddenException(GenealogyProblemCodes.FORBIDDEN,
                "Chi Truong chi (trong pham vi duoc giao), Hoi dong Toc bieu hoac Quan tri he thong "
                        + "duoc sua du lieu pha he");
    }

    /**
     * Như {@link #requireWriteAccess} nhưng cho phép <b>chính chủ</b> sửa hồ sơ của mình.
     *
     * <p>Thành viên được sửa hồ sơ mình (kể cả {@code privacyLevel} của mình) nhưng không được
     * đụng vào quan hệ phả hệ - đó là lý do quan hệ có use case riêng, không đi qua endpoint sửa
     * hồ sơ.</p>
     */
    public void requireProfileWriteAccess(CallerContext caller, BranchPath target,
                                          java.util.UUID targetPersonId) {
        if (caller.isSelf(targetPersonId)) {
            return;
        }
        requireWriteAccess(caller, target);
    }

    /** Bắt buộc vai có phạm vi toàn dòng họ - khôi phục bản ghi đã xoá mềm, xem dữ liệu đã xoá. */
    public void requireClanWide(CallerContext caller, String what) {
        if (!caller.role().isClanWide()) {
            throw new ForbiddenException(GenealogyProblemCodes.FORBIDDEN,
                    "Chi Hoi dong Toc bieu hoac Quan tri he thong duoc " + what);
        }
    }

    /**
     * Bắt buộc quyền trên <b>cả hai</b> chi khi chuyển nhân khẩu sang chi khác.
     *
     * <p>Chỉ kiểm chi đích thì một Trưởng chi có thể kéo người của chi khác về chi mình rồi tự
     * cấp cho mình quyền sửa người đó - vòng lách kinh điển của phân quyền theo phạm vi.</p>
     */
    public void requireMoveAccess(CallerContext caller, BranchPath from, BranchPath to) {
        requireWriteAccess(caller, from);
        requireWriteAccess(caller, to);
    }
}

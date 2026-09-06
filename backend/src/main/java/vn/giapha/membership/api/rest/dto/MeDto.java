package vn.giapha.membership.api.rest.dto;

import java.util.List;
import java.util.UUID;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.vo.BranchPath;

/**
 * Hồ sơ phiên làm việc của người đang đăng nhập — {@code GET /api/v1/me}.
 *
 * <h2>Vì sao frontend cần đúng những trường này</h2>
 * Giao diện phải biết <b>phạm vi</b>, không chỉ vai, để quyết định hiện hay ẩn nút "Duyệt": một
 * Trưởng chi thấy nút ấy trên hồ sơ của chi mình và không thấy trên chi khác. Nếu chỉ trả vai thì
 * frontend sẽ hiện nút cho mọi hồ sơ rồi để backend trả 403 — đúng về bảo mật nhưng tệ về trải
 * nghiệm, và nó rò rỉ thông tin về cấu trúc quyền qua chuỗi lỗi.
 *
 * <p><b>Đây không phải là quyết định phân quyền.</b> Danh sách này chỉ để vẽ giao diện; mọi phép
 * kiểm thật nằm ở backend. Client sửa được phản hồi này, nhưng sửa xong vẫn không ghi được gì.</p>
 *
 * @param appUserId       {@code null} nếu tài khoản chưa được khởi tạo
 * @param personId        {@code null} nếu chưa được ghép vào cây phả hệ
 * @param role            vai rộng nhất trong token
 * @param clanWide        có phạm vi toàn dòng họ hay không
 * @param managedBranches các chi được giao; <b>rỗng nghĩa là không có phạm vi nào</b>
 * @param homeBranch      chi chính của người dùng
 * @param linkedToTree    tiện dụng cho giao diện: đã ghép vào cây chưa
 */
public record MeDto(UUID appUserId, UUID personId, String role, boolean clanWide,
                    List<String> managedBranches, String homeBranch, boolean linkedToTree) {

    public static MeDto from(MemberScopeView scope) {
        return new MeDto(scope.appUserId(), scope.personId(), scope.role(), scope.clanWide(),
                scope.managedBranches().stream().map(BranchPath::value).toList(),
                scope.homeBranch() == null ? null : scope.homeBranch().value(),
                scope.personId() != null);
    }
}

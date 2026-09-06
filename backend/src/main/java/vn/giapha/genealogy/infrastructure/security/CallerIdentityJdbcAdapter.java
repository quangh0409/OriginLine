package vn.giapha.genealogy.infrastructure.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.domain.port.CallerIdentityPort;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.vo.BranchPath;

/**
 * Hiện thực {@link CallerIdentityPort} bằng cách hỏi <b>đúng context sở hữu dữ liệu</b>:
 * {@code membership}. Chuỗi {@code keycloak_sub → app_user → person} và phạm vi chi/ngành đều do
 * {@link MemberScopeService} phân giải.
 *
 * <h2>Khoản nợ W2 đã được trả</h2>
 * Bản Giai đoạn 1 đọc thẳng {@code app_user} và {@code branch_assignment} bằng SQL (lớp
 * {@code AppUserDirectory}, nay đã xoá) vì W6 chưa có. Hệ quả là <b>hai</b> chỗ cùng biết cấu trúc
 * của hai bảng ấy và cùng phải nhớ điều kiện hiệu lực {@code valid_from}/{@code valid_to} — đúng
 * kiểu trùng lặp mà chỉ cần một bên quên lọc là sinh ra một lỗ hổng phân quyền không ai thấy.
 * {@code membership.application} nay là {@code @NamedInterface}, nên lời gọi này hợp lệ với
 * Spring Modulith và luật phạm vi chỉ còn <b>một bản</b>.
 *
 * <h2>Ngữ nghĩa được giữ nguyên từng điểm một</h2>
 * <ul>
 *   <li>{@link #managedBranches()} chỉ trả các chi có <b>phân công thật</b>. Vai toàn dòng họ
 *       (ADMIN/COUNCIL) không sinh ra chi nào ở đây — đúng như câu SQL cũ vốn lọc
 *       {@code branch_id IS NOT NULL}. Danh sách rỗng nghĩa là <b>không có phạm vi nào</b>, tuyệt
 *       đối không được diễn giải ngược thành "không giới hạn"; phía {@code genealogy} tự xét vai
 *       toàn cục bằng {@code CallerRole} lấy từ token.</li>
 *   <li>Phân công hết hiệu lực bị loại — nay ở {@code BranchAssignmentRepository.activeFor}.</li>
 *   <li>Khách vãng lai: cả ba phương thức trả rỗng.</li>
 * </ul>
 *
 * <h2>Ba lần dựng phạm vi cho một lượt gọi</h2>
 * {@code PrivacyTierService.caller()} gọi cả ba phương thức liền nhau, mỗi phương thức dựng lại
 * {@code MemberScope} một lần. Chấp nhận được vì {@code caller()} chỉ chạy <b>một lần cho mỗi
 * request</b> chứ không chạy trên từng nhân khẩu. Cách sửa đúng là thu {@link CallerIdentityPort}
 * còn một phương thức trả cả ba giá trị — nhưng cổng đó thuộc {@code genealogy}, nên để lại thành
 * việc của bên sở hữu. Cố ý <b>không</b> cache: một Trưởng chi vừa bị bãi nhiệm mà vẫn thao tác
 * được cho tới lúc cache hết hạn là cái giá đắt hơn nhiều so với hai câu SELECT theo khoá chính.
 */
@Component
public class CallerIdentityJdbcAdapter implements CallerIdentityPort {

    private final MemberScopeService scopes;

    public CallerIdentityJdbcAdapter(MemberScopeService scopes) {
        this.scopes = scopes;
    }

    @Override
    public Optional<UUID> currentPersonId() {
        return Optional.ofNullable(scopes.currentScope().personId());
    }

    @Override
    public List<BranchPath> managedBranches() {
        return scopes.currentScope().managedBranches();
    }

    @Override
    public Optional<BranchPath> homeBranch() {
        MemberScopeView scope = scopes.currentScope();
        return Optional.ofNullable(scope.homeBranch());
    }
}

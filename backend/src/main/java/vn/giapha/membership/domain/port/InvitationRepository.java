package vn.giapha.membership.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.Invitation;
import vn.giapha.shared.vo.BranchPath;

/**
 * Lưu trữ lời mời. Khoá tra cứu của người nhận là <b>băm của mã</b>, không phải id.
 *
 * <p>Không có phương thức nào nhận mã thô: mã thô không được rời tầng application, và một chữ ký
 * {@code byCode(String)} ở đây sớm muộn sẽ dẫn tới một câu log in ra tham số của nó.</p>
 */
public interface InvitationRepository {

    Optional<Invitation> byId(UUID id);

    /** Tra theo {@code ux_invitation_code_hash}. Băm do {@code InvitationCode.hash} dựng. */
    Optional<Invitation> byCodeHash(String codeHash);

    /**
     * Lời mời <b>đang mở</b> của một nhân khẩu — nhiều nhất một
     * ({@code ux_invitation_open_person}).
     *
     * <p>"Đang mở" ở đây nghĩa là {@code status = PENDING}, <b>chưa</b> xét hạn: phát lại cho cùng
     * một người phải thu hồi cả mã quá hạn, nếu không {@code ux_invitation_open_person} sẽ chặn
     * lệnh chèn và Trưởng chi nhận một lỗi CSDL thay vì một mã mới.</p>
     */
    Optional<Invitation> openForPerson(UUID personId);

    /**
     * Lời mời thuộc các chi nằm trong phạm vi người gọi.
     *
     * <p>{@code clanWide = false} và {@code scopes} rỗng phải trả rỗng. Tuyệt đối không hiểu ngược
     * "không có phạm vi nào" thành "thấy tất" — đây là lỗi kinh điển của phân quyền theo scope.</p>
     */
    List<Invitation> inScope(List<BranchPath> scopes, boolean clanWide, int limit, int offset);

    Invitation save(Invitation invitation);
}

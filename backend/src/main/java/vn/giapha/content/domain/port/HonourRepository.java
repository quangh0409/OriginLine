package vn.giapha.content.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Honour;
import vn.giapha.content.domain.HonourKind;
import vn.giapha.shared.vo.BranchPath;

/**
 * Lưu trữ vinh danh.
 *
 * <h2>Cột chi không tồn tại; phép lọc theo chi là một JOIN sang {@code person}</h2>
 * Xem javadoc {@code Honour}: chi của một vinh danh luôn là chi <b>hiện tại</b> của nhân khẩu, vì
 * câu hỏi nghiệp vụ là "chi nào có bao nhiêu người đỗ đạt". Hiện thực vì thế phải nối
 * {@code honour → person → branch} và so {@code ltree} ở đó, không được thêm một cột
 * {@code branch_id} vào bảng cho tiện.
 *
 * <h2>Bản ghi đã xoá mềm không bao giờ ra khỏi đây</h2>
 * Mọi phương thức lọc {@code is_deleted = FALSE} trong SQL. Để bộ lọc ấy cho tầng trên nghĩa là
 * mỗi lối đọc mới lại phải nhớ một lần — và sẽ có lối quên.
 */
public interface HonourRepository {

    /** Kể cả bản ghi đã xoá mềm: tầng use case cần phân biệt {@code 404} với "đã gỡ". */
    Optional<Honour> byId(UUID id);

    Honour save(Honour honour);

    /**
     * Tra cứu có lọc. Chỉ trả bản ghi <b>chưa xoá mềm</b>.
     *
     * @param personId lọc theo nhân khẩu; {@code null} = mọi người
     * @param kind     lọc theo loại; {@code null} = mọi loại
     * @param branchId lọc theo chi — nối sang {@code person.primary_branch_id} rồi lấy cả
     *                 <b>cây con</b> theo {@code ltree}, vì "Chi Giáp có bao nhiêu người đỗ đạt"
     *                 phải tính cả các ngành/cành bên dưới
     * @param status   lọc theo trạng thái duyệt; {@code null} = mọi trạng thái người gọi được thấy
     * @param callerUserId {@code app_user.id} của người gọi — căn cứ "bản ghi tôi khai"
     * @param scopes   các chi người gọi duyệt được; rỗng = không chi nào
     * @param clanWide phạm vi toàn dòng họ
     */
    List<Honour> search(UUID personId, HonourKind kind, UUID branchId, ContentStatus status,
                        UUID callerUserId, List<BranchPath> scopes, boolean clanWide,
                        int limit, int offset);

    /** Đếm cho phân trang — <b>cùng mệnh đề WHERE</b> với {@link #search}. */
    long count(UUID personId, HonourKind kind, UUID branchId, ContentStatus status,
               UUID callerUserId, List<BranchPath> scopes, boolean clanWide);

    /** Số bản ghi chờ duyệt trong phạm vi — cho badge trên giao diện. */
    long countPendingInScope(List<BranchPath> scopes, boolean clanWide);

    /**
     * Lối đọc của <b>Khách</b> (quyết định 26/09/2026): chỉ bản ghi {@code PUBLISHED} của nhân
     * khẩu <b>đã khuất</b> và chưa xoá mềm.
     *
     * <p>Tách hẳn khỏi {@link #search} thay vì truyền {@code callerUserId = null} vào đó, vì hai lẽ.
     * Một: luật "ai được XỬ LÝ" của {@link #search} không có nghĩa gì với người không có tài khoản,
     * và một tham số {@code uuid} rỗng đứng cạnh {@code =} là loại lỗi kiểu Postgres chỉ nổ lúc
     * chạy. Hai, quan trọng hơn: lọc {@code is_alive = FALSE} <b>trong SQL</b> làm
     * {@code totalElements} của Khách đếm đúng — đếm trước bộ lọc riêng tư như lối thành viên thì
     * chính con số tổng đã nói ra "có N vinh danh của người còn sống", một lần lộ không cần tên.</p>
     *
     * <p>Tầng trên <b>vẫn</b> hỏi {@code genealogy} cho từng nhân khẩu; mệnh đề SQL này là lớp chặn
     * thứ nhất, không phải lớp duy nhất.</p>
     */
    List<Honour> searchPublishedOfDeceased(UUID personId, HonourKind kind, UUID branchId,
                                           int limit, int offset);

    /** Đếm cho phân trang của Khách — <b>cùng mệnh đề WHERE</b> với {@link #searchPublishedOfDeceased}. */
    long countPublishedOfDeceased(UUID personId, HonourKind kind, UUID branchId);
}

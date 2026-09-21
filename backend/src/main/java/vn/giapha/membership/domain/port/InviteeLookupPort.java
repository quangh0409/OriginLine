package vn.giapha.membership.domain.port;

import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.ClanOffice;
import vn.giapha.membership.domain.Invitee;

/**
 * Tra <b>đúng những gì màn nhận lời mời được phép hiện</b> về một nhân khẩu.
 *
 * <h2>Nợ kiến trúc đã biết — y hệt {@link BranchLookupPort}</h2>
 * {@code person}, {@code person_name} và {@code branch} thuộc context {@code genealogy}, mà
 * {@code genealogy.application} không được đánh dấu {@code @NamedInterface} nên không gọi sang được
 * ở cấp Java mà không làm {@code ModularityTests} đỏ. Adapter Giai đoạn 1 vì vậy đọc <b>chỉ đọc</b>
 * ba bảng đó bằng SQL. Khi {@code genealogy} công bố mặt tiền tra cứu, chỗ phải sửa chỉ có một.
 *
 * <h2>Cổng này cố ý HẸP</h2>
 * Nó trả về tên hiển thị, đời thứ, khối chi/ngành và chức danh dòng tộc của người mời — không gì
 * khác. Không nghề nghiệp, không năm sinh, không điện thoại, không ảnh. Người đọc đầu ra của cổng
 * này là <b>người chưa đăng nhập</b> đang cầm một mã mời; mở rộng chữ ký ở đây là mở rộng đúng thứ
 * mà một mã mời rò rỉ sẽ tiết lộ.
 *
 * <h2>Vì sao KHÔNG có "danh xưng với người mời" ở đây</h2>
 * Bản thiết kế muốn in "Con dâu ông Nguyễn Văn Bốn" trên màn nhận lời mời, và đó là một câu đẹp.
 * Giai đoạn 1 <b>không</b> trả nó, vì ba lý do độc lập, mỗi lý do tự nó đã đủ:
 * <ol>
 *   <li>Danh xưng là việc của context {@code kinship}, mà {@code kinship.application} không phải
 *       {@code @NamedInterface} — gọi sang làm {@code ModularityTests} đỏ, và nới nhãn cho cả gói
 *       ấy là một quyết định về ranh giới chứ không phải một chi tiết của luồng mời.</li>
 *   <li>{@code ResolveKinshipTitleService} chạy {@code KinshipDisplayPolicy} — bộ che tên theo phân
 *       tầng riêng tư, xét theo <i>người gọi</i>. Người gọi ở đây là <b>khách không token</b>, nên
 *       kết quả sẽ bị che và câu trả lời hoặc rỗng hoặc sai.</li>
 *   <li>Tính danh xưng là một truy vấn LCA bằng Cypher. Mở một phép duyệt đồ thị trên một endpoint
 *       không cần đăng nhập là một bề mặt tấn công khác hẳn một lần tra tên.</li>
 * </ol>
 * Trường tương ứng ở API vì vậy <b>vắng mặt</b>, không phải {@code null} rỗng nghĩa.
 */
public interface InviteeLookupPort {

    /** Rỗng khi nhân khẩu không tồn tại. Nhân khẩu đã xoá mềm vẫn trả về, kèm cờ {@code deleted}. */
    Optional<Invitee> byId(UUID personId);

    /**
     * Chức danh dòng tộc của một nhân khẩu — suy từ {@code branch.head_person_id}.
     *
     * <p>Rỗng là câu trả lời <b>đúng</b> với phần lớn người trong họ, không phải một giá trị thiếu.
     * Và lưu ý: đây là chức danh <i>dòng tộc</i>, hoàn toàn tách khỏi vai kỹ thuật
     * {@code BRANCH_HEAD} trong {@code branch_assignment}.</p>
     */
    Optional<ClanOffice> clanOfficeOf(UUID personId);

    /**
     * Số nhân khẩu <b>còn sống, chưa xoá mềm</b> trong cả dòng họ — <b>mẫu số của bộ đếm mã mời</b>.
     *
     * <h2>Vì sao một phép đếm lại nằm ở cổng này</h2>
     * Lập luận của design 07 §1.2 là <i>"mã đã dùng 400 lần trong khi dòng họ có 600 người"</i>.
     * Vế thứ hai mới làm vế thứ nhất có nghĩa: <b>một bộ đếm không có mẫu số thì không ai phán xét
     * được</b>. Hội đồng nhìn con số 400 trần trụi sẽ không biết nên lo hay không.
     *
     * <p>Đây là {@code count(*) WHERE is_alive}, không phải một báo cáo dân số — nó cố ý
     * <b>không</b> phân theo chi, không phân theo đời, và không đi qua bộ lọc riêng tư nào, vì nó
     * không tiết lộ ai cả. Khi context {@code reporting} của Giai đoạn 3 có một con số chính thức
     * thì chỗ đúng để lấy là ở đó, và chỗ phải sửa chỉ có một.</p>
     */
    int countLivingPersons();
}

package vn.giapha.dataimport.domain;

import java.util.List;
import vn.giapha.shared.exception.DomainException;

/**
 * Lời <b>từ chối gỡ lô</b> — và từ chối là hành vi <b>đúng</b>, không phải một hạn chế tạm thời.
 *
 * <h2>Gỡ lô khác xoá người</h2>
 * Xoá một nhân khẩu là việc của Trưởng chi và luôn là xoá mềm. Gỡ một lô thì khác: nó xoá mềm
 * <b>hàng trăm người cùng lúc</b> và gỡ mọi cạnh của họ. Chừng nào chưa ai khác động vào, đó là
 * một phép hoàn tác sạch. Nhưng nếu sau khi ghi đã có người bổ sung ảnh, sửa ngày giỗ, treo thêm
 * một đứa con, hay nhận hồ sơ của mình qua Zalo — thì gỡ lô sẽ <b>âm thầm nuốt mất</b> công của
 * họ, và con của họ mất cha trong phả đồ. Một nút gỡ làm mất dữ liệu còn tệ hơn không có nút gỡ,
 * vì nó tạo cảm giác an toàn giả.
 *
 * <h2>Nói thẳng, và đưa danh sách việc phải làm tay</h2>
 * Kế hoạch §13.2 ghi rõ: sau ba tháng lệnh này gần như chắc chắn sẽ từ chối, và giá trị thật của
 * nó nằm ở <b>tuần đầu</b>. Vì vậy khi từ chối, thông điệp phải nói ra <b>ai/ cái gì</b> đang
 * chặn, chứ không phải một câu "không thể hoàn tác" — người dùng còn phải quyết định làm tay.
 *
 * @param lyDo danh sách vướng mắc cụ thể, mỗi phần tử là một câu tiếng Việt đọc được
 */
public class RollbackRefusedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "IMP_ROLLBACK_REFUSED";

    private final transient List<String> lyDo;

    public RollbackRefusedException(String message, List<String> lyDo) {
        super(CODE, message);
        this.lyDo = List.copyOf(lyDo);
    }

    public List<String> lyDo() {
        return lyDo;
    }
}

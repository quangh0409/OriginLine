package vn.giapha.content.application;

import vn.giapha.shared.exception.DomainException;

/**
 * Thiếu hoặc sai {@code If-Match} khi sửa — ánh xạ sang HTTP <b>412</b>.
 *
 * <h2>Vì sao lớp này ở application chứ không ở api, khác {@code genealogy}</h2>
 * {@code genealogy.api.rest.PreconditionRequiredException} nằm ở tầng api vì ở đó chỉ <i>lớp
 * HTTP</i> ném nó ra. Ở context này thì <b>cả hai tầng</b> đều ném: tầng api khi header vắng mặt,
 * và tầng application khi {@code expectedVersion} rỗng ở một lối vào không đi qua HTTP (GraphQL,
 * job nền). Một kiểu ngoại lệ ở tầng api mà tầng application phải ném là phụ thuộc ngược chiều —
 * thứ {@code api → application → domain} cấm. Nên nó ở đây, và tầng api chỉ dùng lại.
 *
 * <p><b>Bắt buộc {@code If-Match} chứ không cho ghi mù:</b> §2 yêu cầu <i>lưu nháp tự động</i>,
 * tức hai tab cùng ghi là hành vi bình thường chứ không phải ca hiếm. Im lặng ghi đè ở đó là mất
 * dữ liệu mà không ai phát hiện ra.</p>
 */
public class ContentPreconditionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ContentPreconditionException(String message) {
        super(ContentProblemCodes.PRECONDITION_REQUIRED, message);
    }
}

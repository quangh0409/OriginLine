package vn.giapha.genealogy.api.rest;

import vn.giapha.genealogy.application.GenealogyProblemCodes;
import vn.giapha.shared.exception.DomainException;

/**
 * Thiếu hoặc sai header {@code If-Match} khi sửa hồ sơ - ánh xạ sang HTTP <b>412</b>.
 *
 * <p>Thuần tuý là mối bận tâm của lớp HTTP nên nằm ở tầng api, không phải application: cơ chế
 * chống ghi đè của tầng dưới là {@code expectedVersion}, còn cách client mang giá trị đó lên
 * ({@code ETag}/{@code If-Match}) là chuyện của giao thức.</p>
 *
 * <p>Bắt buộc {@code If-Match} chứ không cho ghi mù: gia phả là dữ liệu nhiều người cùng biên tập,
 * và im lặng ghi đè công của người khác là loại mất dữ liệu không ai phát hiện ra.</p>
 */
public class PreconditionRequiredException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PreconditionRequiredException(String message) {
        super(GenealogyProblemCodes.PRECONDITION_REQUIRED, message);
    }
}

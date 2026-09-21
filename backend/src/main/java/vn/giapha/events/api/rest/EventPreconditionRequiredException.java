package vn.giapha.events.api.rest;

import vn.giapha.events.application.EventProblemCodes;

/**
 * Thiếu hoặc sai header {@code If-Match} khi sửa sự kiện — ánh xạ sang HTTP <b>412</b>.
 *
 * <p>Nằm ở tầng {@code api} chứ không ở {@code application}: khoá lạc quan là luật nghiệp vụ, còn
 * <i>cách diễn đạt nó bằng {@code ETag}/{@code If-Match}</i> là chuyện của giao thức. Tầng
 * application chỉ nhận một con số {@code expectedVersion}.</p>
 *
 * <p>Bắt buộc {@code If-Match} chứ không cho ghi mù: trước mỗi mùa giỗ chạp, lịch việc họ là thứ
 * nhiều người cùng ngồi sửa.</p>
 *
 * <p>Có lớp riêng thay vì dùng {@code genealogy.api.rest.PreconditionRequiredException}: lớp kia
 * nằm trong package nội bộ của context {@code genealogy} (không phải {@code @NamedInterface}), nên
 * chạm vào là {@code ModularityTests} đỏ.</p>
 */
public class EventPreconditionRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventPreconditionRequiredException(String message) {
        super(message);
    }

    public String getCode() {
        return EventProblemCodes.PRECONDITION_REQUIRED;
    }
}

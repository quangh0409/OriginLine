package vn.giapha.membership.api.rest.dto;

import java.util.UUID;
import vn.giapha.membership.application.MemberScopeView;

/**
 * Nhân khẩu ứng với người đang đăng nhập — {@code GET /api/v1/me/person}.
 *
 * <h2>Vì sao tách khỏi {@link MeDto} dù không mang thêm trường nào mới</h2>
 * {@code /api/v1/me} luôn {@code 200} và nói "chưa ghép" bằng một trường {@code null}
 * ({@code personId}) cộng một cờ ({@code linkedToTree}). Cách ấy đúng cho màn hình phiên làm việc,
 * nhưng nó biến "tài khoản chưa được ghép vào phả" thành <b>một điều kiện mà mọi nơi gọi phải nhớ
 * tự kiểm</b> — và chỗ nào quên thì mang một {@code null} đi tiếp cho tới khi nó nổ ở một endpoint
 * khác dưới hình dạng một lỗi chẳng liên quan gì.
 *
 * <p>Endpoint này trả lời đúng một câu và trả lời <b>bằng mã HTTP</b>: {@code 200} là đã ghép,
 * {@code 409} kèm {@code code = ACCOUNT_NOT_PROVISIONED} là chưa. Bộ định tuyến của giao diện rẽ
 * nhánh theo mã, không phải theo một trường có thể {@code null}.</p>
 *
 * <p><b>Cố ý không mang dữ liệu nhân khẩu.</b> Hồ sơ đầy đủ nằm ở {@code GET /api/v1/persons/{id}}
 * của context {@code genealogy}, nơi bộ lọc riêng tư theo nhóm trường thực sự chạy. Trả thêm tên
 * hay ngày sinh ở đây là dựng một lối ra thứ hai cho dữ liệu nhân khẩu, đi vòng qua bộ lọc ấy.</p>
 *
 * @param appUserId  định danh tài khoản
 * @param personId   nhân khẩu đã được ghép — <b>không bao giờ {@code null}</b> ở phản hồi 200
 * @param homeBranch đường dẫn {@code ltree} của chi nhà, {@code null} nếu nhân khẩu chưa gắn chi
 */
public record MePersonDto(UUID appUserId, UUID personId, String homeBranch) {

    public static MePersonDto from(MemberScopeView scope) {
        return new MePersonDto(scope.appUserId(), scope.personId(),
                scope.homeBranch() == null ? null : scope.homeBranch().value());
    }
}

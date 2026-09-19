package vn.giapha.genealogy.api.rest.public_;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ép mọi lượt gọi trên bề mặt công khai chạy với tư cách <b>Khách vãng lai</b>.
 *
 * <h2>Vì sao phải ép, thay vì tin rằng khách thì không có token</h2>
 * {@code /api/v1/public/**} là {@code permitAll}, nhưng {@code permitAll} <b>không</b> nghĩa là
 * "không có ai đăng nhập": một thành viên (hay Quản trị) vẫn gửi được {@code Authorization} tới
 * đây, và Spring Security vẫn dựng {@code SecurityContext} cho họ. Nếu để nguyên, cùng một URL
 * công khai sẽ trả nội dung khác nhau tuỳ token — kéo theo hai hệ quả xấu:
 * <ul>
 *   <li>{@code PrivacyTierService.toView} mở khối liên hệ của người đã khuất cho vai toàn dòng họ
 *       ({@code publicTier && role.isClanWide()}). Số điện thoại ghi trong hồ sơ một cụ đã mất
 *       trên thực tế là số của người thân <b>đang sống</b>.</li>
 *   <li>Không thể đặt {@code Cache-Control: public} — một bản cache của vai này rơi vào tay vai
 *       khác là rò rỉ không để lại dấu vết nào trong log.</li>
 * </ul>
 *
 * <p>Sau khi ép, {@code CurrentUserProvider.current()} trả rỗng ⇒ {@code CallerContext.guest()} ⇒
 * {@code canSee} loại sạch người còn sống. Bề mặt công khai vì thế có <b>một</b> hình dạng phản hồi
 * duy nhất, giống nhau với mọi người gọi.</p>
 *
 * <p><b>Không dùng lớp này ở đâu khác.</b> Nó hạ quyền, và hạ quyền giữa một luồng nghiệp vụ có
 * ghi dữ liệu sẽ làm cột {@code created_by}/{@code updated_by} thành {@code anonymous} một cách
 * lặng lẽ. Ở đây an toàn vì mọi endpoint công khai đều chỉ đọc.</p>
 */
@Component
public class PublicGuestScope {

    private static final Logger log = LoggerFactory.getLogger(PublicGuestScope.class);

    /**
     * Chạy {@code action} với {@code SecurityContext} rỗng rồi <b>khôi phục nguyên trạng</b>.
     *
     * <p>Khôi phục nằm trong {@code finally}: bỏ sót sẽ để lại một thread của pool servlet mang
     * ngữ cảnh rỗng, và request kế tiếp dùng lại thread đó sẽ mất danh tính người gọi.</p>
     */
    public <T> T asGuest(Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        boolean hadToken = previous.getAuthentication() != null
                && previous.getAuthentication().isAuthenticated();
        try {
            SecurityContextHolder.clearContext();
            if (hadToken) {
                log.debug("Cong thong tin cong khai: ha quyen ve Khach du yeu cau co mang token");
            }
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}

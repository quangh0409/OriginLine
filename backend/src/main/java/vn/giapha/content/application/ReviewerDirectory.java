package vn.giapha.content.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.MemberScopeView;

/**
 * {@code app_user.id} của người duyệt → {@code person.id} của người ấy.
 *
 * <h2>Vì sao phải có bước này, thay vì lưu thẳng {@code person_id} ở cột {@code reviewed_by}</h2>
 * Cột {@code reviewed_by} giữ <b>khoá tài khoản</b>, đúng quy ước của
 * {@code change_request.reviewer_id}: câu hỏi "ai đã bấm nút, lúc nào" là câu hỏi về một <i>phiên
 * đăng nhập</i>, và nó phải trả lời được kể cả khi tài khoản ấy về sau bị gỡ khỏi nhân khẩu. Nhưng
 * thứ hiện lên màn hình lại là câu hỏi khác — <b>người nào trong họ</b> đã duyệt — và câu ấy chỉ
 * trả lời được qua nhân khẩu, rồi qua bộ lọc riêng tư.
 *
 * <h2>Đi qua {@code MemberScopeService}, không đọc bảng {@code app_user}</h2>
 * {@code app_user} thuộc context {@code membership}, và context ấy <b>có</b> mặt tiền công khai
 * ({@code @NamedInterface("application")}). Đọc thẳng bảng khi đã có mặt tiền là tự chọn con đường
 * xấu hơn: mọi luật về trạng thái tài khoản sẽ có hai bản. Khác hẳn ca {@code branch}, nơi
 * {@code genealogy.application} <i>không</i> được công bố nên đọc SQL là lối duy nhất còn lại.
 *
 * <h2>Giá phải trả, nói thẳng</h2>
 * {@link MemberScopeService#scopeOfUser} dựng cả phạm vi chi/ngành chỉ để lấy một
 * {@code person_id} — thừa vài câu SELECT cho mỗi người duyệt <i>khác nhau</i> trong một trang.
 * Thực tế một trang 20 bài thường có hai hoặc ba người duyệt, nên lớp này <b>gộp trùng</b> trước
 * khi hỏi. Nếu đo đạc về sau cho thấy đây là điểm nghẽn thì cách sửa đúng là thêm một phương thức
 * tra theo lô vào {@code MemberScopeService}, <b>không</b> phải mở một câu SQL riêng ở đây.
 */
@Component
public class ReviewerDirectory {

    private final MemberScopeService scopes;

    public ReviewerDirectory(MemberScopeService scopes) {
        this.scopes = scopes;
    }

    /**
     * Tra {@code app_user.id} → {@code person.id}.
     *
     * @return map chỉ chứa những tài khoản <b>đã được gắn nhân khẩu</b>. Tài khoản vắng mặt nghĩa
     *         là chưa gắn (hoặc đã bị xoá), và bên gọi phải hiển thị như "không biết tên" — chứ
     *         không bịa một chuỗi thay thế
     */
    public Map<UUID, UUID> personIdsOf(Collection<UUID> appUserIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (appUserIds != null) {
            appUserIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        Map<UUID, UUID> result = new LinkedHashMap<>();
        for (UUID appUserId : ids) {
            scopes.scopeOfUser(appUserId)
                    .map(MemberScopeView::personId)
                    .ifPresent(personId -> result.put(appUserId, personId));
        }
        // LinkedHashMap, KHONG phai Map.of()/Map.copyOf(): ben goi tra bang chinh gia tri
        // reviewed_by, va bai chua ai duyet thi gia tri ay la null. Map bat bien cua JDK goi
        // key.hashCode() trong get(), nen get(null) NEM NPE — va no no o duong binh thuong nhat
        // cua ca context: tao mot ban nhap. Da tra gia mot lan cho bai hoc nay.
        return java.util.Collections.unmodifiableMap(result);
    }
}

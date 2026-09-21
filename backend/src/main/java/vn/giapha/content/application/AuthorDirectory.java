package vn.giapha.content.application;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.application.DisclosureAudience;
import vn.giapha.genealogy.application.PersonDisclosureService;

/**
 * Lối <b>duy nhất</b> mà context {@code content} chạm tới dữ liệu nhân khẩu.
 *
 * <h2>Một lượt nạp theo lô cho cả phản hồi, không một lượt cho mỗi dòng</h2>
 * Trang chủ trả 20 bài; hàng đợi duyệt trả 50. Hỏi từng dòng thì vừa tốn vừa mở đường cho hai bài
 * trong cùng một phản hồi bị xét theo hai ngữ cảnh người gọi khác nhau — chính xác cái mà javadoc
 * của {@code DisclosureAudience} cảnh báo.
 *
 * <h2>Người gọi được dựng ở đây, không nhận từ ngoài</h2>
 * {@link DisclosureAudience} chỉ dựng được bằng hai cách, và cách "có quyền" là
 * {@code PersonDisclosureService.nguoiGoiHienTai()} đọc {@code SecurityContext} của request đang
 * chạy. Không lớp nào ngoài {@code genealogy} tự nâng quyền cho mình được — constructor thật của
 * lớp ấy là package-private. Lớp này không phá vào bất biến đó; nó chỉ gọi đúng cửa.
 */
@Component
public class AuthorDirectory {

    private final PersonDisclosureService disclosure;

    public AuthorDirectory(PersonDisclosureService disclosure) {
        this.disclosure = disclosure;
    }

    /**
     * Hồ sơ đã lọc của một lô nhân khẩu, theo <b>người đang gọi</b>.
     *
     * @param personIds {@code null} và trùng lặp bị bỏ qua; lô rỗng trả về thấu kính rỗng mà không
     *                  chạm CSDL
     */
    public PersonLens load(Collection<UUID> personIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (personIds != null) {
            personIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return PersonLens.empty();
        }
        DisclosureAudience audience = disclosure.nguoiGoiHienTai();
        return new PersonLens(disclosure.hoSo(ids, audience));
    }

    /** Tiện dụng cho lối đọc một bản ghi. */
    public PersonLens loadOne(UUID personId) {
        return load(personId == null ? Set.of() : Set.of(personId));
    }
}

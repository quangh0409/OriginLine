package vn.giapha.genealogy.application.view;

import java.util.UUID;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.shared.vo.Gender;

/**
 * Dạng gọn cho danh sách, kết quả tìm kiếm và node trên phả đồ.
 *
 * <p>Chỉ chứa dữ liệu <b>Tầng 1 trở xuống</b> nên an toàn để trả hàng loạt: hàng nghìn node trên
 * canvas không kéo theo tiểu sử, liên hệ hay ảnh của người còn sống. Bấm vào một node thì giao
 * diện gọi {@code GET /persons/{id}} để lấy hồ sơ đầy đủ, và bộ lọc chạy lại từ đầu ở đó.</p>
 *
 * @param birthYear       chỉ <b>năm</b> sinh — Tầng 2 với người còn sống, công khai với người đã khuất
 * @param matchedNameType lớp tên đã khớp truy vấn; chỉ có trong kết quả tìm kiếm
 */
public record PersonSummaryView(UUID id,
                                String displayName,
                                String nameHanNom,
                                Gender gender,
                                Integer generation,
                                boolean alive,
                                Integer birthYear,
                                Integer deathYear,
                                BranchRef primaryBranch,
                                String nativePlace,
                                String avatarKey,
                                NameType matchedNameType) {

    /** Gắn lớp tên đã khớp — chỉ tầng tìm kiếm biết được thông tin này. */
    public PersonSummaryView withMatchedNameType(NameType value) {
        return new PersonSummaryView(id, displayName, nameHanNom, gender, generation, alive, birthYear,
                deathYear, primaryBranch, nativePlace, avatarKey, value);
    }
}

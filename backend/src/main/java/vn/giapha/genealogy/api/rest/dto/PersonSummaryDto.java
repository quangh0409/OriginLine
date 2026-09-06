package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.shared.vo.Gender;

/**
 * Nhân khẩu dạng rút gọn cho danh sách, kết quả tìm kiếm và node trên phả đồ.
 *
 * <p>Chỉ chứa dữ liệu Tầng 1 trở xuống nên an toàn để trả hàng loạt: canvas vẽ hàng nghìn node mà
 * không kéo theo tiểu sử, liên hệ hay ảnh của người còn sống.</p>
 *
 * @param matchedNameType lớp tên đã khớp truy vấn, chỉ có trong kết quả tìm kiếm - để giao diện
 *        hiện "khớp ở tên tự" thay vì để người dùng bối rối vì tên hiện ra khác tên vừa gõ
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonSummaryDto(UUID id,
                               String displayName,
                               String nameHanNom,
                               Gender gender,
                               Integer generation,
                               @JsonProperty("isAlive") boolean isAlive,
                               Integer birthYear,
                               Integer deathYear,
                               BranchRefDto primaryBranch,
                               String nativePlace,
                               String avatarUrl,
                               NameType matchedNameType) {
}

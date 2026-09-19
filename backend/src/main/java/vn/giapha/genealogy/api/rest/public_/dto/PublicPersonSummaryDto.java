package vn.giapha.genealogy.api.rest.public_.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import vn.giapha.genealogy.api.rest.dto.BranchRefDto;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.shared.vo.Gender;

/**
 * Dạng gọn của một người đã khuất — dùng cho kết quả tìm kiếm và node phả đồ công khai.
 *
 * <p>{@code birthYear}/{@code deathYear} chỉ có phần <b>năm</b>. Ngày đầy đủ của người đã khuất
 * vẫn công khai (xem {@link PublicPersonDto#death()}), nhưng một danh sách thì không cần tới nó,
 * và trường nào không cần thì không nên có mặt trên bề mặt không cần đăng nhập.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicPersonSummaryDto(UUID id,
                                     String displayName,
                                     String nameHanNom,
                                     Gender gender,
                                     Integer generation,
                                     @JsonProperty("isAlive") boolean isAlive,
                                     Integer birthYear,
                                     Integer deathYear,
                                     BranchRefDto primaryBranch,
                                     String nativePlace,
                                     String avatarKey,
                                     NameType matchedNameType) {
}

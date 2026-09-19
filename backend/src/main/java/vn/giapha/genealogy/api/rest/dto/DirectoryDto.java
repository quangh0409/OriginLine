package vn.giapha.genealogy.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Thân phản hồi của {@code GET /api/v1/directory} — <b>danh bạ dòng họ</b>.
 *
 * <p>Ba khối, và hai trong số đó là lý do danh bạ không phải một bộ lọc của
 * {@code /persons/search}:</p>
 * <ul>
 *   <li>{@code items} + {@code page} — như mọi danh sách khác;</li>
 *   <li>{@code coverage} — "218 / 627 người còn sống đã điền", thứ giải thích vì sao danh sách
 *       thưa và ngầm mời người dùng điền;</li>
 *   <li>{@code facets} — các tỉnh/nghề/chi <b>có thật</b> trong danh bạ của người gọi, để bộ lọc
 *       không bao giờ là một ô nhập tự do dẫn tới không kết quả.</li>
 * </ul>
 *
 * <p><b>Trường vắng mặt trong {@code items[]} là câu trả lời cuối cùng.</b>
 * {@code @JsonInclude(NON_NULL)} bỏ hẳn khỏi JSON những nhóm trường mà chủ thể chưa mở cho người
 * gọi — đúng một hình dạng với {@code PersonDto}. Giao diện không được vẽ ô trống thay cho trường
 * vắng, và không có cờ nào phân biệt "bị giấu" với "chưa điền": phân biệt được hai nguyên nhân ấy
 * là phá đúng điều mà bộ lọc riêng tư bảo vệ.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DirectoryDto(List<DirectoryEntryDto> items,
                           PageDto.PageMetaDto page,
                           DirectoryCoverageDto coverage,
                           DirectoryFacetsDto facets) {

    /**
     * Một người trong danh bạ.
     *
     * @param avatarUrl khoá đối tượng MinIO, <b>không</b> phải URL đã ký — giữ đúng tên trường mà
     *                  {@code PersonSummaryDto} đang dùng cho cùng giá trị, để client không phải
     *                  học hai quy ước
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DirectoryEntryDto(UUID personId,
                                    String displayName,
                                    Integer generation,
                                    BranchRefDto primaryBranch,
                                    String occupation,
                                    String currentPlaceProvince,
                                    String avatarUrl) {
    }

    /**
     * Tử số / mẫu số của câu "218 / 627 người còn sống đã điền".
     *
     * @param sharedCount số người hiện ra trong danh bạ của người gọi, <b>bỏ qua mọi bộ lọc</b>
     * @param livingCount tổng số người còn sống mà người gọi được biết là tồn tại
     */
    public record DirectoryCoverageDto(long sharedCount, long livingCount) {
    }

    public record DirectoryFacetValueDto(String value, long count) {
    }

    /** @param path {@code ltree}, dùng để xếp thứ tự/thụt đầu dòng — không bao giờ hiển thị. */
    public record DirectoryBranchFacetDto(UUID id, String name, String path, long count) {
    }

    /** Tính trên tập <b>không lọc</b>, nên chọn một tỉnh xong vẫn còn thấy các tỉnh khác. */
    public record DirectoryFacetsDto(List<DirectoryFacetValueDto> provinces,
                                     List<DirectoryFacetValueDto> occupations,
                                     List<DirectoryBranchFacetDto> branches) {
    }
}

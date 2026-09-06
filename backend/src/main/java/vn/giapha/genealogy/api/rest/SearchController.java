package vn.giapha.genealogy.api.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.genealogy.api.rest.dto.PageDto;
import vn.giapha.genealogy.api.rest.dto.PersonSummaryDto;
import vn.giapha.genealogy.application.PersonSearchService;
import vn.giapha.genealogy.application.command.PersonSearchQuery;

/**
 * REST tìm kiếm nhân khẩu - {@code /api/v1/persons/search} (FR-4.4).
 *
 * <p>Tìm theo tên, <b>có dấu hoặc không dấu</b>: {@code "nguyen van duc"} khớp
 * {@code "Nguyễn Văn Đức"}. Tìm trên <b>mọi lớp tên</b> (húy / tự / hiệu / thụy / thường gọi /
 * pháp danh), không chỉ tên chính - người trong họ thường nhớ tên thường gọi chứ không nhớ tên
 * khai sinh của các cụ.</p>
 *
 * <p>Kết quả đã lọc phân tầng riêng tư: <b>Khách chỉ nhận người đã khuất</b>, và
 * {@code page.totalElements} cũng đã lọc nên không rò rỉ số lượng người bị ẩn.</p>
 *
 * <p>Đường dẫn {@code /persons/search} nằm cùng tiền tố với {@code /persons/{id}} nhưng không xung
 * đột: Spring luôn ưu tiên đoạn cố định hơn biến đường dẫn.</p>
 */
@RestController
@RequestMapping("/api/v1/persons")
@Validated
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final PersonSearchService personSearch;

    public SearchController(PersonSearchService personSearch) {
        this.personSearch = personSearch;
    }

    @GetMapping("/search")
    public ResponseEntity<PageDto<PersonSummaryDto>> search(
            @RequestParam("q") @NotBlank @Size(max = 100) String query,
            @RequestParam(required = false) @Min(1) Integer generation,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String nativePlace,
            @RequestParam(required = false) Boolean isAlive,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "relevance,desc") String sort) {

        PageDto<PersonSummaryDto> result = GenealogyDtoMapper.toDto(personSearch.search(
                new PersonSearchQuery(query, generation, branchId, nativePlace, isAlive,
                        includeDeleted, page, size, sort)));
        log.debug("GET /api/v1/persons/search q={} -> {} ket qua", query, result.items().size());
        return ResponseEntity.ok(result);
    }
}

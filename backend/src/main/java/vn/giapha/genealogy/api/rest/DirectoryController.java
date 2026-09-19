package vn.giapha.genealogy.api.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.genealogy.api.rest.dto.DirectoryDto;
import vn.giapha.genealogy.application.DirectoryService;
import vn.giapha.genealogy.application.command.DirectoryQuery;

/**
 * REST cho <b>danh bạ dòng họ</b> — {@code GET /api/v1/directory}.
 *
 * <p>Danh sách <b>người còn sống</b> đã tự mở ít nhất một nhóm trường cho người đang xem, kèm
 * {@code coverage} và {@code facets}. Lý do nó là một endpoint riêng chứ không phải một bộ lọc của
 * {@code /persons/search} nằm ở javadoc của {@code DirectoryService}.</p>
 *
 * <h2>Khách nhận {@code 401}</h2>
 * Chuỗi lọc của Spring Security ({@code anyRequest().authenticated()}) chặn trước, và
 * {@code DirectoryService} kiểm lại lần nữa. <b>Không</b> trả danh sách rỗng: một danh sách rỗng
 * kèm {@code coverage} bằng 0 là lời nói dối mang hình dạng dữ liệu thật, nó bảo người dùng rằng
 * dòng họ không có ai còn sống.
 *
 * <h2>Không có {@code ETag}, và {@code Cache-Control} là {@code no-store}</h2>
 * Khác {@code /api/v1/tree}, phản hồi ở đây <b>toàn bộ</b> là dữ liệu người còn sống đã lọc theo
 * người gọi. Một bản cache của người này rơi vào tay người kia là rò rỉ không để lại dấu vết nào
 * trong log, và {@code coverage} còn thay đổi mỗi khi có người bật một công tắc — một phản hồi
 * {@code 304} ở đây sẽ giấu đúng thứ mà màn hình sinh ra để hiện.
 */
@RestController
@RequestMapping("/api/v1/directory")
@Validated
public class DirectoryController {

    private static final Logger log = LoggerFactory.getLogger(DirectoryController.class);

    private final DirectoryService directory;

    public DirectoryController(DirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    public ResponseEntity<DirectoryDto> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 100) String province,
            @RequestParam(required = false) @Size(max = 100) String occupation,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = DirectoryQuery.SORT_NAME) String sort) {

        DirectoryDto body = DirectoryDtoMapper.toDto(directory.list(
                new DirectoryQuery(q, province, occupation, branchId, page, size, sort)));
        log.debug("GET /api/v1/directory -> {} dong, coverage {}/{}", body.items().size(),
                body.coverage().sharedCount(), body.coverage().livingCount());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }
}

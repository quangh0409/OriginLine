package vn.giapha.genealogy.api.rest.public_;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
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
import vn.giapha.genealogy.api.rest.public_.dto.PublicPageDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonSummaryDto;
import vn.giapha.genealogy.application.PersonSearchService;
import vn.giapha.genealogy.application.command.PersonSearchQuery;

/**
 * Tìm kiếm công khai — {@code GET /api/v1/public/persons/search}.
 *
 * <p>Tìm theo tên, <b>có dấu hoặc không dấu</b>, trên mọi lớp tên (húy / tự / hiệu / thụy / thường
 * gọi / pháp danh): người đi tìm mộ tổ thường chỉ nhớ tên thường gọi chứ không nhớ tên khai sinh.</p>
 *
 * <h2>Đây là lối liệt kê duy nhất của cổng công khai, nên nó bị siết nhất</h2>
 * <ul>
 *   <li><b>{@code isAlive = false} bị ghim cứng</b>, không phải tham số. Nhờ vậy người còn sống bị
 *       loại ngay trong câu SQL của {@code PersonSearchPort} — sớm hơn hẳn {@code canSee}, và sớm
 *       hơn hẳn chốt chặn ở biên API. Ghim ở đây cũng có nghĩa client <b>không</b> hỏi được
 *       {@code isAlive=true} để đo xem dòng họ có bao nhiêu người sống.</li>
 *   <li><b>Chặn phân trang sâu</b>: quá {@code maxSearchPage} là {@code 400}. Không có cách nào lật
 *       trang cho tới khi hết dòng họ.</li>
 *   <li><b>Từ khoá tối thiểu {@code minQueryLength} ký tự.</b> Một ký tự không phải là tìm kiếm, đó
 *       là phép liệt kê trá hình.</li>
 *   <li><b>Không trả tổng số kết quả</b> — xem {@link PublicPageDto}.</li>
 *   <li><b>Không có {@code sort}</b>: chỉ xếp theo độ khớp. Mỗi tiêu chí sắp xếp là một lát cắt
 *       khác nhau vào cùng tập dữ liệu, và bề mặt công khai không cần lát cắt nào ngoài "gần đúng
 *       nhất trước".</li>
 *   <li><b>Không có bộ lọc {@code includeDeleted}</b>: bản ghi đã xoá mềm chỉ dành cho Hội đồng Tộc
 *       biểu và Quản trị hệ thống.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/public/persons")
@Validated
public class PublicSearchController {

    private static final Logger log = LoggerFactory.getLogger(PublicSearchController.class);

    /** Xếp theo độ khớp, ghim cứng — bề mặt công khai không mở tham số sắp xếp. */
    private static final String PUBLIC_SORT = "relevance,desc";

    private final PersonSearchService personSearch;
    private final PublicGuestScope guestScope;
    private final PublicVisibilityGuard guard;
    private final PublicPortalProperties properties;

    public PublicSearchController(PersonSearchService personSearch, PublicGuestScope guestScope,
                                  PublicVisibilityGuard guard, PublicPortalProperties properties) {
        this.personSearch = personSearch;
        this.guestScope = guestScope;
        this.guard = guard;
        this.properties = properties;
    }

    @GetMapping("/search")
    public ResponseEntity<PublicPageDto<PublicPersonSummaryDto>> search(
            @RequestParam("q") @NotBlank @Size(max = 100) String query,
            @RequestParam(required = false) @Min(1) Integer generation,
            @RequestParam(defaultValue = "0") @Min(0) @Max(50) int page) {

        String term = query.trim();
        if (term.length() < properties.getMinQueryLength()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Tu khoa tim kiem cong khai phai co it nhat %d ky tu",
                    properties.getMinQueryLength()));
        }
        if (page > properties.getMaxSearchPage()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Cong thong tin cong khai chi tra toi trang %d; hay thu hep tu khoa"
                            + " hoac dang nhap de tra cuu day du",
                    properties.getMaxSearchPage()));
        }

        int size = properties.getMaxSearchSize();
        PublicPageDto<PublicPersonSummaryDto> body = guestScope.asGuest(() -> {
            // isAlive = FALSE va includeDeleted = false deu la hang so, khong phai tham so.
            PersonSearchQuery command = new PersonSearchQuery(term, generation, null, null,
                    Boolean.FALSE, false, page, size, PUBLIC_SORT);
            return guard.page(personSearch.search(command));
        });

        // Khong bao gio moi client di xa hon tran cua chinh minh.
        PublicPageDto<PublicPersonSummaryDto> capped = new PublicPageDto<>(body.items(),
                new PublicPageDto.PublicPageMetaDto(body.page().page(), body.page().size(),
                        body.page().hasNext() && page < properties.getMaxSearchPage()));

        log.debug("GET /api/v1/public/persons/search -> {} ket qua cong khai", capped.items().size());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(properties.getCacheSeconds(), TimeUnit.SECONDS)
                        .cachePublic())
                .body(capped);
    }
}

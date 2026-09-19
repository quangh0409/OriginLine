package vn.giapha.genealogy.api.rest.public_;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonDto;
import vn.giapha.genealogy.application.PersonQueryService;

/**
 * Hồ sơ một người đã khuất cho Khách vãng lai — {@code GET /api/v1/public/persons/{id}}.
 *
 * <p>Đây là endpoint làm cho nửa "cổng thông tin dòng họ" của sản phẩm thật sự mở: trước nó, khách
 * vào xem một cụ đã khuất cũng nhận {@code 401}, trái với BA v2 §10.</p>
 *
 * <h2>Đường đi của một yêu cầu, và bốn chỗ người còn sống bị chặn</h2>
 * <ol>
 *   <li>{@link PublicGuestScope#asGuest} ép ngữ cảnh về Khách — token (nếu có) bị bỏ qua.</li>
 *   <li>{@code PersonQueryService.find} gọi {@code PrivacyTierService.canSee}; Khách + người còn
 *       sống ⇒ rỗng.</li>
 *   <li>Rỗng ⇒ {@link PublicVisibilityGuard} ném {@code 404}.</li>
 *   <li>Kể cả khi (2) thủng, guard vẫn hỏi lại {@code alive} trên chính bản ghi trước khi dựng DTO.</li>
 * </ol>
 *
 * <p><b>{@code 404}, không phải {@code 403}</b> — cho cả người không tồn tại lẫn người còn sống.
 * Phân biệt hai trường hợp đó là tự xác nhận người kia có thật.</p>
 *
 * <p>{@code Cache-Control: public} chỉ hợp lệ nhờ bước (1): phản hồi giống hệt nhau với mọi người
 * gọi. <b>Bỏ bước ép ngữ cảnh Khách mà giữ nguyên header này là biến mọi proxy trung gian thành
 * một chỗ rò rỉ dữ liệu.</b></p>
 */
@RestController
@RequestMapping("/api/v1/public/persons")
public class PublicPersonController {

    private static final Logger log = LoggerFactory.getLogger(PublicPersonController.class);

    private final PersonQueryService personQuery;
    private final PublicGuestScope guestScope;
    private final PublicVisibilityGuard guard;
    private final PublicPortalProperties properties;

    public PublicPersonController(PersonQueryService personQuery, PublicGuestScope guestScope,
                                  PublicVisibilityGuard guard, PublicPortalProperties properties) {
        this.personQuery = personQuery;
        this.guestScope = guestScope;
        this.guard = guard;
        this.properties = properties;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicPersonDto> get(@PathVariable UUID id) {
        PublicPersonDto dto = guestScope.asGuest(
                () -> guard.person(personQuery.find(id).orElse(null)));
        log.debug("GET /api/v1/public/persons/{} -> {} quan he cong khai", id, dto.relations().size());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(properties.getCacheSeconds(), TimeUnit.SECONDS)
                        .cachePublic())
                .body(dto);
    }
}

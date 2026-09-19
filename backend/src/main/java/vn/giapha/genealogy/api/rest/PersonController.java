package vn.giapha.genealogy.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.genealogy.api.rest.dto.CreatePersonRequest;
import vn.giapha.genealogy.api.rest.dto.PersonDto;
import vn.giapha.genealogy.api.rest.dto.UpdatePersonRequest;
import vn.giapha.genealogy.application.AddPersonService;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.SoftDeletePersonService;
import vn.giapha.genealogy.application.UpdatePersonService;
import vn.giapha.genealogy.application.view.PersonView;

/**
 * REST cho nhân khẩu - {@code /api/v1/persons}.
 *
 * <h2>Ba điều controller này cố ý làm khác thói quen thông thường</h2>
 * <ol>
 *   <li><b>Không có endpoint xoá cứng.</b> {@code DELETE} là xoá mềm, và sẽ mãi như vậy: xoá cứng
 *       một nhân khẩu làm đứt cây ở mọi đời phía dưới người đó.</li>
 *   <li><b>{@code PATCH} đọc thân yêu cầu dưới dạng cây JSON trước</b>, để biết trường nào
 *       <i>thực sự có mặt</i>. Bind thẳng vào record thì "không gửi" và "gửi null" lẫn vào nhau, và
 *       ngữ nghĩa "vắng mặt = giữ nguyên" của contract sụp đổ.</li>
 *   <li><b>Người còn sống mà người gọi không được thấy trả {@code 404}, không phải {@code 403}.</b>
 *       Trả 403 là tự xác nhận người đó tồn tại. Việc này do tầng application quyết định, controller
 *       chỉ không được phá đi.</li>
 * </ol>
 *
 * <p>{@code ETag} sinh từ {@code version} của bản ghi; client gửi lại qua {@code If-Match} để khoá
 * lạc quan hoạt động.</p>
 */
@RestController
@RequestMapping("/api/v1/persons")
public class PersonController {

    private static final Logger log = LoggerFactory.getLogger(PersonController.class);

    private final AddPersonService addPerson;
    private final UpdatePersonService updatePerson;
    private final SoftDeletePersonService softDeletePerson;
    private final PersonQueryService personQuery;
    private final RelationshipSummaryLoader relationshipSummaries;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PersonController(AddPersonService addPerson, UpdatePersonService updatePerson,
                            SoftDeletePersonService softDeletePerson, PersonQueryService personQuery,
                            RelationshipSummaryLoader relationshipSummaries,
                            ObjectMapper objectMapper, Validator validator) {
        this.addPerson = addPerson;
        this.updatePerson = updatePerson;
        this.softDeletePerson = softDeletePerson;
        this.personQuery = personQuery;
        this.relationshipSummaries = relationshipSummaries;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Thêm nhân khẩu, kèm tuỳ chọn nối ngay vào cây.
     *
     * <p>Trùng tên húy bậc trên mà chưa xác nhận thì trả {@code 409 KY_HUY_CONFLICT} và
     * <b>không bản ghi nào được tạo</b>; client hiện hộp thoại rồi gửi lại y hệt payload cũ cộng
     * {@code confirmTabooOverride: true}.</p>
     */
    @PostMapping
    public ResponseEntity<PersonDto> create(@Valid @RequestBody CreatePersonRequest request) {
        PersonView created = addPerson.add(PersonRequestMapper.toCommand(request));
        log.debug("POST /api/v1/persons -> {}", created.id());
        return ResponseEntity.created(URI.create("/api/v1/persons/" + created.id()))
                .eTag(etagOf(created))
                .body(toDtoWithRelationSummaries(created));
    }

    /** Hồ sơ đã lọc theo phân tầng riêng tư; Khách hỏi người còn sống sẽ nhận {@code 404}. */
    @GetMapping("/{id}")
    public ResponseEntity<PersonDto> get(@PathVariable UUID id) {
        PersonView view = personQuery.byId(id);
        return ResponseEntity.ok().eTag(etagOf(view)).body(toDtoWithRelationSummaries(view));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PersonDto> update(@PathVariable UUID id,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH,
                                                    required = false) String ifMatch,
                                            @RequestBody JsonNode body) {
        UpdatePersonRequest request = readBody(body);
        PersonView updated = updatePerson.update(PersonRequestMapper.toCommand(id, request,
                presentFieldsOf(body), parseIfMatch(ifMatch)));
        return ResponseEntity.ok().eTag(etagOf(updated)).body(toDtoWithRelationSummaries(updated));
    }

    /**
     * <b>Xoá mềm</b> (FR-1.5). Node đồ thị và mọi cạnh quan hệ được giữ nguyên để cây không gãy.
     *
     * <p>Xoá dữ liệu cá nhân theo yêu cầu hợp pháp <b>không</b> dùng endpoint này - nghiệp vụ đó là
     * ẩn danh hoá, và endpoint cho nó chưa được chốt trong contract.</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id,
                                           @RequestParam(required = false) String reason) {
        softDeletePerson.softDelete(id, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bind cây JSON sang record rồi <b>tự chạy bean validation</b>.
     *
     * <p>{@code @Valid} không dùng được ở đây vì thân yêu cầu phải đi qua {@link JsonNode} trước
     * (xem javadoc của lớp), nên phần kiểm tra ràng buộc được gọi tay để {@code @Size} và
     * {@code @NotBlank} trên các lớp tên vẫn có hiệu lực.</p>
     */
    private UpdatePersonRequest readBody(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Than yeu cau PATCH phai la mot object JSON");
        }
        try {
            UpdatePersonRequest request = objectMapper.treeToValue(body, UpdatePersonRequest.class);
            Set<ConstraintViolation<UpdatePersonRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return request;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Than yeu cau khong hop le: " + ex.getOriginalMessage(), ex);
        }
    }

    /** Tên các khoá thực sự có mặt ở cấp cao nhất của body - căn cứ cho "vắng mặt = giữ nguyên". */
    private Set<String> presentFieldsOf(JsonNode body) {
        Set<String> fields = new LinkedHashSet<>();
        if (body != null && body.isObject()) {
            Iterator<String> names = body.fieldNames();
            while (names.hasNext()) {
                fields.add(names.next());
            }
        }
        return fields;
    }

    /**
     * {@code If-Match} bắt buộc với {@code PATCH}.
     *
     * <p>Chấp nhận cả dạng yếu {@code W/"3"} lẫn {@code "3"}. Không có header thì trả {@code 412}
     * thay vì ghi mù - hai người cùng sửa một hồ sơ là chuyện thường ngày của một dòng họ đang số
     * hoá gia phả.</p>
     */
    private Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Thieu header If-Match; hay lay ETag tu GET /persons/{id} truoc khi sua");
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "").trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new PreconditionRequiredException("If-Match khong hop le: " + ifMatch);
        }
    }

    /**
     * Hồ sơ kèm tóm tắt của đầu kia mỗi cạnh quan hệ.
     *
     * <p>Tóm tắt đi qua {@link RelationshipSummaryLoader}, tức qua đúng bộ lọc phân tầng riêng tư của
     * người gọi. Đừng thay bằng một lượt đọc kho trực tiếp cho "nhanh": màn "Quan hệ" sẽ thành nơi
     * tên người còn sống rò ra qua hồ sơ công khai của một cụ đã khuất.</p>
     */
    private PersonDto toDtoWithRelationSummaries(PersonView view) {
        return GenealogyDtoMapper.toDto(view, relationshipSummaries.forSubject(view));
    }

    private String etagOf(PersonView view) {
        return "\"" + (view.version() == null ? 0L : view.version()) + "\"";
    }
}

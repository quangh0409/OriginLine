package vn.giapha.events.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.events.api.rest.dto.CreateEventRequest;
import vn.giapha.events.api.rest.dto.EventDto;
import vn.giapha.events.api.rest.dto.UpdateEventRequest;
import vn.giapha.events.application.EventCommandService;
import vn.giapha.events.application.view.EventView;

/**
 * Lối <b>ghi</b> của lịch việc họ: tạo, sửa, xoá mềm sự kiện dòng họ.
 *
 * <p>Tách khỏi {@code EventController} (chỉ đọc) vì hai lối có hai bộ quan tâm khác hẳn nhau —
 * bên kia lo phân trang, lọc theo khoảng ngày dương và phân tầng riêng tư; bên này lo phân quyền
 * theo {@code ltree}, khoá lạc quan và nhật ký kiểm toán. Cùng ánh xạ {@code /api/v1/events},
 * khác phương thức HTTP.</p>
 *
 * <h2>Ai được cấu hình việc họ</h2>
 * Trưởng cành/chi/ngành trong <b>phạm vi được giao</b>, Hội đồng Tộc biểu và Quản trị hệ thống trên
 * toàn dòng họ. Phép kiểm là phép kiểm {@code ltree} duy nhất của hệ thống
 * ({@code membership.BranchScopeGuard}), không phải một bản chép riêng cho endpoint này — xem
 * {@code EventScopeGuard}.
 *
 * <h2>Vì sao không dùng {@code @Valid} cho {@code PATCH}</h2>
 * {@code PATCH} phải phân biệt "không gửi trường này" với "gửi trường này bằng null", và một record
 * Java sau khi bind xong không còn nhớ khoá nào từng có mặt trong JSON. Vì vậy thân yêu cầu đi qua
 * {@link JsonNode} trước rồi mới bind, và bean validation được gọi tay. Cùng cách
 * {@code PersonController} đã dùng; không có nó thì mọi lần sửa tiêu đề sẽ âm thầm xoá địa điểm.
 */
@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "events", description = "Gio, chap, le dong ho")
public class EventCommandController {

    private static final Logger log = LoggerFactory.getLogger(EventCommandController.class);

    private final EventCommandService commands;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public EventCommandController(EventCommandService commands, ObjectMapper objectMapper,
                                  Validator validator) {
        this.commands = commands;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Tạo một việc họ: lễ Tết, chạp mả, họp họ, khánh thành từ đường…
     *
     * <p>Lịch nhắc (mốc 7/3/1 ngày) được dựng ngay trong <b>cùng transaction</b>, không chờ job
     * 01:30 — một sự kiện đặt cho ngày kia thì mốc D-1 đã trôi mất nếu phải chờ tới đêm.</p>
     */
    @PostMapping
    @Operation(summary = "Tao su kien dong ho",
            description = "Ngay am la mac dinh. Pham vi (clanWide hoac scopeBranchId) quyet dinh AI DUOC NHAC.")
    public ResponseEntity<EventDto> create(@Valid @RequestBody CreateEventRequest request) {
        EventView created = commands.create(EventRequestMapper.toCommand(request));
        log.debug("POST /api/v1/events -> {}", created.id());
        return ResponseEntity.created(URI.create("/api/v1/events/" + created.id()))
                .eTag(EventDtoMapper.etagOf(created))
                .body(EventDtoMapper.toDto(created));
    }

    /** Sửa từng phần. Bắt buộc {@code If-Match} lấy từ {@code ETag} của {@code GET}. */
    @PatchMapping("/{id}")
    @Operation(summary = "Sua su kien dong ho",
            description = "Doi ngay hoac doi pham vi se dung lai lich nhac chua ban cua su kien nay.")
    public ResponseEntity<EventDto> update(@PathVariable UUID id,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH,
                                                   required = false) String ifMatch,
                                           @RequestBody JsonNode body) {
        UpdateEventRequest request = readBody(body);
        EventView updated = commands.update(id,
                EventRequestMapper.toCommand(request, presentFieldsOf(body), parseIfMatch(ifMatch)));
        return ResponseEntity.ok()
                .eTag(EventDtoMapper.etagOf(updated))
                .body(EventDtoMapper.toDto(updated));
    }

    /**
     * <b>Xoá mềm</b>: cờ, không phải lệnh xoá.
     *
     * <p>Bản ghi ở lại vì lịch nhắc đã phát, nhật ký gửi và nhật ký kiểm toán còn trỏ vào nó — xoá
     * cứng thì {@code ON DELETE CASCADE} của {@code reminder_job} kéo theo cả lịch sử, và người
     * trong họ bấm vào một thông báo cũ sẽ rơi vào trang trắng.</p>
     *
     * <p>Lịch nhắc <b>chưa bắn</b> của sự kiện bị dọn đi; lịch đã gửi giữ nguyên.</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xoa mem su kien dong ho",
            description = "Giu ban ghi de loi nhac da phat va nhat ky gui con cho tro ve.")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id,
                                           @RequestParam(required = false) String reason) {
        commands.softDelete(id, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bind cây JSON sang record rồi <b>tự chạy bean validation</b>.
     *
     * <p>{@code @Valid} không dùng được ở đây vì thân yêu cầu phải đi qua {@link JsonNode} trước
     * (xem javadoc của lớp), nên phần kiểm tra ràng buộc được gọi tay để {@code @Size} trên các
     * trường vẫn có hiệu lực.</p>
     */
    private UpdateEventRequest readBody(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Than yeu cau PATCH phai la mot object JSON");
        }
        try {
            UpdateEventRequest request = objectMapper.treeToValue(body, UpdateEventRequest.class);
            Set<ConstraintViolation<UpdateEventRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return request;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Than yeu cau khong hop le: " + ex.getOriginalMessage(), ex);
        }
    }

    /** Tên các khoá thực sự có mặt ở cấp cao nhất của body — căn cứ cho "vắng mặt = giữ nguyên". */
    private static Set<String> presentFieldsOf(JsonNode body) {
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
     * thay vì ghi mù — trước mỗi mùa giỗ chạp, lịch việc họ là thứ nhiều người cùng ngồi sửa.</p>
     */
    private static long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new EventPreconditionRequiredException(
                    "Thieu header If-Match; hay lay ETag tu GET /api/v1/events/{id} truoc khi sua");
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "").trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new EventPreconditionRequiredException("If-Match khong hop le: " + ifMatch);
        }
    }
}

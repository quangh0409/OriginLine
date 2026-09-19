package vn.giapha.genealogy.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.giapha.genealogy.application.DuplicateMatch;
import vn.giapha.genealogy.application.DuplicatePersonSuspectedException;
import vn.giapha.genealogy.application.DuplicateSignal;
import vn.giapha.genealogy.application.GenealogyConflictException;
import vn.giapha.genealogy.application.TabooNameConflictException;
import vn.giapha.genealogy.domain.TabooConflict;
import vn.giapha.shared.api.ProblemTypes;

/**
 * Bổ sung cho {@code shared.api.GlobalExceptionHandler} bốn tình huống mà nó cố ý không biết tới:
 * <b>409</b> xung đột trạng thái, <b>409 kỵ húy</b> và <b>409 nghi trùng</b> (cả hai đều có danh
 * sách va chạm và ghi đè được), và <b>412</b> thiếu {@code If-Match}.
 *
 * <p>Chỉ khai báo handler cho đúng bốn kiểu ngoại lệ riêng của context này - <b>không</b> bắt
 * {@code DomainException} chung. Bắt kiểu cha ở đây sẽ nuốt luôn {@code NotFoundException} và
 * {@code ForbiddenException} của shared kernel và làm mọi lỗi 404/403 của toàn hệ thống đi sai
 * đường.</p>
 *
 * <p>Ưu tiên cao nhất để chọn trước advice mặc định khi cả hai cùng khớp.</p>
 *
 * <h2>Hai thân lỗi 409 có danh sách va chạm là một BỀ MẶT ĐỌC DỮ LIỆU — và nó không có bộ lọc</h2>
 * Cả phép dò trùng lẫn phép dò kỵ húy đều quét <b>toàn dòng họ</b> và <b>không biết người gọi là
 * ai</b>: bộ dò trùng chấm điểm theo tên / năm sinh / ngày giỗ, bộ dò kỵ húy chọn bậc trên theo
 * <b>đời thứ</b> chứ không theo sống-mất và không có một điều kiện chi/ngành nào. Hệ quả là hồ sơ
 * lọt vào {@code conflicts[]} hoàn toàn có thể là một người <b>còn sống ở một chi khác</b>, và
 * {@code PersonVisibility.ungroupedFieldsVisible()} chỉ mở tên huý / năm sinh / nguyên quán của
 * người còn sống cho <b>người đã khuất, chính chủ, Hội đồng Tộc biểu và Admin</b> — Trưởng chi
 * cũng không. Thân lỗi này đi thẳng ra HTTP mà không qua {@code PrivacyTierService}, nên mọi giá
 * trị đọc từ phả nhét vào đây là một kênh đọc <b>song song</b> với
 * {@code GET /api/v1/persons/&#123;id&#125;}, chỉ khác là không có bộ lọc.
 *
 * <h3>Ranh giới đã chốt</h3>
 * <b>Được phép nói trường nào của chính người gọi vừa nhập đã khớp; không bao giờ nói một giá trị
 * đọc từ phả.</b> Giống hệt đường nhập liệu hàng loạt
 * ({@code dataimport.application.rule.SuspectDuplicateRule}). Vì vậy {@code conflicts[]} chỉ mang
 * <b>khoá + loại tín hiệu + điểm</b>; giao diện cầm khoá gọi
 * {@code GET /api/v1/persons/&#123;id&#125;}, nơi bộ lọc thật chạy và trả đúng phần người gọi được
 * phép xem (hoặc {@code 404} khi họ không được biết bản ghi tồn tại). Nhờ vậy luật lọc chỉ có
 * <b>một bản</b>, ở đúng chỗ của nó, và hai kênh không thể lệch nhau.
 *
 * <h3>Vì sao KHÔNG gọi {@code PersonDisclosureService} ngay tại đây</h3>
 * Nghe hợp lý — "trả phần người gọi được phép xem" — nhưng nó biến thân lỗi thành kênh đọc thứ
 * hai, lọc theo một <i>chiếu</i> khác ({@code DisclosedPerson}, vốn được cắt riêng cho việc xuất
 * phả) so với chiếu mà {@code GET /persons/&#123;id&#125;} trả ({@code PersonView} →
 * {@code PersonDto}). Hai chiếu khác nhau, cùng chở dữ liệu nhân khẩu, sẽ lệch nhau đúng vào ngày
 * một trong hai được nới ra — và triệu chứng là <b>hộp thoại đối chiếu hiện thứ mà màn hồ sơ
 * giấu</b>. Thêm nữa, nó bắt một lượt đọc CSDL chạy <i>trong</i> exception handler, ngoài
 * transaction đã kết thúc của lệnh ghi. Giao diện dù sao cũng phải gọi {@code GET} để dựng thẻ đối
 * chiếu (ảnh, quan hệ lõi), nên kênh thứ hai này không mua thêm được gì.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GenealogyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GenealogyExceptionHandler.class);

    /**
     * Va chạm <b>kỵ húy</b> (FR-1.6).
     *
     * <p>Phản hồi mang đủ dữ liệu để giao diện dựng hộp thoại xác nhận có ý thức, nhưng "đủ" ở đây
     * nghĩa là: <b>khoá của bậc trên</b>, <b>lớp tên</b> của chính người gọi đã gây va chạm, và
     * <b>kiểu khớp</b> ({@code EXACT} / {@code GIVEN_NAME} / {@code UNACCENTED}). Ba mẩu ấy trả lời
     * được "vì sao cảnh báo" mà không nói "bậc trên ấy là ai". {@code overridable} và
     * {@code overrideField} nói cho client biết đây là cảnh báo ghi đè được, không phải lệnh cấm -
     * quyền quyết định thuộc về dòng họ.</p>
     *
     * <p><b>Đã bỏ, có chủ ý:</b> {@code ancestorDisplayName}, {@code ancestorGeneration},
     * {@code tabooName} và {@code relationHint} (chuỗi này nhúng chính đời thứ của bậc trên). Phép
     * dò kỵ húy chọn bậc trên theo <b>đời thứ</b>, không theo sống-mất và không theo chi, nên "bậc
     * trên" rất có thể là một ông bác <b>còn sống ở chi khác</b>. Riêng {@code tabooName} là
     * {@code person_name.full_name} <b>đọc từ phả</b> chứ không phải ô người gọi vừa gõ: với
     * {@code GIVEN_NAME} nó là tên huý đầy đủ, với {@code UNACCENTED} nó là bản có dấu — hai ca mà
     * giá trị trả về khác hẳn thứ người gọi đã biết. Mất mát gần bằng không: bậc trên trong tuyệt
     * đại đa số ca là người <b>đã khuất</b>, mà người đã khuất là dữ liệu công khai, nên
     * {@code GET /persons/&#123;id&#125;} trả lại đầy đủ tên huý và đời thứ để hộp thoại hiện y như
     * trước.</p>
     */
    @ExceptionHandler(TabooNameConflictException.class)
    public ProblemDetail handleTabooConflict(TabooNameConflictException ex, HttpServletRequest request) {
        log.info("409 {} - ky huy: {} va cham", request.getRequestURI(), ex.conflicts().size());
        // KHONG dung ex.getMessage(): cau do noi thang tabooName doc tu pha vao detail.
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Trùng tên húy bậc trên",
                moTaKyHuy(ex.conflicts()), ex.getCode(), request);
        problem.setProperty("overridable", true);
        problem.setProperty("overrideField", "confirmTabooOverride");
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (TabooConflict conflict : ex.conflicts()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            // LinkedHashMap chu khong Map.of: matchedNameType/matchKind deu co the null.
            entry.put("ancestorPersonId", conflict.ancestorPersonId() == null
                    ? null : conflict.ancestorPersonId().toString());
            entry.put("matchedNameType", conflict.matchedNameType() == null
                    ? null : conflict.matchedNameType().name());
            entry.put("matchKind", conflict.matchKind() == null ? null : conflict.matchKind().name());
            conflicts.add(entry);
        }
        problem.setProperty("conflicts", conflicts);
        return problem;
    }

    /**
     * <b>Nghi trùng nhân khẩu</b> — cùng hình dạng nghiệp vụ với kỵ húy: cảnh báo ghi đè được, không
     * phải lệnh cấm.
     *
     * <p>Phải có handler riêng dù {@link DuplicatePersonSuspectedException} kế thừa
     * {@link GenealogyConflictException}: nhánh chung đặt {@code overridable: false} và không mang
     * theo danh sách ứng viên, nên giao diện không dựng nổi hộp thoại đối chiếu và người nhập liệu
     * chỉ thấy một lỗi 409 cụt lủn không có đường đi tiếp. Spring chọn handler khớp <b>sát kiểu
     * nhất</b> nên khai báo thêm ở đây là đủ, không cần đụng vào nhánh chung.</p>
     *
     * <p>{@code conflicts[]} giữ nguyên thứ tự giảm dần theo điểm của tầng application — dòng đầu
     * tiên là ứng viên đáng ngờ nhất và là dòng giao diện nên làm nổi bật.</p>
     *
     * <p><b>Đã bỏ, có chủ ý:</b> {@code displayName}, {@code generation}, {@code branchId} và
     * {@code matchedName} — bốn giá trị <b>đọc từ phả</b>. {@code matchedName} là cái bẫy kín nhất:
     * nó là {@code person_name.full_name} của <i>hồ sơ bên kia</i>, nên khi khớp ở mức bỏ dấu thì nó
     * phát ra bản <b>có dấu</b> mà người gọi chưa từng biết, và phép khớp chạy chéo mọi lớp tên nên
     * nó có thể là <b>tên huý</b> — đúng khối mà {@code ungroupedFieldsVisible()} giữ kín với người
     * còn sống. Vẫn giữ {@code score}, {@code signals} và {@code hint}: cả ba nói <b>trường nào của
     * chính người gọi vừa nhập đã khớp</b> chứ không nói một giá trị nào của hồ sơ bên kia, và
     * thiếu chúng thì người nhập liệu không còn gì để đối chiếu nên sẽ bấm ghi đè theo phản xạ —
     * bịt được lỗ rò nhưng làm hỏng chính cơ chế chống trùng.</p>
     */
    @ExceptionHandler(DuplicatePersonSuspectedException.class)
    public ProblemDetail handleDuplicateSuspected(DuplicatePersonSuspectedException ex,
                                                  HttpServletRequest request) {
        log.info("409 {} - nghi trung: {} ung vien", request.getRequestURI(), ex.matches().size());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Nghi trùng nhân khẩu", ex.getMessage(),
                ex.getCode(), request);
        problem.setProperty("overridable", true);
        problem.setProperty("overrideField", "confirmDuplicateOverride");
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (DuplicateMatch match : ex.matches()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            // Dung LinkedHashMap chu khong Map.of: personId null la truong hop BINH THUONG (ung vien
            // la mot dong khac trong cung lo nhap lieu chua duoc ghi), ma Map.of nem NPE voi null.
            entry.put("personId", match.personId() == null ? null : match.personId().toString());
            entry.put("ref", match.ref());
            entry.put("score", match.score());
            entry.put("signals", match.signals().stream().map(DuplicateSignal::name).toList());
            entry.put("hint", match.hint());
            conflicts.add(entry);
        }
        problem.setProperty("conflicts", conflicts);
        return problem;
    }

    /** Xung đột trạng thái: khoá lạc quan, đã xoá mềm, hoặc quan hệ tạo chu trình. */
    @ExceptionHandler(GenealogyConflictException.class)
    public ProblemDetail handleConflict(GenealogyConflictException ex, HttpServletRequest request) {
        log.info("409 {} - [{}] {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Xung đột dữ liệu", ex.getMessage(),
                ex.getCode(), request);
        problem.setProperty("overridable", false);
        return problem;
    }

    /** Thiếu hoặc sai {@code If-Match} khi sửa hồ sơ - không cho ghi mù lên bản của người khác. */
    @ExceptionHandler(PreconditionRequiredException.class)
    public ProblemDetail handlePrecondition(PreconditionRequiredException ex,
                                            HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Thiếu điều kiện tiên quyết", ex.getMessage(),
                ex.getCode(), request);
    }

    /**
     * Câu {@code detail} cho kỵ húy, soạn <b>tại đây</b> chứ không lấy từ ngoại lệ.
     *
     * <p>{@code TabooNameConflictException.getMessage()} nối {@code tabooName} và đời thứ của bậc
     * trên vào câu mô tả. Câu ấy vẫn có ích trong log của tiến trình, nhưng <b>không được</b> đi ra
     * HTTP: nó là giá trị đọc từ phả. Bản này nói số va chạm và kiểu khớp — đủ để người dùng hiểu
     * chuyện gì xảy ra và biết bước tiếp theo.</p>
     */
    private static String moTaKyHuy(List<TabooConflict> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return "Ten huy moi trung ten huy cua bac tren";
        }
        TabooConflict dau = conflicts.get(0);
        String kieu = dau.matchKind() == null ? "" : " (" + kieuKhop(dau.matchKind().name()) + ")";
        return "Ten huy vua nhap trung ten huy cua " + conflicts.size() + " bac tren" + kieu
                + ". Vi ly do rieng tu, than loi khong neu danh tinh bac tren:"
                + " mo ho so theo ancestorPersonId de xem phan minh duoc phep xem."
                + " Xac nhan de van ghi.";
    }

    /** Nhãn không dấu cho {@code TabooMatchKind} - chỉ để hiển thị trong câu mô tả. */
    private static String kieuKhop(String matchKind) {
        return switch (matchKind) {
            case "EXACT" -> "trung nguyen van";
            case "GIVEN_NAME" -> "trung phan ten chinh";
            case "UNACCENTED" -> "trung khi bo dau";
            default -> matchKind;
        };
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setType(status == HttpStatus.CONFLICT ? ProblemTypes.CONFLICT : ProblemTypes.VALIDATION);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}

package vn.giapha.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Dịch mọi ngoại lệ của tầng API sang <b>RFC 7807 Problem Details</b> (TDD: bắt buộc cho toàn bộ
 * {@code /api/v1}).
 *
 * <p>Mọi phản hồi lỗi đều có thêm hai thuộc tính mở rộng: {@code code} (mã lỗi ổn định để FE hiển
 * thị thông điệp song ngữ VI/EN) và {@code timestamp}.</p>
 *
 * <p><b>Nguyên tắc riêng tư (Nghị định 13/2023):</b> thông điệp lỗi không được rò rỉ dữ liệu người
 * còn sống. Lỗi 500 chỉ trả câu chung chung, chi tiết nằm ở log phía máy chủ.</p>
 *
 * <h2>Vì sao lớp này KẾ THỪA {@link ResponseEntityExceptionHandler}</h2>
 * Đây là điểm đã hỏng một lần và hỏng nặng, nên chép lại nguyên nhân:
 * <ol>
 *   <li>{@code ExceptionHandlerExceptionResolver} duyệt advice <b>theo thứ tự</b> và dừng ở advice
 *       <b>đầu tiên có bất kỳ phương thức nào khớp</b> — không phải ở phương thức khớp sát nhất
 *       trên toàn bộ các advice.</li>
 *   <li>Lớp này được nâng lên {@code HIGHEST_PRECEDENCE + 100} để giành lại
 *       {@code MethodArgumentNotValidException} từ {@code ProblemDetailsExceptionHandler} của
 *       Spring Boot ({@code @Order(0)}).</li>
 *   <li>Nhưng lúc ấy nó <b>vẫn giữ một {@code @ExceptionHandler(Exception.class)}</b>. Một handler
 *       bắt-tất khớp với <i>mọi</i> ngoại lệ, nên advice ưu tiên cao này nuốt trọn danh sách mà
 *       {@code ResponseEntityExceptionHandler} từng dịch sang đúng mã HTTP.</li>
 * </ol>
 * <b>Đã đo trên hệ đang chạy:</b> sáu lớp lỗi của client cùng rơi xuống {@code 500} — thân JSON
 * hỏng (400), thiếu {@code Content-Type} (415), tham số sai kiểu (400), thiếu tham số bắt buộc
 * (400), {@code Accept: application/xml} (406), sai method (405). Màn tải Excel là nơi nhìn thấy
 * hậu quả sớm nhất: {@code POST /api/v1/import-batches} khai {@code consumes=multipart/form-data}
 * + {@code @RequestPart("file")}, nên hai lỗi client thường gặp nhất ở màn ấy đều thành
 * {@code 500} và người vận hành đọc nó là "máy chủ hỏng".
 *
 * <p>Cách vá <b>không</b> phải là hạ {@code @Order} xuống — làm thế là trả lại khiếm khuyết
 * {@code about:blank} cũ. Cái sai là <b>advice ưu tiên cao lại chứa handler bắt-tất</b>. Nên:</p>
 * <ul>
 *   <li>lớp này kế thừa {@link ResponseEntityExceptionHandler} ⇒ tự nó trả đúng status cho cả 20
 *       ngoại lệ khung, và {@link #handleExceptionInternal} gắn {@code code} + {@code timestamp}
 *       + {@code type} cho tất cả — thứ mà advice của Spring không bao giờ làm được;</li>
 *   <li>{@code spring.mvc.problemdetails.enabled} đặt {@code false} ({@code application.yml}) vì
 *       advice ấy nay thừa và chỉ làm rối khi đọc thứ tự;</li>
 *   <li>handler bắt-tất dọn sang {@link UnexpectedErrorHandler} ở {@code LOWEST_PRECEDENCE} — nơi
 *       duy nhất nó vô hại.</li>
 * </ul>
 *
 * <p>{@code HIGHEST_PRECEDENCE + 100} vẫn đủ lớn để các advice theo ngữ cảnh
 * ({@code ImportApiExceptionHandler} / {@code EventsExceptionHandler} /
 * {@code GenealogyExceptionHandler} ở {@code HIGHEST_PRECEDENCE}, {@code ContentExceptionHandler}
 * và {@code MembershipExceptionHandler} ở {@code +10}, {@code MediaExceptionHandler} ở {@code +11})
 * thắng advice chung này. {@code ApiErrorContractIT} ghim cả hai chiều, vì một con số sai chỉ lộ ra
 * ở một chiều.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Mã dùng cho mọi lỗi 4xx do khung sinh ra - xem {@link #maKhung}. */
    private static final String CODE_VALIDATION = "VALIDATION_FAILED";

    private static final String CODE_NOT_FOUND = "NOT_FOUND";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        if (ApiProblems.CODE_ACCOUNT_NOT_PROVISIONED.equals(ex.getCode())) {
            return accountNotProvisioned(ex, request);
        }
        log.debug("404 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Không tìm thấy dữ liệu",
                ex.getMessage(), ex.getCode(), request);
    }

    /**
     * <b>{@code 403}, không phải {@code 404} và cũng không phải {@code 409}</b> — tài khoản đăng
     * nhập được nhưng chưa được ghép với một nhân khẩu trong phả.
     *
     * <h2>Vì sao ĐÃ ĐỔI từ {@code 409} sang {@code 403}</h2>
     * Bản đầu chọn {@code 409}, và lập luận của nó giữ nguyên bên dưới vì nó vẫn đáng đọc — một
     * quyết định bị đảo mà xoá luôn lý do của bên thua thì người sau sẽ đảo ngược lại lần nữa.
     * Hai điều đã lật nó:
     * <ol>
     *   <li><b>{@code 409} hỏng ở chính định nghĩa của nó.</b> RFC 9110 nói {@code 409} dùng cho
     *       tình huống <i>"the user might be able to resolve the conflict and resubmit"</i>. Ở đây
     *       người gọi <b>không thể</b> tự gỡ: chỉ Trưởng chi bấm nút ghép mới gỡ được, và gửi lại
     *       bao nhiêu lần cũng ra đúng kết quả ấy. Còn lập luận chống {@code 403} bên dưới dựa vào
     *       một <i>hàm ý</i> ("đã dứt điểm") mà RFC không hề nói — {@code 403} chỉ có nghĩa "hiểu
     *       yêu cầu nhưng từ chối cấp quyền", và RFC còn mời nói rõ lý do trong thân phản hồi,
     *       đúng thứ {@code code} + {@code nextStep} đang làm.</li>
     *   <li><b>Cùng một mã lỗi đang trả HAI status khác nhau.</b> {@code BranchScopeGuard} và
     *       {@code ImportScopeGuard} ném {@code ACCOUNT_NOT_PROVISIONED} dưới dạng
     *       {@code ForbiddenException} ⇒ {@code 403}, và {@code contracts/openapi.yaml} cũng ghi
     *       {@code 403}. Một mã, hai status là khiếm khuyết nặng hơn cả hai lựa chọn: client thứ
     *       hai đọc hợp đồng sẽ gặp {@code 409} ở một nửa số lời gọi.</li>
     * </ol>
     *
     * <p>Giao diện không đổi hành vi: nó rẽ nhánh theo {@code code} chứ không theo status
     * ({@code contracts/README} §3), nên đây thuần tuý là chuyện nói đúng với client thứ hai.</p>
     *
     * <h2>Lập luận cũ, giữ lại nguyên văn</h2>
     *
     * <h2>Vì sao ba mã kia đều sai</h2>
     * <ul>
     *   <li>{@code 404} nói "không có gì ở đây". Sai hẳn: người gọi <b>đã đăng nhập thành công</b>,
     *       tài nguyên có thật, và giao diện vẽ ra màn "không tìm thấy trang" cho một người vừa
     *       nhập đúng mật khẩu. Đây chính là khiếm khuyết đang vá.</li>
     *   <li>{@code 403} nói "bạn không được phép" — hàm ý một quyết định đã dứt điểm. Nhưng người
     *       này <b>sẽ</b> được phép ngay khi Trưởng chi bấm nút ghép; không có gì bị từ chối cả.</li>
     *   <li>{@code 401} sai vì token hoàn toàn hợp lệ; trả 401 sẽ khiến client đá người dùng về
     *       trang đăng nhập và họ đăng nhập lại mãi mà không bao giờ thoát khỏi vòng lặp.</li>
     * </ul>
     *
     * <p>{@code 409 Conflict} nói đúng điều đang xảy ra: yêu cầu hợp lệ nhưng <b>xung đột với
     * trạng thái hiện tại của tài nguyên</b> (RFC 9110 §15.5.10) — và trạng thái ấy thay đổi được
     * bằng một hành động ngoài luồng yêu cầu này. Nó cũng là mã duy nhất trong bốn mã mà một
     * client cache/proxy không diễn giải nhầm.</p>
     *
     * <p>Thân lỗi mang thêm {@code nextStep} — <b>mã máy đọc</b>, không phải chuỗi mô tả — để giao
     * diện rẽ nhánh sang màn "chờ ghép vào phả" mà không phải đọc tiếng Việt trong {@code detail}.</p>
     *
     * <p><b>Đã biết và cố ý chưa đụng tới:</b> {@code BranchScopeGuard.requireProvisionedAccount}
     * và {@code ImportScopeGuard} ném cùng mã này dưới dạng {@code ForbiddenException} ⇒ vẫn
     * {@code 403}. Hai chỗ đó nằm trong vùng của agent khác và có test ghim {@code 403}; việc gộp
     * về một mã HTTP duy nhất đã được báo cáo cho chủ hợp đồng.</p>
     */
    private ProblemDetail accountNotProvisioned(DomainException ex, HttpServletRequest request) {
        log.info("403 {} - tai khoan chua san sang: {}", request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, ProblemTypes.ACCOUNT_NOT_PROVISIONED,
                "Tài khoản chưa được ghép vào gia phả",
                "Tài khoản đã đăng nhập nhưng chưa được ghép với một nhân khẩu trong phả."
                        + " Hãy liên hệ Trưởng chi hoặc Hội đồng Tộc biểu để được ghép.",
                ApiProblems.CODE_ACCOUNT_NOT_PROVISIONED, request);
        problem.setProperty("nextStep", "CONTACT_BRANCH_HEAD");
        return problem;
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        log.warn("403 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Không đủ thẩm quyền",
                ex.getMessage(), ex.getCode(), request);
    }

    /** Vi phạm quy tắc nghiệp vụ (kỵ húy, vòng lặp quan hệ, ...) - tra ve 422. */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        log.info("422 {} - [{}] {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ProblemTypes.BUSINESS_RULE,
                "Vi phạm quy tắc nghiệp vụ", ex.getMessage(), ex.getCode(), request);
    }

    // =========================================================================================
    // Ngoại lệ của khung — override, KHÔNG khai @ExceptionHandler
    //
    // ResponseEntityExceptionHandler.handleException() da khai san ca 20 kieu trong MOT
    // @ExceptionHandler. Khai lai bat ky kieu nao trong so do o lop con se lam
    // ExceptionHandlerMethodResolver nem "Ambiguous @ExceptionHandler method mapped for ..."
    // NGAY LUC KHOI DONG. Do la ly do handleBeanValidation() va handleNoHandler() cu bien mat
    // khoi day: chung tro thanh override ben duoi (va NoHandlerFoundException nay do chinh lop
    // cha tra 404), khong phai bi xoa.
    // =========================================================================================

    /**
     * Lỗi kiểm thân yêu cầu — {@code 400} kèm mảng {@code errors} từng trường.
     *
     * <p><b>Thân lỗi không bao giờ chở giá trị bị từ chối.</b> Mỗi phần tử {@code errors} có đúng
     * hai khoá {@code field} + {@code message}. Thêm {@code fieldError.getRejectedValue()} vào đây
     * là đưa số điện thoại / ngày sinh của một người <b>đang sống</b> ra một phản hồi {@code 400}
     * mà bất kỳ ai gõ sai biểu mẫu cũng nhận được — một đường rò Tầng 3 (Nghị định 13/2023) trông
     * y như một cải tiến trải nghiệm, và {@code SensitiveFieldRedactorTest} sẽ <b>không</b> đỏ vì
     * nó chỉ canh nhật ký kiểm toán. {@code ApiErrorContractIT} ghim rằng mỗi phần tử chỉ có đúng
     * hai khoá ấy.</p>
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return loiKiemTruong(ex, ex.getBindingResult(), headers, request);
    }

    /** Cùng hình dạng thân lỗi với {@link #handleMethodArgumentNotValid} — gắn {@code @ModelAttribute}. */
    @Override
    protected ResponseEntity<Object> handleBindException(
            BindException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return loiKiemTruong(ex, ex.getBindingResult(), headers, request);
    }

    private ResponseEntity<Object> loiKiemTruong(Exception ex, BindingResult ketQua,
                                                 HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = ApiProblems.of(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION,
                "Dữ liệu gửi lên không hợp lệ", "Một hoặc nhiều trường không hợp lệ",
                CODE_VALIDATION, duongDan(request));
        problem.setProperty("errors", tungTruong(ketQua));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static List<Map<String, String>> tungTruong(BindingResult ketQua) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ketQua.getFieldErrors()) {
            // CHI hai khoa. Xem javadoc handleMethodArgumentNotValid.
            errors.add(Map.of("field", fieldError.getField(),
                    "message", cau(fieldError.getDefaultMessage())));
        }
        for (var globalError : ketQua.getGlobalErrors()) {
            errors.add(Map.of("field", globalError.getObjectName(),
                    "message", cau(globalError.getDefaultMessage())));
        }
        return errors;
    }

    private static String cau(String message) {
        return message == null ? "" : message;
    }

    /**
     * Tham số sai kiểu ({@code /persons/khong-phai-uuid}) — {@code 400}, và <b>không nhắc lại giá
     * trị đã gửi</b>.
     *
     * <p>Mặc định của Spring dựng {@code detail} là {@code Failed to convert 'x' with value: 'y'}.
     * Chuỗi ấy đi thẳng vào thân phản hồi <i>và</i> vào log, mang theo nguyên văn thứ client vừa
     * gõ — mà một trường ngày tháng bị gõ nhầm số điện thoại vào là ca thường gặp, không phải ca
     * giả định. Tên tham số đủ để sửa lỗi; giá trị thì không thêm gì ngoài rủi ro.</p>
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        String ten = ex.getPropertyName();
        ProblemDetail problem = ApiProblems.of(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION,
                "Dữ liệu gửi lên không hợp lệ",
                ten == null ? "Một tham số sai kiểu dữ liệu."
                        : "Tham số " + ten + " sai kiểu dữ liệu.",
                CODE_VALIDATION, duongDan(request));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * <b>Cửa duy nhất</b> mọi ngoại lệ khung đi qua trước khi thành phản hồi — nơi gắn
     * {@code code}, {@code timestamp}, {@code type} và {@code instance}.
     *
     * <p>Không có bước này thì lỗi khung trả {@code type: about:blank} và <b>không có
     * {@code code}</b>, tức client chỉ còn cách đọc chuỗi tiếng Việt trong {@code detail} để rẽ
     * nhánh — đúng khiếm khuyết mà {@code ApiErrorContractIT} đã ghim cho
     * {@code MethodArgumentNotValidException}, chỉ là ở 19 ngoại lệ còn lại.</p>
     *
     * <p>Chỉ <i>điền chỗ trống</i>: các override bên trên đã đặt sẵn {@code code} thì giữ nguyên.</p>
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        ResponseEntity<Object> response =
                super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null) {
            return null;
        }
        if (response.getBody() instanceof ProblemDetail problem) {
            boSungChoKhung(problem, statusCode, request);
        }
        ghiNhat(ex, statusCode, request);
        return response;
    }

    private static void boSungChoKhung(ProblemDetail problem, HttpStatusCode status,
                                       WebRequest request) {
        Map<String, Object> props = problem.getProperties();
        if (props == null || !props.containsKey("code")) {
            problem.setProperty("code", maKhung(status));
        }
        if (props == null || !props.containsKey("timestamp")) {
            problem.setProperty("timestamp", Instant.now().toString());
        }
        URI type = problem.getType();
        if (type == null || "about:blank".equals(type.toString())) {
            problem.setType(loaiKhung(status));
            problem.setTitle(tieuDeKhung(status));
        }
        if (problem.getInstance() == null) {
            String duongDan = duongDan(request);
            if (duongDan != null) {
                problem.setInstance(URI.create(duongDan));
            }
        }
    }

    /**
     * Mã lỗi cho ngoại lệ khung, lấy <b>trong danh sách đóng</b> {@code ProblemCode} của
     * {@code contracts/openapi.yaml}.
     *
     * <p>Cố ý <b>không</b> mở ba mã mới ({@code METHOD_NOT_ALLOWED} / {@code NOT_ACCEPTABLE} /
     * {@code UNSUPPORTED_MEDIA_TYPE}): danh sách ấy là hợp đồng đóng, thêm mã là việc phải có chữ
     * ký của cả BE lẫn FE. Ba tình huống ấy cũng là lỗi <i>client gọi sai giao thức</i>, không phải
     * lỗi người dùng sửa được trên màn hình, nên FE không có nhánh nào để rẽ; chính {@code status}
     * của RFC 7807 đã nói đủ chính xác. Nếu có ngày FE cần phân biệt, đó là lúc mở hợp đồng.</p>
     */
    private static String maKhung(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return CODE_NOT_FOUND;
        }
        return status.is4xxClientError() ? CODE_VALIDATION : "INTERNAL_ERROR";
    }

    private static URI loaiKhung(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ProblemTypes.NOT_FOUND;
        }
        return status.is4xxClientError() ? ProblemTypes.VALIDATION : ProblemTypes.INTERNAL;
    }

    private static String tieuDeKhung(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Dữ liệu gửi lên không hợp lệ";
            case 404 -> "Không tìm thấy endpoint";
            case 405 -> "Phương thức HTTP không được hỗ trợ";
            case 406 -> "Không đáp ứng được Accept của yêu cầu";
            case 415 -> "Kiểu nội dung không được hỗ trợ";
            default -> status.is4xxClientError() ? "Yêu cầu không hợp lệ" : "Lỗi hệ thống";
        };
    }

    /**
     * <b>Lỗi của client không được ghi ở mức ERROR, và không được ghi kèm thông điệp gốc.</b>
     *
     * <p>Hai lý do, cả hai đã thành sự thật một lần khi handler bắt-tất nuốt các ngoại lệ này:</p>
     * <ul>
     *   <li>Mọi thân JSON hỏng, mọi {@code Accept} lạ, mọi lượt dò endpoint đều thành một stack
     *       trace ERROR — cảnh báo vận hành chìm trong nhiễu do chính người ngoài gây ra.</li>
     *   <li>Thông điệp của Jackson ({@code InvalidFormatException}) <b>nhúng giá trị bị từ chối</b>.
     *       Một số điện thoại gõ nhầm vào trường ngày sẽ nằm nguyên trong log ở mức ERROR — đường
     *       rò Tầng 3 sang nhật ký, nơi {@code SensitiveFieldRedactor} không với tới.</li>
     * </ul>
     * Nên 4xx chỉ ghi <i>tên lớp ngoại lệ</i> ở mức {@code INFO}; ai cần nội dung thì bật
     * {@code DEBUG} cho logger này một cách có chủ ý.
     */
    private static void ghiNhat(Exception ex, HttpStatusCode status, WebRequest request) {
        if (status.is4xxClientError()) {
            log.info("{} {} - {}", status.value(), duongDan(request), ex.getClass().getSimpleName());
            log.debug("Chi tiet loi {} tai {}", ex.getClass().getSimpleName(), duongDan(request), ex);
        } else {
            log.error("{} {} - loi khung", status.value(), duongDan(request), ex);
        }
    }

    private static String duongDan(WebRequest request) {
        if (request instanceof ServletWebRequest servlet) {
            return servlet.getRequest().getRequestURI();
        }
        return null;
    }

    // =========================================================================================
    // Ngoại lệ ngoài danh sách của khung
    // =========================================================================================

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex,
                                                   HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION,
                "Dữ liệu gửi lên không hợp lệ", "Một hoặc nhiều tham số không hợp lệ",
                CODE_VALIDATION, request);
        List<Map<String, String>> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            // CHI hai khoa - khong bao gio them violation.getInvalidValue().
            errors.add(Map.of(
                    "field", String.valueOf(violation.getPropertyPath()),
                    "message", violation.getMessage()));
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex,
                                               HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION,
                "Dữ liệu gửi lên không hợp lệ", ex.getMessage(), CODE_VALIDATION, request);
    }

    /**
     * <b>Nhánh này gần như không bao giờ chạy, và đó không phải lỗi.</b>
     *
     * <p>Tuyệt đại đa số {@code 401} của hệ thống sinh ra <i>trong chuỗi lọc của Spring Security</i>,
     * trước khi {@code DispatcherServlet} được gọi — nên không một {@code @ExceptionHandler} nào
     * có cơ hội chạy. Đó chính là lý do mọi {@code 401} từng trả {@code Content-Length: 0}. Chỗ vá
     * thật nằm ở {@link SecurityProblemSupport}, được cắm vào
     * {@code exceptionHandling}/{@code oauth2ResourceServer} trong {@code SecurityConfig}.</p>
     *
     * <p>Giữ lại nhánh này cho phần dư: một {@code AuthenticationException} ném ra từ bên trong
     * controller hay service (ví dụ khi đọc token thủ công). Hai nơi trả cùng một thân lỗi vì cùng
     * đi qua {@link ApiProblems}.</p>
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex,
                                              HttpServletRequest request) {
        log.debug("401 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Chưa đăng nhập",
                "Yêu cầu cần một JWT hợp lệ do Keycloak cấp", ApiProblems.CODE_UNAUTHENTICATED,
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("403 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Không đủ thẩm quyền",
                "Tài khoản không có quyền trên tài nguyên này", ApiProblems.CODE_FORBIDDEN, request);
    }

    /** Xung đột khoá lạc quan {@code @Version} - hai nguoi cung sua mot ho so nhan khau. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                              HttpServletRequest request) {
        log.info("409 {} - {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, ProblemTypes.CONFLICT,
                "Dữ liệu đã bị người khác thay đổi",
                "Bản ghi đã được cập nhật bởi người dùng khác, hãy tải lại và thử lại",
                "OPTIMISTIC_LOCK_CONFLICT", request);
    }

    /** Uỷ quyền cho {@link ApiProblems} để thân lỗi ở đây và ở tầng lọc bảo mật không lệch nhau. */
    private ProblemDetail problem(HttpStatus status, URI type, String title, String detail,
                                  String code, HttpServletRequest request) {
        return ApiProblems.of(status, type, title, detail, code, request);
    }
}

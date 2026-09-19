package vn.giapha.dataimport.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.giapha.dataimport.api.rest.dto.ImportBatchDto;
import vn.giapha.dataimport.api.rest.dto.ImportCommitProgressDto;
import vn.giapha.dataimport.api.rest.dto.ImportDuplicateDecisionRequest;
import vn.giapha.dataimport.api.rest.dto.ImportDuplicateDecisionResultDto;
import vn.giapha.dataimport.api.rest.dto.ImportDuplicatePairDto;
import vn.giapha.dataimport.api.rest.dto.ImportIssueDto;
import vn.giapha.dataimport.api.rest.dto.ImportRowDto;
import vn.giapha.dataimport.api.support.ImportBranchDirectory;
import vn.giapha.dataimport.api.support.ImportCommitExecutor;
import vn.giapha.dataimport.api.support.ImportCommitGate;
import vn.giapha.dataimport.api.support.ImportProblemCodes;
import vn.giapha.dataimport.api.support.ImportScopeGuard;
import vn.giapha.dataimport.application.CommitImportBatchService;
import vn.giapha.dataimport.application.DecideDuplicateService;
import vn.giapha.dataimport.application.RollbackImportBatchService;
import vn.giapha.dataimport.application.StageImportBatchService;
import vn.giapha.dataimport.application.ValidateImportBatchService;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.DuplicateDecision;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportRejectedException;
import vn.giapha.dataimport.domain.IssueSeverity;
import vn.giapha.dataimport.domain.PlannedAction;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.DomainException;

/**
 * Đường ống nhập liệu hàng loạt — {@code /api/v1/import/batches}.
 *
 * <h2>Bất biến của mọi endpoint trong lớp này</h2>
 * Không một lời gọi nào ở đây ghi vào {@code person}, {@code relationship} hay đồ thị AGE. Kể cả
 * {@code POST /commit}: <b>nó</b> không ghi — nó mở cổng rồi giao cho
 * {@code CommitImportBatchService} chạy ở luồng nền. Bất biến ấy được canh bằng cách đếm ba con số
 * trước và sau trong {@code ImportApiIT}, không bằng lời hứa.
 *
 * <h2>Kiểm quyền nằm ở đâu, và vì sao không ở {@code @PreAuthorize}</h2>
 * Quyền nhập liệu phụ thuộc vào <i>chi của lô</i>, mà controller chưa nạp lô lên thì chưa biết chi
 * ấy là gì. Vì vậy mọi phương thức đều đi qua {@link ImportScopeGuard} sau khi đã phân giải được
 * chi. Sai vai → {@code 403 FORBIDDEN}; đúng vai sai nhánh → {@code 403 BRANCH_SCOPE_VIOLATION}.
 *
 * <h2>Khu vực chờ chỉ mở cho người có quyền ghi trên chi ấy</h2>
 * Cả các endpoint <b>đọc</b> cũng đòi quyền ghi. Một lô đang đối soát là bản nháp nội bộ của một
 * chi: nó chứa nguyên văn những ô người ta gõ sai, những dòng sẽ bị bỏ, và danh sách mọi thứ bộ
 * kiểm chê. Mở nó cho cả họ xem là biến bước đối soát thành một buổi soi lỗi công khai, và Trưởng
 * chi sẽ nhập dè dặt — đúng thứ làm hỏng chất lượng dữ liệu.
 *
 * <h2>Tải lên trả về lô đã kiểm xong, không phải một biên nhận rỗng</h2>
 * {@code POST /batches} chạy luôn bộ kiểm rồi mới trả về. Người nhập vừa chờ tải xong thì thứ họ
 * cần là kết quả đối soát, không phải một mã lô để đi hỏi tiếp. Đọc tệp diễn ra <b>ngoài</b> giao
 * dịch, còn bộ kiểm là giao dịch riêng — xem javadoc hai service.
 */
@RestController
@RequestMapping("/api/v1/import/batches")
@Tag(name = "import", description = "Nhập liệu hàng loạt từ tệp Excel của Trưởng chi")
public class ImportBatchController {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchController.class);

    private final StageImportBatchService stage;
    private final ValidateImportBatchService validate;
    private final ImportReadModel readModel;
    private final ImportScopeGuard guard;
    private final ImportCommitGate gate;
    private final CommitImportBatchService commitService;
    private final DecideDuplicateService decideService;
    private final RollbackImportBatchService rollbackService;
    private final ImportCommitExecutor executor;
    private final ImportBranchDirectory branchDirectory;

    public ImportBatchController(StageImportBatchService stage, ValidateImportBatchService validate,
                                 ImportReadModel readModel, ImportScopeGuard guard,
                                 ImportCommitGate gate, CommitImportBatchService commitService,
                                 DecideDuplicateService decideService,
                                 RollbackImportBatchService rollbackService,
                                 ImportCommitExecutor executor,
                                 ImportBranchDirectory branchDirectory) {
        this.stage = stage;
        this.validate = validate;
        this.readModel = readModel;
        this.guard = guard;
        this.gate = gate;
        this.commitService = commitService;
        this.decideService = decideService;
        this.rollbackService = rollbackService;
        this.executor = executor;
        this.branchDirectory = branchDirectory;
    }

    /**
     * Tải tệp lên, đọc vào khu vực chờ, rồi chạy bộ kiểm ngay.
     *
     * @param force bỏ qua chốt bấm-hai-lần. Chốt ấy chỉ chặn việc <b>ghi lại đúng một tệp đã
     *        ghi</b>; sửa một ô rồi tải lại không bị chặn, vì sửa một ô là đổi mã băm. Giao diện
     *        chỉ được đặt {@code true} sau khi người dùng đã đọc câu cảnh báo — không bao giờ đặt
     *        sẵn.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải tệp Excel lên và chạy bộ kiểm")
    public ResponseEntity<ImportBatchDto> upload(@RequestPart("file") MultipartFile file,
                                                 @RequestParam("branchId") UUID branchId,
                                                 @RequestParam(name = "force", defaultValue = "false")
                                                 boolean force) {
        MemberScopeView caller = guard.requireProvisioned();
        ImportBranchDirectory.Chi chi = guard.requireWriteAccess(branchId);

        byte[] content = doc(file);
        String filename = file.getOriginalFilename() == null ? "khong-ro-ten.xlsx"
                : file.getOriginalFilename();

        ImportBatch batch = stage.stage(chi.id(), caller.appUserId(), filename, content, force);
        validate.validate(batch.id());
        ImportBatchDto dto = readModel.batchDto(batch.id());

        log.info("Lo nhap lieu {} cho chi {}: {} loi chan, {} canh bao", dto.id(), chi.path(),
                dto.blockingCount(), dto.warningCount());
        return ResponseEntity.created(URI.create("/api/v1/import/batches/" + dto.id())).body(dto);
    }

    /**
     * Các lô của một chi, hoặc của <b>mọi chi trong phạm vi</b> khi bỏ trống {@code branchId}.
     *
     * <h2>Vì sao endpoint này không phải thứ thêm cho đủ bộ</h2>
     * Nó là thứ làm cho "đóng trình duyệt rồi quay lại hôm sau" chạy được. Một lô 400 dòng mất vài
     * buổi để đối soát, và trạng thái dở dang ấy phải sống ở <b>máy chủ</b>: không có danh sách
     * này thì người nhập chỉ tìm lại được lô của mình nếu còn nhớ mã lô trong thanh địa chỉ.
     *
     * <p><b>Không lọc sau khi phân trang.</b> Phạm vi được quyết trước — tập chi người gọi có
     * quyền ghi — rồi mới truy vấn. Lấy 20 lô rồi bỏ đi những lô ngoài phạm vi sẽ trả về những
     * trang thủng lỗ chỗ, và trang cuối cùng có thể rỗng trong khi vẫn còn dữ liệu.</p>
     *
     * @param branchId bỏ trống = mọi chi người gọi được phép ghi. Người không quản chi nào nhận
     *        một danh sách rỗng — đúng, vì họ thật sự không có lô nào; còn khách vãng lai thì đã bị
     *        chặn ở {@code requireProvisioned} từ trước đó
     */
    @GetMapping
    @Operation(summary = "Các lô nhập liệu trong phạm vi người gọi")
    public List<ImportBatchDto> batches(@RequestParam(required = false) UUID branchId,
                                        @RequestParam(required = false) BatchStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        MemberScopeView caller = guard.requireProvisioned();
        if (branchId != null) {
            // Chi nay ngoai pham vi thi 403 ngay tai day, khong tra mot danh sach rong: "khong co
            // lo nao" va "chi nay khong phai cua ban" la hai cau tra loi khac nhau han.
            return readModel.batchDtos(guard.requireWriteAccess(branchId).id(), status, page, size);
        }
        List<UUID> phamVi = new ArrayList<>();
        for (ImportBranchDirectory.Chi chi : branchDirectory.all()) {
            if (guard.canWriteOn(caller, chi.path())) {
                phamVi.add(chi.id());
            }
        }
        return readModel.batchDtos(phamVi, status, page, size);
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "Trạng thái lô kèm mọi con số đã đếm sẵn")
    public ImportBatchDto batch(@PathVariable UUID batchId) {
        return readModel.batchDto(requireAccessibleBatch(batchId));
    }

    /**
     * Chạy lại bộ kiểm trên một lô đang chờ.
     *
     * <p>Chạy lại nhiều lần là hành vi <b>bình thường</b> của quy trình, không phải ngoại lệ: mỗi
     * lần chạy sinh lại toàn bộ danh sách lỗi và toàn bộ kết quả đối soát CREATE/UPDATE, không tích
     * luỹ. Nếu lỗi cũ còn sót thì người nhập sửa xong vẫn thấy y nguyên danh sách và sẽ kết luận bộ
     * kiểm hỏng.</p>
     */
    @PostMapping("/{batchId}/validate")
    @Operation(summary = "Chạy lại bộ kiểm")
    public ImportBatchDto revalidate(@PathVariable UUID batchId) {
        ImportBatch batch = requireAccessibleBatch(batchId);
        if (!batch.status().coKiemLaiDuoc()) {
            throw new DomainException(ImportProblemCodes.BATCH_CLOSED,
                    "Lô này đã ở trạng thái " + batch.status() + " nên không kiểm lại được."
                            + " Hãy tải lên một lô mới.");
        }
        validate.validate(batchId);
        return readModel.batchDto(batchId);
    }

    /**
     * Danh sách lỗi và cảnh báo.
     *
     * @param severity {@code BLOCKING} hoặc {@code WARNING}; bỏ trống là cả hai. Hai nhóm khác nhau
     *        về <b>hệ quả</b> chứ không phải về mức độ, nên giao diện phải hiện chúng thành hai
     *        bảng và không bao giờ hiện một con số gộp
     */
    @GetMapping("/{batchId}/issues")
    @Operation(summary = "Lỗi chặn và cảnh báo của một lô")
    public List<ImportIssueDto> issues(@PathVariable UUID batchId,
                                       @RequestParam(required = false) IssueSeverity severity) {
        requireAccessibleBatch(batchId);
        return readModel.issueDtos(batchId, severity);
    }

    /**
     * Xem trước từng dòng đang chờ.
     *
     * @param action lọc {@code CREATE} / {@code UPDATE} / {@code SKIP}. {@code UPDATE} là câu trả
     *        lời cho nỗi lo duy nhất khi tải lại: mã này đã có chủ trong phả nên sẽ cập nhật đúng
     *        người ấy, không sinh người thứ hai
     */
    @GetMapping("/{batchId}/rows")
    @Operation(summary = "Xem trước các dòng trong khu vực chờ")
    public List<ImportRowDto> rows(@PathVariable UUID batchId,
                                   @RequestParam(required = false) PlannedAction action) {
        requireAccessibleBatch(batchId);
        return readModel.rowDtos(batchId, action);
    }

    /**
     * Các cặp nghi trùng để người đối chiếu.
     *
     * <p><b>Không trả một trường dữ liệu nào của người đã có trong phả</b> — chỉ {@code personId}.
     * Giao diện cầm khoá ấy gọi {@code GET /api/v1/persons/&#123;id&#125;}, nơi bộ lọc phân tầng
     * riêng tư quyết định trường nào được xem. Xem {@code ImportDuplicatePairDto}.</p>
     */
    @GetMapping("/{batchId}/duplicates")
    @Operation(summary = "Các cặp nghi trùng chờ người quyết")
    public List<ImportDuplicatePairDto> duplicates(@PathVariable UUID batchId) {
        requireAccessibleBatch(batchId);
        return readModel.duplicateDtos(batchId);
    }

    /**
     * <b>Quyết một cặp nghi trùng</b>: gộp · để riêng · hoãn.
     *
     * <h2>Vì sao lối gọi này là điều kiện để đường ống dùng được trên dữ liệu thật</h2>
     * Không có nó thì mọi cặp vĩnh viễn {@code PENDING}, {@code undecidedDuplicateCount} không bao
     * giờ giảm, và cổng duyệt chặn <b>mọi lô có dù chỉ một người nghi trùng</b> — tức là đường ống
     * chỉ chạy được trên tệp không có ai nghi trùng, mà một dòng họ chép lại từ nhiều cuốn sổ thì
     * gần như luôn có. Đó chính là lý do bộ dò trùng tồn tại.
     *
     * <h2>"Gộp" nghĩa là gì — hai ca, hai nghĩa hoàn toàn khác nhau</h2>
     * <ul>
     *   <li><b>Bên kia đã có trong phả</b> — dòng trong tệp <b>không tạo người mới</b> mà
     *       <b>cập nhật</b> hồ sơ ấy: {@code CREATE} đổi thành {@code UPDATE}, và
     *       {@code person_external_ref} sẽ trỏ mã trong tệp sang {@code person.id} đã tồn tại để
     *       lần tải lại sau nhận ra ngay mà không hỏi lại.</li>
     *   <li><b>Bên kia là một dòng khác trong chính tệp</b> — một trong hai dòng phải
     *       <b>biến mất trước khi ghi</b>, và mọi mã cha / mã mẹ / mã kế tự / mã hôn phối trỏ tới
     *       dòng bị bỏ được <b>trỏ lại</b> sang dòng ở lại. Xem {@code DuplicateMergePlan}: đây là
     *       chỗ dễ sinh người mồ côi nhất trong cả đường ống.</li>
     * </ul>
     *
     * <h2>"Hoãn" không phải "đã quyết"</h2>
     * {@code DEFERRED} vẫn nằm trong {@code undecidedDuplicateCount} và vẫn chặn nút duyệt. Nó tồn
     * tại để người đối chiếu không phải chọn bừa giữa hai đáp án khi chưa chắc — ép chọn nhị phân
     * lúc chưa chắc thì cái bấm đại trở thành sự thật trong phả. Nhưng nếu nó mở khoá nút duyệt thì
     * nó lập tức thành nút "cho tôi qua" và cả cơ chế thành trang trí.
     *
     * <h2>Không rò rỉ</h2>
     * Yêu cầu <b>không</b> nhận {@code personId}: đích của phép gộp đã nằm sẵn trong cặp mà máy chủ
     * dò ra, nên người gọi chỉ chọn được trong số những cặp ấy. Phản hồi cũng chỉ mang
     * {@code personId}, {@code ref}, {@code score} và nhãn tín hiệu — không một trường nhân khẩu
     * nào của người đã có trong phả.
     *
     * @param pairId khoá của cặp, lấy từ {@code GET /duplicates}. Khoá này <b>ổn định</b> qua các
     *        lần kiểm lại, nên một cặp không đổi giữ nguyên quyết định của nó
     */
    @PostMapping("/{batchId}/duplicates/{pairId}/decision")
    @Operation(summary = "Quyết một cặp nghi trùng: gộp / để riêng / hoãn")
    public ImportDuplicateDecisionResultDto decideDuplicate(
            @PathVariable UUID batchId,
            @PathVariable UUID pairId,
            @RequestBody ImportDuplicateDecisionRequest body) {
        MemberScopeView caller = guard.requireProvisioned();
        ImportBatch batch = requireAccessibleBatch(batchId);
        if (batch.status().daChot()) {
            throw new DomainException(ImportProblemCodes.BATCH_CLOSED,
                    "Lô này đã ở trạng thái " + batch.status() + " nên không còn gì để quyết."
                            + " Hãy tải lên một lô mới thay vì sửa lô cũ.");
        }
        DuplicateDecision decision =
                DuplicateDecision.tuYeuCau(body == null ? null : body.decision());

        DecideDuplicateService.KetQua ketQua = decideService.quyet(batchId, pairId, decision,
                caller.appUserId(), body == null ? null : body.note());

        ImportBatchDto dto = readModel.batchDto(batchId);
        log.info("app_user {} quyet cap {} cua lo {} la {}; con {}/{} cap chua quyet, canApprove = {}",
                caller.appUserId(), pairId, batchId, decision, ketQua.conChuaQuyet(),
                ketQua.tongSoCap(), dto.canApprove());
        return new ImportDuplicateDecisionResultDto(
                readModel.duplicateDto(batchId, pairId), dto);
    }

    /**
     * "Tôi đã xem hết phần cần xem lại."
     *
     * <h2>Vì sao endpoint này là điều kiện để đường ống dùng được</h2>
     * Một cuốn gia phả thật <b>luôn</b> có cảnh báo — thuỷ tổ thì lần nào cũng sinh
     * {@code IMP_LONE_NODE}, và đó là dữ liệu đúng chứ không phải lỗi. Không có lối gọi này thì mọi
     * lô thật đều dừng ở {@code VALIDATED} với nút duyệt xám vĩnh viễn, và cả đường ống chỉ chạy
     * được trên những tệp mẫu không tồn tại ngoài đời.
     *
     * <h2>Nó ghi lại một lời khai, không bật một cái cờ</h2>
     * <b>Ai</b> xác nhận, <b>lúc nào</b>, và <b>tập cảnh báo nào</b> (bằng vân tay). Nếu lô được
     * kiểm lại và sinh cảnh báo <b>mới</b> thì xác nhận này <b>tự</b> hết hiệu lực và nút duyệt
     * đóng lại — nếu không, người duyệt đang xác nhận những dòng họ chưa từng nhìn thấy, và hệ
     * thống ghi điều đó nhân danh họ.
     *
     * <p>Thân yêu cầu không bắt buộc; giao diện gửi {@code {"acknowledged": true}} cho tường minh.
     * Máy chủ <b>không</b> nhận {@code acknowledged: false} như một lệnh rút lại: rút lại một lời
     * khai đã ghi không phải là xoá nó đi, và chưa ai cần nghiệp vụ ấy.</p>
     */
    @PostMapping("/{batchId}/acknowledge-warnings")
    @Operation(summary = "Xác nhận đã xem cảnh báo, mở khoá nút duyệt")
    public ImportBatchDto acknowledgeWarnings(@PathVariable UUID batchId,
                                              @RequestBody(required = false)
                                              Map<String, Object> body) {
        MemberScopeView caller = guard.requireProvisioned();
        ImportBatch batch = requireAccessibleBatch(batchId);
        if (batch.status().daChot()) {
            throw new DomainException(ImportProblemCodes.BATCH_CLOSED,
                    "Lô này đã ở trạng thái " + batch.status() + " nên không còn gì để xác nhận."
                            + " Hãy tải lên một lô mới thay vì sửa lô cũ.");
        }
        commitService.xacNhanDaXemCanhBao(batchId, caller.appUserId());
        ImportBatchDto dto = readModel.batchDto(batchId);
        log.info("app_user {} xac nhan da xem {} canh bao cua lo {}; canApprove = {}",
                caller.appUserId(), dto.warningCount(), batchId, dto.canApprove());
        return dto;
    }

    /**
     * <b>Ranh giới ghi duy nhất của cả đường ống.</b>
     *
     * <p>Trả {@code 202}: việc ghi chạy ở luồng nền và luồng web không bao giờ chờ nó. Trạng thái
     * cuối <b>chỉ</b> đến từ {@code GET /commit-progress} — đừng suy từ phản hồi này.</p>
     *
     * <p>Bốn điều kiện của cổng duyệt được kiểm ở máy chủ dù giao diện đã ẩn nút: nút bị ẩn không
     * phải là một phép kiểm.</p>
     */
    @PostMapping("/{batchId}/commit")
    @Operation(summary = "Duyệt lô và xếp hàng ghi vào phả")
    public ResponseEntity<ImportBatchDto> commit(@PathVariable UUID batchId) {
        MemberScopeView caller = guard.requireProvisioned();
        ImportBatch batch = requireAccessibleBatch(batchId);
        gate.check(batch, readModel.undecidedDuplicatePairs(batchId));

        UUID actor = caller.appUserId();

        // Luong web KHONG cho buoc ghi. 400 nguoi la vai giay toi vai chuc giay; giu request mo
        // suot thoi gian ay la moi goi timeout cua proxy roi mot cu bam lai cua nguoi dung — tuc
        // hai lan ghi cho cung mot lo. Moi ngoai le cua buoc ghi da duoc chinh service doi thanh
        // trang thai FAILED kem ly do, va client doc no qua /commit-progress.
        executor.execute(() -> {
            try {
                commitService.commit(batchId, actor);
            } catch (RuntimeException ex) {
                log.error("Ghi lo {} that bai; trang thai cuoi doc qua /commit-progress", batchId, ex);
            }
        });

        log.info("Lo {} da qua cong duyet, da xep hang ghi vao pha (app_user {})", batchId, actor);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/import/batches/" + batchId + "/commit-progress"))
                .body(readModel.batchDto(batchId));
    }

    /**
     * Xem <b>trước</b> việc gỡ: lô này còn gỡ được không, và vướng ở đâu.
     *
     * <p>Phải có, vì một hộp thoại "Bạn chắc chứ?" không nói gì về việc đã có người sửa hồ sơ do lô
     * ấy sinh ra là một hộp thoại nói dối. Endpoint này <b>không</b> gỡ gì cả.</p>
     */
    @GetMapping("/{batchId}/rollback-preflight")
    @Operation(summary = "Lô này còn gỡ được không, và vướng ở đâu")
    public Map<String, Object> rollbackPreflight(@PathVariable UUID batchId) {
        requireAccessibleBatch(batchId);
        List<String> vuongMac = rollbackService.vuongMac(batchId);
        return Map.of("canRollback", vuongMac.isEmpty(), "blockers", vuongMac);
    }

    /**
     * Gỡ toàn bộ lô, kể cả sau khi đã ghi vào phả.
     *
     * <p>Đây là nút quyết định người ta có dám nhập <b>lần thứ hai</b> hay không. Gỡ là <b>xoá
     * mềm</b>: node vẫn nằm trong đồ thị, vì xoá cứng một nhân khẩu là cắt đứt mọi liên kết treo
     * vào nó.</p>
     *
     * <p>Từ chối khi đã có người khác động vào dữ liệu lô ấy sinh ra
     * ({@code 422 IMP_ROLLBACK_REFUSED}): một nút gỡ làm mất công người khác còn tệ hơn không có
     * nút gỡ, vì nó tạo cảm giác an toàn giả.</p>
     */
    @PostMapping("/{batchId}/rollback")
    @Operation(summary = "Gỡ cả lô khỏi phả (xoá mềm)")
    public ImportBatchDto rollback(@PathVariable UUID batchId,
                                   @RequestBody(required = false) Map<String, String> body) {
        MemberScopeView caller = guard.requireProvisioned();
        requireAccessibleBatch(batchId);
        String lyDo = body == null ? null : body.get("reason");
        RollbackImportBatchService.KetQua ketQua =
                rollbackService.rollback(batchId, caller.appUserId(), lyDo);
        log.info("Da go lo {}: {} nguoi xoa mem, {} canh go, {} ma tra lai", batchId,
                ketQua.daXoaMem(), ketQua.daGoCanh(), ketQua.daTraLaiMa());
        return readModel.batchDto(batchId);
    }

    /**
     * Tiến độ ghi — nguồn duy nhất của trạng thái cuối.
     *
     * <p>{@code estimated} luôn {@code true} và giao diện phải hiện điều đó thành chữ: con số đếm
     * ngoài giao dịch ghi nên nó là ước lượng theo bản chất, không phải vì hiện thực còn dở.</p>
     */
    @GetMapping("/{batchId}/commit-progress")
    @Operation(summary = "Tiến độ ghi vào phả")
    public ImportCommitProgressDto commitProgress(@PathVariable UUID batchId) {
        ImportBatch batch = requireAccessibleBatch(batchId);
        return progressOf(batch);
    }

    // -------------------------------------------------------------------------------------

    /**
     * Phân giải lô và kiểm quyền trên chi của nó.
     *
     * <p>Lô không tồn tại và lô của chi khác đều đi qua đây, nhưng trả hai mã khác nhau:
     * {@code 404} cho khoá không có thật, {@code 403 BRANCH_SCOPE_VIOLATION} cho lô có thật ngoài
     * phạm vi. Trưởng chi cần phân biệt "gõ nhầm mã lô" với "lô này không phải của tôi".</p>
     */
    private ImportBatch requireAccessibleBatch(UUID batchId) {
        ImportBatch batch = readModel.batchOrThrow(batchId);
        guard.requireWriteAccess(batch.branchId());
        return batch;
    }

    /**
     * Suy tiến độ từ trạng thái lô.
     *
     * <p>Chưa có bảng tiến độ riêng, và cố ý chưa vẽ ra một con số giả: {@code QUEUED} với
     * {@code processedRows = 0} nói đúng sự thật — lô đã qua cổng duyệt và đang chờ bộ ghi. Một
     * thanh tiến độ nhích dần trong khi chưa có gì chạy là kiểu nói dối khó gỡ nhất.</p>
     */
    private ImportCommitProgressDto progressOf(ImportBatch batch) {
        int total = batch.personRowCount();
        return switch (batch.status()) {
            case COMMITTED -> new ImportCommitProgressDto(batch.id(), batch.status().name(), "DONE",
                    total, total, true, null);
            case COMMITTING -> new ImportCommitProgressDto(batch.id(), batch.status().name(),
                    "PERSONS", 0, total, true, null);
            case FAILED -> new ImportCommitProgressDto(batch.id(), batch.status().name(), "FAILED",
                    0, total, true, batch.failureReason());
            default -> new ImportCommitProgressDto(batch.id(), batch.status().name(), "QUEUED", 0,
                    total, true, null);
        };
    }

    /** Đọc toàn bộ tệp vào bộ nhớ — trần 10 MB đã chặn ở {@code ImportLimits}, xem javadoc ở đó. */
    private static byte[] doc(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportRejectedException(ImportRejectedException.CORRUPT_FILE,
                    "Không nhận được tệp nào. Hãy chọn lại tệp .xlsx rồi gửi.");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ImportRejectedException(ImportRejectedException.CORRUPT_FILE,
                    "Không đọc được tệp vừa tải lên: " + ex.getMessage(), ex);
        }
    }
}

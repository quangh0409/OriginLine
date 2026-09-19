package vn.giapha.dataimport.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.dataimport.api.rest.dto.ImportIssueDto;
import vn.giapha.dataimport.api.support.ImportBranchDirectory;
import vn.giapha.dataimport.api.support.ImportScopeGuard;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.infrastructure.excel.export.ImportIssueExportGenerator;
import vn.giapha.dataimport.infrastructure.excel.export.LoBaoLoi;
import vn.giapha.dataimport.infrastructure.excel.export.LoiXuatExcel;
import vn.giapha.dataimport.infrastructure.excel.template.ExcelTemplateFile;

/**
 * Tải danh sách lỗi của một lô về dạng Excel —
 * {@code GET /api/v1/import/batches/&#123;id&#125;/issues.xlsx}.
 *
 * <h2>Vì sao là một tệp riêng, không phải một phương thức của {@code ImportBatchController}</h2>
 * Lớp kia đã là vòng đời của một lô: tải lên, kiểm lại, xem trước, duyệt, gỡ. Endpoint này không
 * nằm trong vòng đời ấy — nó không đổi trạng thái gì, và nó là thứ duy nhất trong cả context trả về
 * một luồng byte thay vì JSON, với chuỗi kiểm quyền → dựng tệp → ghi audit của riêng nó. Nhập nó
 * vào lớp kia là làm một lớp vốn đã dài thêm một trách nhiệm thứ hai.
 *
 * <h2>Đọc cũng đòi quyền GHI trên chi của lô</h2>
 * Giống hệt mọi endpoint đọc của {@code ImportBatchController}, và lý do mạnh hơn ở đây: một lô
 * đang đối soát là bản nháp nội bộ của một chi — nó liệt kê nguyên văn những ô người ta gõ sai. Tệp
 * này lại <b>rời khỏi hệ thống</b> và không có đường thu hồi, nên đây là bề mặt cuối cùng đáng nới
 * lỏng phân quyền.
 *
 * <h2>Ba lớp chắn riêng tư, và không lớp nào thừa</h2>
 * <ol>
 *   <li>Bộ kiểm soạn {@code message} và {@code context} <b>bằng khoá, không bằng trường</b> — chỗ
 *       sửa tận gốc, nằm trong {@code SuspectDuplicateRule} / {@code TabooCollisionRule}.</li>
 *   <li>{@link ImportReadModel#issueDtos} đi qua {@code ImportIssueRedactor}: một danh sách khoá
 *       được phép trên {@code context}. Endpoint này <b>bắt buộc</b> đi qua đường đọc ấy chứ không
 *       hỏi thẳng {@code ImportIssueRepository} — đọc thẳng JSONB rồi đổ ra Excel là đúng cái lỗi
 *       vừa được vá ở bốn bề mặt khác.</li>
 *   <li>{@code ContextChoPhep} của gói xuất áp thêm một danh sách khoá <b>theo từng mã lỗi</b>, vì
 *       chốt ở (2) chỉ canh hai mã và dòng dữ liệu có thể do một bản nhị phân cũ hơn ghi ra. Một
 *       tệp Excel là chỗ tệ nhất để một khoá cũ lọt ra.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/import/batches")
@Tag(name = "import", description = "Nhập liệu hàng loạt từ tệp Excel của Trưởng chi")
public class ImportIssueExportController {

    private static final Logger log = LoggerFactory.getLogger(ImportIssueExportController.class);

    private final ImportReadModel readModel;
    private final ImportScopeGuard guard;
    private final ImportIssueExportGenerator generator;
    private final AuditTrailService audit;

    public ImportIssueExportController(ImportReadModel readModel, ImportScopeGuard guard,
                                       ImportIssueExportGenerator generator,
                                       AuditTrailService audit) {
        this.readModel = readModel;
        this.guard = guard;
        this.generator = generator;
        this.audit = audit;
    }

    @GetMapping(value = "/{batchId}/issues.xlsx", produces = ExcelTemplateFile.CONTENT_TYPE)
    @Operation(summary = "Tải danh sách lỗi và cảnh báo của một lô về dạng Excel")
    public ResponseEntity<byte[]> issuesXlsx(@PathVariable UUID batchId) {
        ImportBatch batch = readModel.batchOrThrow(batchId);
        ImportBranchDirectory.Chi chi = guard.requireWriteAccess(batch.branchId());

        // Khong loc theo muc: ca hai nhom deu phai co mat, va chinh bo sinh tach chung ra hai
        // trang. Cho client chon muc o day nghia la co mot ban xuat chi co canh bao — nghe vo hai,
        // nhung no la mot tep mang ten "Danh sach can sua" ma thieu phan phai sua.
        List<ImportIssueDto> dtos = readModel.issueDtos(batchId, null);

        LoBaoLoi lo = new LoBaoLoi(batchId.toString(), chi.name(), batch.originalFilename(),
                LocalDate.now(), dong(dtos));
        ExcelTemplateFile tep = generator.sinh(lo);

        // Ghi audit SAU khi dung tep xong: mot dong "da xuat" cho mot lan xuat that bai la mot dong
        // sai, va bang audit la thu duy nhat tra loi duoc "ai da cam tep nay" ve sau.
        audit.record("ImportBatch", batchId.toString(), AuditAction.EXPORT,
                "Xuat danh sach loi ra Excel: " + lo.loiChan().size() + " loi chan, "
                        + lo.canhBao().size() + " canh bao");

        log.info("Xuat loi lo {} cho chi {}: {} byte", batchId, chi.path(), tep.kichThuoc());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, tep.contentDisposition())
                .contentType(MediaType.parseMediaType(ExcelTemplateFile.CONTENT_TYPE))
                .body(tep.noiDung());
    }

    /**
     * {@code ImportIssueDto} (đã cắt riêng tư) → {@code LoiXuatExcel} (kiểu vào của bộ sinh).
     *
     * <p>Phép dịch một-một, cố tình nhạt nhẽo. Nó tồn tại để gói {@code infrastructure.excel.export}
     * không phải nhìn ngược lên {@code api.rest.dto} — chiều phụ thuộc đúng là
     * {@code api → application → domain}, và {@code infrastructure → api} là một vòng.</p>
     */
    private static List<LoiXuatExcel> dong(List<ImportIssueDto> dtos) {
        List<LoiXuatExcel> ket = new ArrayList<>(dtos.size());
        for (ImportIssueDto dto : dtos) {
            ket.add(new LoiXuatExcel(dto.severity(), dto.code(), dto.sheet(), dto.rowNo(),
                    dto.field(), dto.externalCode(), dto.message(), dto.context()));
        }
        return List.copyOf(ket);
    }
}

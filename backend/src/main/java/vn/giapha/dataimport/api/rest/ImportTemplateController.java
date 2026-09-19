package vn.giapha.dataimport.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import vn.giapha.dataimport.api.support.ImportBranchDirectory;
import vn.giapha.dataimport.api.support.ImportScopeGuard;
import vn.giapha.dataimport.infrastructure.excel.template.ExcelTemplateFile;
import vn.giapha.dataimport.infrastructure.excel.template.ImportTemplateGenerator;

/**
 * Mẫu Excel <b>riêng cho từng chi</b> — {@code /api/v1/admin/branches/{branchId}/import-template.xlsx}.
 *
 * <h2>Vì sao đường dẫn nằm dưới {@code /admin} chứ không dưới {@code /import}</h2>
 * Đó là chỗ {@code plan/02 §3.1} đặt nó: sinh mẫu đọc bảng {@code branch} và danh sách nhân khẩu
 * đã có của chi, là một thao tác <b>quản trị dữ liệu chi</b> chứ không phải một bước trong vòng đời
 * của một lô. Giữ nguyên để giao diện không phải sửa.
 *
 * <p><b>Không phải "chỉ System Admin".</b> Khác với {@code /admin/reminders/**}, endpoint này phục
 * vụ đúng Trưởng chi — người sẽ điền vào mẫu. Phép kiểm là quyền ghi trên chi ấy, giống hệt
 * {@code POST /import/batches}: để lấy được mẫu của chi Giáp, phải quản chi Giáp.
 *
 * <h2>Vì sao mẫu phải theo chi chứ không dùng chung một tệp tĩnh</h2>
 * Từ lần nhập thứ hai trở đi, mẫu mang sẵn <b>những người đã có trong phả</b> của chi ấy. Đó là
 * toàn bộ cơ chế giữ cho lần nhập sau không sinh người trùng: người nhập sửa ngay trên dòng đã có
 * mã, thay vì gõ lại từ đầu rồi nhận về 300 dòng {@code CREATE}.
 *
 * <h2>Tên tệp có dấu</h2>
 * {@code Content-Disposition} mang <b>cả hai</b> dạng: dạng thường đã rụng dấu cho trình duyệt cũ,
 * và dạng RFC 5987 {@code filename*=UTF-8''} cho tên thật ("Mẫu nhập liệu — Chi Ất.xlsx"). Thiếu
 * dạng thứ hai thì tệp về tay Trưởng chi là một cái tên không dấu — chi tiết nhỏ, nhưng là chi tiết
 * đầu tiên họ nhìn thấy của cả hệ thống.
 *
 * <h2>Khoản nợ kiến trúc đã biết</h2>
 * Controller gọi thẳng {@code infrastructure.excel.template.ImportTemplateGenerator}, trong khi
 * chiều phụ thuộc đúng là {@code api → application → domain}. Chỗ đúng là một use case service
 * (ví dụ {@code GenerateImportTemplateService}) ở {@code application} bọc lấy bộ sinh. Đợt này chỉ
 * gói {@code api} được mở nên chưa dựng được — xem báo cáo bàn giao.
 */
@RestController
@RequestMapping("/api/v1/admin/branches")
@Tag(name = "import", description = "Nhập liệu hàng loạt từ tệp Excel của Trưởng chi")
public class ImportTemplateController {

    private static final Logger log = LoggerFactory.getLogger(ImportTemplateController.class);

    private final ImportScopeGuard guard;
    private final ImportTemplateGenerator generator;

    public ImportTemplateController(ImportScopeGuard guard, ImportTemplateGenerator generator) {
        this.guard = guard;
        this.generator = generator;
    }

    @GetMapping(value = "/{branchId}/import-template.xlsx",
            produces = ExcelTemplateFile.CONTENT_TYPE)
    @Operation(summary = "Tải mẫu Excel của một chi")
    public ResponseEntity<byte[]> template(@PathVariable UUID branchId) {
        ImportBranchDirectory.Chi chi = guard.requireWriteAccess(branchId);
        ExcelTemplateFile tep = generator.sinh(chi.id());
        log.info("Sinh mau nhap lieu cho chi {}: {} byte", chi.path(), tep.kichThuoc());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, tep.contentDisposition())
                .contentType(MediaType.parseMediaType(ExcelTemplateFile.CONTENT_TYPE))
                .body(tep.noiDung());
    }
}

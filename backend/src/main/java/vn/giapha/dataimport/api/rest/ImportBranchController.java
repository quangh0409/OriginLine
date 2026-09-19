package vn.giapha.dataimport.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.dataimport.api.rest.dto.ImportBranchDto;
import vn.giapha.dataimport.api.rest.dto.ImportBranchProgressDto;
import vn.giapha.dataimport.api.rest.dto.ImportDuplicatePolicyDto;
import vn.giapha.dataimport.api.support.ImportBranchDirectory;
import vn.giapha.dataimport.api.support.ImportDuplicatePolicy;
import vn.giapha.dataimport.api.support.ImportScopeGuard;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.membership.application.MemberScopeView;

/**
 * Hai bề mặt phụ trợ của đường ống nhập liệu — {@code /api/v1/import}.
 *
 * <h2>Vì sao {@code /branches} trả cả chi ngoài phạm vi</h2>
 * Bốn chi nhập song song, và màn đầu tiên của quy trình phải trả lời được "tôi đang ở đâu trong cả
 * dòng họ". Danh sách chi và {@code ltree} của chúng là <b>cấu trúc tổ chức của dòng họ</b>, không
 * phải dữ liệu cá nhân: nó vốn đã hiện trên phả đồ cho mọi thành viên. Giấu ba chi kia đi không bảo
 * vệ thêm được gì, chỉ làm Trưởng chi tưởng hệ thống chỉ biết mỗi chi mình.
 *
 * <p>Phân quyền vẫn là hạng nhất, và nó nằm ở <b>cờ {@code canImport} của từng dòng</b> cộng với
 * phép kiểm thật ở {@code POST /batches}. Trả cờ ra là để giao diện khoá nút <i>trước</i> khi người
 * dùng chọn tệp, thay vì để họ chọn xong rồi nhận 403 — cùng một ranh giới, khác nhau ở chỗ người
 * dùng biết nó tồn tại trước hay sau khi mất công.</p>
 *
 * <p><b>Khách vãng lai và tài khoản chưa khởi tạo không thấy gì:</b> {@code 403}, không phải một
 * danh sách rỗng. Trả danh sách rỗng là nói với họ rằng màn này có thật.</p>
 */
@RestController
@RequestMapping("/api/v1/import")
@Tag(name = "import", description = "Nhập liệu hàng loạt từ tệp Excel của Trưởng chi")
public class ImportBranchController {

    private final ImportScopeGuard guard;
    private final ImportBranchDirectory branches;
    private final ImportProgressReadModel progress;
    private final ImportBatchRepository batches;

    public ImportBranchController(ImportScopeGuard guard, ImportBranchDirectory branches,
                                  ImportProgressReadModel progress, ImportBatchRepository batches) {
        this.guard = guard;
        this.branches = branches;
        this.progress = progress;
        this.batches = batches;
    }

    /** Cây chi/ngành, đã sắp theo {@code ltree}, kèm cờ "tôi nhập được vào chi này không". */
    @GetMapping("/branches")
    @Operation(summary = "Các chi/ngành, kèm quyền nhập liệu của người gọi")
    public List<ImportBranchDto> branches() {
        MemberScopeView caller = guard.requireProvisioned();
        List<ImportBranchDto> result = new ArrayList<>();
        for (ImportBranchDirectory.Chi chi : branches.all()) {
            boolean ghiDuoc = guard.canWriteOn(caller, chi.path());
            // Chi tra lo dang do cua chi NGUOI GOI quan. Viec dang lam do cua chi khac khong phai
            // chuyen cua man nay, va mot ma lo la mot lieu tro tro thang vao khu vuc cho cua ho.
            UUID loDangDo = ghiDuoc
                    ? batches.latestOpenBatch(chi.id()).map(ImportBatch::id).orElse(null)
                    : null;
            result.add(new ImportBranchDto(chi.id(), chi.name(), chi.path().value(), chi.kind(),
                    ghiDuoc, loDangDo));
        }
        return List.copyOf(result);
    }

    /**
     * Tiến độ nhập liệu của <b>mọi chi</b>.
     *
     * <h2>Trả cả dòng họ, nhưng không trả mọi thứ về mỗi chi</h2>
     * Bốn chi nhập song song; màn này tồn tại để cả họ biết <i>còn thiếu gì</i>, nên giấu ba chi
     * kia đi làm nó vô dụng. Nhưng chi <b>ngoài phạm vi</b> người gọi chỉ mang số liệu tổng hợp:
     * năm trường {@code coordinatorName} · {@code openBatch} · {@code blockingCount} ·
     * {@code warningCount} · {@code undecidedDuplicateCount} bị <b>bỏ hẳn khỏi JSON</b>, không null
     * hoá — null hoá vẫn nói cho người đọc biết trường ấy tồn tại, và với
     * {@code coordinatorName}, vốn là <b>tên một người còn sống</b>, chừng đó đã là rò rỉ.
     *
     * <p><b>Khách vãng lai và tài khoản chưa có {@code app_user} nhận {@code 403}</b>, không phải
     * một danh sách rỗng. Danh sách rỗng nói dối hai lần: rằng dòng họ này không có chi nào, và
     * rằng màn này có thật với họ.</p>
     *
     * <p>Đây <b>không</b> phải bảng xếp hạng — xem {@link ImportBranchProgressDto}.</p>
     */
    @GetMapping("/progress")
    @Operation(summary = "Tiến độ nhập liệu từng chi")
    public List<ImportBranchProgressDto> tienDoTheoChi() {
        return progress.tienDo(guard.requireProvisioned());
    }

    /**
     * Ngưỡng nghi trùng, <b>do máy chủ công bố</b>.
     *
     * <p>Giao diện không ghi cứng con số nào. Thang điểm là dữ liệu hiệu chỉnh của bộ dò trùng và
     * sẽ còn được chỉnh; một bản sao ở client chắc chắn sẽ trôi, và triệu chứng rất khó truy: giao
     * diện tick sẵn "hợp nhất" cho một cặp mà máy chủ coi là chưa đáng nghi.</p>
     */
    @GetMapping("/duplicate-policy")
    @Operation(summary = "Ngưỡng nghi trùng và ngưỡng tick sẵn hợp nhất")
    public ImportDuplicatePolicyDto duplicatePolicy() {
        guard.requireProvisioned();
        return new ImportDuplicatePolicyDto(ImportDuplicatePolicy.SUSPECT_THRESHOLD,
                ImportDuplicatePolicy.PRESELECT_MERGE_THRESHOLD, ImportDuplicatePolicy.AUTO_MERGE);
    }
}

package vn.giapha.dataimport.api.rest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.api.rest.dto.ImportBranchProgressDto;
import vn.giapha.dataimport.api.support.ImportBranchDirectory;
import vn.giapha.dataimport.api.support.ImportScopeGuard;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.port.BranchProgressPort;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.ImportIssueRepository;
import vn.giapha.membership.application.MemberScopeView;

/**
 * Dựng màn <b>tiến độ theo chi</b>, và cắt nó theo phạm vi người gọi.
 *
 * <h2>Luật phạm vi đã chốt, và ba cách làm sai nó</h2>
 * <ol>
 *   <li><b>Trả mọi chi</b> cho tài khoản đã khởi tạo. Bốn chi nhập song song; màn này tồn tại để
 *       cả dòng họ biết còn thiếu gì, nên giấu ba chi kia làm nó vô dụng. Cấu trúc chi và
 *       {@code ltree} vốn đã hiện trên phả đồ cho mọi thành viên.</li>
 *   <li><b>Khách và tài khoản chưa có {@code app_user} nhận 403</b>, <b>không</b> phải một danh
 *       sách rỗng. Danh sách rỗng nói dối rằng dòng họ này không có chi nào — và đồng thời xác
 *       nhận rằng màn này có thật.</li>
 *   <li><b>Chi ngoài phạm vi chỉ mang số liệu, không mang người và không mang việc dở dang.</b>
 *       Năm trường bị bỏ <b>hẳn</b> khỏi JSON. Null hoá không đủ: một khoá null vẫn công bố rằng
 *       trường ấy tồn tại và ở đâu đó có giá trị.</li>
 * </ol>
 *
 * <p>Trong năm trường bị cắt, {@code coordinatorName} là trường duy nhất thật sự <b>là dữ liệu
 * của một người còn sống</b>. Bốn trường còn lại ({@code openBatch} và ba con số của nó) bị cắt vì
 * lý do khác: chúng mô tả <i>việc đang làm dở của người khác</i> — bao nhiêu lỗi, bao nhiêu cặp
 * nghi trùng chưa quyết — và biến màn "còn thiếu gì" thành màn "ai đang sai nhiều". Xem ghi chú
 * "đây không phải bảng xếp hạng" ở {@link ImportBranchProgressDto}.</p>
 */
@Component
public class ImportProgressReadModel {

    private final ImportScopeGuard guard;
    private final ImportBranchDirectory branches;
    private final ImportBatchRepository batches;
    private final ImportIssueRepository issues;
    private final BranchProgressPort branchProgress;
    private final ImportReadModel readModel;

    public ImportProgressReadModel(ImportScopeGuard guard, ImportBranchDirectory branches,
                                   ImportBatchRepository batches, ImportIssueRepository issues,
                                   BranchProgressPort branchProgress, ImportReadModel readModel) {
        this.guard = guard;
        this.branches = branches;
        this.batches = batches;
        this.issues = issues;
        this.branchProgress = branchProgress;
        this.readModel = readModel;
    }

    @Transactional(readOnly = true)
    public List<ImportBranchProgressDto> tienDo(MemberScopeView caller) {
        List<ImportBranchDirectory.Chi> tatCa = branches.all();
        Set<UUID> khoa = new LinkedHashSet<>();
        for (ImportBranchDirectory.Chi chi : tatCa) {
            khoa.add(chi.id());
        }
        // Hai luot goi CSDL cho ca dong ho, khong phai hai luot moi chi.
        Map<UUID, ImportBatchRepository.TinhHinhChi> tinhHinh = batches.tongHopTheoChi(khoa);
        Map<UUID, BranchProgressPort.SoLieuChi> soLieu = branchProgress.theoChi(khoa);

        List<ImportBranchProgressDto> ketQua = new ArrayList<>(tatCa.size());
        for (ImportBranchDirectory.Chi chi : tatCa) {
            ketQua.add(dong(chi, tinhHinh.get(chi.id()), soLieu.get(chi.id()),
                    guard.canWriteOn(caller, chi.path())));
        }
        return List.copyOf(ketQua);
    }

    private ImportBranchProgressDto dong(ImportBranchDirectory.Chi chi,
                                         ImportBatchRepository.TinhHinhChi tinhHinh,
                                         BranchProgressPort.SoLieuChi soLieu,
                                         boolean trongPhamVi) {
        ImportBatch lo = tinhHinh == null ? null : tinhHinh.loDangMo();
        long daCo = soLieu == null ? 0L : soLieu.personsInTree();
        Integer mongDoi = soLieu == null ? null : soLieu.expectedPersons();

        // Dem tren lo dang mo; khong co lo nao thi khong co gi de dem. Con so nay khong bi cat theo
        // pham vi: no la mot phep dem ve NGUOI DA KHUAT, khong phai mot loi phan ve nguoi song.
        int thieuGio = lo == null ? 0 : issues.demTheoMa(lo.id(), IssueCode.IMP_MISSING_GIO);

        if (!trongPhamVi) {
            // null o day KHONG phai "khong co gia tri" ma la "khong duoc phat ra";
            // @JsonInclude(NON_NULL) bien no thanh vang mat han khoi than phan hoi.
            return new ImportBranchProgressDto(chi.id(), chi.name(), chi.path().value(),
                    null, buoc(tinhHinh, lo), daCo, mongDoi, null, null, null, null, thieuGio);
        }
        return new ImportBranchProgressDto(chi.id(), chi.name(), chi.path().value(),
                soLieu == null ? null : soLieu.coordinatorName(),
                buoc(tinhHinh, lo), daCo, mongDoi,
                lo == null ? null : new ImportBranchProgressDto.LoDangMo(lo.id(),
                        lo.status().name(), lo.createdAt()),
                lo == null ? 0 : lo.blockingCount(),
                lo == null ? 0 : lo.warningCount(),
                lo == null ? 0 : readModel.undecidedDuplicatePairs(lo.id()),
                thieuGio);
    }

    /**
     * Vị trí của chi trong quy trình.
     *
     * <p>Thứ tự kiểm là thứ tự ưu tiên: đã ghi được một lô vào phả là bậc cao nhất và không bị một
     * lô nháp mới kéo tụt xuống — chi ấy <b>đã</b> có dữ liệu trong phả, đó là sự thật bền hơn
     * trạng thái của lô đang mở.</p>
     *
     * <p>Không có {@code TEMPLATE_DOWNLOADED}: hệ thống chưa ghi lại lượt tải mẫu, và đoán ra một
     * bậc không đo được thì cả thang bậc mất giá trị. Thêm nó vào là việc của lúc có bảng ghi lượt
     * tải, không phải của một câu {@code CASE}.</p>
     */
    private static String buoc(ImportBatchRepository.TinhHinhChi tinhHinh, ImportBatch loDangMo) {
        if (tinhHinh != null && tinhHinh.soLoDaGhi() > 0) {
            return ImportBranchProgressDto.COMMITTED;
        }
        if (loDangMo == null) {
            return ImportBranchProgressDto.NOT_STARTED;
        }
        return switch (loDangMo.status()) {
            case DRAFT, PARSED -> ImportBranchProgressDto.UPLOADED;
            default -> ImportBranchProgressDto.RECONCILING;
        };
    }
}

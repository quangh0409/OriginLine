package vn.giapha.dataimport.api.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.api.rest.dto.ImportBatchDto;
import vn.giapha.dataimport.api.rest.dto.ImportDuplicatePairDto;
import vn.giapha.dataimport.api.rest.dto.ImportIssueDto;
import vn.giapha.dataimport.api.rest.dto.ImportRowDto;
import vn.giapha.dataimport.api.support.ImportBranchDirectory;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.IssueSeverity;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;
import vn.giapha.dataimport.domain.port.DuplicatePairRepository;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.ImportIssueRepository;
import vn.giapha.dataimport.domain.port.ImportRowRepository;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Đường <b>đọc</b> của tầng REST: nạp một lô và dựng mọi khung nhìn của nó.
 *
 * <h2>Vì sao gom về một lớp</h2>
 * Bốn endpoint đọc đều cần cùng ba thứ: lô, các dòng chờ theo số dòng, và danh sách lỗi. Để mỗi
 * controller tự nạp thì chúng sẽ dần lệch nhau về việc lỗi nào được cắt dữ liệu riêng tư — và một
 * endpoint quên cắt là một lỗ rò không để lại dấu vết nào trong log.
 *
 * <h2>Khoản nợ đã biết: đường đọc nên nằm ở {@code application}</h2>
 * Lớp này gọi thẳng ba port của {@code domain}. Chiều phụ thuộc vẫn đúng ({@code api → domain}),
 * nhưng chỗ đúng của nó là một query service ở {@code application} để lối vào GraphQL sau này dùng
 * lại được mà không phải đi qua HTTP. Đợt này chỉ gói {@code api} được mở — xem báo cáo bàn giao.
 *
 * <h2>Đếm hộ giao diện, ở đúng một nơi</h2>
 * {@code suspectDuplicateCount} đếm <b>cặp</b> chứ không đếm dòng cảnh báo: một dòng có thể bị nghi
 * trùng với nhiều hồ sơ và người đối chiếu phải quyết từng cặp. Đếm ở client thì con số này sẽ
 * lệch ngay khi danh sách lỗi được phân trang.
 *
 * <p>Từ V14, cả hai con số đọc thẳng từ {@code import_duplicate_pair}:
 * {@code suspectDuplicateCount} là tổng số cặp, {@code undecidedDuplicateCount} là số cặp còn
 * {@code PENDING} hoặc {@code DEFERRED}. Trước đó chúng <b>luôn bằng nhau</b> vì chưa có chỗ nào
 * ghi quyết định xuống, và hệ quả là cổng duyệt chặn mọi lô có dù chỉ một người nghi trùng.</p>
 */
@Component
public class ImportReadModel {

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final ImportIssueRepository issues;
    private final DuplicatePairRepository duplicatePairs;
    private final ImportBranchDirectory branches;

    public ImportReadModel(ImportBatchRepository batches, ImportRowRepository rows,
                           ImportIssueRepository issues, DuplicatePairRepository duplicatePairs,
                           ImportBranchDirectory branches) {
        this.batches = batches;
        this.rows = rows;
        this.issues = issues;
        this.duplicatePairs = duplicatePairs;
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public ImportBatch batchOrThrow(UUID batchId) {
        return batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
    }

    @Transactional(readOnly = true)
    public ImportBatchDto batchDto(ImportBatch batch) {
        return ImportDtoMapper.batch(batch, branches.byId(batch.branchId()).orElse(null),
                duplicatePairs.demTatCa(batch.id()), duplicatePairs.demChuaQuyet(batch.id()));
    }

    @Transactional(readOnly = true)
    public ImportBatchDto batchDto(UUID batchId) {
        return batchDto(batchOrThrow(batchId));
    }

    /**
     * Các lô của <b>một</b> chi đã biết, mới nhất trước — lối gọi thường gặp nhất của giao diện.
     */
    @Transactional(readOnly = true)
    public List<ImportBatchDto> batchDtos(UUID branchId, BatchStatus status, int page, int size) {
        return dung(batches.byBranch(branchId, status, page, size));
    }

    /**
     * Các lô của <b>một tập chi</b> — khi người gọi không nêu chi nào và ta phải trả về mọi chi
     * trong phạm vi của họ.
     *
     * <h2>Vì sao phạm vi đi xuống thành một tập khoá, không suy ra ở đây</h2>
     * Phân trang. Hỏi từng chi rồi gộp bốn danh sách <i>đã cắt trang</i> lại cho ra một trang
     * <b>sai</b>: trang 2 của phép gộp không phải là gộp của bốn trang 2. Vì vậy tầng api quyết
     * phạm vi trước, rồi cả tập đi xuống trong đúng một câu lệnh.
     */
    @Transactional(readOnly = true)
    public List<ImportBatchDto> batchDtos(Collection<UUID> branchIds, BatchStatus status, int page,
                                          int size) {
        return dung(batches.byBranches(branchIds, status, page, size));
    }

    /**
     * Dựng DTO cho một trang lô.
     *
     * <p>Tên chi nạp <b>một lượt</b> cho cả danh sách ({@code byIds}) thay vì tra từng lô — nếu
     * không, một trang 20 lô là 20 lượt hỏi CSDL cho một màn hình chỉ để hiện tên.</p>
     */
    private List<ImportBatchDto> dung(List<ImportBatch> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> cacChi = new ArrayList<>();
        for (ImportBatch batch : found) {
            cacChi.add(batch.branchId());
        }
        Map<UUID, ImportBranchDirectory.Chi> tenChi = branches.byIds(cacChi);

        List<ImportBatchDto> result = new ArrayList<>(found.size());
        for (ImportBatch batch : found) {
            result.add(ImportDtoMapper.batch(batch, tenChi.get(batch.branchId()),
                    duplicatePairs.demTatCa(batch.id()), duplicatePairs.demChuaQuyet(batch.id())));
        }
        return List.copyOf(result);
    }

    /**
     * Số <b>cặp</b> nghi trùng chưa ai quyết — đầu vào của cổng duyệt.
     *
     * <p>"Chưa quyết" gồm cả {@code PENDING} lẫn {@code DEFERRED}: nếu "hoãn" không nằm trong con
     * số này thì nó thành nút "cho tôi qua" và cả cơ chế dò trùng thành trang trí.</p>
     */
    @Transactional(readOnly = true)
    public int undecidedDuplicatePairs(UUID batchId) {
        return duplicatePairs.demChuaQuyet(batchId);
    }

    /**
     * Danh sách lỗi, lọc theo mức.
     *
     * <p>Lọc ở máy chủ chứ không để client lọc: hai nhóm khác nhau về <b>hệ quả</b>, và một giao
     * diện tải cả hai rồi tự chia sẽ sớm muộn hiện ra một con số gộp "15 vấn đề" — đúng cái làm
     * người nhập chuyển từ "làm được" sang "hỏng cả tệp rồi" rồi bấm bừa.</p>
     */
    @Transactional(readOnly = true)
    public List<ImportIssueDto> issueDtos(UUID batchId, IssueSeverity severity) {
        Map<Integer, PersonRow> byNo = rowsByNo(batchId);
        List<ImportIssueDto> result = new ArrayList<>();
        // byBatchWithId chu khong phai byBatch: giao dien can KHOA cua tung dong. Bo ghep
        // (sheet, rowNo, code, field) khong duy nhat, nen dung no lam khoa danh sach se lam bang
        // loi nhay cho moi lan kiem lai — va nguoi nhap dang doc dung bang do de sua tep.
        for (ImportIssueRepository.Luu luu : issues.byBatchWithId(batchId)) {
            if (severity == null || luu.issue().severity() == severity) {
                result.add(ImportDtoMapper.issue(luu.id(), luu.issue(), byNo));
            }
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<ImportRowDto> rowDtos(UUID batchId, PlannedAction action) {
        List<ImportRowDto> result = new ArrayList<>();
        for (PersonRow row : rows.personRows(batchId)) {
            if (action == null || row.plannedAction() == action) {
                result.add(ImportDtoMapper.row(row));
            }
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<ImportDuplicatePairDto> duplicateDtos(UUID batchId) {
        List<PersonRow> personRows = rows.personRows(batchId);
        Map<Integer, PersonRow> byNo = new LinkedHashMap<>();
        Map<String, PersonRow> byCode = new LinkedHashMap<>();
        for (PersonRow row : personRows) {
            byNo.put(row.rowNo(), row);
            if (row.externalCode() != null) {
                byCode.putIfAbsent(row.externalCode(), row);
            }
        }
        return ImportDtoMapper.duplicatePairs(duplicatePairs.byBatch(batchId), byNo, byCode);
    }

    /**
     * Một cặp sau khi vừa ghi quyết định — thứ giao diện thay thẳng vào dòng đang hiện.
     *
     * <p>Dựng lại qua đúng {@link ImportDtoMapper} như danh sách, không dựng tay: đó là chỗ duy
     * nhất biết luật "cặp đối chiếu vào phả thì chỉ trả khoá", và một lối dựng thứ hai sớm muộn sẽ
     * quên mất luật ấy.</p>
     */
    @Transactional(readOnly = true)
    public ImportDuplicatePairDto duplicateDto(UUID batchId, UUID pairId) {
        for (ImportDuplicatePairDto dto : duplicateDtos(batchId)) {
            if (dto.id().equals(pairId.toString())) {
                return dto;
            }
        }
        throw NotFoundException.of("ImportDuplicatePair", pairId);
    }

    private Map<Integer, PersonRow> rowsByNo(UUID batchId) {
        Map<Integer, PersonRow> byNo = new LinkedHashMap<>();
        for (PersonRow row : rows.personRows(batchId)) {
            byNo.put(row.rowNo(), row);
        }
        return byNo;
    }
}

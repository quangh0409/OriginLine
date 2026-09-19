package vn.giapha.dataimport.api.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import vn.giapha.dataimport.api.rest.dto.DuplicateEvidenceDto;
import vn.giapha.dataimport.api.rest.dto.DuplicatePartyDto;
import vn.giapha.dataimport.api.rest.dto.ImportBatchDto;
import vn.giapha.dataimport.api.rest.dto.ImportDuplicatePairDto;
import vn.giapha.dataimport.api.rest.dto.ImportIssueDto;
import vn.giapha.dataimport.api.rest.dto.ImportRowDto;
import vn.giapha.dataimport.api.support.ImportBranchDirectory;
import vn.giapha.dataimport.api.support.ImportDuplicatePolicy;
import vn.giapha.dataimport.domain.DuplicatePair;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.PersonRow;

/**
 * Dịch record của {@code domain} sang DTO của hợp đồng. Hàm thuần, không Spring, không CSDL.
 *
 * <p>Mọi phép <b>cắt dữ liệu riêng tư</b> đi qua {@link ImportIssueRedactor} hoặc nằm ngay trong
 * {@link #duplicatePairs}; không có lối nào khác dựng được {@code ImportIssueDto} hay
 * {@code ImportDuplicatePairDto} trong gói này, nên "quên cắt" không phải một lỗi viết được.</p>
 */
final class ImportDtoMapper {

    private ImportDtoMapper() {
    }

    // -------------------------------------------------------------------------------------
    // Lô
    // -------------------------------------------------------------------------------------

    /**
     * @param suspectPairs số <b>cặp</b> nghi trùng, không phải số dòng cảnh báo: một dòng có thể bị
     *        nghi trùng với nhiều hồ sơ, và người đối chiếu phải quyết từng cặp một
     */
    static ImportBatchDto batch(ImportBatch batch, ImportBranchDirectory.Chi chi, int suspectPairs,
                                int undecidedPairs) {
        return new ImportBatchDto(
                batch.id(),
                batch.branchId(),
                chi == null ? null : chi.name(),
                chi == null ? null : chi.path().value(),
                batch.sourceKind().name(),
                batch.status().name(),
                batch.originalFilename(),
                batch.fileSha256(),
                batch.fileSizeBytes(),
                batch.uploadedBy(),
                batch.createdAt(),
                batch.validatedAt(),
                batch.committedAt(),
                batch.warningsAcknowledgedAt(),
                batch.warningsAcknowledgedBy(),
                batch.committedBy(),
                batch.rolledBackAt(),
                batch.failureReason(),
                batch.personRowCount(),
                batch.marriageRowCount(),
                batch.blockingCount(),
                batch.warningCount(),
                batch.createCount(),
                batch.updateCount(),
                suspectPairs,
                undecidedPairs,
                batch.coTheDuyet() && undecidedPairs == 0,
                batch.version());
    }

    // -------------------------------------------------------------------------------------
    // Lỗi / cảnh báo
    // -------------------------------------------------------------------------------------

    /**
     * @param id khoá dòng {@code import_issue}; {@code null} khi vấn đề chưa được lưu (bước soát
     *        trước khi ghi dựng vấn đề trong bộ nhớ rồi mới ghi xuống)
     */
    static ImportIssueDto issue(UUID id, ImportIssue issue, Map<Integer, PersonRow> rowsByNo) {
        PersonRow row = issue.rowNo() == null ? null : rowsByNo.get(issue.rowNo());
        ImportIssueRedactor.NoiDung noiDung = ImportIssueRedactor.apply(issue, row);
        return new ImportIssueDto(
                id,
                issue.severity().name(),
                issue.code().name(),
                issue.sheet(),
                issue.rowNo(),
                issue.field(),
                row == null ? null : row.externalCode(),
                noiDung.message(),
                noiDung.context());
    }

    // -------------------------------------------------------------------------------------
    // Dòng chờ
    // -------------------------------------------------------------------------------------

    static ImportRowDto row(PersonRow row) {
        return new ImportRowDto(
                row.rowNo(),
                row.externalCode(),
                row.fullName(),
                row.tabooName(),
                row.posthumousName(),
                row.hanNomName(),
                row.gender() == null ? null : row.gender().name(),
                row.generation(),
                row.fatherCode(),
                row.motherCode(),
                row.parentRel() == null ? null : row.parentRel().name(),
                row.alive(),
                row.birthYear(),
                row.death() == null ? null : row.death().toString(),
                row.nativePlace(),
                row.nativePlaceCode(),
                row.heirOfCode(),
                row.heirType() == null ? null : row.heirType().name(),
                row.plannedAction().name(),
                row.resolvedPersonId());
    }

    // -------------------------------------------------------------------------------------
    // Cặp nghi trùng
    // -------------------------------------------------------------------------------------

    /**
     * Dựng danh sách cặp đối chiếu từ bảng quyết định {@code import_duplicate_pair} (V14).
     *
     * <h2>Nguồn đã đổi, và đổi vì một lý do cụ thể</h2>
     * Trước V14 danh sách này được dựng lại từ {@code import_issue.context} mỗi lần hỏi, nên nó
     * không mang được <b>trạng thái</b>: mọi cặp vĩnh viễn {@code PENDING}, và cổng duyệt vì thế
     * chặn mọi lô có dù chỉ một người nghi trùng. Nay nguồn là bảng quyết định — cùng một tập cặp,
     * cộng thêm câu trả lời của người và khoá chính ổn định để gửi quyết định lên.
     *
     * <h2>Hai loại cặp, hai mức dữ liệu — và đó là toàn bộ luật riêng tư ở đây</h2>
     * <ul>
     *   <li><b>Cặp trong tệp</b> ({@code kind = FILE}): hai bên đều là dòng người nhập vừa nộp nên
     *       trả đủ cả hai bên, kèm mảng {@code evidence} so từng ô.</li>
     *   <li><b>Cặp với phả</b> ({@code kind = TREE}): trả <b>đúng một khoá</b>, mảng
     *       {@code evidence} <b>rỗng</b>. Không trường nào bị gửi sang dạng ô rỗng — một ô rỗng vừa
     *       rò rỉ sự tồn tại của dữ liệu bị giấu, vừa dẫn người đối chiếu tới quyết định gộp sai.
     *       </li>
     * </ul>
     */
    static List<ImportDuplicatePairDto> duplicatePairs(List<DuplicatePair> pairs,
                                                       Map<Integer, PersonRow> rowsByNo,
                                                       Map<String, PersonRow> rowsByCode) {
        List<ImportDuplicatePairDto> result = new ArrayList<>(pairs.size());
        for (DuplicatePair cap : pairs) {
            PersonRow incoming = rowsByCode.get(cap.incomingCode());
            if (incoming == null) {
                incoming = rowsByNo.get(cap.rowNo());
            }
            result.add(pair(cap, incoming, rowsByCode));
        }
        return List.copyOf(result);
    }

    private static ImportDuplicatePairDto pair(DuplicatePair cap, PersonRow incoming,
                                               Map<String, PersonRow> rowsByCode) {
        boolean preselect = cap.score() >= ImportDuplicatePolicy.PRESELECT_MERGE_THRESHOLD;
        String id = cap.id() == null ? cap.pairKey() : cap.id().toString();

        if (cap.kind() == DuplicatePair.Kind.TREE) {
            // Ben kia da o trong pha: chi khoa, evidence rong. Xem javadoc phuong thuc goi.
            //
            // `hint` phai VANG MAT o nhanh nay va day la mot chot chan ro ri, khong phai mot thieu
            // sot: cau giai thich tu do cua ve trong tep mang gia tri truong ("trung nam sinh
            // 1975"), va ve trong pha co the la mot nguoi CON SONG o mot chi khac. Thu tra lai la
            // `signals` — danh sach NHAN tin hieu, tra loi "vi sao nghi" ma khong tiet lo "nguoi ay
            // la ai". Hai khoa CO Y khac ten de mot ben doc nham khoa thi nhan null chu khong nhan
            // du lieu cua muc kia.
            return new ImportDuplicatePairDto(id, cap.rowNo(), cap.score(), preselect,
                    cap.decision().name(), null, cap.signals(), cap.decidedBy(), cap.decidedAt(),
                    cap.note(), party(incoming), DuplicatePartyDto.tree(cap.existingPersonId()),
                    List.of());
        }
        // Ve trong tep: ca hai ben deu la thu nguoi nhap vua go, nen cau giai thich tu do di duoc.
        PersonRow other = rowsByCode.get(cap.existingCode());
        return new ImportDuplicatePairDto(id, cap.rowNo(), cap.score(), preselect,
                cap.decision().name(), cap.signals(), null, cap.decidedBy(), cap.decidedAt(),
                cap.note(), party(incoming), party(other), evidence(other, incoming));
    }

    /** Ảnh chụp một dòng trong tệp — toàn bộ là thứ người nhập vừa gõ. */
    private static DuplicatePartyDto party(PersonRow row) {
        if (row == null) {
            return null;
        }
        return new DuplicatePartyDto(DuplicatePartyDto.SOURCE_FILE, null, row.externalCode(),
                row.rowNo(), row.fullName(), row.tabooName(), row.generation(), row.birthYear(),
                row.death() == null ? null : row.death().toString(), row.fatherCode(),
                row.nativePlace());
    }

    /**
     * So từng ô giữa hai dòng <b>cùng nằm trong tệp</b>.
     *
     * <p>Đây là phép so chuỗi thuần, <b>không</b> phải bộ chấm điểm: điểm số là dữ liệu hiệu chỉnh
     * của bộ dò trùng bên {@code genealogy} và chỉ có một bản. Vì bộ dò chưa công bố bản tách điểm
     * theo từng tín hiệu nên {@code points} vắng mặt — giao diện không được tự cộng bù.</p>
     *
     * <p>Ô mà <b>cả hai</b> bên đều trống thì bỏ hẳn khỏi mảng thay vì trả một dòng hai ô rỗng:
     * một dấu hiệu không có dữ kiện nào không giúp gì cho người đối chiếu, chỉ làm loãng bảng.</p>
     */
    private static List<DuplicateEvidenceDto> evidence(PersonRow existing, PersonRow incoming) {
        if (existing == null || incoming == null) {
            return List.of();
        }
        List<DuplicateEvidenceDto> list = new ArrayList<>();
        add(list, "FULL_NAME", existing.fullName(), incoming.fullName());
        add(list, "TABOO_NAME", existing.tabooName(), incoming.tabooName());
        add(list, "GENERATION", text(existing.generation()), text(incoming.generation()));
        add(list, "BIRTH_YEAR", text(existing.birthYear()), text(incoming.birthYear()));
        add(list, "DEATH_LUNAR", text(existing.death()), text(incoming.death()));
        add(list, "FATHER", existing.fatherCode(), incoming.fatherCode());
        add(list, "ORIGIN_PLACE", existing.nativePlace(), incoming.nativePlace());
        return List.copyOf(list);
    }

    private static void add(List<DuplicateEvidenceDto> list, String field, String existingValue,
                            String incomingValue) {
        String a = blankToNull(existingValue);
        String b = blankToNull(incomingValue);
        if (a == null && b == null) {
            return;
        }
        String match;
        if (a == null) {
            match = DuplicateEvidenceDto.MISSING_IN_TREE;
        } else if (b == null) {
            match = DuplicateEvidenceDto.MISSING_IN_FILE;
        } else {
            match = a.equalsIgnoreCase(b) ? DuplicateEvidenceDto.SAME : DuplicateEvidenceDto.DIFFERENT;
        }
        list.add(new DuplicateEvidenceDto(field, match, null, a, b));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }
}

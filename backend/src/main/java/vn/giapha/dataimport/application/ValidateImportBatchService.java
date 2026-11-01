package vn.giapha.dataimport.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.application.rule.ImportRule;
import vn.giapha.dataimport.application.rule.ValidationContext;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.DuplicateMergePlan;
import vn.giapha.dataimport.domain.DuplicatePair;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;
import vn.giapha.dataimport.domain.port.DuplicatePairRepository;
import vn.giapha.dataimport.domain.port.ExternalRefPort;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.ImportIssueRepository;
import vn.giapha.dataimport.domain.port.ImportRowRepository;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Bước 2 của đường ống:</b> đối soát rồi chạy toàn bộ bộ luật trên một lô đang chờ.
 *
 * <h2>Chạy lại nhiều lần là hành vi bình thường, không phải ngoại lệ</h2>
 * Bước đối soát của quy trình là một vòng lặp: Trưởng chi kiểm, sửa tệp, tải lại, kiểm tiếp, cho
 * tới khi sạch. Vì vậy mọi thứ ở đây phải <b>luôn tính lại từ đầu</b> và <b>không tích luỹ</b>:
 * danh sách lỗi bị xoá sạch rồi ghi lại, kết quả đối soát CREATE/UPDATE cũng vậy. Nếu lỗi cũ còn
 * sót lại thì người nhập sửa xong vẫn thấy y nguyên danh sách và sẽ kết luận bộ kiểm hỏng.
 *
 * <h2>Đối soát trước, luật sau</h2>
 * Việc tra {@code person_external_ref} làm <b>một lần</b> ở đây, không phải trong từng luật: một
 * vòng gọi CSDL cho cả lô. Kết quả của nó — dòng nào là CREATE, dòng nào là UPDATE — chính là cơ
 * chế bảo đảm <b>tải lại không sinh người trùng</b>, và nhiều luật đọc lại nó.
 *
 * <h2>Bộ luật, rồi lượt soát trước khi ghi</h2>
 * Sau bộ luật (mâu thuẫn nội tại của tệp) là {@link ImportPreflight} — đúng lượt soát mà bước ghi
 * sẽ chạy lại: ba ca Hội đồng chưa chốt, vòng lặp trong thứ tự ghi, mã Hôn phối / kế tự không
 * phân giải được, nhóm gộp không áp dụng được. Nguyên tắc: <b>bước kiểm không được hứa điều bước
 * ghi không giữ</b>. Lô {@code VALIDATED} là lô mà bước ghi, với dữ liệu không đổi, sẽ nhận.
 *
 * <h2>Vẫn không ghi vào phả</h2>
 * Lô kết thúc ở {@link BatchStatus#VALIDATED} (0 lỗi chặn, chờ duyệt) hoặc
 * {@link BatchStatus#FAILED}. Bước ghi là một use case khác, và ranh giới ấy là thứ được kiểm bằng
 * cách đếm số dòng {@code person} trước và sau chứ không bằng lời hứa.
 */
@Service
public class ValidateImportBatchService {

    private static final Logger log = LoggerFactory.getLogger(ValidateImportBatchService.class);

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final ImportIssueRepository issues;
    private final ExternalRefPort externalRefs;
    private final DuplicatePairRepository duplicatePairs;
    private final List<ImportRule> rules;
    private final ImportPreflight preflight;

    public ValidateImportBatchService(ImportBatchRepository batches, ImportRowRepository rows,
                                      ImportIssueRepository issues, ExternalRefPort externalRefs,
                                      DuplicatePairRepository duplicatePairs,
                                      List<ImportRule> rules, ImportPreflight preflight) {
        this.batches = batches;
        this.rows = rows;
        this.issues = issues;
        this.externalRefs = externalRefs;
        this.duplicatePairs = duplicatePairs;
        this.rules = List.copyOf(rules);
        this.preflight = preflight;
    }

    /** Kết quả tóm tắt một lần kiểm — thứ giao diện hiện ngay đầu báo cáo đối soát. */
    public record KetQua(UUID batchId, BatchStatus status, int loiChan, int canhBao,
                         int seTao, int seCapNhat, List<ImportIssue> issues) {

        public boolean sach() {
            return loiChan == 0;
        }
    }

    @Transactional
    public KetQua validate(UUID batchId) {
        ImportBatch batch = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        if (batch.status().daChot()) {
            throw new IllegalStateException(
                    "Lô " + batchId + " đã ở trạng thái " + batch.status() + ", không kiểm lại được");
        }
        Instant batDau = Instant.now();
        batches.updateStatus(batchId, BatchStatus.VALIDATING, null);

        List<PersonRow> personRows = rows.personRows(batchId);
        List<MarriageRow> marriageRows = rows.marriageRows(batchId);

        // --- Doi soat: mot vong goi CSDL cho ca lo ---
        Set<String> maTrongTep = new LinkedHashSet<>();
        for (PersonRow row : personRows) {
            if (row.externalCode() != null) {
                maTrongTep.add(row.externalCode());
            }
        }
        Map<String, UUID> resolved = externalRefs.resolve(batch.branchId(), maTrongTep);
        Set<String> maDaCoCuaChi = externalRefs.codesOf(batch.branchId());
        // Tra TOAN CUC: ma nay dang thuoc chi nao. Day la phep kiem ranh gioi phan quyen, khong
        // phai phep kiem du lieu — xem RowShapeRule.
        Map<String, UUID> chiSoHuuMa = externalRefs.ownerBranchOf(maTrongTep);

        List<PersonRow> daDoiSoat = new ArrayList<>(personRows.size());
        for (PersonRow row : personRows) {
            UUID personId = resolved.get(row.externalCode());
            daDoiSoat.add(row.withResolution(personId,
                    personId == null ? PlannedAction.CREATE : PlannedAction.UPDATE));
        }
        rows.updateResolutions(batchId, daDoiSoat);

        // --- Chay bo luat ---
        ValidationContext ctx = new ValidationContext(batchId, batch.branchId(), daDoiSoat,
                marriageRows, resolved, maDaCoCuaChi, chiSoHuuMa);
        for (ImportRule rule : rules) {
            long t0 = System.nanoTime();
            rule.apply(ctx);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (ms > 1_000) {
                log.warn("Luat {} chay {} ms tren lo {} ({} dong)", rule.ten(), ms, batchId,
                        daDoiSoat.size());
            }
        }

        List<ImportIssue> found = ctx.issues();

        // --- Dong bo tap cap nghi trung, roi ap quyet dinh len ket qua doi soat ---
        // Thu tu hai buoc nay KHONG doi duoc, va ly do khong hien nhien: bo luat vua chay tren
        // `daDoiSoat` — ket qua doi soat THUAN TUY tu person_external_ref, chua co quyet dinh nao
        // chen vao. Nho vay tap cap dò ra o lan kiem nay giong het lan truoc khi khong co gi doi,
        // nen moi quyet dinh cu deu tim lai duoc cap cua no. Neu ap quyet dinh TRUOC roi moi chay
        // luat thi mot cap da duoc quyet "gop" se tu bien mat o lan kiem sau (dong ay da co chu
        // nen bo do loai chinh no ra), cap bi xoa, quyet dinh mat, va nguoi dung phai quyet lai
        // dung cai ho vua quyet — mot vong lap tu huy khong de lai dau vet nao trong log.
        List<DuplicatePair> capVuaDo = DuplicatePair.tuCanhBao(batchId, found, theoSoDong(daDoiSoat));
        int capMoi = duplicatePairs.dongBo(batchId, capVuaDo);
        DuplicateMergePlan keHoachGop =
                DuplicateMergePlan.cua(duplicatePairs.byBatch(batchId), daDoiSoat);
        List<PersonRow> sauQuyetDinh = keHoachGop.apLenDoiSoat(daDoiSoat);
        if (!keHoachGop.rong()) {
            // Ghi de ket qua doi soat: CREATE doi thanh UPDATE cho dong gop vao ho so da co, va
            // SKIP cho dong bi bo vi gop voi mot dong khac trong chinh tep nay.
            rows.updateResolutions(batchId, sauQuyetDinh);
        }

        // --- Luot soat truoc khi ghi, NGAY O BUOC KIEM ---
        // Cung mot bo voi buoc ghi (ImportPreflight), chay tren du lieu SAU KHI AP quyet dinh gop —
        // dung tap dong ma buoc ghi se thay. Truoc 24/09 luot nay chi chay luc ghi: man doi soat bao
        // "khong con loi phai sua", nguoi dung bam ghi, lo nhan 202 roi FAILED voi
        // IMP_UNDECIDED_INLAW_DOI (NT-TC-15). Chay no o day thi loi hien dung dong, dung o, nhu moi
        // loi chan khac, va nut ghi khong bao gio mo cho mot lo ma buoc ghi se tu choi.
        // Chay KE CA khi bo luat da co loi chan: nguoi nhap sua mot luot cho het, thay vi sua xong
        // 15 loi roi moi biet con 2 loi nua o lan tai len sau.
        List<ImportIssue> tatCa = preflight.ghepVao(found, batch.branchId(), keHoachGop,
                keHoachGop.apDung(sauQuyetDinh, marriageRows));
        issues.replaceAll(batchId, tatCa);

        int loiChan = (int) tatCa.stream().filter(ImportIssue::chan).count();
        int canhBao = tatCa.size() - loiChan;
        int seTao = (int) sauQuyetDinh.stream().filter(r -> r.plannedAction() == PlannedAction.CREATE).count();
        int seCapNhat = sauQuyetDinh.size() - seTao;

        // Van tay cua dung tap canh bao vua sinh ra, ghi cung luc voi cac con so. Neu lan kiem nay
        // sinh canh bao MOI thi van tay doi, va cai tick "da xem" cua lan truoc tu het hieu luc —
        // khong ai phai nho di xoa no. Xem WarningDigest.
        batches.updateCounters(batchId, daDoiSoat.size(), marriageRows.size(), loiChan, canhBao,
                seTao, seCapNhat, WarningDigest.cua(tatCa));
        BatchStatus status = loiChan == 0 ? BatchStatus.VALIDATED : BatchStatus.FAILED;
        batches.updateStatus(batchId, status,
                loiChan == 0 ? null : loiChan + " lỗi chặn cần sửa trong tệp");

        log.info("Kiem lo {} trong {} ms: {} loi chan, {} canh bao, {} se tao, {} se cap nhat,"
                        + " {} cap nghi trung ({} cap moi chua ai quyet)",
                batchId, Duration.between(batDau, Instant.now()).toMillis(), loiChan, canhBao,
                seTao, seCapNhat, capVuaDo.size(), capMoi);
        return new KetQua(batchId, status, loiChan, canhBao, seTao, seCapNhat, tatCa);
    }

    /** Tra dòng theo số dòng — bộ dò cặp cần nó để ghép cảnh báo về đúng mã của dòng. */
    private static Map<Integer, PersonRow> theoSoDong(List<PersonRow> rows) {
        Map<Integer, PersonRow> byNo = new LinkedHashMap<>();
        for (PersonRow row : rows) {
            byNo.put(row.rowNo(), row);
        }
        return byNo;
    }
}

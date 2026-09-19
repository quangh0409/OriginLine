package vn.giapha.dataimport.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.DuplicateDecision;
import vn.giapha.dataimport.domain.DuplicateMergePlan;
import vn.giapha.dataimport.domain.DuplicatePair;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;
import vn.giapha.dataimport.domain.port.DuplicatePairRepository;
import vn.giapha.dataimport.domain.port.ExternalRefPort;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.ImportRowRepository;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Ghi <b>quyết định của người</b> cho một cặp nghi trùng, rồi tính lại ngay hệ quả của nó.
 *
 * <h2>Vì sao lối gọi này là điều kiện để đường ống dùng được trên dữ liệu thật</h2>
 * Bảng {@code import_duplicate_pair} không tồn tại cho tới V14, nên mọi cặp vĩnh viễn
 * {@code PENDING} và {@code ImportCommitGate} chặn mọi lô có dù chỉ một người nghi trùng. Nói cách
 * khác: đường ống <b>chỉ chạy được trên tệp không có ai nghi trùng</b> — mà một dòng họ chép lại
 * từ nhiều cuốn sổ thì gần như luôn có, và đó chính là lý do bộ dò trùng tồn tại.
 *
 * <h2>Hai việc, không phải một</h2>
 * <ol>
 *   <li><b>Ghi lời khai</b>: quyết gì, ai quyết, lúc nào. Đây là thao tác có hệ quả <b>vĩnh
 *       viễn</b> lên phả — "gộp" làm một dòng biến mất hoặc làm một hồ sơ đã có bị viết đè.</li>
 *   <li><b>Tính lại kết quả đối soát ngay</b>. Không làm bước này thì màn xem trước vẫn hiện
 *       "sẽ tạo 400 người" sau khi người ta vừa gộp ba mươi dòng, và con số ấy là một lời nói dối
 *       đúng vào lúc người dùng đang cân nhắc có bấm ghi hay không.</li>
 * </ol>
 *
 * <h2>Tính lại từ GỐC, không sửa chồng lên kết quả cũ</h2>
 * Bước 2 dựng lại kết quả đối soát <b>từ {@code person_external_ref}</b> rồi mới áp toàn bộ kế
 * hoạch gộp hiện hành lên. Sửa chồng lên trạng thái cũ thì đổi ý không bao giờ lùi được: một dòng
 * đã bị đánh {@code SKIP} vì gộp sẽ nằm mãi ở {@code SKIP} kể cả sau khi người ta đổi quyết định
 * thành "hai người khác nhau" — và nó biến mất khỏi phả trong im lặng.
 */
@Service
public class DecideDuplicateService {

    private static final Logger log = LoggerFactory.getLogger(DecideDuplicateService.class);

    private final ImportBatchRepository batches;
    private final ImportRowRepository rows;
    private final DuplicatePairRepository pairs;
    private final ExternalRefPort externalRefs;

    public DecideDuplicateService(ImportBatchRepository batches, ImportRowRepository rows,
                                  DuplicatePairRepository pairs, ExternalRefPort externalRefs) {
        this.batches = batches;
        this.rows = rows;
        this.pairs = pairs;
        this.externalRefs = externalRefs;
    }

    /**
     * @param pair cặp sau khi đã ghi quyết định
     * @param conChuaQuyet số cặp còn chặn cổng duyệt ({@code PENDING} + {@code DEFERRED})
     * @param tongSoCap tổng số cặp của lô
     */
    public record KetQua(DuplicatePair pair, int conChuaQuyet, int tongSoCap) {
    }

    /**
     * Ghi một quyết định.
     *
     * @param actorUserId tài khoản bấm; đi vào {@code import_duplicate_pair.decided_by}
     * @throws NotFoundException cặp không có thật, hoặc không thuộc lô này
     * @throws IllegalStateException lô đã chốt — gửi lô mới, đừng sửa lô cũ
     * @throws IllegalArgumentException thiếu người bấm
     */
    @Transactional
    public KetQua quyet(UUID batchId, UUID pairId, DuplicateDecision decision, UUID actorUserId,
                        String note) {
        if (actorUserId == null) {
            // KHONG cho quyet vo danh. Gop hai ho so la thao tac co he qua vinh vien len pha; mot
            // dau thoi gian khong co chu thi nhat ky kiem toan chi noi duoc "luc 21:14 co ai do
            // gop hai cu lam mot" — cau vo dung dung vao luc can no nhat.
            throw new IllegalArgumentException(
                    "Quyet dinh nghi trung phai co chu: thieu app_user cua nguoi bam");
        }
        ImportBatch batch = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        if (batch.status().daChot()) {
            throw new IllegalStateException("Lo " + batchId + " da o trang thai " + batch.status()
                    + "; khong con gi de quyet");
        }
        DuplicatePair cap = pairs.byId(pairId)
                .orElseThrow(() -> NotFoundException.of("ImportDuplicatePair", pairId));
        if (!batchId.equals(cap.batchId())) {
            // KHONG tra 403: cap nay khong thuoc lo duoc neu trong URL, va URL la thu duy nhat da
            // qua phep kiem pham vi chi. Coi no nhu khong ton tai la cau tra loi dung.
            throw NotFoundException.of("ImportDuplicatePair", pairId);
        }

        pairs.ghiQuyetDinh(pairId, decision, actorUserId, Instant.now(), note);
        tinhLaiDoiSoat(batch);

        DuplicatePair sau = pairs.byId(pairId).orElseThrow();
        int conChuaQuyet = pairs.demChuaQuyet(batchId);
        log.info("app_user {} quyet cap {} cua lo {} la {} (ben kia: {}); con {} cap chua quyet",
                actorUserId, pairId, batchId, decision, cap.kind(), conChuaQuyet);
        return new KetQua(sau, conChuaQuyet, pairs.demTatCa(batchId));
    }

    /**
     * Dựng lại kết quả đối soát từ gốc rồi áp kế hoạch gộp hiện hành.
     *
     * <p>Cập nhật luôn hai con số {@code create_count} / {@code update_count} của lô: chúng là thứ
     * màn xem trước hiện ra, và để chúng lệch với {@code planned_action} của từng dòng thì người
     * dùng thấy hai câu trả lời khác nhau cho cùng một câu hỏi trên cùng một màn hình.</p>
     */
    private void tinhLaiDoiSoat(ImportBatch batch) {
        List<PersonRow> personRows = rows.personRows(batch.id());
        if (personRows.isEmpty()) {
            return;
        }
        Set<String> ma = new LinkedHashSet<>();
        for (PersonRow row : personRows) {
            if (row.externalCode() != null) {
                ma.add(row.externalCode());
            }
        }
        Map<String, UUID> daCo = externalRefs.resolve(batch.branchId(), ma);

        List<PersonRow> goc = new ArrayList<>(personRows.size());
        for (PersonRow row : personRows) {
            UUID personId = daCo.get(row.externalCode());
            goc.add(row.withResolution(personId,
                    personId == null ? PlannedAction.CREATE : PlannedAction.UPDATE));
        }

        List<PersonRow> sau = DuplicateMergePlan.cua(pairs.byBatch(batch.id()), goc).apLenDoiSoat(goc);
        rows.updateResolutions(batch.id(), sau);

        int seTao = (int) sau.stream().filter(r -> r.plannedAction() == PlannedAction.CREATE).count();
        batches.updateCounters(batch.id(), batch.personRowCount(), batch.marriageRowCount(),
                batch.blockingCount(), batch.warningCount(), seTao, sau.size() - seTao,
                batch.warningsDigest());
    }
}

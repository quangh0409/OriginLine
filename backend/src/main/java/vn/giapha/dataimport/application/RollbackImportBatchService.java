package vn.giapha.dataimport.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.BatchStatus;
import vn.giapha.dataimport.domain.CommitEntry;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.dataimport.domain.RollbackRefusedException;
import vn.giapha.dataimport.domain.port.CommitLedgerPort;
import vn.giapha.dataimport.domain.port.ExternalRefPort;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.PhaWritePort;
import vn.giapha.dataimport.domain.port.RollbackGuardPort;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Gỡ lại một lô vừa ghi</b> — rút toàn bộ những người và những cạnh mà lô ấy đã viết ra.
 *
 * <h2>Gỡ lô KHÁC xoá người, và đó là toàn bộ nội dung của lớp này</h2>
 * Xoá một nhân khẩu là việc thường ngày của Trưởng chi. Gỡ một lô thì khác hẳn: nó chạm
 * <b>hàng trăm</b> người cùng lúc và gỡ mọi cạnh của họ. Chừng nào chưa ai khác động vào, đó là một
 * phép hoàn tác sạch — và nó cứu được cả buổi làm việc khi Trưởng chi nhận ra mình vừa nhập nhầm
 * chi. Nhưng nếu đã có người bổ sung ảnh, sửa ngày giỗ, <b>treo thêm một đứa con</b>, hay nhận hồ
 * sơ của mình qua Zalo, thì gỡ lô sẽ âm thầm nuốt mất công của họ và để đứa bé mất cha trong phả
 * đồ. Vì thế lớp này <b>từ chối</b>, và từ chối là hành vi đúng.
 *
 * <h2>Cửa sổ gỡ, và điều kiện thật</h2>
 * <ul>
 *   <li><b>Thời hạn {@value ImportLimits#ROLLBACK_WINDOW_DAYS} ngày</b> kể từ
 *       {@code committed_at}. Đây chỉ là cái chặn cuối. Giá trị thật của lệnh gỡ nằm ở tuần đầu;
 *       sau ba tháng nó gần như chắc chắn sẽ từ chối, vì người trong họ đã động vào — và rút lại
 *       một lô ba tháng tuổi là tai hoạ thứ hai chồng lên tai hoạ thứ nhất.</li>
 *   <li><b>Chưa ai sửa hồ sơ</b> của bất kỳ người nào trong lô — so {@code person.version} với giá
 *       trị đã chụp trong sổ cái. Đây mới là điều kiện thật.</li>
 *   <li><b>Chưa có cạnh mới</b> treo vào người của lô.</li>
 *   <li><b>Chưa có dữ liệu phụ thuộc ngoài phả hệ</b>: tài khoản đã nhận hồ sơ, giỗ đã lên lịch,
 *       yêu cầu đính chính đang treo, chức danh Trưởng chi đã trỏ tới.</li>
 * </ul>
 *
 * <h2>Xoá mềm là luật tuyệt đối, kể cả ở đây</h2>
 * Không một người nào bị xoá cứng. Người của lô bị hạ cờ {@code is_deleted}, và <b>mọi cạnh của
 * họ được gỡ khỏi cả đồ thị lẫn bản chiếu</b>. Bước gỡ cạnh là bắt buộc chứ không phải dọn dẹp cho
 * đẹp: node đã xoá mềm vẫn được đi <b>xuyên qua</b> khi duyệt cây (để không làm đứt đường nối các
 * đời), nên để nguyên cạnh thì người vừa gỡ thành một <b>người cha ma</b> — vẫn ảnh hưởng danh
 * xưng và LCA, và không có gì báo.
 *
 * <h2>Người của lô mà lô KHÔNG tạo ra thì không đụng tới</h2>
 * Dòng {@code UPDATE} trỏ tới người đã có trong phả từ trước. Lô chỉ sửa hồ sơ của họ; gỡ lô
 * không được phép xoá họ. Sổ cái phân biệt hai nhóm bằng cột {@code person_created} — đây là lý do
 * bảng ấy tồn tại.
 */
@Service
public class RollbackImportBatchService {

    private static final Logger log = LoggerFactory.getLogger(RollbackImportBatchService.class);

    private final ImportBatchRepository batches;
    private final CommitLedgerPort ledger;
    private final RollbackGuardPort guard;
    private final PhaWritePort pha;
    private final ExternalRefPort externalRefs;

    public RollbackImportBatchService(ImportBatchRepository batches, CommitLedgerPort ledger,
                                      RollbackGuardPort guard, PhaWritePort pha,
                                      ExternalRefPort externalRefs) {
        this.batches = batches;
        this.ledger = ledger;
        this.guard = guard;
        this.pha = pha;
        this.externalRefs = externalRefs;
    }

    /** Kết quả một lần gỡ. */
    public record KetQua(UUID batchId, int daXoaMem, int daGoCanh, int daTraLaiMa) {
    }

    /**
     * Kiểm xem lô có gỡ được không mà <b>không</b> gỡ gì cả — dùng cho màn xác nhận.
     *
     * <p>Phải có, vì một hộp thoại "Bạn chắc chứ?" không nói gì về việc 12 người đã nhận hồ sơ là
     * một hộp thoại nói dối.</p>
     *
     * @return danh sách vướng mắc; rỗng nghĩa là gỡ được
     */
    public List<String> vuongMac(UUID batchId) {
        ImportBatch batch = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        return vuongMac(batch);
    }

    /**
     * Gỡ lô, trong <b>đúng một transaction</b>.
     *
     * @throws RollbackRefusedException đã quá cửa sổ, hoặc đã có dữ liệu phụ thuộc mới
     */
    @Transactional(timeout = 300)
    public KetQua rollback(UUID batchId, UUID actorUserId, String lyDo) {
        ImportBatch batch = batches.byId(batchId)
                .orElseThrow(() -> NotFoundException.of("ImportBatch", batchId));
        // Cung khoa tu van voi buoc ghi: khong ai duoc ghi mot lo khac cua chi nay trong luc ta
        // dang go, neu khong thi phep kiem "chua ai dong vao" vua chay xong da lac hau.
        batches.khoaChi(batch.branchId());

        List<String> vuong = vuongMac(batch);
        if (!vuong.isEmpty()) {
            log.warn("Tu choi go lo {}: {} vuong mac", batchId, vuong.size());
            throw new RollbackRefusedException(
                    "Không gỡ được lô này: " + vuong.size() + " vướng mắc. Dữ liệu đã có người"
                            + " khác động vào, nên gỡ sẽ làm mất công của họ. Phần còn lại phải"
                            + " sửa tay.", vuong);
        }

        List<CommitEntry> soCai = ledger.doc(batchId);
        Instant batDau = Instant.now();

        // Go canh TRUOC, xoa mem nguoi SAU. Nguoc lai thi trong mot nhip ngan van ton tai mot
        // nguoi da xoa mem ma con nguyen canh — dung trang thai "nguoi cha ma" ta dang tranh.
        int daGoCanh = 0;
        for (CommitEntry e : soCai) {
            if (e.kind() == CommitEntry.Kind.EDGE) {
                daGoCanh += pha.goCanh(e.edgeFrom(), e.edgeTo(), e.edgeType());
            }
        }

        int daXoaMem = 0;
        String ghiChu = "Gỡ lô nhập liệu " + batchId
                + (lyDo == null || lyDo.isBlank() ? "" : ": " + lyDo);
        for (CommitEntry e : soCai) {
            if (e.kind() == CommitEntry.Kind.PERSON && e.personCreated()) {
                pha.xoaMem(e.personId(), ghiChu);
                daXoaMem++;
            }
        }

        // Tra lai ma cho chi: khong tra thi tai lai dung tep ay se ra toan dong UPDATE tro toi
        // dung nhung nguoi vua bi xoa mem, va Truong chi nhin thay mot lo "khong tao ai ca".
        int daTraLaiMa = externalRefs.xoaTheoLo(batchId);
        ledger.xoa(batchId);
        batches.markRolledBack(batchId, actorUserId, lyDo);

        log.info("Da go lo {} trong {} ms: xoa mem {} nguoi, go {} canh, tra lai {} ma",
                batchId, Duration.between(batDau, Instant.now()).toMillis(), daXoaMem, daGoCanh,
                daTraLaiMa);
        return new KetQua(batchId, daXoaMem, daGoCanh, daTraLaiMa);
    }

    private List<String> vuongMac(ImportBatch batch) {
        List<String> vuong = new ArrayList<>();
        if (batch.status() != BatchStatus.COMMITTED || batch.committedAt() == null) {
            vuong.add("Lô này chưa từng được ghi vào phả (trạng thái " + batch.status()
                    + "), nên không có gì để gỡ.");
            return vuong;
        }
        Duration tuoi = Duration.between(batch.committedAt(), Instant.now());
        if (tuoi.toDays() >= ImportLimits.ROLLBACK_WINDOW_DAYS) {
            vuong.add("Lô đã ghi được " + tuoi.toDays() + " ngày, quá cửa sổ gỡ "
                    + ImportLimits.ROLLBACK_WINDOW_DAYS + " ngày. Sau ngần ấy thời gian, gỡ cả một"
                    + " lô là tai hoạ thứ hai chồng lên tai hoạ thứ nhất — phần sai phải sửa tay"
                    + " từng người.");
        }
        vuong.addAll(guard.hoSoDaBiSua(batch.id()));
        vuong.addAll(guard.canhMoiTreoVaoLo(batch.id(), batch.committedAt()));
        vuong.addAll(guard.phuThuocNgoaiPhaHe(batch.id()));
        return vuong;
    }
}

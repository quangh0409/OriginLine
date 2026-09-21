package vn.giapha.media.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.media.application.view.GcResult;
import vn.giapha.media.domain.MediaAsset;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.port.MediaAssetRepository;
import vn.giapha.media.domain.port.ObjectStoragePort;

/**
 * <b>Dọn tệp mồ côi</b> — điều kiện tiên quyết mà {@code V17} đã ghi sẵn, và là thứ giữ cho kho
 * không biến thành bãi rác vĩnh viễn.
 *
 * <h2>Ba nguồn rác, và cả ba đều là hành vi BÌNH THƯỜNG của người dùng</h2>
 * <ol>
 *   <li><b>Xin URL rồi bỏ ngang.</b> Người dùng mở màn soạn bài, chọn một tấm ảnh, rồi đổi ý (hay
 *       mất mạng, hay đóng tab). Hàng {@code PENDING} ở lại, và đôi khi cả một đối tượng đã tải
 *       xong nhưng chưa kịp xác nhận. Đây là nguồn <b>phổ biến nhất</b>, không phải ca hiếm.</li>
 *   <li><b>Bài bị gỡ.</b> {@code MediaLinkService.detachFromPost} cắt liên kết; tệp mất chủ.</li>
 *   <li><b>Đổi ảnh chân dung.</b> Ảnh cũ mất chủ ngay khi ảnh mới được gắn.</li>
 * </ol>
 *
 * <h2>Ai chạy nó — hai lối, cùng một mã</h2>
 * <ul>
 *   <li><b>Tự động:</b> {@code MediaGcScheduler} lúc <b>03:15 GMT+7</b> hằng đêm. Giờ ấy không
 *       phải chọn bừa — 01:30 đã là giờ sinh nhắc giỗ, và hai công việc đêm cùng giờ sẽ tranh nhau
 *       cùng một pool kết nối rồi cùng chậm; người vận hành sẽ thấy hai thứ hỏng và không biết cái
 *       nào kéo cái nào.</li>
 *   <li><b>Bằng tay:</b> {@code POST /api/v1/admin/media/gc}, <b>chỉ Quản trị hệ thống</b>. Có nó
 *       vì cùng lý do mà {@code POST /api/v1/admin/reminders/dispatch} tồn tại: một đường ống chỉ
 *       chạy được lúc 3 giờ sáng là một đường ống không kiểm thử được, và bài kiểm tích hợp của
 *       đợt này lái đúng lối ấy qua HTTP.</li>
 * </ul>
 *
 * <h2>Kho lỗi thì KHÔNG đánh dấu đã dọn</h2>
 * Xoá byte không thành công ⇒ hàng giữ nguyên trạng thái cũ và lượt sau thử lại. Đánh dấu
 * {@code PURGED} trong khi byte vẫn nằm trên kho là cách tạo ra một tệp <b>không ai còn biết là
 * mình đang giữ</b> — thứ tệ nhất có thể có dưới Nghị định 13/2023, vì nó vừa tồn tại vừa vô hình
 * với mọi báo cáo.
 */
@Service
public class MediaGcService {

    private static final Logger log = LoggerFactory.getLogger(MediaGcService.class);

    private static final String ENTITY = "MediaAsset";

    private final MediaAssetRepository assets;
    private final ObjectStoragePort storage;
    private final AuditTrailService audit;
    private final Clock clock;

    public MediaGcService(MediaAssetRepository assets, ObjectStoragePort storage,
                          AuditTrailService audit, Clock clock) {
        this.assets = assets;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Một lượt dọn.
     *
     * <p><b>Không kiểm quyền ở đây</b> — cố ý. Lớp này được gọi từ hai chỗ đã tự kiểm: bộ lập lịch
     * (chạy không có người dùng nào) và {@code MediaAdminController} (đòi Quản trị hệ thống). Đặt
     * thêm một phép kiểm ở đây sẽ làm lối lập lịch phải giả một danh tính, và một danh tính giả
     * trong mã sản xuất là thứ sớm muộn bị dùng vào việc khác.</p>
     */
    @Transactional
    public GcResult sweep() {
        Instant now = clock.instant();
        int failures = 0;

        // --- Nguon 1: phieu qua han ---
        List<MediaAsset> expired = assets.findExpiredPending(
                now.minus(MediaLimits.PENDING_GRACE), MediaLimits.GC_BATCH_SIZE);
        int expiredDone = 0;
        for (MediaAsset asset : expired) {
            if (purge(asset, now, "phieu qua han chua xac nhan")) {
                expiredDone++;
            } else {
                failures++;
            }
        }

        // --- Nguon 2+3: tep da xac nhan nhung mat chu ---
        List<MediaAsset> orphans = assets.findOrphanReady(
                now.minus(MediaLimits.ORPHAN_GRACE), MediaLimits.GC_BATCH_SIZE);
        int orphanDone = 0;
        for (MediaAsset asset : orphans) {
            if (purge(asset, now, "mo coi: khong con ban ghi nao mang tep nay")) {
                orphanDone++;
            } else {
                failures++;
            }
        }

        GcResult result = new GcResult(expiredDone, orphanDone, failures);
        if (result.total() > 0 || failures > 0) {
            log.info("Don tep: {} phieu qua han, {} tep mo coi, {} loi kho",
                    expiredDone, orphanDone, failures);
        } else {
            log.debug("Don tep: khong co gi de don");
        }
        return result;
    }

    /**
     * Xoá byte rồi đánh dấu. Trả {@code false} khi kho từ chối — hàng giữ nguyên để lượt sau thử
     * lại.
     *
     * <p>Một đối tượng <b>không tồn tại</b> trên kho không phải lỗi: {@code removeObject} của S3
     * là idempotent, nên ca "phiếu được phát rồi người dùng chưa hề tải gì lên" đi qua đây sạch sẽ
     * và hàng vẫn được đóng lại đúng.</p>
     */
    private boolean purge(MediaAsset asset, Instant now, String why) {
        try {
            storage.delete(asset.objectKey());
        } catch (RuntimeException ex) {
            log.warn("Khong xoa duoc doi tuong cua tep {} ({}); giu nguyen trang thai de thu lai",
                    asset.id(), why, ex);
            return false;
        }
        asset.markPurged(now);
        assets.save(asset);
        audit.record(ENTITY, asset.id().toString(), AuditAction.SOFT_DELETE, "Don tep: " + why);
        return true;
    }
}

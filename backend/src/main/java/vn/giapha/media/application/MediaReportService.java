package vn.giapha.media.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.media.application.view.MediaReportView;
import vn.giapha.media.domain.MediaAsset;
import vn.giapha.media.domain.MediaConflictException;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.MediaLink;
import vn.giapha.media.domain.MediaOwnerType;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.MediaReport;
import vn.giapha.media.domain.ReportReason;
import vn.giapha.media.domain.ReportStatus;
import vn.giapha.media.domain.event.PersonAvatarRemovedEvent;
import vn.giapha.media.domain.port.MediaAssetRepository;
import vn.giapha.media.domain.port.MediaLinkRepository;
import vn.giapha.media.domain.port.MediaReportRepository;
import vn.giapha.media.domain.port.ObjectStoragePort;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Đường báo gỡ</b> — nửa còn lại của quyết định "ảnh đi theo quyền của bài".
 *
 * <h2>Vì sao lớp này là điều kiện, không phải tính năng thêm</h2>
 * Chủ dự án đã chốt: bài đã đăng thì ảnh hiện với <i>mọi</i> thành viên, không đi qua bộ lọc nhóm
 * trường của từng người có mặt trong ảnh. Đó là một lựa chọn hợp lý cho một trang thông tin dòng
 * họ — nhưng nó chuyển rủi ro từ hệ thống sang con người: tấm ảnh tập thể chụp ngày giỗ có thể
 * lọt mặt một đứa trẻ, một tờ giấy mời có số điện thoại, một người vừa xin ẩn hồ sơ. Không có lối
 * báo và lối gỡ thì người trong họ không có cách nào ngoài gọi điện cho ai đó biết dùng máy tính.
 * <b>Nút ấy là thứ làm quyết định kia an toàn</b>, và điều này được ghi ở cả {@code V19},
 * {@code PostMediaAccessPort} lẫn đây, vì cả ba sẽ phải xét lại cùng lúc nếu một ngày nó bị bỏ.
 *
 * <h2>Gỡ là gỡ THẬT, và gỡ NGAY</h2>
 * Khác mọi đường xoá khác trong sản phẩm này, {@link #takeDown} không có ân hạn:
 * {@code MediaLimits.ORPHAN_GRACE} (24 giờ) áp cho tệp mất chủ vì bài bị gỡ, <i>không</i> áp cho
 * tệp bị gỡ vì vi phạm. Một tấm ảnh đang làm lộ dữ liệu cá nhân phải ngừng được phục vụ trong
 * cùng giao dịch, không phải sau một đêm. Hàng {@code media_asset} ở lại với {@code PURGED} làm
 * sổ — ai gỡ, lúc nào, theo đơn nào.
 *
 * <h2>Lời người báo KHÔNG đi vào {@code audit_log}</h2>
 * {@code note} là văn bản tự do và người ta sẽ viết đúng thứ họ đang muốn giấu ("ảnh này có số
 * điện thoại của mẹ tôi"). Nhật ký ghi <i>mã lý do</i> và <i>khoá đơn</i>, không ghi lời. Đây là
 * cùng nguyên tắc mà {@code V5} đã đặt cho hành động {@code ANONYMIZE}: ghi tên trường, không ghi
 * giá trị. {@code SensitiveFieldRedactor} là lưới cuối, nhưng lưới cuối không phải là chỗ để đặt
 * một bất biến.
 */
@Service
public class MediaReportService {

    private static final Logger log = LoggerFactory.getLogger(MediaReportService.class);

    private static final String ENTITY = "MediaReport";
    private static final String ENTITY_ASSET = "MediaAsset";

    /** Trần một lượt đọc hàng đợi. Lọc phạm vi chạy ở Java nên phải có chặn trên. */
    private static final int MAX_QUEUE = 200;

    private final MediaReportRepository reports;
    private final MediaAssetRepository assets;
    private final MediaLinkRepository links;
    private final ObjectStoragePort storage;
    private final MediaAccessGuard access;
    private final AuditTrailService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public MediaReportService(MediaReportRepository reports, MediaAssetRepository assets,
                              MediaLinkRepository links, ObjectStoragePort storage,
                              MediaAccessGuard access, AuditTrailService audit,
                              ApplicationEventPublisher events, Clock clock) {
        this.reports = reports;
        this.assets = assets;
        this.links = links;
        this.storage = storage;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // =====================================================================================
    // Người trong họ báo
    // =====================================================================================

    /**
     * Mở một đơn báo gỡ.
     *
     * <p><b>Chỉ báo được thứ mình nhìn thấy.</b> Phép kiểm {@code canView} ở đây không phải thủ
     * tục: thiếu nó thì điểm cuối này thành công cụ dò — gửi một dãy {@code mediaId} và đọc mã trả
     * về để biết tệp nào có thật.</p>
     */
    @Transactional
    public MediaReportView report(UUID mediaId, ReportReason reason, String note) {
        MemberScopeView caller = access.requireProvisioned();
        MediaAsset asset = servableAsset(mediaId);
        MediaLink link = ownerOf(asset);
        if (!access.canView(link.ownerType(), link.ownerId())) {
            // 404, khong phai 403 — tra 403 la tu xac nhan tep do co that.
            throw new NotFoundException(MediaProblemCodes.NOT_FOUND,
                    "Khong tim thay tep dinh kem nao co ma nay.");
        }
        Optional<MediaReport> existing = reports.findOpenBy(mediaId, caller.appUserId());
        if (existing.isPresent()) {
            throw new MediaConflictException(MediaProblemCodes.REPORT_DUPLICATE,
                    "Ban da bao tep nay roi va don van dang cho nguoi duyet xem."
                            + " Bam them lan nua khong lam no duoc xu som hon.");
        }

        MediaReport saved;
        try {
            saved = reports.save(MediaReport.open(mediaId, reason, note, caller.appUserId()));
        } catch (IllegalArgumentException ex) {
            throw new DomainException(MediaProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        // KHONG ghi `note`. Xem javadoc cua lop.
        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE,
                "Bao go tep " + mediaId + ", ly do " + reason);
        log.info("Don bao go {} cho tep {} (ly do {}) boi app_user {}",
                saved.id(), mediaId, reason, caller.appUserId());
        return toView(saved, asset);
    }

    // =====================================================================================
    // Người duyệt
    // =====================================================================================

    /** Hàng đợi báo gỡ <b>trong phạm vi chi</b> của người gọi. */
    @Transactional(readOnly = true)
    public List<MediaReportView> queue(int limit) {
        MemberScopeView caller = access.requireProvisioned();
        int size = Math.clamp(limit <= 0 ? 20 : limit, 1, MAX_QUEUE);
        List<MediaReportView> visible = new ArrayList<>();
        for (MediaReport report : reports.findByStatus(ReportStatus.OPEN, MAX_QUEUE)) {
            Optional<MediaAsset> asset = assets.findById(report.mediaId());
            if (asset.isEmpty()) {
                continue;
            }
            Optional<MediaLink> link = links.findByMediaId(report.mediaId());
            // Tep da mat chu (bai bi go truoc khi don duoc xu): khong con chi nao de so pham vi,
            // nen chi vai toan dong ho thay. Dong, khong mo.
            MediaOwnerType ownerType = link.map(MediaLink::ownerType).orElse(null);
            UUID ownerId = link.map(MediaLink::ownerId).orElse(null);
            boolean allowed = ownerType != null && access.canReview(caller, ownerType, ownerId);
            if (!allowed && !caller.clanWide()) {
                continue;
            }
            visible.add(toView(report, asset.get()));
            if (visible.size() >= size) {
                break;
            }
        }
        return visible;
    }

    /**
     * Gỡ: cắt liên kết, xoá byte khỏi kho <b>ngay</b>, đóng đơn.
     *
     * <p>Thứ tự có chủ ý — cắt liên kết trước, xoá byte sau. Nếu kho lỗi thì liên kết đã cắt nên
     * tệp không còn phục vụ cho ai, và đường dọn sẽ thử xoá byte lại ở lượt sau. Làm ngược lại
     * (xoá byte trước) mà giao dịch rollback sẽ để một liên kết trỏ vào một đối tượng đã biến mất
     * — đúng cái ô ảnh vỡ mà cả thiết kế này dựng lên để tránh.</p>
     */
    @Transactional
    public MediaReportView takeDown(UUID reportId, String resolution) {
        MemberScopeView caller = access.requireProvisioned();
        MediaReport report = openReport(reportId);
        MediaAsset asset = assets.findById(report.mediaId())
                .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                        "Khong tim thay tep cua don nay."));
        MediaLink link = links.findByMediaId(asset.id()).orElse(null);

        requireReviewAuthority(caller, link);
        if (report.reportedBy().equals(caller.appUserId())) {
            // Cung luat "khong tu duyet viec cua minh" da co o luong dinh chinh va luong bai viet.
            throw new ForbiddenException("SELF_REVIEW_FORBIDDEN",
                    "Nguoi bao khong tu duyet don cua minh. Hay de mot nguoi khac trong hoi dong"
                            + " xem giup.");
        }

        Instant now = clock.instant();
        if (link != null) {
            links.deleteByMediaId(asset.id());
        }
        boolean storageOk = true;
        try {
            storage.delete(asset.objectKey());
        } catch (RuntimeException ex) {
            storageOk = false;
            log.error("Khong xoa duoc doi tuong cua tep {} khi go theo don {} — lien ket DA cat nen"
                    + " tep khong con phuc vu cho ai; duong don se thu lai", asset.id(), reportId, ex);
        }
        if (storageOk) {
            asset.markPurged(now);
            assets.save(asset);
        }
        report.takeDown(caller.appUserId(), resolution, now);
        MediaReport saved = reports.save(report);

        if (link != null && link.ownerType() == MediaOwnerType.PERSON_AVATAR) {
            // genealogy phai xoa con tro person.avatar_key, khong thi ho so ay hien mot o anh vo
            // mai mai. Su kien la loi nguoc DUY NHAT ma kien truc cho phep — xem
            // PersonAvatarRemovedEvent.
            events.publishEvent(new PersonAvatarRemovedEvent(link.ownerId(), asset.id()));
        }

        audit.record(ENTITY, saved.id().toString(), AuditAction.APPROVE,
                "Go tep " + asset.id() + " theo don bao, ly do " + saved.reason());
        audit.record(ENTITY_ASSET, asset.id().toString(), AuditAction.SOFT_DELETE,
                "Go theo don bao " + saved.id());
        log.info("Go tep {} theo don {} boi app_user {}", asset.id(), reportId, caller.appUserId());
        return toView(saved, asset);
    }

    /** Giữ nguyên tệp — nhưng vẫn ký tên. Người báo có quyền biết ai đã quyết và vì sao. */
    @Transactional
    public MediaReportView dismiss(UUID reportId, String resolution) {
        MemberScopeView caller = access.requireProvisioned();
        MediaReport report = openReport(reportId);
        MediaAsset asset = assets.findById(report.mediaId())
                .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                        "Khong tim thay tep cua don nay."));
        requireReviewAuthority(caller, links.findByMediaId(asset.id()).orElse(null));

        report.dismiss(caller.appUserId(), resolution, clock.instant());
        MediaReport saved = reports.save(report);
        audit.record(ENTITY, saved.id().toString(), AuditAction.REJECT,
                "Giu nguyen tep " + asset.id() + " sau khi xem don bao");
        return toView(saved, asset);
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private void requireReviewAuthority(MemberScopeView caller, MediaLink link) {
        if (link == null) {
            // Tep da mat chu: khong co chi nao de so. Chi vai toan dong ho xu duoc — dung luat
            // "chi rong khong phai chi cong cong".
            if (!caller.clanWide()) {
                throw new ForbiddenException(MediaProblemCodes.BRANCH_SCOPE_VIOLATION,
                        "Tep nay khong con gan vao ban ghi nao nen khong xac dinh duoc chi."
                                + " Chi vai toan dong ho xu ly duoc don nay.");
            }
            return;
        }
        access.requireReviewer(caller, link.ownerType(), link.ownerId());
    }

    private MediaReport openReport(UUID reportId) {
        MediaReport report = reports.findById(reportId)
                .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                        "Khong tim thay don bao go nao co ma nay."));
        if (report.status() != ReportStatus.OPEN) {
            throw new MediaConflictException(MediaProblemCodes.REPORT_CLOSED,
                    "Don nay da duoc xu ly roi (" + report.status() + ")."
                            + " Hay tai lai danh sach de xem trang thai hien tai.");
        }
        return report;
    }

    private MediaAsset servableAsset(UUID mediaId) {
        MediaAsset asset = assets.findById(mediaId)
                .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                        "Khong tim thay tep dinh kem nao co ma nay."));
        if (!asset.isServable()) {
            throw new MediaConflictException(MediaProblemCodes.REPORT_CLOSED,
                    "Tep nay da bi go roi.");
        }
        return asset;
    }

    private MediaLink ownerOf(MediaAsset asset) {
        return links.findByMediaId(asset.id())
                .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                        "Tep nay khong gan vao ban ghi nao nen khong ai dang nhin thay no."));
    }

    private MediaReportView toView(MediaReport report, MediaAsset asset) {
        // Nguoi duyet phai NHIN THAY thu minh dang quyet. Tep da go thi khong con URL nao de ky.
        String url = asset.isServable()
                ? storage.presignGet(asset.objectKey(), MediaLimits.VIEW_URL_TTL) : null;
        return new MediaReportView(report.id(), report.mediaId(), asset.kind().name(), url,
                report.reason().name(), report.note(), report.reportedBy(),
                report.status().name(), report.reviewedBy(), report.reviewedAt(),
                report.resolutionNote(), report.createdAt(), report.version());
    }
}

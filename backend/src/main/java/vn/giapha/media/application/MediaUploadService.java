package vn.giapha.media.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.media.application.view.MediaTicketView;
import vn.giapha.media.domain.MediaAsset;
import vn.giapha.media.domain.MediaConflictException;
import vn.giapha.media.domain.MediaKind;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.MediaRejectedException;
import vn.giapha.media.domain.MediaSignature;
import vn.giapha.media.domain.VideoHeaderProbe;
import vn.giapha.media.domain.port.MediaAssetRepository;
import vn.giapha.media.domain.port.ObjectStoragePort;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Hai bước của đường tải tệp lên: <b>xin phiếu</b> rồi <b>xác nhận</b>.
 *
 * <h2>Tệp KHÔNG đi qua backend — và đây là quyết định kiến trúc chính của đợt</h2>
 * Xem javadoc của {@code ObjectStoragePort} cho lý do đầy đủ. Tóm tắt: một video 100 MiB đi xuyên
 * Tomcat chiếm một luồng xử lý suốt thời gian tải, và trần {@code spring.servlet.multipart} 10 MB
 * của dự án <b>cố ý</b> khớp với {@code ImportLimits.MAX_FILE_BYTES} nên không được nâng.
 *
 * <h2>Ba lớp chặn, và chỉ lớp thứ ba mới có thẩm quyền</h2>
 * <ol>
 *   <li><b>Lúc xin phiếu</b> — từ chối nếu số byte client <i>khai</i> đã vượt trần. Rẻ, và tiết
 *       kiệm băng thông cho người trung thực: họ biết ngay, trước khi tải một byte nào. Nhưng nó
 *       <b>không</b> là phép kiểm: client nào cũng khai được số 1.</li>
 *   <li><b>Lúc xác nhận</b> — {@code statObject} cho kích thước <i>thật</i>. Đây là lớp bắt người
 *       nói dối. Vượt trần ⇒ xoá đối tượng khỏi kho ngay rồi mới trả lỗi, để một kẻ lặp lại thao
 *       tác này không tích được rác.</li>
 *   <li><b>Chữ ký byte</b> — đọc {@value MediaSignature#SNIFF_BYTES} byte đầu <i>ngược về từ
 *       kho</i>. Không tin đuôi tệp, không tin {@code Content-Type} — mà ở đường này backend còn
 *       không hề thấy hai thứ đó, vì tệp đi thẳng lên MinIO.</li>
 * </ol>
 *
 * <p><b>Bất biến:</b> một tệp chỉ được coi là có thật sau khi backend tự nhìn thấy nó trên kho.
 * Hàng {@code PENDING} không phải bằng chứng gì cả. Xem {@code MediaStatus}.</p>
 */
@Service
public class MediaUploadService {

    private static final Logger log = LoggerFactory.getLogger(MediaUploadService.class);

    private static final String ENTITY = "MediaAsset";

    /**
     * Tiền tố khoá đối tượng, chia theo tháng.
     *
     * <p>Chia thư mục theo {@code yyyy/MM} không để cho đẹp: nó làm câu {@code mc ls} lúc đi điều
     * tra sự cố có giới hạn, và làm chính sách vòng đời phía kho (nếu một ngày cần) viết được theo
     * tiền tố. Phần còn lại của khoá là một {@link UUID} ngẫu nhiên — <b>không</b> có tên tệp
     * người dùng đặt trong đó: tên tệp là dữ liệu người dùng, hay chứa tên người thật
     * ("anh-cuoi-bac-Ba-0912345678.jpg"), và một khoá đoán được là một lối đọc không qua kiểm
     * quyền nếu chính sách bucket có ngày bị nới.</p>
     */
    private static final DateTimeFormatter KEY_PREFIX =
            DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final MediaAssetRepository assets;
    private final ObjectStoragePort storage;
    private final MediaAccessGuard access;
    private final AuditTrailService audit;
    private final Clock clock;

    public MediaUploadService(MediaAssetRepository assets, ObjectStoragePort storage,
                              MediaAccessGuard access, AuditTrailService audit, Clock clock) {
        this.assets = assets;
        this.storage = storage;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    // =====================================================================================
    // Bước 1 — xin phiếu
    // =====================================================================================

    /**
     * Phát một URL {@code PUT} đã ký.
     *
     * @param kind              loại tệp người dùng <i>định</i> tải lên. Đây là <b>khai báo</b>, và
     *                          nó chỉ quyết định trần nào được áp; loại thật do chữ ký byte quyết
     *                          ở bước xác nhận, và lệch nhau thì từ chối
     * @param declaredSizeBytes số byte client khai. Xem lớp chặn thứ nhất ở javadoc của lớp
     */
    @Transactional
    public MediaTicketView issueTicket(MediaKind kind, long declaredSizeBytes) {
        MemberScopeView caller = access.requireProvisioned();
        if (kind == null) {
            throw new DomainException(MediaProblemCodes.VALIDATION_FAILED,
                    "Phai noi ro tep la anh (IMAGE) hay video (VIDEO).");
        }
        long cap = MediaLimits.maxBytes(kind);
        if (declaredSizeBytes <= 0) {
            throw new DomainException(MediaProblemCodes.VALIDATION_FAILED,
                    "Phai khai kich thuoc tep (sizeBytes) de he thong tu choi som thay vi de ban"
                            + " tai het roi moi bao loi.");
        }
        if (declaredSizeBytes > cap) {
            // Tu choi TRUOC KHI ton bang thong — day la ca "vuot tran bi tu choi som".
            log.info("Tu choi phat phieu: app_user {} khai {} byte cho {} (tran {})",
                    caller.appUserId(), declaredSizeBytes, kind, cap);
            throw new MediaRejectedException(MediaProblemCodes.MEDIA_TOO_LARGE, quaTran(kind, cap));
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(MediaLimits.UPLOAD_TICKET_TTL);
        UUID id = UUID.randomUUID();
        String objectKey = KEY_PREFIX.format(now) + "/" + UUID.randomUUID();

        MediaAsset saved = assets.save(MediaAsset.ticket(
                id, objectKey, storage.bucket(), kind, caller.appUserId(), expiresAt));

        String uploadUrl = storage.presignPut(objectKey, MediaLimits.UPLOAD_TICKET_TTL);
        log.info("Phat phieu tai len {} ({}) cho app_user {}, han {}",
                saved.id(), kind, caller.appUserId(), expiresAt);

        return new MediaTicketView(uploadUrl, saved.id(), objectKey, expiresAt, cap,
                kind == MediaKind.VIDEO ? MediaLimits.MAX_VIDEO_SECONDS : null,
                acceptedTypes(kind));
    }

    // =====================================================================================
    // Bước 2 — xác nhận
    // =====================================================================================

    /**
     * Xác nhận rằng tệp đã nằm trên kho, và kiểm nó.
     *
     * <p>Phương thức này <b>chạy ngoài giao dịch cho phần đọc kho</b> theo nghĩa thực tế: nó gọi
     * MinIO hai tới ba lượt trước khi ghi. Giữ nguyên một giao dịch bao trùm là chấp nhận được ở
     * đây vì các lượt gọi ấy đều là đọc vài trăm KB và đều có hạn chờ của okhttp — nhưng nếu một
     * ngày cần gọi thêm thứ gì chậm hơn, hãy tách phần kiểm ra ngoài {@code @Transactional} trước
     * khi thêm, chứ đừng nới hạn chờ.</p>
     *
     * @param alt chữ thay ảnh — <b>bắt buộc với ảnh</b> (WCAG 2.2 AA 1.1.1), tuỳ chọn với video
     */
    @Transactional
    public MediaAsset confirm(UUID mediaId, String alt) {
        MemberScopeView caller = access.requireProvisioned();
        MediaAsset asset = assets.findById(mediaId)
                .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                        "Khong tim thay phieu tai len nao co ma nay."));

        // Ai xin phieu thi nguoi ay xac nhan. Chan loi "doan ma phieu cua nguoi khac roi gan chu
        // thay anh cua minh vao".
        if (!asset.isUploadedBy(caller.appUserId())) {
            throw new ForbiddenException(MediaProblemCodes.MEDIA_NOT_OWNED,
                    "Phieu tai len nay khong phai cua ban.");
        }

        Instant now = clock.instant();
        if (asset.status() != vn.giapha.media.domain.MediaStatus.PENDING) {
            throw new MediaConflictException(MediaProblemCodes.MEDIA_TICKET_CLOSED,
                    "Phieu nay da duoc xu ly roi (dang o trang thai " + asset.status() + ").");
        }
        if (asset.isTicketExpired(now)) {
            // KHONG xoa doi tuong o day: duong don se lam viec ay sau an han, va xoa ngay se dua
            // mot lan tai len cham 16 phut vao the "mat tep ma khong hieu vi sao".
            throw new MediaConflictException(MediaProblemCodes.MEDIA_TICKET_CLOSED,
                    "Phieu tai len da qua han " + MediaLimits.UPLOAD_TICKET_TTL.toMinutes()
                            + " phut. Hay xin phieu moi va tai lai.");
        }

        // --- Lop 2: tep CO THAT tren kho chua? ---
        ObjectStoragePort.StoredObject stored = storage.stat(asset.objectKey())
                .orElseThrow(() -> new MediaConflictException(MediaProblemCodes.MEDIA_NOT_UPLOADED,
                        "Tren kho khong co tep nao ung voi phieu nay. Hay tai tep len dia chi da"
                                + " ky (uploadUrl) TRUOC, roi moi goi xac nhan."));

        long cap = MediaLimits.maxBytes(asset.kind());
        if (stored.sizeBytes() > cap) {
            throw purge(asset, MediaProblemCodes.MEDIA_TOO_LARGE, quaTran(asset.kind(), cap), now);
        }

        // --- Lop 3: chu ky byte ---
        byte[] head = storage.readRange(asset.objectKey(), 0,
                (int) Math.min(VideoHeaderProbe.PROBE_BYTES, stored.sizeBytes()));
        MediaSignature.Detected detected;
        try {
            detected = MediaSignature.detect(head, "vua tai len");
        } catch (MediaRejectedException ex) {
            throw purge(asset, ex.getCode(), ex.getMessage(), now);
        }
        if (detected.kind() != asset.kind()) {
            throw purge(asset, MediaProblemCodes.MEDIA_BAD_SIGNATURE,
                    "Ban xin phieu cho " + nhan(asset.kind()) + " nhung tep tai len la "
                            + nhan(detected.kind()) + " (" + detected.contentType() + ")."
                            + " Hay xin phieu dung loai roi tai lai.", now);
        }

        // --- Thoi luong, chi voi video ---
        Long durationMs = null;
        if (asset.kind() == MediaKind.VIDEO) {
            byte[] tail = readTail(asset.objectKey(), stored.sizeBytes());
            try {
                durationMs = VideoHeaderProbe.durationMillis(detected.contentType(), head, tail);
            } catch (MediaRejectedException ex) {
                throw purge(asset, ex.getCode(), ex.getMessage(), now);
            }
            if (durationMs > MediaLimits.MAX_VIDEO_SECONDS * 1000L) {
                throw purge(asset, MediaProblemCodes.MEDIA_TOO_LONG,
                        "Doan video dai " + (durationMs / 1000) + " giay, vuot gioi han "
                                + MediaLimits.MAX_VIDEO_SECONDS + " giay cua trang dong ho."
                                + " Hay cat ngan lai, hoac dua len mot nen tang video roi dan lien"
                                + " ket vao than bai.", now);
            }
        }

        try {
            asset.confirm(detected.contentType(), stored.sizeBytes(), durationMs, alt, now);
        } catch (IllegalArgumentException ex) {
            // Thieu chu thay anh: KHONG xoa doi tuong — tep hoan toan hop le, nguoi dung chi can
            // go them mot cau roi goi lai. Xoa o day bat ho tai lai 8 MiB vi mot o input bo trong.
            throw new DomainException(MediaProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
        MediaAsset saved = assets.save(asset);

        audit.record(ENTITY, saved.id().toString(), AuditAction.CREATE, null,
                saved.auditSnapshot(), List.of("status", "contentType", "sizeBytes"), null);
        log.info("Xac nhan tep {} ({} {} byte{}) boi app_user {}", saved.id(),
                saved.contentType(), saved.sizeBytes(),
                durationMs == null ? "" : ", " + durationMs + "ms", caller.appUserId());
        return saved;
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    /**
     * Hộp {@code moov} của một MP4 chưa "faststart" nằm ở <b>cuối</b> tệp, nên khi phần đầu không
     * đủ để đọc thời lượng, ta đọc thêm phần đuôi. Tệp nhỏ hơn một lượt dò thì phần đầu đã phủ hết
     * và không cần lượt thứ hai.
     */
    private byte[] readTail(String objectKey, long size) {
        if (size <= VideoHeaderProbe.PROBE_BYTES) {
            return null;
        }
        int length = (int) Math.min(VideoHeaderProbe.PROBE_BYTES, size);
        return storage.readRange(objectKey, size - length, length);
    }

    /**
     * Xoá byte khỏi kho rồi ném lỗi.
     *
     * <p>Trả về ngoại lệ thay vì tự ném, để bên gọi viết {@code throw purge(...)} — nhờ đó trình
     * biên dịch <i>biết</i> luồng kết thúc ở đó. Bản đầu của phương thức này tự ném và trả
     * {@code void}, và hệ quả là một nhánh sau nó vẫn chạy tiếp với {@code durationMs == null} rồi
     * ném NPE ở dòng kế — một lỗi chỉ lộ ra đúng ở ca tệp hỏng, tức ca ít được thử nhất.</p>
     *
     * <p>Thứ tự quan trọng: xoá <b>trước</b>, vì nếu chỉ ném lỗi thì một tệp 100 MiB sai định dạng
     * vẫn nằm lại kho cho tới lượt dọn kế tiếp — và một kẻ lặp lại thao tác này biến kho thành bãi
     * rác trong vài phút. Hàng {@code media_asset} ở lại với {@code PURGED}, làm sổ.</p>
     */
    private MediaRejectedException purge(MediaAsset asset, String code, String message,
                                        Instant now) {
        try {
            storage.delete(asset.objectKey());
        } catch (RuntimeException ex) {
            log.warn("Khong xoa duoc doi tuong {} sau khi tu choi ({}); duong don se don lai",
                    asset.id(), code, ex);
        }
        asset.markPurged(now);
        assets.save(asset);
        audit.record(ENTITY, asset.id().toString(), AuditAction.SOFT_DELETE,
                "Tu choi luc xac nhan: " + code);
        return new MediaRejectedException(code, message);
    }

    private static String quaTran(MediaKind kind, long cap) {
        long mib = cap / (1024 * 1024);
        return kind == MediaKind.VIDEO
                ? "Doan video vuot gioi han " + mib + " MB. Vi he thong khong chuyen ma lai video,"
                        + " dung bay nhieu byte se duoc tai xuong tren may cua moi nguoi trong ho —"
                        + " hay xuat lai o 1080p hoac cat ngan clip."
                : "Tam anh vuot gioi han " + mib + " MB. Hay xuat lai o kich thuoc nho hon"
                        + " (anh chup bang dien thoai thuong chi 3-5 MB).";
    }

    private static String nhan(MediaKind kind) {
        return kind == MediaKind.VIDEO ? "video" : "anh";
    }

    /**
     * Kiểu MIME được nhận, để giao diện đặt thuộc tính {@code accept}.
     *
     * <p>Danh sách này là <b>gợi ý cho người dùng</b>, không phải phép kiểm — phép kiểm là chữ ký
     * byte. Nó phải khớp {@code MediaSignature}; {@code MediaLimitsContractTest} ghim điều đó.</p>
     */
    private static List<String> acceptedTypes(MediaKind kind) {
        return kind == MediaKind.VIDEO
                ? List.of(MediaSignature.VIDEO_MP4, MediaSignature.VIDEO_WEBM)
                : List.of(MediaSignature.IMAGE_JPEG, MediaSignature.IMAGE_PNG,
                        MediaSignature.IMAGE_WEBP);
    }
}

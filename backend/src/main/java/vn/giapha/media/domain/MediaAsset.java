package vn.giapha.media.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Một đối tượng trên kho, và vòng đời của nó. POJO thuần — bản chiếu JPA ở
 * {@code media.infrastructure.jpa}.
 *
 * <h2>Bất biến của cả context, nhốt vào một phương thức</h2>
 * {@link #confirm} là <b>lối duy nhất</b> đưa một tệp sang {@link MediaStatus#READY}, và chữ ký
 * của nó đòi đúng những thứ <i>chỉ backend mới biết</i>: kiểu MIME đã dò bằng chữ ký byte, kích
 * thước thật lấy từ {@code statObject}, thời lượng đọc từ header. Không một tham số nào trong đó
 * đến từ client. Đó là cách "tệp chỉ có thật sau khi backend nhìn thấy nó trên MinIO" được giữ
 * bằng kiểu thay vì bằng trí nhớ — và {@code ck_media_ready_evidence} của V19 là lưới cuối cho
 * cùng luật ấy, phòng một lối ghi nào đó đi vòng qua lớp này.
 *
 * <h2>Aggregate này KHÔNG tự kiểm quyền</h2>
 * Nó không biết {@code ltree}, không biết vai trò, không biết nhóm trường riêng tư — đúng khuôn
 * {@code content.domain.Post} và {@code membership.domain.ChangeRequest}. Ai được tải lên, ai được
 * gắn vào bài nào, ai được xem: tất cả nằm ở {@code MediaAccessGuard} và ở các hiện thực
 * {@code MediaOwnerAccessPort} của chính module sở hữu bản ghi.
 */
public final class MediaAsset {

    private final UUID id;
    private final String objectKey;
    private final String bucket;
    private final MediaKind kind;
    private final UUID uploadedBy;
    private final Instant ticketExpiresAt;
    private final Instant createdAt;
    private final long version;

    private MediaStatus status;
    private String contentType;
    private Long sizeBytes;
    private Integer durationMs;
    private String altText;
    private Instant confirmedAt;
    private Instant purgedAt;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public MediaAsset(UUID id, String objectKey, String bucket, MediaKind kind, MediaStatus status,
                      String contentType, Long sizeBytes, Integer durationMs, String altText,
                      UUID uploadedBy, Instant ticketExpiresAt,
                      Instant confirmedAt, Instant purgedAt, Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id, "MediaAsset.id khong duoc null");
        this.objectKey = Objects.requireNonNull(objectKey, "MediaAsset.objectKey khong duoc null");
        this.bucket = Objects.requireNonNull(bucket, "MediaAsset.bucket khong duoc null");
        this.kind = Objects.requireNonNull(kind, "MediaAsset.kind khong duoc null");
        this.status = status == null ? MediaStatus.PENDING : status;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.durationMs = durationMs;
        this.altText = altText;
        this.uploadedBy = Objects.requireNonNull(uploadedBy, "MediaAsset.uploadedBy khong duoc null");
        this.ticketExpiresAt = Objects.requireNonNull(ticketExpiresAt,
                "MediaAsset.ticketExpiresAt khong duoc null — mot phieu khong han la mot tep mo coi"
                        + " khong bao gio don duoc");
        this.confirmedAt = confirmedAt;
        this.purgedAt = purgedAt;
        this.createdAt = createdAt;
        this.version = version;
    }

    /** Phiếu mới: đã phát URL đã ký, <b>chưa</b> có byte nào trên kho. */
    public static MediaAsset ticket(UUID id, String objectKey, String bucket, MediaKind kind,
                                    UUID uploadedBy, Instant expiresAt) {
        return new MediaAsset(id, objectKey, bucket, kind, MediaStatus.PENDING,
                null, null, null, null, uploadedBy, expiresAt, null, null, null, 0L);
    }

    // -------------------------------------------------------------------------------------
    // Chuyển trạng thái
    // -------------------------------------------------------------------------------------

    /**
     * Xác nhận: backend đã tự thấy tệp trên kho và đã đọc byte đầu của nó.
     *
     * @param detectedContentType kiểu MIME <b>dò bằng chữ ký byte</b>, không phải do client khai
     * @param actualSizeBytes     kích thước thật từ {@code statObject}
     * @param durationMillis      thời lượng đọc từ header; {@code null} với ảnh
     * @param alt                 chữ thay ảnh — bắt buộc với {@link MediaKind#IMAGE}
     */
    public void confirm(String detectedContentType, long actualSizeBytes, Long durationMillis,
                        String alt, Instant now) {
        if (status != MediaStatus.PENDING) {
            throw new IllegalStateException(
                    "Chi xac nhan duoc phieu dang o PENDING; phieu nay dang o " + status);
        }
        if (kind == MediaKind.IMAGE) {
            String trimmed = alt == null ? "" : alt.strip();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(
                        "Anh bat buoc phai co chu thay anh (alt) — WCAG 2.2 AA tieu chi 1.1.1."
                                + " Nguoi dung trinh doc man hinh khong co cach nao khac de biet"
                                + " trong anh co gi.");
            }
            if (trimmed.length() > MediaLimits.MAX_ALT_LENGTH) {
                throw new IllegalArgumentException("Chu thay anh toi da "
                        + MediaLimits.MAX_ALT_LENGTH + " ky tu; mo ta dai thuoc ve than bai.");
            }
            this.altText = trimmed;
            this.durationMs = null;
        } else {
            this.altText = alt == null || alt.isBlank() ? null
                    : alt.strip().substring(0, Math.min(alt.strip().length(),
                            MediaLimits.MAX_ALT_LENGTH));
            Objects.requireNonNull(durationMillis,
                    "Video phai co thoi luong da do — mot tran khong do duoc la mot tran khong"
                            + " ton tai");
            this.durationMs = (int) Math.min(Integer.MAX_VALUE, durationMillis);
        }
        this.contentType = Objects.requireNonNull(detectedContentType);
        this.sizeBytes = actualSizeBytes;
        this.confirmedAt = now;
        this.status = MediaStatus.READY;
    }

    /**
     * Byte đã bị xoá khỏi kho. Hàng ở lại làm sổ — xem khối "XOÁ MỀM, VÀ RANH GIỚI CỦA NÓ" của
     * {@code V19}.
     */
    public void markPurged(Instant now) {
        this.status = MediaStatus.PURGED;
        this.purgedAt = now;
    }

    // -------------------------------------------------------------------------------------
    // Câu hỏi
    // -------------------------------------------------------------------------------------

    /** Tệp có thật và còn phục vụ được. Mọi lối đọc phải hỏi câu này trước. */
    public boolean isServable() {
        return status == MediaStatus.READY;
    }

    public boolean isTicketExpired(Instant now) {
        return status == MediaStatus.PENDING && ticketExpiresAt.isBefore(now);
    }

    public boolean isUploadedBy(UUID appUserId) {
        return appUserId != null && uploadedBy.equals(appUserId);
    }

    /**
     * Ảnh chụp cho {@code audit_log}.
     *
     * <p><b>Cố ý KHÔNG chở {@code objectKey}.</b> Một khoá đối tượng là thứ ký được thành URL đọc,
     * nên nó là dữ liệu nhạy cảm chứ không phải một định danh vô hại — và với ảnh chân dung của
     * người còn sống thì nó là dữ liệu Tầng 3 theo đúng nghĩa của BA v2 §10.
     * {@code SensitiveFieldRedactor} đã che các khoá tên {@code mediakey}/{@code avatarkey} và sẽ
     * che cái này, nhưng dựa vào lưới cuối để giữ một bất biến là cách nó trượt qua vào ngày ai đó
     * đổi tên trường. Nhật ký cần biết <i>tệp nào</i> — {@code id} nói đủ.</p>
     */
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", id.toString());
        snapshot.put("kind", kind.name());
        snapshot.put("status", status.name());
        snapshot.put("contentType", contentType);
        snapshot.put("sizeBytes", sizeBytes);
        snapshot.put("durationMs", durationMs);
        return snapshot;
    }

    public UUID id() {
        return id;
    }

    public String objectKey() {
        return objectKey;
    }

    public String bucket() {
        return bucket;
    }

    public MediaKind kind() {
        return kind;
    }

    public MediaStatus status() {
        return status;
    }

    public String contentType() {
        return contentType;
    }

    public Long sizeBytes() {
        return sizeBytes;
    }

    public Integer durationMs() {
        return durationMs;
    }

    public String altText() {
        return altText;
    }

    public UUID uploadedBy() {
        return uploadedBy;
    }

    public Instant ticketExpiresAt() {
        return ticketExpiresAt;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Instant purgedAt() {
        return purgedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }
}

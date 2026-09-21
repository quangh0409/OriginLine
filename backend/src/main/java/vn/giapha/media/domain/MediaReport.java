package vn.giapha.media.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Một đơn báo gỡ tệp đính kèm. POJO thuần.
 *
 * <h2>Lớp này là NỬA CÒN LẠI của quyết định "ảnh đi theo quyền của bài"</h2>
 * Chủ dự án đã chốt: bài đã đăng thì ảnh hiện với mọi thành viên, không đi qua bộ lọc nhóm trường
 * của từng người có mặt trong ảnh. Đó là nửa mở. Nửa đóng là lớp này — người trong họ nói "tấm
 * này gỡ giùm", và một người có thẩm quyền <i>trong phạm vi chi</i> gỡ được. Thiếu nửa đóng thì
 * nửa mở không an toàn, và điều đó đã được ghi vào cả {@code V19} lẫn javadoc của
 * {@code MediaReportService}.
 *
 * <p>Aggregate này <b>không</b> tự kiểm quyền, đúng khuôn {@code Post}: {@link #takeDown} và
 * {@link #dismiss} chỉ nhận {@code reviewerId} <i>sau khi</i> phép so {@code ltree} đã qua.</p>
 */
public final class MediaReport {

    public static final int MAX_NOTE_LENGTH = 2_000;

    private final UUID id;
    private final UUID mediaId;
    private final ReportReason reason;
    private final String note;
    private final UUID reportedBy;
    private final Instant createdAt;
    private final long version;

    private ReportStatus status;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String resolutionNote;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public MediaReport(UUID id, UUID mediaId, ReportReason reason, String note, UUID reportedBy,
                       ReportStatus status, UUID reviewedBy, Instant reviewedAt,
                       String resolutionNote, Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id, "MediaReport.id khong duoc null");
        this.mediaId = Objects.requireNonNull(mediaId, "MediaReport.mediaId khong duoc null");
        this.reason = Objects.requireNonNull(reason, "MediaReport.reason khong duoc null");
        this.note = requireNote(reason, note);
        this.reportedBy = Objects.requireNonNull(reportedBy, "MediaReport.reportedBy khong duoc null");
        this.status = status == null ? ReportStatus.OPEN : status;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.resolutionNote = resolutionNote;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static MediaReport open(UUID mediaId, ReportReason reason, String note, UUID reportedBy) {
        return new MediaReport(UUID.randomUUID(), mediaId, reason, note, reportedBy,
                ReportStatus.OPEN, null, null, null, null, 0L);
    }

    /**
     * Lý do {@link ReportReason#KHAC} bắt buộc kèm mô tả.
     *
     * <p>Bốn lý do kia tự nói hết; "Khác" mà không nói gì thêm là một đơn người duyệt không xử
     * được, và một hàng đợi đầy đơn không xử được là một hàng đợi không ai mở.</p>
     */
    private static String requireNote(ReportReason reason, String note) {
        String trimmed = note == null ? "" : note.strip();
        if (reason == ReportReason.KHAC && trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chon ly do \"Khac\" thi phai noi ro la chuyen gi — nguoi duyet khong doan duoc.");
        }
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NOTE_LENGTH);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Gỡ: byte bị xoá khỏi kho <b>ngay</b>, không ân hạn. Xem {@code MediaLimits.ORPHAN_GRACE}. */
    public void takeDown(UUID reviewerId, String resolution, Instant now) {
        requireOpen();
        this.status = ReportStatus.ACTIONED;
        this.reviewedBy = Objects.requireNonNull(reviewerId);
        this.reviewedAt = now;
        this.resolutionNote = blankToNull(resolution);
    }

    /** Giữ nguyên tệp — nhưng vẫn phải ký tên, vì người báo có quyền biết ai đã quyết. */
    public void dismiss(UUID reviewerId, String resolution, Instant now) {
        requireOpen();
        this.status = ReportStatus.DISMISSED;
        this.reviewedBy = Objects.requireNonNull(reviewerId);
        this.reviewedAt = now;
        this.resolutionNote = blankToNull(resolution);
    }

    private void requireOpen() {
        if (status != ReportStatus.OPEN) {
            throw new IllegalStateException("Don bao go nay da dong o trang thai " + status);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID id() {
        return id;
    }

    public UUID mediaId() {
        return mediaId;
    }

    public ReportReason reason() {
        return reason;
    }

    public String note() {
        return note;
    }

    public UUID reportedBy() {
        return reportedBy;
    }

    public ReportStatus status() {
        return status;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public String resolutionNote() {
        return resolutionNote;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }
}

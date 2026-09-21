package vn.giapha.media.domain;

/**
 * Vòng đời một đối tượng trên kho.
 *
 * <p><b>{@link #PENDING} không phải bằng chứng tệp tồn tại.</b> Nó chỉ nói "ai đó đã xin một chỗ
 * để tải lên". Bất biến của đợt này: một tệp chỉ được coi là có thật sau khi backend <i>tự</i>
 * nhìn thấy nó trên MinIO ({@code statObject} + đọc chữ ký byte). Tin lời client là cách một bài
 * viết trỏ vào một tệp không tồn tại, và triệu chứng xuất hiện ở máy người đọc chứ không ở log của
 * người tải.</p>
 */
public enum MediaStatus {
    PENDING,
    READY,
    PURGED
}

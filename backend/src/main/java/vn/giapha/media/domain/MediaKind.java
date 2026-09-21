package vn.giapha.media.domain;

/** Hai loại tệp mà đợt này nhận. Xem {@link MediaSignature} cho danh sách định dạng cụ thể. */
public enum MediaKind {
    IMAGE,
    VIDEO;

    public static MediaKind of(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

package vn.giapha.genealogy.domain;

/**
 * Tình trạng nối dõi của một nhân khẩu — khái niệm phả hệ không có tương đương phương Tây.
 *
 * <p>Chi tiết ai là người kế tự nằm ở cạnh {@code HEIR}, không nằm ở đây; cột này chỉ trả lời
 * "chi này đã đứt chưa, đã lập người nối chưa".</p>
 */
public enum LineageStatus {

    /** Bình thường. */
    NORMAL,

    /** Tuyệt tự — không có người nối dõi. */
    TUYET_TU,

    /** Kế tự — đã lập người nối dõi cho một chi tuyệt tự. */
    KE_TU
}

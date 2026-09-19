package vn.giapha.dataimport.domain;

import java.util.Set;

/**
 * Máy trạng thái của <b>một lô nhập liệu</b>.
 *
 * <pre>
 *   DRAFT --&gt; PARSED --&gt; VALIDATING --&gt; VALIDATED --&gt; COMMITTING --&gt; COMMITTED
 *                             |
 *                             +--&gt; FAILED   (còn lỗi chặn; sửa tệp rồi tải lại = lô mới)
 *   mọi trạng thái ---------------&gt; SUPERSEDED (cùng chi đã có lô mới hơn)
 * </pre>
 *
 * <h2>Ranh giới ghi nằm ở đúng một mũi tên</h2>
 * Mọi trạng thái <b>trước</b> {@link #COMMITTING} chỉ chạm các bảng {@code import_*}. Nửa đầu
 * đường ống (đợt này) dừng ở {@link #VALIDATED} — tức là "chờ duyệt". Bước
 * {@code VALIDATED -> COMMITTING -> COMMITTED} là nửa sau và chưa được hiện thực; cố ý để sẵn ở
 * đây để lược đồ không phải đổi lần nữa.
 *
 * <h2>Vì sao "chờ duyệt" không phải một trạng thái riêng</h2>
 * Vì nó không mang thêm thông tin nào: một lô {@code VALIDATED} <b>theo định nghĩa</b> là lô có 0
 * lỗi chặn, và đó chính xác là điều kiện để được bấm duyệt. Thêm một trạng thái
 * {@code AWAITING_APPROVAL} chỉ tạo ra hai nguồn chân lý cho cùng một câu hỏi, rồi sớm muộn chúng
 * lệch nhau.
 */
public enum BatchStatus {

    /** Tệp đã nhận, chưa phân tích. */
    DRAFT,

    /** Đã thành dòng trong khu vực chờ. */
    PARSED,

    /** Bộ kiểm đang chạy. */
    VALIDATING,

    /** 0 lỗi chặn — <b>chờ duyệt</b>. Người nhập được phép bấm ghi vào phả. */
    VALIDATED,

    /** Còn lỗi chặn, hoặc tệp hỏng. Sửa tệp rồi tải lại sinh ra một lô mới. */
    FAILED,

    /** Đang ghi vào phả, trong đúng một transaction. <b>Chưa hiện thực ở đợt này.</b> */
    COMMITTING,

    /** Đã vào phả. <b>Chưa hiện thực ở đợt này.</b> */
    COMMITTED,

    /** Cùng chi đã có lô mới hơn. */
    SUPERSEDED;

    private static final Set<BatchStatus> TERMINAL = Set.of(COMMITTED, SUPERSEDED);

    /** Trạng thái mà bộ kiểm được phép chạy lên: đối soát lặp lại là hành vi bình thường. */
    public boolean coKiemLaiDuoc() {
        return this == PARSED || this == VALIDATED || this == FAILED || this == VALIDATING;
    }

    /** Lô đã chốt, không đổi được nữa. */
    public boolean daChot() {
        return TERMINAL.contains(this);
    }

    /** Được phép bấm ghi vào phả. */
    public boolean sanSangGhi() {
        return this == VALIDATED;
    }
}

package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Quan hệ giữa người khai (chưa có trong phả) và <b>người thân đã có trong phả</b> mà họ chỉ ra.
 *
 * <h2>Vì sao chỉ có ba giá trị, và vì sao người thân là bắt buộc</h2>
 * Ràng buộc 2 của design 07 §1.5: không có người thân thì nhân khẩu mới thành <b>node mồ côi</b> —
 * không gắn vào cây, không tính được đời, không tra được danh xưng. Ba quan hệ này là ba quan hệ
 * duy nhất mà {@code AddPersonService} suy ra được đời thứ từ đó: cha, mẹ (đời = đời cha/mẹ + 1)
 * và vợ/chồng (đời = đời người phối ngẫu — đúng cách dâu/rể vào phả).
 *
 * <p>Con cái <b>không</b> có mặt ở đây, và đó là chủ ý: "tôi là con của người này" thì người thân
 * là cha/mẹ; "tôi là cha của người đã có trong phả" là một nghiệp vụ khác hẳn — nó chèn một đời
 * <i>lên trên</i> và đánh số lại cả một nhánh, việc mà Trưởng chi phải làm bằng tay chứ không phải
 * hệ quả phụ của một đơn tự nhận.</p>
 */
public enum RelativeKind {

    /** Người thân là <b>cha</b> của người khai. */
    FATHER,

    /** Người thân là <b>mẹ</b> của người khai. */
    MOTHER,

    /**
     * Người thân là <b>vợ hoặc chồng</b> của người khai — lối của con dâu/con rể mới về.
     *
     * <p>Đây là ca khiến lối này tồn tại: hôn phối vừa diễn ra tháng trước và chưa ai kịp ghi vào
     * phả, nên người ấy mở phả đồ ra và không tìm thấy mình.</p>
     */
    SPOUSE;

    public static RelativeKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Phai chi ra quan he voi nguoi than da co trong pha");
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}

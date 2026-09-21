package vn.giapha.membership.domain;

/**
 * Mã mời dòng họ có dùng được <b>ngay lúc này</b> không, và nếu không thì vì sao.
 *
 * <h2>Vì sao tách khỏi {@link ClanInviteStatus}</h2>
 * {@code ClanInviteStatus} là thứ nằm trong CSDL; kiểu này trả lời câu hỏi của người đang cầm mã,
 * và câu trả lời ấy phụ thuộc vào đồng hồ <i>và</i> vào bộ đếm. Trộn lại thì "hết hạn" buộc phải
 * trở thành một dòng dữ liệu cần job đi lật.
 *
 * <h2>Ba lý do thất bại được nói thẳng, lý do thứ tư thì không</h2>
 * {@link #EXPIRED}, {@link #REVOKED}, {@link #EXHAUSTED} đi tới người gọi: họ đang cầm một mã có
 * thật, và "mã đã hết hạn, hỏi lại người đưa mã" là thông tin họ cần để đi tiếp. Nhưng <b>mã không
 * tồn tại</b> không có mặt trong enum này — nó không bao giờ dựng được một {@link ClanInviteCode}
 * nên không có gì để hỏi, và ở tầng trên nó trả về {@code 404 NOT_FOUND} chung.
 */
public enum ClanInviteUsability {

    USABLE,

    /** Quá {@code expires_at} — chốt 1 của §1.2 đang làm đúng việc của nó. */
    EXPIRED,

    /** Hội đồng đã thu hồi — chốt 2. Người đã vào bằng mã này không bị ảnh hưởng. */
    REVOKED,

    /**
     * Đã dùng hết {@code max_uses}.
     *
     * <p>Khác {@code INVITATION_ALREADY_USED} của mã cá nhân ở chỗ đây là một <b>trần</b> do Hội
     * đồng tự đặt, không phải bản chất "một lần" của mã cá nhân. Mã dòng họ vốn dùng nhiều lần.</p>
     */
    EXHAUSTED;

    public boolean isUsable() {
        return this == USABLE;
    }
}

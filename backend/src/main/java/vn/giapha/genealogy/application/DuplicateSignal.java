package vn.giapha.genealogy.application;

/**
 * Một mẩu bằng chứng (hoặc phản chứng) cho câu hỏi "hai hồ sơ này có phải cùng một người không",
 * kèm số điểm nó đóng góp.
 *
 * <h2>Vì sao ngày giỗ nặng điểm hơn năm sinh</h2>
 * Năm sinh trong gia phả phần lớn được chép theo trí nhớ của con cháu, và lệch một hai năm là
 * chuyện thường — nhất là khi người chép quy đổi tuổi mụ sang năm dương. Ngày giỗ thì ngược lại:
 * cả họ cúng đúng ngày ấy mỗi năm, có khi vài trăm năm liền, nên đây là mốc khó sai nhất trong
 * toàn bộ hồ sơ một người đã khuất. Vì vậy {@link #GIO_TRUNG_KHIT} nặng điểm nhất, hơn cả
 * {@link #NAM_SINH_KHOP}.
 *
 * <h2>Vì sao khác đời là phản chứng nặng</h2>
 * Đời thứ là thuộc tính <b>đơn trị</b> của một người và được suy ra từ cạnh cha–con chứ không phải
 * từ trí nhớ, nên nó là dữ kiện cấu trúc đáng tin nhất. Trong khi đó trùng tên giữa các đời là
 * chuyện <b>bình thường</b> của dòng họ Việt: tục đặt tên theo chữ đệm của đời khiến hàng loạt
 * người chỉ khác nhau đúng chữ cuối, và một cái tên đẹp thường được dùng lại sau vài đời. Nếu
 * không trừ điểm nặng ở đây thì bộ dò sẽ kêu suốt và người dùng sẽ bấm "vẫn ghi" theo phản xạ —
 * lúc đó cảnh báo mất sạch giá trị.
 */
public enum DuplicateSignal {

    /** Trùng tên nguyên văn cả dấu (chỉ bỏ qua hoa/thường và khoảng trắng thừa). */
    TEN_TRUNG_CO_DAU(40),

    /** Chỉ trùng sau khi bỏ dấu: "Nguyen Van Tuan" ↔ "Nguyễn Văn Tuân". Dễ nhầm hơn nên nhẹ điểm hơn. */
    TEN_TRUNG_KHONG_DAU(30),

    /** Ngày giỗ âm lịch khớp cả ngày, tháng và cờ tháng nhuận. Bằng chứng mạnh nhất. */
    GIO_TRUNG_KHIT(50),

    /** Năm sinh khớp đúng. */
    NAM_SINH_KHOP(22),

    /**
     * Năm sinh lệch 1–2 năm.
     *
     * <p>Chỉ nhẹ hơn {@link #NAM_SINH_KHOP} khoảng một phần tư chứ không phải một phần mười, và đó
     * là cố ý. Năm sinh trong gia phả được chép theo trí nhớ, lại hay lẫn giữa tuổi mụ và tuổi
     * thật, nên "lệch một hai năm" là kết quả <b>bình thường</b> khi cùng một người được hai người
     * khác nhau chép lại. Đánh tụt tín hiệu này xuống quá thấp là tự tay bỏ sót đúng cái lớp bản
     * trùng hay gặp nhất.</p>
     */
    NAM_SINH_LECH_IT(16),

    /** Năm mất khớp (ngày giỗ thì không, hoặc không có). */
    NAM_MAT_KHOP(8),

    /**
     * Cùng một chi/ngành chính.
     *
     * <p>Nặng điểm hơn {@link #CUNG_DOI} một cách có chủ ý. Cùng đời là điều kiện gần như đương
     * nhiên của mọi cặp trùng tên trong cùng một lứa con cháu, nên nó gần như không phân biệt được
     * gì; còn cùng chi thì thu hẹp phạm vi xuống khoảng một phần mười dòng họ. Đảo hai trọng số này
     * là mở cửa cho cả một lớp cảnh báo giả: hai người anh em họ khác chi, cùng đời, tình cờ trùng
     * tên và sinh cách nhau một năm.</p>
     */
    CUNG_CHI(18),

    /**
     * Cùng đời thứ, cả hai đều đã biết đời — bằng chứng <b>yếu</b>, xem {@link #CUNG_CHI}.
     */
    CUNG_DOI(6),

    /** Khác đời thứ, cả hai đều đã biết đời — phản chứng nặng. */
    KHAC_DOI(-35),

    /**
     * Cùng nguyên quán (so không dấu) — <b>0 điểm, cố ý</b>.
     *
     * <p>Trong phạm vi một dòng họ, nguyên quán gần như là hằng số: cả họ cùng một làng gốc. Một
     * tín hiệu mà 90% số cặp đều thoả thì không phân biệt được gì, nó chỉ cộng điểm đều cho tất cả
     * và kéo hàng loạt cặp vô can vượt ngưỡng. Đo trên dòng họ mô phỏng 1.506 người: cho tín hiệu
     * này 4 điểm làm số cảnh báo giả nhảy từ 12 lên 90 — xem
     * {@code DuplicateFalseAlarmSimulationTest}. Vẫn giữ lại trong danh sách tín hiệu để hộp thoại
     * hiển thị được ngữ cảnh, nhưng không bao giờ được tính điểm.</p>
     */
    CUNG_NGUYEN_QUAN(0),

    /** Khác giới tính, cả hai đều đã ghi rõ — phản chứng. */
    KHAC_GIOI(-25);

    private final int points;

    DuplicateSignal(int points) {
        this.points = points;
    }

    /** Số điểm tín hiệu này cộng vào (hoặc trừ đi) tổng điểm nghi ngờ. */
    public int points() {
        return points;
    }

    /** {@code true} nếu đây là phản chứng (điểm âm). */
    public boolean isPhanChung() {
        return points < 0;
    }
}

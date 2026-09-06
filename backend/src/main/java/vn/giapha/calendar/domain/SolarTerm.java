package vn.giapha.calendar.domain;

/**
 * 24 <b>tiết khí</b> — các mốc chia hoàng đạo thành 24 cung 15°.
 *
 * <p>Hai vai trò trong hệ thống gia phả:</p>
 * <ul>
 *   <li><b>Thanh minh</b> ({@link #THANH_MINH}, 15°) là mốc của tục <i>chạp mả</i> / tảo mộ;</li>
 *   <li><b>Đông chí</b> ({@link #DONG_CHI}, 270°) là mốc mà chính thuật toán âm lịch dựa vào:
 *       Đông chí luôn rơi vào tháng 11 âm lịch, và từ đó mới xác định được tháng nhuận.</li>
 * </ul>
 *
 * <p>{@code trungKhi} phân biệt <b>Trung khí</b> (12 mốc ở kinh độ chia hết cho 30°) với
 * <b>Tiết khí</b> theo nghĩa hẹp. Luật nhuận chỉ đếm Trung khí: tháng âm lịch nào không chứa
 * Trung khí nào thì là tháng nhuận.</p>
 */
public enum SolarTerm {

    LAP_XUAN(315, "Lập xuân", false),
    VU_THUY(330, "Vũ thủy", true),
    KINH_TRAP(345, "Kinh trập", false),
    XUAN_PHAN(0, "Xuân phân", true),
    THANH_MINH(15, "Thanh minh", false),
    COC_VU(30, "Cốc vũ", true),
    LAP_HA(45, "Lập hạ", false),
    TIEU_MAN(60, "Tiểu mãn", true),
    MANG_CHUNG(75, "Mang chủng", false),
    HA_CHI(90, "Hạ chí", true),
    TIEU_THU(105, "Tiểu thử", false),
    DAI_THU(120, "Đại thử", true),
    LAP_THU(135, "Lập thu", false),
    XU_THU(150, "Xử thử", true),
    BACH_LO(165, "Bạch lộ", false),
    THU_PHAN(180, "Thu phân", true),
    HAN_LO(195, "Hàn lộ", false),
    SUONG_GIANG(210, "Sương giáng", true),
    LAP_DONG(225, "Lập đông", false),
    TIEU_TUYET(240, "Tiểu tuyết", true),
    DAI_TUYET(255, "Đại tuyết", false),
    DONG_CHI(270, "Đông chí", true),
    TIEU_HAN(285, "Tiểu hàn", false),
    DAI_HAN(300, "Đại hàn", true);

    private final int longitudeDegrees;
    private final String vietnameseName;
    private final boolean trungKhi;

    SolarTerm(int longitudeDegrees, String vietnameseName, boolean trungKhi) {
        this.longitudeDegrees = longitudeDegrees;
        this.vietnameseName = vietnameseName;
        this.trungKhi = trungKhi;
    }

    /** Kinh độ Mặt Trời tại mốc này, tính bằng độ (0 = Xuân phân). */
    public int longitudeDegrees() {
        return longitudeDegrees;
    }

    /** Tên tiếng Việt có dấu, ví dụ "Thanh minh". */
    public String vietnameseName() {
        return vietnameseName;
    }

    /** {@code true} nếu đây là một <b>Trung khí</b> — mốc mà luật tháng nhuận đếm. */
    public boolean isTrungKhi() {
        return trungKhi;
    }

    /** Tiết khí ứng với kinh độ Mặt Trời đã cho (phải là bội của 15°). */
    public static SolarTerm ofLongitude(int degrees) {
        int normalized = Math.floorMod(degrees, 360);
        for (SolarTerm term : values()) {
            if (term.longitudeDegrees == normalized) {
                return term;
            }
        }
        throw new IllegalArgumentException("Kinh do " + degrees + " khong ung voi tiet khi nao (phai la boi cua 15)");
    }
}

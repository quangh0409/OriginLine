package vn.giapha.demo.generator;

import java.time.LocalDate;

/**
 * Tham số sinh dữ liệu giả. Bất biến, không Spring — {@code DemoDataProperties} chỉ ánh xạ vào đây.
 *
 * <p><b>Tất cả đều phải tất định.</b> Đặc biệt {@link #referenceDate()}: tỉ lệ còn sống/đã khuất
 * được tính so với ngày này chứ KHÔNG so với đồng hồ hệ thống. Nếu dùng {@code LocalDate.now()} thì
 * mỗi tháng trôi qua lại có thêm vài cụ "qua đời", bộ test phân tầng hiển thị sẽ đỏ mà không ai
 * hiểu vì sao.</p>
 *
 * @param seed            hạt giống cố định cho mọi nguồn ngẫu nhiên và cho việc sinh UUID
 * @param referenceDate   "hôm nay" của bộ dữ liệu
 * @param rootBirthYear   năm sinh dương lịch của thuỷ tổ
 * @param bloodlinePlan   số nhân khẩu HUYẾT THỐNG theo từng đời, chỉ số 0 = đời 1
 * @param spousePlan      số nhân khẩu KẾT HÔN VÀO (dâu/rể) theo từng đời
 * @param chiCount        số chi (con trai của thuỷ tổ đứng ra lập chi)
 * @param nhanhPerChi     số ngành/nhánh mỗi chi
 */
public record DemoSeedConfig(long seed,
                             LocalDate referenceDate,
                             int rootBirthYear,
                             int[] bloodlinePlan,
                             int[] spousePlan,
                             int chiCount,
                             int nhanhPerChi) {

    /** Seed mặc định — ĐỔI GIÁ TRỊ NÀY LÀ ĐỔI TOÀN BỘ UUID, mọi test đang assert trên id sẽ đỏ. */
    public static final long DEFAULT_SEED = 20260101L;

    /** "Hôm nay" của bộ dữ liệu demo. */
    public static final LocalDate DEFAULT_REFERENCE_DATE = LocalDate.of(2026, 1, 1);

    /**
     * Năm sinh dương lịch của thuỷ tổ.
     *
     * <p><b>Đây là cái núm chỉnh tỉ lệ còn sống / đã khuất</b>, chứ không phải một chi tiết trang
     * trí. Khoảng cách thế hệ trung bình khoảng 31 năm, nên dịch năm sinh thuỷ tổ đi 10 năm là dịch
     * cả đời thứ bảy đi 10 năm, và tỉ lệ còn sống đổi theo rất mạnh. Đo thực tế với
     * {@link #DEFAULT_REFERENCE_DATE}:</p>
     * <pre>
     *   1770 -> 30% sống / 70% khuất      1790 -> 52% sống / 48% khuất
     *   1780 -> 42% sống / 58% khuất  &lt;- chốt, gần nhất với plan §10 (40 / 60)
     * </pre>
     * <p>Đổi giá trị này thì phải đo lại tỉ lệ, đừng đoán.</p>
     */
    public static final int DEFAULT_ROOT_BIRTH_YEAR = 1780;

    /**
     * Quy mô chốt ở plan §10: ~1.500 nhân khẩu, 7 đời.
     * <pre>
     *   huyết thống 1 + 9 + 26 + 66 + 150 + 300 + 376 =  928
     *   dâu/rể      2 + 5 + 20 + 55 + 125 + 210 + 155 =  572
     *                                          tổng   = 1500
     * </pre>
     */
    public static DemoSeedConfig defaults() {
        return new DemoSeedConfig(
                DEFAULT_SEED,
                DEFAULT_REFERENCE_DATE,
                DEFAULT_ROOT_BIRTH_YEAR,
                new int[] {1, 9, 26, 66, 150, 300, 376},
                new int[] {2, 5, 20, 55, 125, 210, 155},
                4,
                3);
    }

    public DemoSeedConfig withSeed(long newSeed) {
        return new DemoSeedConfig(newSeed, referenceDate, rootBirthYear, bloodlinePlan, spousePlan,
                chiCount, nhanhPerChi);
    }

    public int generations() {
        return bloodlinePlan.length;
    }

    public int totalPlanned() {
        int total = 0;
        for (int n : bloodlinePlan) { total += n; }
        for (int n : spousePlan) { total += n; }
        return total;
    }

    /** Số huyết thống dự kiến của đời {@code generation} (1-based). */
    public int bloodlineOf(int generation) {
        return bloodlinePlan[generation - 1];
    }

    /** Số dâu/rể dự kiến của đời {@code generation} (1-based). */
    public int spouseOf(int generation) {
        return spousePlan[generation - 1];
    }
}

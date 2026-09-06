package vn.giapha.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Bảng ca biên bắt buộc của plan Giai đoạn 1 §10 — và phép kiểm tra rằng bộ dữ liệu đã đủ.
 *
 * <h2>Vì sao thiếu ca biên là lỗi khởi động chứ không phải cảnh báo</h2>
 * <p>Những con số dưới đây không phải mục tiêu "cho đẹp": chúng là <b>điều kiện để bộ test kinship
 * có gì mà chạy</b>. Không có ca đa thê thì không kiểm được "anh/chị/em cùng cha khác mẹ"; không có
 * con nuôi thì cạnh {@code PARENT_ADOPT} chưa từng được đi qua; không có người sống mang dữ liệu
 * Tầng 3 thì bộ lọc phân tầng riêng tư — phần chịu trách nhiệm pháp lý của hệ thống — chưa bao giờ
 * bị thử ở nhánh nhạy cảm nhất của nó.</p>
 *
 * <p>Nếu chỉ ghi log cảnh báo thì một hôm nào đó ai đó đổi seed, vài ca biên biến mất, test kinship
 * vẫn xanh vì không có dữ liệu để đỏ — và mọi người sẽ tin rằng engine danh xưng đã được kiểm
 * chứng. Đó là lý do ở đây ném lỗi.</p>
 */
final class DemoEdgeCaseQuota {

    /** Khoá thống kê của generator -> {mô tả, số lượng tối thiểu}. */
    private static final Map<String, Requirement> REQUIREMENTS = new LinkedHashMap<>();

    static {
        REQUIREMENTS.put("da_the", new Requirement("Đa thê (2–3 vợ, có spouse_order)", 3));
        REQUIREMENTS.put("con_nuoi", new Requirement("Con nuôi (cạnh PARENT_ADOPT)", 5));
        REQUIREMENTS.put("dau_re", new Requirement("Dâu / rể", 10));
        REQUIREMENTS.put("tai_hon", new Requirement("Tái hôn", 2));
        REQUIREMENTS.put("con_rieng", new Requirement("Con riêng của vợ/chồng trước", 2));
        REQUIREMENTS.put("tuyet_tu", new Requirement("Tuyệt tự", 1));
        REQUIREMENTS.put("ke_tu", new Requirement("Kế tự (lập người nối dõi)", 1));
        REQUIREMENTS.put("dich_ton", new Requirement("Đích tôn", 2));
        REQUIREMENTS.put("ky_huy_collisions", new Requirement("Trùng tên huý với bậc trên (kỵ húy)", 5));
        REQUIREMENTS.put("deep_line_depth", new Requirement("Nhánh sâu đủ 7 đời (test LCA xa)", 7));
        REQUIREMENTS.put("tier3_persons", new Requirement("Người sống có dữ liệu Tầng 3 (SĐT, email)", 20));
    }

    private DemoEdgeCaseQuota() {
    }

    /**
     * Ghi log bảng ca biên rồi ném lỗi nếu thiếu bất kỳ hạng mục nào.
     *
     * @param stats thống kê do {@code ClanTreeGenerator} tính, đã cộng gộp dâu + rể
     */
    static void verify(Map<String, Integer> stats, Logger log) {
        Map<String, Integer> merged = new LinkedHashMap<>(stats);
        merged.put("dau_re", merged.getOrDefault("dau", 0) + merged.getOrDefault("re", 0));

        List<String> missing = new ArrayList<>();
        log.info("Ca bien bat buoc (plan §10) — thuc te / toi thieu:");
        for (Map.Entry<String, Requirement> entry : REQUIREMENTS.entrySet()) {
            int actual = merged.getOrDefault(entry.getKey(), 0);
            Requirement requirement = entry.getValue();
            boolean ok = actual >= requirement.minimum();
            log.info("  [{}] {} — {} / {}", ok ? "DU" : "THIEU", requirement.description(),
                    actual, requirement.minimum());
            if (!ok) {
                missing.add("%s: %d/%d".formatted(requirement.description(), actual, requirement.minimum()));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Bo du lieu demo thieu ca bien bat buoc, bo test kinship se khong co gi de chay: "
                            + missing);
        }
    }

    private record Requirement(String description, int minimum) {
    }
}

package vn.giapha.dataimport.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Các cột của trang <b>Hôn phối</b>.
 *
 * <h2>Ba cột không có trong mẫu gốc, và vì sao chúng cần thiết</h2>
 * <ul>
 *   <li>{@link #BAC} — vợ cả / vợ hai / vợ ba. Không có nó thì
 *       {@code ux_relationship_spouse_order} không có dữ liệu, và <b>danh xưng con của các bà
 *       không tính đúng</b>. Vợ hai vì đa thê khác hẳn vợ kế vì goá, và dòng họ phân biệt rất
 *       rõ hai chuyện đó.</li>
 *   <li>{@link #TU_NAM} / {@link #DEN_NAM} / {@link #LY_DO_KET_THUC} — không có chúng thì mọi
 *       cuộc hôn nhân sập thành một quan hệ vô thời hạn, và tái hôn, ly hôn, goá rồi lấy tiếp đều
 *       không nhập được. Lược đồ đã đỡ sẵn ({@code valid_from}, {@code valid_to},
 *       {@code end_reason}); chỉ mẫu Excel là thiếu.</li>
 * </ul>
 *
 * <p>Cả ba đều <b>không bắt buộc</b>, nên tệp theo mẫu cũ vẫn đọc được.</p>
 */
public enum MarriageColumn {

    MA_CHONG("Mã chồng", true, "ma chong", "chong", "ma nam"),
    MA_VO("Mã vợ", true, "ma vo", "vo", "ma nu"),
    BAC("Bậc", false, "bac", "thu tu vo", "bac hon phoi", "vo thu may"),
    TU_NAM("Từ năm", false, "tu nam", "nam cuoi", "nam ket hon"),
    DEN_NAM("Đến năm", false, "den nam", "nam ket thuc"),
    LY_DO_KET_THUC("Lý do kết thúc", false, "ly do ket thuc", "ly do", "den nam ly do");

    private final String tieuDe;
    private final boolean batBuoc;
    private final List<String> khoaGhep;

    MarriageColumn(String tieuDe, boolean batBuoc, String... aliases) {
        this.tieuDe = tieuDe;
        this.batBuoc = batBuoc;
        this.khoaGhep = List.of(aliases);
    }

    public String tieuDe() {
        return tieuDe;
    }

    public boolean batBuoc() {
        return batBuoc;
    }

    public static Map<String, MarriageColumn> bangTra() {
        Map<String, MarriageColumn> map = new LinkedHashMap<>();
        for (MarriageColumn col : values()) {
            map.put(ImportColumn.khoa(col.tieuDe), col);
            for (String alias : col.khoaGhep) {
                map.putIfAbsent(alias, col);
            }
        }
        return map;
    }
}

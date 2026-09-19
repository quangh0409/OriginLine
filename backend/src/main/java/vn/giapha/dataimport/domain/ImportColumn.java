package vn.giapha.dataimport.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Các cột của trang <b>Nhân khẩu</b>, ghép theo <b>tên tiêu đề</b> chứ không theo vị trí.
 *
 * <h2>Vì sao ghép theo tên</h2>
 * Người nhập chèn thêm một cột ghi chú ở giữa là chuyện <b>chắc chắn sẽ xảy ra</b>: họ đang làm
 * việc trên một tệp Excel của riêng mình, không phải điền vào một biểu mẫu khoá cứng. Ghép theo
 * chỉ số cột thì cả tệp lệch một ô và mọi họ tên biến thành mã cha — một kiểu hỏng im lặng và
 * thảm hoạ. Ghép theo tên thì cột lạ bị bỏ qua, cột thiếu bị báo tử tế.
 *
 * <h2>Khoá ghép đã bỏ dấu</h2>
 * Tiêu đề được so khớp sau khi bỏ dấu và hạ chữ thường, nên "Ngày mất âm", "NGÀY MẤT ÂM" và
 * "ngay mat am" là một. Không dùng {@code vn_unaccent} của CSDL ở đây vì đây là phép so khớp
 * <b>tiêu đề cột do ta tự định nghĩa</b>, không phải dữ liệu của dòng họ — nó không cần trùng
 * từng ký tự với phép bỏ dấu của cơ sở dữ liệu, và một vòng gọi CSDL chỉ để đọc dòng đầu tiên của
 * tệp là phí.
 *
 * <h2>Bốn cột thêm so với mẫu 11 cột đã chốt</h2>
 * {@link #KE_TU_CHO_AI}, {@link #LOAI_KE_TU}, {@link #THUY_HIEU}, {@link #TEN_HAN_NOM} — bốn ca
 * dữ liệu mà lược đồ <b>đã đỡ được đầy đủ</b> nhưng mẫu Excel không có chỗ để gõ. Chúng không bắt
 * buộc, nên tệp theo mẫu 11 cột cũ vẫn đọc được nguyên vẹn.
 *
 * <h2>Và hai cột mã địa danh</h2>
 * {@link #NGUYEN_QUAN} + {@link #MA_NGUYEN_QUAN}. Không có cột mã thì báo cáo dân số (FR-4.3)
 * không dựng được, và quyết muộn nghĩa là cả dòng họ phải nhập lại nơi ở.
 * <b>Cố ý không có cột nơi ở hiện tại:</b> với người còn sống đó là dữ liệu Tầng 2 và chỉ được
 * lấy từ chính họ tự khai, không nhập hộ.
 */
public enum ImportColumn {

    MA("Mã", true, "ma", "ma so", "so hieu", "stt so"),
    HO_TEN("Họ tên", true, "ho ten", "ho va ten", "ten", "hoten"),
    TEN_HUY("Tên huý", false, "ten huy", "huy", "ten huy ky"),
    GIOI("Giới", false, "gioi", "gioi tinh"),
    DOI("Đời", false, "doi", "doi thu", "the he"),
    MA_CHA("Mã cha", false, "ma cha", "cha", "ma bo"),
    MA_ME("Mã mẹ", false, "ma me", "me"),
    QUAN_HE("Quan hệ", false, "quan he", "quan he voi cha me", "ruot nuoi"),
    CON_SONG("Còn sống", false, "con song", "tinh trang", "song mat"),
    NAM_SINH("Năm sinh", false, "nam sinh", "sinh nam"),
    NGAY_MAT_AM("Ngày mất âm", false, "ngay mat am", "ngay gio", "gio", "ngay mat am lich"),

    // --- Bốn ca §14 thêm được cột ngay, không cần Hội đồng chốt trước ---
    KE_TU_CHO_AI("Kế tự cho ai (Mã)", false, "ke tu cho ai ma", "ke tu cho ai", "ma nguoi duoc ke tu"),
    LOAI_KE_TU("Loại kế tự", false, "loai ke tu", "loai thua tu", "kieu ke tu"),
    THUY_HIEU("Thuỵ hiệu", false, "thuy hieu", "thuy", "ten thuy"),
    TEN_HAN_NOM("Tên chữ Hán", false, "ten chu han", "han nom", "chu han", "ten han nom"),

    // --- Mã địa danh ---
    NGUYEN_QUAN("Nguyên quán", false, "nguyen quan", "que quan", "que"),
    MA_NGUYEN_QUAN("Mã nguyên quán", false, "ma nguyen quan", "ma tinh", "ma tinh quoc gia",
            "ma que quan");

    private final String tieuDe;
    private final boolean batBuoc;
    private final List<String> khoaGhep;

    ImportColumn(String tieuDe, boolean batBuoc, String... aliases) {
        this.tieuDe = tieuDe;
        this.batBuoc = batBuoc;
        this.khoaGhep = List.of(aliases);
    }

    /** Tiêu đề chính tắc, dùng khi sinh mẫu và khi báo lỗi cho người nhập. */
    public String tieuDe() {
        return tieuDe;
    }

    /** Cột thiếu hẳn khỏi tệp thì không đọc được gì cả. */
    public boolean batBuoc() {
        return batBuoc;
    }

    /**
     * Bảng tra {@code khoá đã bỏ dấu -> cột}. Dựng một lần, bất biến.
     *
     * <p>Trả {@link LinkedHashMap} để thứ tự duyệt ổn định — bộ kiểm phải tất định tới mức hai lần
     * chạy cho danh sách lỗi giống hệt nhau.</p>
     */
    public static Map<String, ImportColumn> bangTra() {
        Map<String, ImportColumn> map = new LinkedHashMap<>();
        for (ImportColumn col : values()) {
            map.put(khoa(col.tieuDe), col);
            for (String alias : col.khoaGhep) {
                map.putIfAbsent(alias, col);
            }
        }
        return map;
    }

    /** Bỏ dấu + hạ chữ thường + gộp khoảng trắng, đủ để ghép tiêu đề cột. */
    public static String khoa(String tieuDe) {
        String s = TextNormalizer.normalize(tieuDe);
        if (s == null) {
            return "";
        }
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        // Bo moi ky tu khong phai chu/so: "Ke tu cho ai (Ma)" -> "ke tu cho ai ma".
        s = s.replaceAll("[^a-z0-9]+", " ").trim();
        return s;
    }
}

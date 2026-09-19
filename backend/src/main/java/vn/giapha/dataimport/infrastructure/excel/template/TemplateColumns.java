package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.MarriageColumn;

/**
 * <b>Chỗ duy nhất</b> mà hợp đồng cột của mẫu Excel được dựng — và nó được dựng bằng cách duyệt
 * {@link ImportColumn#values()} / {@link MarriageColumn#values()}, không bằng một danh sách chép
 * tay.
 *
 * <h2>Vì sao không có danh sách tiêu đề nào ở đây</h2>
 * Một danh sách chép tay sẽ đúng vào hôm viết và sai vào hôm có người thêm cột. Triệu chứng của
 * lần sai ấy là thứ tệ nhất trong cả đường ống: <b>người dùng điền đúng mẫu vừa tải về mà hệ thống
 * báo sai định dạng</b>. Cả hai phía đều đúng theo tài liệu của riêng mình, nên không ai truy ra
 * được. Ở đây thêm một hằng số vào enum là mẫu có thêm cột <b>ngay</b>.
 *
 * <h2>Phần duy nhất phải khai tay, và nó tự bắt lỗi</h2>
 * Câu hướng dẫn và ví dụ không suy ra được từ enum. Chúng nằm trong {@code MO_TA} / {@code VI_DU},
 * và khối {@code static} dưới đây <b>ném ngay lúc nạp lớp</b> nếu một cột mới chưa được khai. Thêm
 * cột mà quên mô tả thì mọi test chạm tới bộ sinh đều đỏ — không có đường nào để một cột lặng lẽ
 * ra đời với ô hướng dẫn trống.
 */
public final class TemplateColumns {

    private static final Map<ImportColumn, String> MO_TA = new EnumMap<>(ImportColumn.class);
    private static final Map<ImportColumn, String> VI_DU = new EnumMap<>(ImportColumn.class);
    private static final Map<MarriageColumn, String> MO_TA_HP = new EnumMap<>(MarriageColumn.class);
    private static final Map<MarriageColumn, String> VI_DU_HP = new EnumMap<>(MarriageColumn.class);

    static {
        khai(ImportColumn.MA,
                "Bắt buộc. Số hiệu của người này trong sổ chi — khoá bất biến của cả đường ống."
                        + " Đặt rồi thì ĐỪNG đổi: lần nhập sau tra đúng mã này để cập nhật thay vì"
                        + " tạo thêm một người trùng.",
                "AT-03-005");
        khai(ImportColumn.HO_TEN,
                "Bắt buộc. Tên thường gọi, viết đủ dấu tiếng Việt.",
                "Nguyễn Văn Đức");
        khai(ImportColumn.TEN_HUY,
                "Tên huý (tên kiêng). Điền thì hệ thống cảnh báo được kỵ húy cho con cháu đời sau.",
                "Đức");
        khai(ImportColumn.GIOI,
                "Chọn trong danh sách. Không rõ thì để trống hoặc chọn Không rõ — đừng đoán.",
                "Nam");
        khai(ImportColumn.DOI,
                "Đời thứ mấy tính từ Thuỷ tổ của dòng họ (số nguyên, Thuỷ tổ là 1).",
                "3");
        khai(ImportColumn.MA_CHA,
                "Mã của người cha, lấy ở cột Mã. Cha ở chi khác hoặc chưa nhập thì để trống.",
                "AT-02-001");
        khai(ImportColumn.MA_ME,
                "Mã của người mẹ. Sổ cũ chép mẹ rất thưa — điền được đến đâu điền, vì bên ngoại"
                        + " ghi ngang bằng bên nội là quyết định đã chốt của dòng họ.",
                "AT-02-014");
        khai(ImportColumn.QUAN_HE,
                "Con ruột hay con nuôi. Để trống thì hiểu là con ruột.",
                "Con ruột");
        khai(ImportColumn.CON_SONG,
                "Có / Không. Để trống KHÔNG có nghĩa là còn sống — hệ thống sẽ suy từ ô Ngày mất"
                        + " âm, và nếu cũng trống thì hỏi lại.",
                "Không");
        khai(ImportColumn.NAM_SINH,
                "Năm dương lịch, 4 chữ số. Không rõ thì để trống, đừng bịa.",
                "1915");
        khai(ImportColumn.NGAY_MAT_AM,
                "NGÀY GIỖ, tính theo ÂM LỊCH. Gõ: 15/8 (không rõ năm) · 15/8/1945 · 15/8 nhuận."
                        + " Ô này đã được định dạng Văn bản sẵn, đừng đổi sang định dạng Ngày —"
                        + " Excel sẽ nuốt 15/8 thành một ngày dương lịch và ngày giỗ sẽ sai.",
                "15/8/1945");
        khai(ImportColumn.KE_TU_CHO_AI,
                "Mã của người mà người này kế tự / thừa tự. Chỉ điền khi có việc lập người nối dõi.",
                "");
        khai(ImportColumn.LOAI_KE_TU,
                "Chỉ điền khi cột Kế tự cho ai có mã: đích tôn / thừa tự / kế tự.",
                "");
        khai(ImportColumn.THUY_HIEU,
                "Tên thuỵ — tên đặt sau khi mất, dùng trong văn khấn.",
                "Phúc Trung");
        khai(ImportColumn.TEN_HAN_NOM,
                "Tên viết bằng chữ Hán / Nôm, chép từ gia phả cũ hoặc bia đá nếu có.",
                "阮文德");
        khai(ImportColumn.NGUYEN_QUAN,
                "Quê gốc, viết nguyên văn như trong sổ (làng Đông Ngạc, huyện Từ Liêm).",
                "Làng Đông Ngạc, huyện Từ Liêm");
        khai(ImportColumn.MA_NGUYEN_QUAN,
                "Mã tỉnh/quốc gia của nguyên quán, chọn trong danh sách nếu có. Danh mục chưa nạp"
                        + " thì để trống — cột chữ bên cạnh vẫn đủ dùng.",
                "VN");

        khaiHp(MarriageColumn.MA_CHONG,
                "Bắt buộc. Mã người chồng, lấy ở cột Mã trang Nhân khẩu.",
                "AT-03-005");
        khaiHp(MarriageColumn.MA_VO, "Bắt buộc. Mã người vợ.", "AT-03-006");
        khaiHp(MarriageColumn.BAC,
                "Vợ cả là 1, vợ hai là 2... Thiếu ô này thì danh xưng con của các bà tính KHÔNG"
                        + " đúng, và chuyện đó không báo lỗi ở đâu cả.",
                "1");
        khaiHp(MarriageColumn.TU_NAM, "Năm cưới (dương lịch). Không rõ thì để trống.", "1938");
        khaiHp(MarriageColumn.DEN_NAM,
                "Năm cuộc hôn nhân kết thúc. Còn đang là vợ chồng thì để trống.", "");
        khaiHp(MarriageColumn.LY_DO_KET_THUC, "Chỉ điền khi có năm kết thúc.", "");

        kiemDuKhai();
    }

    private TemplateColumns() {
    }

    /** Các cột trang Nhân khẩu, đúng thứ tự khai báo trong {@link ImportColumn}. */
    public static List<TemplateColumn> nhanKhau() {
        List<TemplateColumn> cols = new ArrayList<>(ImportColumn.values().length);
        for (ImportColumn cot : ImportColumn.values()) {
            cols.add(new TemplateColumn(cot.tieuDe(), cot.batBuoc(), MO_TA.get(cot), VI_DU.get(cot),
                    TemplateVocabulary.cua(cot)));
        }
        return List.copyOf(cols);
    }

    /** Các cột trang Hôn phối, đúng thứ tự khai báo trong {@link MarriageColumn}. */
    public static List<TemplateColumn> honPhoi() {
        List<TemplateColumn> cols = new ArrayList<>(MarriageColumn.values().length);
        for (MarriageColumn cot : MarriageColumn.values()) {
            cols.add(new TemplateColumn(cot.tieuDe(), cot.batBuoc(), MO_TA_HP.get(cot),
                    VI_DU_HP.get(cot), TemplateVocabulary.cua(cot)));
        }
        return List.copyOf(cols);
    }

    /** Vị trí cột trong trang Nhân khẩu — bộ sinh dùng để đặt định dạng và điền dòng sẵn có. */
    public static int viTri(ImportColumn cot) {
        return cot.ordinal();
    }

    /** Vị trí cột trong trang Hôn phối. */
    public static int viTri(MarriageColumn cot) {
        return cot.ordinal();
    }

    private static void khai(ImportColumn cot, String moTa, String viDu) {
        MO_TA.put(cot, moTa);
        VI_DU.put(cot, viDu);
    }

    private static void khaiHp(MarriageColumn cot, String moTa, String viDu) {
        MO_TA_HP.put(cot, moTa);
        VI_DU_HP.put(cot, viDu);
    }

    /**
     * Ném ngay lúc nạp lớp nếu một cột của enum chưa được khai mô tả.
     *
     * <p>Cố ý ném chứ không ghi log: một cột không có hướng dẫn là một cột người điền sẽ bỏ trống
     * hoặc điền sai, và ta chỉ biết chuyện đó sau khi cả chi đã nộp bài.</p>
     */
    private static void kiemDuKhai() {
        List<String> thieu = new ArrayList<>();
        for (ImportColumn cot : ImportColumn.values()) {
            if (MO_TA.get(cot) == null || VI_DU.get(cot) == null) {
                thieu.add("ImportColumn." + cot.name());
            }
        }
        for (MarriageColumn cot : MarriageColumn.values()) {
            if (MO_TA_HP.get(cot) == null || VI_DU_HP.get(cot) == null) {
                thieu.add("MarriageColumn." + cot.name());
            }
        }
        if (!thieu.isEmpty()) {
            throw new IllegalStateException(
                    "Cot moi chua duoc khai mo ta/vi du trong TemplateColumns: " + thieu
                            + ". Them cot vao enum thi phai khai o day, neu khong mau Excel se co"
                            + " mot cot khong ai biet dien gi.");
        }
    }
}

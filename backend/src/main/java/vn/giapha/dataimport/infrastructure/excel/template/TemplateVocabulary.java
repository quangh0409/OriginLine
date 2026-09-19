package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.MarriageColumn;

/**
 * Năm cột của trang Nhân khẩu và hai cột của trang Hôn phối có <b>tập giá trị đóng</b>; chúng được
 * gắn danh sách thả xuống ngay trong Excel.
 *
 * <h2>Vì sao chỉ cảnh báo, không chặn</h2>
 * Danh sách chọn ở đây đặt ở mức <i>cảnh báo</i> ({@code WARNING}) chứ không <i>chặn</i>
 * ({@code STOP}), vì {@link CellCodec} cố ý rộng rãi với từ vựng: "Trai", "M", "nam" đều là một
 * người đàn ông. Bắt Excel chặn chặt hơn máy chủ nghĩa là từ chối đúng những chữ mà máy chủ vui vẻ
 * nhận — và người điền, vốn đang chép từ một cuốn sổ viết tay, sẽ bỏ cuộc ở lần bị chặn thứ ba.
 *
 * <p>Ngược lại, đặt danh sách vào mẫu <b>vẫn đáng</b>: sai lúc gõ thì sửa ngay tại chỗ, còn sai
 * sau khi tải lên thì phải đọc bảng lỗi, mở lại tệp, tìm dòng, sửa, nộp lại.</p>
 *
 * <h2>Cột "Mã nguyên quán" không có ở đây</h2>
 * Nó là tập đóng nhưng <b>nội dung do quản trị nạp</b> ({@code place_division}), nên danh sách của
 * nó dựng lúc chạy từ danh mục thật — và khi danh mục còn rỗng thì cột ấy không có danh sách nào
 * cả. Xem {@link ImportTemplateGenerator}.
 */
public final class TemplateVocabulary {

    private static final Map<ImportColumn, TuVung> NHAN_KHAU = new EnumMap<>(ImportColumn.class);
    private static final Map<MarriageColumn, TuVung> HON_PHOI = new EnumMap<>(MarriageColumn.class);

    static {
        NHAN_KHAU.put(ImportColumn.GIOI, new TuVung("DM_GIOI", CellCodec::gioi, List.of(
                new TuVung.Muc("Nam", vn.giapha.shared.vo.Gender.MALE),
                new TuVung.Muc("Nữ", vn.giapha.shared.vo.Gender.FEMALE),
                // "Khong ro" la mot cau tra loi that: so cu thieu gioi tinh la chuyen thuong, va
                // bo kiem chi canh bao (IMP_UNKNOWN_GENDER) chu khong chan.
                new TuVung.Muc("Không rõ", vn.giapha.shared.vo.Gender.UNKNOWN))));

        NHAN_KHAU.put(ImportColumn.QUAN_HE, new TuVung("DM_QUAN_HE", CellCodec::quanHe, List.of(
                new TuVung.Muc("Con ruột", CellCodec.ParentRel.BIO),
                new TuVung.Muc("Con nuôi", CellCodec.ParentRel.ADOPT))));

        NHAN_KHAU.put(ImportColumn.CON_SONG, new TuVung("DM_CON_SONG", CellCodec::conSong, List.of(
                new TuVung.Muc("Có", Boolean.TRUE),
                new TuVung.Muc("Không", Boolean.FALSE))));

        NHAN_KHAU.put(ImportColumn.LOAI_KE_TU, new TuVung("DM_LOAI_KE_TU", CellCodec::loaiKeTu,
                List.of(
                        new TuVung.Muc("Đích tôn", CellCodec.HeirType.DICH_TON),
                        new TuVung.Muc("Thừa tự", CellCodec.HeirType.THUA_TU),
                        new TuVung.Muc("Kế tự", CellCodec.HeirType.KE_TU))));

        // "Bac" la so, khong phai chu: "vo ca" = 1. Danh sach de so tran de nguoi dien khong phai
        // doan xem "vo le" nam o bac may — CellCodec gop "vo le" vao bac 2 la mot XAP XI, va mot
        // xap xi khong nen duoc moi goi tu chinh mau ta phat ra.
        HON_PHOI.put(MarriageColumn.BAC, new TuVung("DM_BAC", CellCodec::bac, List.of(
                new TuVung.Muc("1", 1),
                new TuVung.Muc("2", 2),
                new TuVung.Muc("3", 3),
                new TuVung.Muc("4", 4))));

        HON_PHOI.put(MarriageColumn.LY_DO_KET_THUC, new TuVung("DM_LY_DO", CellCodec::lyDoKetThuc,
                List.of(
                        new TuVung.Muc("Ly hôn", CellCodec.EndReason.DIVORCE),
                        new TuVung.Muc("Qua đời", CellCodec.EndReason.DEATH),
                        new TuVung.Muc("Huỷ hôn", CellCodec.EndReason.ANNULLED),
                        new TuVung.Muc("Khác", CellCodec.EndReason.OTHER))));
    }

    private TemplateVocabulary() {
    }

    /** {@code null} khi cột không có tập giá trị đóng. */
    public static TuVung cua(ImportColumn cot) {
        return NHAN_KHAU.get(cot);
    }

    /** {@code null} khi cột không có tập giá trị đóng. */
    public static TuVung cua(MarriageColumn cot) {
        return HON_PHOI.get(cot);
    }

    /**
     * Nhãn tiếng Việt ứng với một giá trị <b>đã giải mã</b> — chiều ngược của {@link TuVung#boGiai}.
     *
     * <p>Dùng khi <b>ghi</b> dữ liệu đã có sẵn ra mẫu: nhãn ghi ra phải nằm trong đúng danh sách
     * thả xuống của cột ấy. Gõ tay chuỗi "Nam" ở chỗ ghi là mời gọi một ngày nào đó danh sách đổi
     * còn chỗ ghi thì không, và người điền nhận về một ô bị chính danh sách của mẫu tô đỏ.</p>
     *
     * @return {@code null} khi cột không có tập giá trị đóng, khi {@code giaTri} là {@code null},
     *         hoặc khi không mục nào khớp — cả ba đều nghĩa là "để trống ô"
     */
    public static String nhan(ImportColumn cot, Object giaTri) {
        return nhan(NHAN_KHAU.get(cot), giaTri);
    }

    /** Như {@link #nhan(ImportColumn, Object)}, cho trang Hôn phối. */
    public static String nhan(MarriageColumn cot, Object giaTri) {
        return nhan(HON_PHOI.get(cot), giaTri);
    }

    private static String nhan(TuVung tuVung, Object giaTri) {
        if (tuVung == null || giaTri == null) {
            return null;
        }
        return tuVung.muc().stream()
                .filter(muc -> giaTri.equals(muc.mongDoi()))
                .map(TuVung.Muc::nhan)
                .findFirst()
                .orElse(null);
    }

    /** Toàn bộ từ vựng tĩnh — dùng để dựng trang "Danh mục" và để test quét hết. */
    public static List<TuVung> tatCa() {
        List<TuVung> all = new java.util.ArrayList<>(NHAN_KHAU.values());
        all.addAll(HON_PHOI.values());
        return List.copyOf(all);
    }
}

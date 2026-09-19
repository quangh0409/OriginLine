package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.Objects;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.genealogy.application.DisclosedPerson;
import vn.giapha.shared.vo.LunarDate;

/**
 * Một người <b>đã có trong phả</b> của chi này, ở dạng sẵn sàng ghi ra một dòng Excel.
 *
 * <h2>Vì sao mẫu lại có sẵn người</h2>
 * Đây là khác biệt lớn nhất giữa "mẫu chung" và "mẫu cho chi này". Trưởng chi mở tệp ra và thấy
 * 180 người đã nhập đợt trước, kèm đúng mã của họ, nên việc còn lại là <b>viết tiếp</b>. Không có
 * phần điền sẵn thì họ khai lại từ đầu, tự đánh số mới, và mọi mã đều không khớp — lúc ấy cả 180
 * người cũ thành CREATE, chi có 360 người, và {@code IMP_MASS_CREATE_GUARD} là thứ duy nhất đứng
 * giữa chuyện đó với cuốn gia phả.
 *
 * <h2>Lớp này KHÔNG biết luật riêng tư, và đó là toàn bộ thiết kế của nó</h2>
 * Trước đây nó mang một hằng số {@code COT_TANG_1} — một <b>bản chép</b> danh sách trường Tầng 1 —
 * rồi tự xoá mọi thứ ngoài danh sách ấy trong constructor. Bản chép đó tồn tại vì
 * {@code genealogy.application} chưa mở lối gọi nào, và nó <b>đã lệch thật</b>: nó giấu chữ
 * Hán-Nôm của tên chính với người còn sống, trong khi phả đồ vẫn hiện đúng chữ ấy cho mọi thành
 * viên. Một màn hình che, màn hình kia không — đúng triệu chứng mà hai bản luật riêng tư luôn tạo
 * ra.
 *
 * <p>Nay phép lọc nằm ở <b>một</b> nơi duy nhất: {@code PrivacyTierService}, gọi qua mặt tiền
 * {@code PersonDisclosureService}. Lớp này chỉ nhận {@link DisclosedPerson} — thứ <b>đã lọc xong
 * theo đúng người đang tải mẫu về</b> — và chép ra ô Excel. Không có lối dựng nào nhận dữ liệu
 * thô, nên "quên che" không còn là một lỗi biểu diễn được: muốn ghi năm sinh của một người còn
 * sống thì phải có một {@code DisclosedPerson} chứa năm sinh ấy, mà chỉ bộ lọc mới phát ra được.</p>
 *
 * <p>Điều đó cũng có nghĩa nội dung tệp <b>phụ thuộc người tải</b>: Hội đồng Tộc biểu nhận được
 * mẫu đầy đủ hơn Trưởng chi, vì trên màn hình họ cũng thấy nhiều hơn. Đó là hành vi đúng, không
 * phải một lỗ hổng — một tệp Excel tải về là dữ liệu rời khỏi hệ thống, nên nó phải chở đúng bằng
 * thứ người ấy vốn đã được xem, không hơn.</p>
 *
 * @param hoSo phần dữ liệu <b>thuộc về {@code genealogy}</b>, đã lọc. Bắt buộc có.
 * @param ma   mã {@code EXCEL_MA} trong {@code person_external_ref} — dữ liệu của
 *             {@code dataimport}, không phải của phả
 * @param maNguyenQuan mã tỉnh/quốc gia, cột {@code native_place_code} do V9 của
 *             {@code dataimport} thêm vào bảng {@code person}. Aggregate của {@code genealogy}
 *             không chở nó, nên nó đi kèm ở đây và bị che theo <b>kết luận</b> của bộ lọc
 *             ({@link DisclosedPerson#duLieuNgoaiNhomHienDuoc()}), chứ không theo một luật chép lại
 */
public record NhanKhauDaCo(DisclosedPerson hoSo,
                           String ma,
                           String maCha,
                           String maMe,
                           String quanHe,
                           String maNguyenQuan,
                           String keTuChoAi,
                           String loaiKeTu) {

    public NhanKhauDaCo {
        Objects.requireNonNull(hoSo, "NhanKhauDaCo phai dung tu mot ho so DA LOC (DisclosedPerson)");
        if (!hoSo.duLieuNgoaiNhomHienDuoc()) {
            // Ma nguyen quan di cung nguyen quan: chung la MOT truong, chi khac o cho luu. Bo loc
            // da tra loi "khong duoc xem khoi nay" thi ca hai cung vang.
            maNguyenQuan = null;
        }
    }

    /** Giá trị của một cột, dùng khi ghi ra Excel. {@code null} nghĩa là để trống ô. */
    public String giaTri(ImportColumn cot) {
        return switch (cot) {
            case MA -> ma;
            case HO_TEN -> hoSo.thuongGoi();
            case GIOI -> TemplateVocabulary.nhan(ImportColumn.GIOI, hoSo.gender());
            case DOI -> so(hoSo.doi());
            case MA_CHA -> maCha;
            case MA_ME -> maMe;
            case QUAN_HE -> quanHe;
            case CON_SONG -> TemplateVocabulary.nhan(ImportColumn.CON_SONG, hoSo.conSong());
            case NAM_SINH -> so(hoSo.namSinh());
            case NGAY_MAT_AM -> ngayGio(hoSo.ngayGio());
            case TEN_HUY -> hoSo.huy();
            case THUY_HIEU -> hoSo.thuy();
            case TEN_HAN_NOM -> hoSo.hanNom();
            case NGUYEN_QUAN -> hoSo.nguyenQuan();
            case MA_NGUYEN_QUAN -> maNguyenQuan;
            case KE_TU_CHO_AI -> keTuChoAi;
            case LOAI_KE_TU -> loaiKeTu;
        };
    }

    /**
     * Người này còn sống — bộ sinh tô dòng khác màu để Trưởng chi để ý rằng các ô trống ở đây là
     * <b>cố ý</b>, không phải sót dữ liệu.
     */
    public boolean phaiCheTangTren() {
        return hoSo.conSong();
    }

    private static String so(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Ngày giỗ theo đúng <b>quy ước gõ</b> mà {@code LunarDeathDate.doc()} đọc lại được:
     * {@code 15/8/1945}, {@code 15/8 nhuận/1945}.
     *
     * <p>Đây là một nửa của vòng khép kín: chuỗi sinh ra ở đây phải quay lại qua bộ đọc mà không
     * mất cờ nhuận. Mất cờ ấy thì ngày giỗ lệch cả một tháng và không một lỗi nào được báo.</p>
     */
    private static String ngayGio(LunarDate gio) {
        if (gio == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder()
                .append(gio.day()).append('/').append(gio.month());
        if (gio.leapMonth()) {
            sb.append(" nhuận");
        }
        return sb.append('/').append(gio.year()).toString();
    }
}

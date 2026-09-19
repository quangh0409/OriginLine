package vn.giapha.dataimport.infrastructure.excel.export;

/**
 * Tám cột của hai trang lỗi — <b>hợp đồng cột của bản xuất</b>, đặt ở một chỗ để test ghim được.
 *
 * <h2>Bốn cột đầu là một bộ toạ độ, và chúng đi cùng nhau</h2>
 * {@link #TRANG} · {@link #DONG} · {@link #MA} · {@link #COT}. Người nhập sẽ mở <b>hai cửa sổ Excel
 * cạnh nhau</b> — bản lỗi bên trái, tệp gia phả bên phải — nên thiếu bất kỳ cột nào trong bốn cột
 * đó là bắt họ dò bằng mắt trên 400 dòng.
 *
 * <p>Vì sao cả {@code Dòng} lẫn {@code Mã}, khi một trong hai đã đủ để định vị: <b>số dòng</b> dùng
 * để nhảy tới trong Excel (Ctrl+G), còn <b>mã</b> dùng để tra ngược vào sổ giấy. Hai thao tác khác
 * nhau, hai lúc khác nhau, và người nhập cần cả hai. Thêm nữa, số dòng <b>đổi</b> khi họ chèn hay
 * xoá một dòng giữa chừng, còn mã thì không — nên khi hai cột này bất đồng, mã là bản đúng.</p>
 *
 * <h2>Vì sao {@link #GOI_Y} có cột riêng</h2>
 * Xem {@link ContextChoPhep#goiY}. Tóm tắt: đó là ô duy nhất trong cả tệp sửa được lỗi mà không cần
 * mở sổ giấy ra tra.
 *
 * <h2>Vì sao {@link #MA_LOI} đứng cuối cùng chứ không đứng đầu</h2>
 * {@code IMP_LUNAR_DATE_IS_SERIAL} không nói gì với Trưởng chi; câu tiếng Việt ở cột {@code Vấn đề}
 * mới là thứ họ đọc. Nhưng mã vẫn phải có mặt: khi họ gọi điện nhờ hỗ trợ, đọc một chuỗi ASCII qua
 * điện thoại chính xác hơn đọc lại một câu dài có dấu.
 */
enum IssueExportColumn {

    /** Tên trang <b>đúng như tab trong tệp gia phả</b>: "Nhân khẩu" · "Hôn phối" · "Cả lô". */
    TRANG("Trang", 14),

    /** Số dòng như Excel đánh. Trống với vấn đề của cả lô. */
    DONG("Dòng", 8),

    /** Cột {@code Mã} của dòng ấy. */
    MA("Mã", 16),

    /** Tên cột tiếng Việt, để biết ô nào trong dòng. */
    COT("Cột", 18),

    /** Câu bộ kiểm soạn, nguyên văn. */
    VAN_DE("Vấn đề", 70),

    /** Mã gần giống, đã xếp theo độ gần. */
    GOI_Y("Gợi ý mã gần giống", 24),

    /** Phần máy đọc được của vấn đề, đã qua {@link ContextChoPhep}. */
    CHI_TIET("Chi tiết", 46),

    /** Mã lỗi ASCII — để đọc qua điện thoại khi nhờ hỗ trợ. */
    MA_LOI("Mã lỗi", 26);

    private final String tieuDe;
    private final int doRong;

    IssueExportColumn(String tieuDe, int doRong) {
        this.tieuDe = tieuDe;
        this.doRong = doRong;
    }

    String tieuDe() {
        return tieuDe;
    }

    /** Độ rộng cột, tính bằng ký tự. */
    int doRong() {
        return doRong;
    }
}

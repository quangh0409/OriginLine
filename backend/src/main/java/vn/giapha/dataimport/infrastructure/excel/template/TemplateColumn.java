package vn.giapha.dataimport.infrastructure.excel.template;

/**
 * Một cột của mẫu, <b>đã kèm mọi thứ cần để vẽ nó ra</b>: tiêu đề, trạng thái bắt buộc, câu hướng
 * dẫn hiện khi bấm vào ô, ví dụ, và tập giá trị đóng nếu có.
 *
 * <p>Không lớp nào ngoài {@link TemplateColumns} được dựng kiểu này, và {@link TemplateColumns}
 * chỉ dựng nó từ {@link vn.giapha.dataimport.domain.ImportColumn} /
 * {@link vn.giapha.dataimport.domain.MarriageColumn}. Đó là chỗ hợp đồng cột được khoá lại.</p>
 *
 * @param tieuDe  tiêu đề chính tắc, <b>chép nguyên</b> từ enum — bộ đọc ghép cột theo chữ này
 * @param batBuoc {@code true} thì tiêu đề được vẽ kèm dấu {@code *} và tô màu, xem
 *                {@link #tieuDeHienThi()}
 * @param moTa    câu hiện trong hộp nhắc của Excel khi người điền bấm vào ô; phải nói <b>cách
 *                gõ</b>, không phải định nghĩa lại tên cột
 * @param viDu    một giá trị thật, dùng cho dòng ví dụ và cho hộp nhắc
 * @param tuVung  {@code null} khi cột nhận chữ tự do
 */
public record TemplateColumn(String tieuDe,
                             boolean batBuoc,
                             String moTa,
                             String viDu,
                             TuVung tuVung) {

    /**
     * Tiêu đề <b>như người điền nhìn thấy</b>: cột bắt buộc mang thêm dấu sao.
     *
     * <h2>Vì sao thêm dấu sao vào tiêu đề là an toàn</h2>
     * {@code ImportColumn.khoa()} bỏ mọi ký tự không phải chữ/số trước khi so khớp, nên
     * {@code "Mã *"} và {@code "Mã"} cho cùng một khoá {@code "ma"}. Đây <b>không</b> phải may mắn:
     * {@code ImportTemplateRoundTripTest} đọc lại chính tệp vừa sinh bằng
     * {@code XlsxWorkbookReader} và sẽ đỏ ngay nếu quy tắc chuẩn hoá ấy đổi.
     *
     * <p>Đánh dấu ngay trên tiêu đề là cố ý: người điền không đọc trang Hướng dẫn, họ đọc cái bảng
     * trước mặt.</p>
     */
    public String tieuDeHienThi() {
        return batBuoc ? tieuDe + " *" : tieuDe;
    }

    public boolean coTuVung() {
        return tuVung != null;
    }
}

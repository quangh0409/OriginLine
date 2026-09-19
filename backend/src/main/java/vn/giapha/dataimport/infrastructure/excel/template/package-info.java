/**
 * <b>Bộ sinh mẫu Excel</b> cho đường ống nhập liệu (hạng mục N1) — tệp {@code .xlsx} mà Trưởng chi
 * tải về, điền, rồi nộp lại qua {@code POST /api/v1/import/batches}.
 *
 * <h2>Bất biến số một: hợp đồng cột là MÃ, không phải tài liệu</h2>
 * Mọi tiêu đề cột của mẫu đều sinh từ {@link vn.giapha.dataimport.domain.ImportColumn} và
 * {@link vn.giapha.dataimport.domain.MarriageColumn} — đúng hai enum mà
 * {@link vn.giapha.dataimport.infrastructure.excel.XlsxWorkbookReader} dùng để <b>đọc</b>. Không
 * một chuỗi tiêu đề nào được gõ tay ở gói này.
 *
 * <p>Vì sao gắt đến thế: nếu mẫu sinh ra từ một danh sách cột chép tay thì ngày nào đó có người
 * thêm cột vào enum mà quên sửa bộ sinh, và triệu chứng là <b>người dùng điền đúng mẫu vừa tải về
 * mà hệ thống báo sai định dạng</b>. Không ai truy ra được vì sao, vì cả hai phía đều "đúng" theo
 * tài liệu của riêng mình. Ở đây việc lệch ấy <b>không xảy ra được</b>: thêm một hằng số vào enum
 * là mẫu có thêm cột ngay, và
 * {@code ImportTemplateColumnContractTest} đỏ nếu phần mô tả/từ vựng chưa được khai.</p>
 *
 * <h2>Vì sao dùng {@code XSSFWorkbook} để ghi trong khi bộ đọc dùng SAX</h2>
 * Chiều <b>đọc</b> nhận tệp không tin cậy nên phải chạy theo dòng (xem
 * {@link vn.giapha.dataimport.infrastructure.excel.XlsxGuards}). Chiều <b>ghi</b> thì kích thước do
 * ta quyết định: một chi 400 người ra vài trăm KB, và data validation + vùng đặt tên + khoá trang
 * chỉ viết được qua API đối tượng. Đây là lý do Apache POI được chọn thay vì một thư viện chỉ ghi
 * được bảng phẳng.
 *
 * <h2>Dữ liệu người còn sống: gói này KHÔNG giữ luật riêng tư nào</h2>
 * Một tệp tải về là dữ liệu <b>rời khỏi hệ thống</b>: không có đường thu hồi, không có bộ lọc nào
 * chạy lần thứ hai. Chính vì thế phép lọc <b>không</b> được ở đây: nó ở
 * {@code genealogy.PrivacyTierService}, chỗ duy nhất của cả hệ thống, và gói này gọi tới qua mặt
 * tiền {@code PersonDisclosureService} (named interface {@code "loc-rieng-tu"}).
 *
 * <p>Trước đó {@code NhanKhauDaCo} mang một hằng số {@code COT_TANG_1} — bản chép danh sách trường
 * Tầng 1 — và bản chép ấy đã lệch: nó giấu chữ Hán-Nôm của tên chính với người còn sống trong khi
 * phả đồ vẫn hiện đúng chữ ấy. Nay
 * {@link vn.giapha.dataimport.infrastructure.excel.template.NhanKhauDaCo} chỉ dựng được từ một
 * {@code DisclosedPerson}, tức từ thứ <b>đã lọc sẵn theo đúng người đang tải mẫu về</b>. Ghi nhầm
 * một trường không còn là chuyện "quên gọi hàm che" — nó không biểu diễn được.
 *
 * <p><b>Hệ quả phải biết:</b> nội dung tệp phụ thuộc <i>người tải</i>. Hội đồng Tộc biểu nhận mẫu
 * đầy đủ hơn Trưởng chi của cùng chi ấy, đúng bằng chênh lệch hai vai vốn đã thấy trên màn hình.
 * Đừng cache tệp theo {@code branchId}.</p>
 */
package vn.giapha.dataimport.infrastructure.excel.template;

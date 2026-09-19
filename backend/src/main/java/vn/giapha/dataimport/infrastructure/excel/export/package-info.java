/**
 * <b>Xuất danh sách lỗi ra Excel</b> — đường thoát của người nhập liệu.
 *
 * <h2>Vì sao gói này tồn tại</h2>
 * Người dùng là Trưởng chi 45–65 tuổi, nhập 350–400 người trong 6–10 buổi, và <b>không</b> phải
 * người nhập liệu chuyên nghiệp. Khi bộ kiểm kêu, chỗ họ sửa được là <b>chính tệp Excel</b> —
 * nơi họ quen tay — chứ không phải một bảng trên web bắt sửa từng dòng. Một danh sách lỗi chỉ nằm
 * trên màn hình nghĩa là đường thoát ấy không tồn tại: họ sẽ chép tay ra giấy, và chép tay 40 dòng
 * là chép sai.
 *
 * <h2>Bốn điều gói này làm khác bảng lỗi trên web</h2>
 * <ol>
 *   <li><b>Hai trang riêng</b> cho lỗi chặn và cảnh báo. Gộp chung thành "15 vấn đề" nghe như hỏng
 *       cả tệp và người nhập bấm bừa; "4 lỗi phải sửa" nghe là việc làm được. Xem
 *       {@link vn.giapha.dataimport.domain.IssueSeverity}.</li>
 *   <li><b>Toạ độ đủ để tìm lại dòng trong tệp gốc</b>: tên trang, số dòng, mã, cột. Người dùng sẽ
 *       mở hai cửa sổ Excel cạnh nhau, nên thiếu một trong bốn thứ đó là bắt họ đếm dòng bằng
 *       mắt.</li>
 *   <li><b>Gợi ý mã gần giống có cột riêng</b>. Nó là thứ duy nhất trong cả tệp sửa được lỗi ngay
 *       mà không phải mở sổ giấy ra tra, nên nó không được chìm trong một ô ghi chú.</li>
 *   <li><b>Mọi cột định dạng Văn bản {@code "@"}</b>, đúng bài học của bộ sinh mẫu: không có nó thì
 *       Excel nuốt {@code 15/8} thành ngày dương lịch và mã {@code 001} mất số 0 đầu — mà cả hai
 *       giá trị ấy đều xuất hiện trong cột "Chi tiết" của chính bảng lỗi này.</li>
 * </ol>
 *
 * <h2>Ranh giới riêng tư — phần dễ sai nhất của gói</h2>
 * Nguyên tắc của cả dự án, phát biểu thành câu kiểm chứng được: <b>được phép nói trường nào của
 * chính người nhập đã khớp; không bao giờ nói một giá trị đọc từ phả.</b>
 *
 * <p>Gói này đứng sau {@code ImportIssueRedactor} của tầng api — nhưng vẫn tự áp <b>một danh sách
 * khoá được phép riêng, theo từng mã lỗi</b> ({@link vn.giapha.dataimport.infrastructure.excel.export.ContextChoPhep}).
 * Không phải "cho chắc". Lý do nêu được: bên ghi {@code import_issue.context} và bên đọc nó bị ngăn
 * cách bởi một cơ sở dữ liệu, tức bởi <b>thời gian</b>. Dòng dữ liệu đang đọc có thể do một bản nhị
 * phân <b>cũ hơn</b> ghi ra — triển khai cuốn chiếu, khôi phục từ bản sao lưu, hoặc một lô đã kiểm
 * từ trước lần vá riêng tư gần nhất. Chốt của tầng api chỉ canh hai mã ({@code IMP_SUSPECT_DUPLICATE},
 * {@code IMP_TABOO_COLLISION}); hai mươi mã còn lại đi qua nguyên vẹn, đủ dùng cho một phản hồi
 * JSON mà giao diện chỉ đọc vài khoá đã biết, <b>không</b> đủ cho một cột "Chi tiết" đổ mọi khoá ra
 * chữ.
 *
 * <p>Và khác biệt quyết định: <b>một tệp Excel đã tải về thì không có đường thu hồi.</b> Nó đi tiếp
 * qua Zalo, qua email, qua USB, và nằm lại trên máy của người khác. Một phản hồi JSON sai có thể vá
 * rồi tải lại; một tệp sai thì không.</p>
 */
package vn.giapha.dataimport.infrastructure.excel.export;

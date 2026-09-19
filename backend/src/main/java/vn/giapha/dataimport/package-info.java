/**
 * Bounded context <b>dataimport</b> — đường ống nhập liệu hàng loạt từ tệp Excel của Trưởng chi.
 *
 * <p>Tên gói là {@code dataimport} chứ không phải {@code import} vì {@code import} là <b>từ khoá
 * Java</b>: gói tên đó không biên dịch được. Nghe buồn cười nhưng đây là thứ ăn nửa buổi của người
 * phát hiện ra muộn.</p>
 *
 * <h2>Bất biến lớn nhất của context này</h2>
 * <p>Dữ liệu tải lên nằm ở <b>khu vực chờ</b> (các bảng {@code import_*} của
 * {@code V9__dataimport.sql}) cho tới khi người nhập bấm duyệt. Ranh giới ghi nằm ở đúng
 * <b>một</b> mũi tên của máy trạng thái {@link vn.giapha.dataimport.domain.BatchStatus}
 * ({@code VALIDATED → COMMITTING}), và nó được đi qua ở đúng <b>một</b> lớp:
 * {@link vn.giapha.dataimport.application.ImportCommitWriter}, trong đúng <b>một</b> transaction
 * tất-cả-hoặc-không. Không lớp nào khác của context này được chạm vào {@code person},
 * {@code relationship} hay đồ thị AGE.</p>
 *
 * <h2>Và context này KHÔNG tự ghi — nó uỷ thác</h2>
 * <p>Cả bước ghi lẫn bước gỡ lô đều đi qua {@link vn.giapha.dataimport.domain.port.PhaWritePort},
 * mà hiện thực duy nhất của nó chỉ dịch kiểu rồi gọi lại đúng những use case của
 * {@code genealogy} mà màn "Thêm nhân khẩu" vẫn dùng. Lý do: bất biến <b>cạnh AGE và dòng
 * {@code relationship} ghi trong cùng một transaction</b> sống ở đúng một chỗ trong hệ thống, và
 * một đường ghi thứ hai dựng riêng cho nhập liệu chính là cách nó bị vi phạm sáu tháng sau. Nhập
 * liệu hàng loạt là <b>phép thử nặng nhất</b> cho bất biến ấy, không phải ngoại lệ của nó.</p>
 *
 * <h2>Bốn lớp theo kiến trúc Hexagonal</h2>
 * <p>{@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. {@code domain} và {@code application} ở đây <b>hoàn toàn không biết tới
 * context {@code genealogy}</b> — mọi lời gọi sang đó bị nhốt trong hai gói adapter:
 * {@code infrastructure.genealogy} cho các phép <b>đọc</b> (dò trùng, kỵ húy) và
 * {@code infrastructure.commit} cho các phép <b>ghi</b>. Ba tệp tất cả, để chỗ phải sửa khi ranh
 * giới module đổi là ba tệp chứ không phải cả context.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Data import — Nhập liệu hàng loạt")
package vn.giapha.dataimport;

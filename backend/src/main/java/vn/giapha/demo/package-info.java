/**
 * Sinh dữ liệu gia phả giả — chỉ chạy dưới Spring profile {@code demo} (dev/test), KHÔNG BAO GIỜ ở prod.
 *
 * <p>Nguồn yêu cầu: plan Giai đoạn 1 §10 "Sinh dữ liệu giả". Mục tiêu là mốc Sprint 2
 * "cây phả đồ hiển thị được dữ liệu thật" và bộ test kinship của Sprint 3.</p>
 *
 * <h2>Vì sao module này KHÔNG nằm trong một bounded context</h2>
 * <p>Đây là công cụ dựng môi trường, không phải nghiệp vụ. Việc ghi dữ liệu đi thẳng bằng
 * JDBC + Cypher, <b>không</b> qua repository hay application service của {@code genealogy}: tầng
 * {@code genealogy.application} không phải mặt tiền công khai của context ấy, chạm vào là vi phạm
 * ranh giới module và {@code ModularityTests} sẽ chặn.</p>
 *
 * <p>Ngoại lệ duy nhất — và là ngoại lệ có chủ đích — là {@code calendar}: ngày âm được quy đổi qua
 * {@code vn.giapha.calendar.application.LunarCalendarService}, đúng mặt tiền công khai
 * ({@code @NamedInterface}) của context ấy. Tự chép một thuật toán âm lịch riêng cho dữ liệu giả thì
 * dữ liệu demo sẽ khớp với chính nó mà lệch với hệ thống thật, và ngày giỗ hiển thị trên phả đồ sẽ
 * khác ngày trong thông báo nhắc giỗ. Dùng chung service thật còn biến mỗi lượt sinh dữ liệu thành
 * một bài kiểm tra chéo cho W4.</p>
 *
 * <h2>Bất biến bắt buộc tôn trọng khi sửa module này</h2>
 * <ul>
 *   <li>Ghi <b>cả hai nơi</b> trong CÙNG MỘT transaction: bảng {@code relationship} và cạnh trong
 *       graph AGE {@code giapha_graph}. Graph là nguồn chân lý, bảng là bản chiếu.</li>
 *   <li>Chiều cạnh PARENT là <b>cha/mẹ → con</b>: {@code (p)-[:PARENT]->(c)}.</li>
 *   <li>Node {@code Person} luôn đồng bộ 4 thuộc tính: {@code id · gender · generation · is_deleted}.</li>
 *   <li>{@code branch.path} là ltree — sinh từ {@code vn_slugify()} (V6__search.sql), không bao giờ
 *       ghép từ tên có dấu.</li>
 *   <li>{@code person.birth_order} luôn có giá trị — thiếu thì rule danh xưng không phân biệt được
 *       bác với chú.</li>
 * </ul>
 *
 * <h2>Tính tất định</h2>
 * <p>Toàn bộ dữ liệu sinh ra từ một seed cố định ({@link vn.giapha.demo.generator.DemoSeedConfig}).
 * Cùng seed + cùng code = cùng bộ UUID, nhờ đó test Sprint 3 assert được trên id cụ thể qua
 * {@link vn.giapha.demo.DemoFixtures}. Ngày "hôm nay" cũng là hằng số
 * ({@code referenceDate}) chứ không phải đồng hồ hệ thống, nếu không tỉ lệ còn sống/đã khuất sẽ
 * trôi theo thời gian và test sẽ đỏ vào một ngày đẹp trời.</p>
 */
package vn.giapha.demo;

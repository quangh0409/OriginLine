/**
 * Ghi bộ dữ liệu giả xuống PostgreSQL + Apache AGE.
 *
 * <p>Ranh giới transaction nằm ở {@link vn.giapha.demo.writer.DemoDataWriter} và <b>chỉ</b> ở đó:
 * bảng {@code relationship} và cạnh trong graph {@code giapha_graph} phải cùng sống hoặc cùng chết.
 * Đọc javadoc của lớp ấy trước khi sửa bất cứ file nào trong package này.</p>
 */
package vn.giapha.demo.writer;

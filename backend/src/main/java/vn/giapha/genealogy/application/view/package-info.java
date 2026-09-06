/**
 * <b>View</b> — mô hình đọc do tầng application trả ra, <b>đã lọc phân tầng riêng tư</b>.
 *
 * <p>Trường bị ẩn mang giá trị {@code null}. Tầng api chọn cách thể hiện: REST <b>loại hẳn</b>
 * trường khỏi JSON ({@code @JsonInclude(NON_NULL)}), GraphQL trả {@code null} vì không có khái
 * niệm trường vắng mặt. Cả hai đều giữ nguyên tính chất quan trọng nhất: <b>ẩn vì thiếu quyền</b>
 * và <b>ẩn vì không có dữ liệu</b> là không phân biệt được.</p>
 *
 * <p>Đây <b>không</b> phải aggregate: view là bản chụp đã lọc, không có hành vi nghiệp vụ, và
 * không bao giờ được ghi ngược xuống kho.</p>
 */
package vn.giapha.genealogy.application.view;

/**
 * **Bề mặt công khai của luồng "tôi là ai trong phả"**.
 *
 * Vùng tệp `components/claim/**` có hơn mười tệp, nhưng chỉ đúng những thứ dưới
 * đây được người ngoài dùng. Quan trọng nhất là {@link claimRoutes}: đó là hợp
 * đồng duy nhất giữa vùng này và **canvas phả đồ**, nơi đặt ô tìm người cùng
 * hai nút "Đây là tôi" và "Tôi chưa có trong phả".
 *
 * Bên phả đồ gọi {@code claimRoutes.forPerson(node.id)} chứ **không** tự ghép
 * chuỗi `"/nhan-dien?nguoi=" + id`: chép tay ở cả hai bên là cách chắc chắn
 * nhất để một bên đổi và bên kia im lặng dẫn tới trang trắng.
 *
 * Ba màn hình ({@code ClaimScreen}, {@code ClaimNewPersonScreen},
 * {@code ClaimPendingScreen}) cố ý **không** xuất ở đây — chúng được nạp thẳng
 * từ tệp trang tương ứng, và không thành phần nào khác được nhúng chúng vào
 * giữa một màn khác.
 */
export { claimRoutes, THAM_SO_NGUOI } from "./routes";

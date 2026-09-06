/**
 * Bounded context <b>heritage</b> — thư viện di sản, từ đường, mộ phần (GPS), media.
 *
 * <p>Khái niệm lõi: HeritageItem, Hall, Grave, MediaAsset. File nằm ở MinIO — không bao giờ lưu blob trong CSDL.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Heritage — Di sản &amp; mộ phần")
package vn.giapha.heritage;

/**
 * Bounded context <b>genealogy</b> — cây phả hệ, thêm/sửa nhân khẩu, xóa mềm, quan hệ họ hàng.
 *
 * <p>Khái niệm lõi: Person (aggregate root), PersonName (húy/tự/hiệu/thụy), Branch (chi/ngành/cành/nhánh, ltree), Relationship; port PersonRepository, TreeGraphPort.</p>
 *
 * <p>Bốn lớp theo kiến trúc Hexagonal, phụ thuộc <b>một chiều</b>:
 * {@code api → application → domain}; {@code infrastructure} hiện thực các port do
 * {@code domain} khai báo. Context khác chỉ được gọi qua application service public
 * của context này hoặc qua domain event — không bao giờ chạm thẳng repository.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Genealogy — Phả hệ")
package vn.giapha.genealogy;

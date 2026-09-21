/**
 * Tầng <b>infrastructure</b> của context {@code content}: hiện thực các port do {@code domain}
 * khai báo.
 *
 * <p>Hai gói con, và ranh giới giữa chúng là ranh giới kỹ thuật:</p>
 * <ul>
 *   <li>{@code jpa} — bảng {@code post} và {@code honour}, do context này sở hữu. Ghi bằng JPA,
 *       đọc có lọc phạm vi bằng native query {@code ltree}.</li>
 *   <li>{@code branch} — đọc bảng {@code branch} và cột {@code person.primary_branch_id}, vốn
 *       thuộc {@code genealogy}. <b>Chỉ SELECT</b>, và tuyệt đối không đọc một trường dữ liệu cá
 *       nhân nào: tên, ngày sinh, liên hệ chỉ ra khỏi hệ thống qua bộ lọc riêng tư của
 *       {@code genealogy}, không qua một câu SQL của context này.</li>
 * </ul>
 */
package vn.giapha.content.infrastructure;

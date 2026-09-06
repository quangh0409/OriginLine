/**
 * Adapter phân giải <b>danh tính người gọi</b>: {@code keycloak_sub → app_user → person} và phạm vi
 * chi/ngành phục vụ RBAC theo {@code ltree}.
 *
 * <p>Ở Giai đoạn 1 gói này từng đọc thẳng {@code app_user} và {@code branch_assignment} bằng SQL
 * chỉ-đọc để W2/W7 không phải chờ W6. <b>Khoản nợ đó đã trả:</b> adapter nay gọi
 * {@code MemberScopeService} do context {@code membership} công bố, không còn SQL và không còn hai
 * chỗ cùng biết cấu trúc bảng của context khác.</p>
 */
package vn.giapha.genealogy.infrastructure.security;

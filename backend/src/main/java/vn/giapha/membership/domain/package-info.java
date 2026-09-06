/**
 * Tầng <b>domain</b> của context {@code membership}: {@code AppUser}, {@code Role},
 * {@code BranchAssignment}, {@code ChangeRequest}, và {@code MemberScope} — ảnh chụp hai chiều
 * phân quyền (vai trò × phạm vi {@code ltree}).
 *
 * <p><b>Quy tắc bất di bất dịch:</b> POJO thuần — không {@code @Entity}, không {@code @Component},
 * không import {@code org.springframework.*} hay {@code jakarta.persistence.*}. Entity JPA và
 * adapter nằm ở {@code vn.giapha.membership.infrastructure}.</p>
 */
package vn.giapha.membership.domain;

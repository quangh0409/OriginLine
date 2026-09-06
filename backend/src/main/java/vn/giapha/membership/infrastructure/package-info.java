/**
 * Tầng <b>infrastructure</b> của context {@code membership}: entity JPA + repository Spring Data
 * cho {@code app_user} / {@code role} / {@code branch_assignment} / {@code change_request}, adapter
 * tra cứu chi bằng SQL, và adapter cung cấp người thực hiện cho context {@code audit}.
 *
 * <p>Đây là nơi <b>duy nhất</b> được phép mang annotation JPA.</p>
 */
package vn.giapha.membership.infrastructure;

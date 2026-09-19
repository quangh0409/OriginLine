/**
 * Adapter phục vụ luồng mời người vào hệ thống.
 *
 * <p>Hai thứ: tra nhân khẩu được mời (chỉ đọc {@code person}/{@code person_name}/{@code branch} —
 * xem {@code vn.giapha.membership.domain.port.InviteeLookupPort} về khoản nợ kiến trúc), và bộ đếm
 * số lần thử mã phục vụ giới hạn tần suất.</p>
 */
package vn.giapha.membership.infrastructure.invite;

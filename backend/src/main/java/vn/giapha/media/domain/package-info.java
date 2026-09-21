/**
 * Domain của context {@code media} — <b>POJO thuần</b>, không một annotation Spring hay JPA nào.
 *
 * <p>Ba lớp ở đây không phụ thuộc gì ngoài JDK và {@code shared.exception}, nên chúng kiểm thử
 * được mà không dựng Spring context: {@link vn.giapha.media.domain.MediaSignature} (dò chữ ký
 * byte), {@link vn.giapha.media.domain.VideoHeaderProbe} (đọc thời lượng từ header container) và
 * {@link vn.giapha.media.domain.MediaLimits} (mọi con số trần). Đó là có chủ ý: ba lớp ấy chứa
 * toàn bộ phần "tệp này có hợp lệ không", và phần ấy phải kiểm được bằng một mảng byte dựng tay.</p>
 */
package vn.giapha.media.domain;

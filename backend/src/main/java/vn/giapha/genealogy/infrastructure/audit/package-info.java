/**
 * Adapter ghi <b>nhật ký thay đổi</b> vào bảng {@code audit_log}.
 *
 * <p>Bảng này là <b>chỉ ghi thêm</b> (trigger chặn UPDATE), nên mọi thứ lọt vào đây là vĩnh viễn.
 * Đó là lý do dữ liệu Tầng 3 bị che trước khi ghi chứ không trông vào kỷ luật của bên gọi.</p>
 *
 * <p>Việc che nay do {@code SensitiveFieldRedactor} của context {@code audit} đảm nhiệm, gọi qua
 * {@code AuditTrailService}. Trước W6 gói này có bản che riêng ({@code Tier3Redactor}); hai danh
 * sách khoá nhạy cảm song song đã bắt đầu lệch nhau, nên bản trùng lặp đã bị xoá.</p>
 */
package vn.giapha.genealogy.infrastructure.audit;

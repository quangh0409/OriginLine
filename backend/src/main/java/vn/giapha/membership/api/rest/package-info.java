/**
 * REST controller của context {@code membership} ({@code /api/v1}).
 *
 * <p>Chỉ gọi xuống application service; không chứa business logic. Đặc biệt: <b>không</b> kiểm
 * quyền theo phạm vi chi/ngành ở đây — phạm vi phụ thuộc vào chi đích của từng bản ghi, thứ mà một
 * biểu thức {@code @PreAuthorize} chạy trước khi nạp dữ liệu không nhìn thấy. Cửa kiểm thật là
 * {@code BranchScopeGuard} ở tầng application.</p>
 */
package vn.giapha.membership.api.rest;

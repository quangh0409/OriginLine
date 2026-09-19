/**
 * Lớp phụ trợ của tầng api: phân quyền theo phạm vi, cổng duyệt, bảng tra chi, và hai bộ hằng
 * ({@code ImportProblemCodes}, {@code ImportDuplicatePolicy}).
 *
 * <p><b>Khoản nợ đã biết:</b> {@code ImportBranchDirectory} đọc bảng {@code branch} bằng SQL và
 * đúng ra thuộc {@code infrastructure}; {@code ImportScopeGuard} chép lại một dòng luật của
 * {@code membership.application.BranchScopeGuard} vì hàm gốc nhận kiểu domain chưa được công bố.
 * Cả hai là chỗ đặt tạm, và cả hai đều là phép di chuyển tệp khi ranh giới được mở — xem javadoc
 * từng lớp.</p>
 */
package vn.giapha.dataimport.api.support;

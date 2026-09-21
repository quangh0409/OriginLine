/**
 * Tầng <b>api</b> của context {@code content}: controller REST, DTO và ánh xạ sang JSON.
 *
 * <p>Tầng này <b>không</b> chứa luật nghiệp vụ hay luật phân quyền. Cụ thể: không có
 * {@code @PreAuthorize} cho việc duyệt, vì quyền duyệt phụ thuộc <i>chi của từng bản ghi</i> mà
 * controller chưa nạp bản ghi thì chưa biết chi ấy là gì — một annotation ở đây sẽ cho Trưởng chi
 * Ất đi qua cửa rồi mới chặn ở trong, và tệ hơn, nó tạo cảm giác đã kiểm quyền xong. Cửa kiểm thật
 * ở {@code PostService}/{@code HonourService} → {@code BranchScopeGuard} → so {@code ltree}. Đặt ở
 * tầng use case cũng có nghĩa một lối vào GraphQL hay một job nền sau này đều đi qua đúng phép kiểm
 * đó.</p>
 */
package vn.giapha.content.api;

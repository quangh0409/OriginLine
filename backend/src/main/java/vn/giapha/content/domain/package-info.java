/**
 * Tầng <b>domain</b> của context {@code content}: aggregate {@code Post} và {@code Honour}, máy
 * trạng thái duyệt dùng chung, và các port mà {@code infrastructure} hiện thực.
 *
 * <p><b>POJO thuần</b> — không một annotation Spring hay JPA nào. Bản chiếu JPA nằm ở
 * {@code content.infrastructure.jpa}. Hai aggregate ở đây cố ý <b>không</b> tự kiểm quyền: chúng
 * không biết {@code ltree}, không biết vai trò, không biết ai đang đăng nhập — việc so phạm vi là
 * của {@code membership.BranchScopeGuard}, và các phương thức chuyển trạng thái chỉ nhận
 * {@code reviewerId} sau khi phép so ấy đã qua.</p>
 */
package vn.giapha.content.domain;

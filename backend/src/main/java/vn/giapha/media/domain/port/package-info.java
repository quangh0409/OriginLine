/**
 * Cổng (port) mà {@code media} khai báo và {@code infrastructure} hiện thực.
 *
 * <p><b>Gói này KHÔNG mang {@code @NamedInterface}.</b> Mở cả gói ra nghĩa là mọi module đều với
 * tới được {@code ObjectStoragePort} và ba repository — tức là với tới được cả kho đối tượng lẫn
 * bảng {@code media_asset}, bỏ qua mọi phép kiểm quyền của
 * {@link vn.giapha.media.application.MediaAccessGuard}. Đúng một kiểu trong gói này được công bố,
 * và nó được công bố <b>theo kiểu</b>:
 * {@link vn.giapha.media.domain.port.MediaOwnerAccessPort} — vì nó là cổng <i>hiện thực từ bên
 * ngoài</i>, cùng khuôn với {@code audit.domain.port.AuditActorPort}.</p>
 */
package vn.giapha.media.domain.port;

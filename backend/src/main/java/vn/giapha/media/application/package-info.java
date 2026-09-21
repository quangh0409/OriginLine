/**
 * Tầng use case của context {@code media}.
 *
 * <p><b>Gói này KHÔNG mang {@code @NamedInterface}.</b> Mở cả gói ra nghĩa là mọi module đều với
 * tới được {@code MediaUploadService} (phát URL ký), {@code MediaViewService} (ký URL đọc),
 * {@code MediaGcService} (xoá byte) và {@code MediaAccessGuard} — tức là với tới được kho đối
 * tượng bỏ qua mọi phép kiểm quyền. Đúng <b>hai kiểu</b> mang nhãn {@code "gan-tep"}, cả hai được
 * gắn <i>theo kiểu</i>, không theo gói:</p>
 *
 * <ul>
 *   <li>{@link vn.giapha.media.application.MediaLinkService} — mặt tiền gắn/gỡ/đọc tệp của một
 *       bản ghi, với chữ ký nói bằng khái niệm của bên gọi
 *       ({@code attachToPost}, {@code setPersonAvatar}) chứ không bằng enum của {@code domain};</li>
 *   <li>{@link vn.giapha.media.application.view.MediaAssetView} — một tệp ở dạng chỉ gồm kiểu
 *       nguyên thuỷ, đã kèm URL đã ký.</li>
 * </ul>
 *
 * <p>Ngoài ra còn {@link vn.giapha.media.domain.event} ({@code @NamedInterface("events")}) — lối
 * ngược duy nhất, đúng một sự kiện.</p>
 *
 * <p><b>Mở rộng bề mặt này chỉ khi nói được lý do.</b> Mỗi lần nới đều có vẻ hợp lý một mình;
 * gộp lại chúng xoá mất ranh giới — cảnh báo này đã có ở {@code genealogy} và nó đúng ở đây hơn,
 * vì thứ nằm sau ranh giới lần này là chính kho tệp.</p>
 */
package vn.giapha.media.application;

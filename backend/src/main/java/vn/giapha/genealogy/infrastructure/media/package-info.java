/**
 * Hiện thực cổng của {@code media} mà {@code genealogy} là chủ sở hữu bản ghi.
 *
 * <p>Một lớp: {@link vn.giapha.genealogy.infrastructure.media.PersonAvatarAccessAdapter}. Đây là
 * chỗ <b>quyết định số 3 của chủ dự án</b> được thi hành — ảnh chân dung đi qua nhóm trường
 * {@code birthDetailAndPhoto} đã có sẵn, không có luật riêng tư mới.</p>
 *
 * <p>Chiều phụ thuộc vẫn là {@code genealogy → media}: {@code media} chỉ khai giao diện. Lối ngược
 * duy nhất là một domain event ({@code PersonAvatarRemovedEvent}), bắt ở
 * {@code genealogy.application.AvatarRemovalListener}.</p>
 */
package vn.giapha.genealogy.infrastructure.media;

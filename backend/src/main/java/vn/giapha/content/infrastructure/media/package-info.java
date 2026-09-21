/**
 * Hiện thực cổng của {@code media} mà {@code content} là chủ sở hữu bản ghi.
 *
 * <p>Một lớp: {@link vn.giapha.content.infrastructure.media.PostMediaAccessAdapter}. Đây là chỗ
 * <b>quyết định số 1 của chủ dự án</b> được thi hành — ảnh trong bài đi theo quyền của <i>bài</i>,
 * không theo bộ lọc nhóm trường của từng người có mặt trong ảnh.</p>
 *
 * <p>Chiều phụ thuộc vẫn là {@code content → media}: {@code media} chỉ khai giao diện, không gọi
 * ngược. Không có chu trình.</p>
 */
package vn.giapha.content.infrastructure.media;

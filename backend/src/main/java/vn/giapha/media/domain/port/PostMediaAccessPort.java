package vn.giapha.media.domain.port;

/**
 * {@code content} trả lời: người gọi hiện tại có xem/sửa được tệp đính kèm của <b>bài viết</b> này
 * không, và bài ấy thuộc chi nào.
 *
 * <p>Đây là nơi <b>quyết định số 1 của chủ dự án</b> được thi hành, và nó nằm ở {@code content}
 * chứ không ở {@code media} một cách có chủ ý: <i>ảnh trong bài đi theo quyền của BÀI</i>, không
 * theo bộ lọc nhóm trường của từng người có mặt trong ảnh. Nên câu trả lời của
 * {@link #canView(java.util.UUID)} phải <b>giống hệt</b> câu trả lời cho "người này có đọc được
 * bài ấy không" — một bài đã đăng thì mọi thành viên xem được ảnh trong đó, kể cả khi trong ảnh có
 * người đang để {@code birthDetailAndPhoto} = {@code PRIVATE}.</p>
 *
 * <p>Cái giá của quyết định ấy được trả bằng đường báo gỡ ({@code media_report}). Nếu một ngày
 * đường ấy bị bỏ đi, quyết định này phải được xét lại cùng lúc — chúng là một cặp.</p>
 */
@org.springframework.modulith.NamedInterface("cong-chu-so-huu")
public interface PostMediaAccessPort extends MediaOwnerAccessPort {
}

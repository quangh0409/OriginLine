package vn.giapha.media.domain.event;

import java.util.UUID;
import vn.giapha.shared.domain.BaseDomainEvent;

/**
 * Ảnh chân dung của một nhân khẩu vừa bị <b>gỡ theo một đơn báo vi phạm</b>.
 *
 * <h2>Vì sao phải có một sự kiện, chứ không gọi thẳng sang {@code genealogy}</h2>
 * Vì {@code genealogy → media} là chiều phụ thuộc đã chọn (để gắn/gỡ tệp), nên
 * {@code media → genealogy} sẽ tạo chu trình và làm {@code ModularityTests} đỏ. Sự kiện là lối
 * ngược duy nhất mà kiến trúc cho phép.
 *
 * <h2>Chuyện gì hỏng nếu thiếu nó</h2>
 * Khoá ảnh chân dung ({@code person.attributes -> '_profile' ->> 'avatarKey'}) là nguồn chân lý
 * cho ô avatar, và nó nằm ở bảng của {@code genealogy}. Người duyệt gỡ tấm ảnh, byte biến mất khỏi
 * kho — nhưng khoá ấy vẫn trỏ vào một đối tượng không còn nữa. Hồ sơ ấy sẽ mãi mãi hiện một ô ảnh vỡ, và triệu chứng
 * trông <i>y hệt</i> một lỗi hạ tầng ("MinIO hỏng à?") chứ không giống một tấm ảnh đã bị gỡ đúng
 * quy trình. {@code genealogy.application.AvatarRemovalListener} bắt sự kiện này và xoá con trỏ.
 *
 * <p>Sự kiện chỉ chở <b>định danh</b>, đúng quy ước của dự án — không chở khoá đối tượng, vì một
 * khoá là thứ ký được thành URL đọc và các đường ghi log sự kiện không có bộ lọc riêng tư.</p>
 */
public final class PersonAvatarRemovedEvent extends BaseDomainEvent {

    private final UUID personId;
    private final UUID mediaId;

    public PersonAvatarRemovedEvent(UUID personId, UUID mediaId) {
        this.personId = personId;
        this.mediaId = mediaId;
    }

    public UUID personId() {
        return personId;
    }

    public UUID mediaId() {
        return mediaId;
    }
}

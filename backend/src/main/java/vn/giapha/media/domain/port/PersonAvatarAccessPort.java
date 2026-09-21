package vn.giapha.media.domain.port;

/**
 * {@code genealogy} trả lời: người gọi hiện tại có xem/đặt được <b>ảnh chân dung</b> của nhân khẩu
 * này không.
 *
 * <p>Đây là nơi <b>quyết định số 3 của chủ dự án</b> được thi hành: ảnh chân dung đi qua nhóm
 * trường <b>{@code birthDetailAndPhoto} đã có sẵn</b> (V8/V17) — <i>không</i> có luật riêng tư mới,
 * <i>không</i> có khoá đồng thuận thứ bảy, <i>không</i> sửa {@code is_valid_privacy_consent()}.
 * Hiện thực phải gọi đúng bản luật đang chạy ({@code PrivacyTierService} qua
 * {@code PersonDisclosureService}), không được chép lại phép so.</p>
 *
 * <p>Hệ quả cần nhớ khi đọc mã: ảnh chân dung của một <b>người còn sống</b> để nhóm trường ấy ở
 * {@code PRIVATE} thì {@code canView} trả {@code false} với mọi người trừ chính chủ và quản trị —
 * kể cả khi người hỏi đang cầm đúng khoá đối tượng. Khoá không phải giấy thông hành; mỗi lần ký
 * URL là một lần bộ lọc chạy lại.</p>
 */
@org.springframework.modulith.NamedInterface("cong-chu-so-huu")
public interface PersonAvatarAccessPort extends MediaOwnerAccessPort {
}

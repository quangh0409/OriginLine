package vn.giapha.membership.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt dùng mã mời dòng họ: <b>ai</b>, <b>mã nào</b>, <b>lúc nào</b>.
 *
 * <p>Cố ý <b>không</b> mang email hay tên tự khai: {@link #appUserId()} đã trỏ tới đủ mọi thứ ấy và
 * bản thân nó đi qua bộ lọc riêng tư khi hiển thị. Chép lại ở đây là tạo một bản sao dữ liệu cá
 * nhân nằm ngoài mọi bộ lọc — và một bản sao thì không ai nhớ mà xoá khi có yêu cầu xoá dữ liệu
 * theo Nghị định 13/2023.</p>
 *
 * @param appUserId tài khoản đã dùng mã
 * @param at        thời điểm dùng
 */
public record ClanInviteRedemption(long id, UUID codeId, UUID appUserId, Instant at) {
}

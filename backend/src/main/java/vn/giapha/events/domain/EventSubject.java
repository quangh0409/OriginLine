package vn.giapha.events.domain;

import java.util.UUID;
import vn.giapha.shared.vo.LunarDate;

/**
 * Ảnh chụp gọn của nhân khẩu chủ thể một sự kiện — vừa đủ để dựng tiêu đề thông báo và trả về
 * {@code EventDto.person}.
 *
 * <p><b>Chỉ chứa dữ liệu Tầng 1</b> (tên hiển thị, đời, còn sống hay đã khuất). Không có số điện
 * thoại, email hay địa chỉ: thông báo đẩy hiện trên màn hình khoá của thiết bị, và dữ liệu Tầng 3
 * lọt vào đó là vi phạm Nghị định 13/2023 — hợp đồng OpenAPI cũng nói thẳng điều này.</p>
 *
 * @param deathLunar ngày mất âm lịch, <b>nguồn chân lý của ngày giỗ</b> (BA v2); {@code null} với
 *                   người còn sống hoặc hồ sơ khuyết ngày
 */
public record EventSubject(UUID personId,
                           String displayName,
                           String nameHanNom,
                           Integer generation,
                           boolean alive,
                           LunarDate deathLunar,
                           BranchSnapshot primaryBranch) {

    /**
     * Chi/ngành dạng rút gọn.
     *
     * @param path đường dẫn {@code ltree} — vừa là phạm vi RBAC vừa là phạm vi người nhận nhắc
     */
    public record BranchSnapshot(UUID id, String name, String path, String region) {
    }
}

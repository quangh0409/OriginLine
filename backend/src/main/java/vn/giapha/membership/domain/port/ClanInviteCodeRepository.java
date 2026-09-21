package vn.giapha.membership.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.ClanInviteCode;

/**
 * Lưu trữ mã mời dòng họ. Khoá tra cứu của người cầm mã là <b>băm của mã</b>, không phải id.
 *
 * <p>Không có phương thức nào nhận mã thô: mã thô không được rời tầng application, và một chữ ký
 * {@code byCode(String)} ở đây sớm muộn sẽ dẫn tới một câu log in ra tham số của nó.</p>
 */
public interface ClanInviteCodeRepository {

    Optional<ClanInviteCode> byId(UUID id);

    /** Tra theo {@code ux_clan_invite_code_hash}. Băm do {@code InvitationCode.hash} dựng. */
    Optional<ClanInviteCode> byCodeHash(String codeHash);

    /**
     * Toàn bộ mã, mới nhất trước — màn phát mã của Hội đồng.
     *
     * <p><b>Không</b> lọc theo chi: mã dòng họ cấp cho cả họ, nên nó không có chi để lọc theo, và
     * chỉ vai toàn dòng họ mới thấy được danh sách này. Phép kiểm quyền nằm ở tầng application.</p>
     */
    List<ClanInviteCode> all(int limit, int offset);

    ClanInviteCode save(ClanInviteCode code);

    /**
     * <b>Chốt 3 — tăng bộ đếm, nguyên tử, có điều kiện.</b> Trả {@code true} nếu thật sự tiêu được
     * một lượt.
     *
     * <h2>Vì sao đây là một câu UPDATE chứ không phải {@code load → useCount+1 → save}</h2>
     * Hai người bấm "Đăng ký" cùng lúc thì lối đọc-rồi-ghi đếm thành <b>một</b> lượt: cả hai đọc
     * cùng một con số rồi cùng ghi con số ấy cộng một. Một bộ đếm đếm thiếu còn tệ hơn không có bộ
     * đếm, vì nó tạo cảm giác an toàn giả — Hội đồng nhìn thấy 200 trong khi thực tế là 400 và kết
     * luận mã chưa rò. Khoá lạc quan {@code @Version} cũng không cứu: nó biến lần thứ hai thành một
     * ngoại lệ, tức một người đăng ký hợp lệ bị từ chối vì người khác bấm cùng giây.
     *
     * <h2>Điều kiện nằm TRONG mệnh đề WHERE, không ở tầng trên</h2>
     * Còn hạn · chưa thu hồi · chưa chạm trần. Kiểm ở tầng trên rồi mới gọi thì giữa hai thời điểm
     * ấy Hội đồng có thể vừa thu hồi mã, và trần {@code max_uses} có thể bị vượt bởi đúng số người
     * bấm đồng thời. Phép kiểm ở tầng trên vẫn giữ — nó cho ra <i>thông điệp đúng</i> cho người
     * dùng — nhưng thứ <i>bảo đảm</i> là câu lệnh này.
     *
     * @return {@code false} khi mã không còn dùng được tại {@code now}; lúc đó bộ đếm không đổi
     */
    boolean tryConsume(UUID codeId, Instant now);
}

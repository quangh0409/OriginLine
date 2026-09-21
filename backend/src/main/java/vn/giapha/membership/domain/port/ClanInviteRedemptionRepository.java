package vn.giapha.membership.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.giapha.membership.domain.ClanInviteRedemption;

/**
 * "Ai đã dùng mã nào" — nhật ký để truy người đưa mã ra ngoài.
 *
 * <p>{@code use_count} nói <b>bao nhiêu</b> lượt; bảng sau cổng này nói <b>ai</b>. Khi mã lan ra
 * ngoài, đây là thứ duy nhất truy được nguồn — và với 1.500 người thì đó là câu hỏi Hội đồng
 * <i>sẽ</i> hỏi, không phải có thể hỏi.</p>
 */
public interface ClanInviteRedemptionRepository {

    /**
     * Ghi một lượt dùng. <b>Nhiều nhất một dòng cho mỗi cặp (mã, tài khoản)</b> —
     * {@code ux_clan_redemption_once}.
     *
     * @return {@code true} nếu thật sự ghi mới; {@code false} nếu tài khoản này đã dùng mã ấy rồi
     */
    boolean record(UUID codeId, UUID appUserId, String clientKeyHash, Instant at);

    /** Những ai đã dùng một mã, mới nhất trước. */
    List<ClanInviteRedemption> byCode(UUID codeId, int limit, int offset);
}

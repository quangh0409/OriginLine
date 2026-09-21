package vn.giapha.membership.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.giapha.membership.domain.ClanInviteCode;
import vn.giapha.membership.domain.ClanInviteRedemption;
import vn.giapha.membership.domain.ClanInviteUsability;
import vn.giapha.membership.domain.port.ClanInviteCodeRepository;
import vn.giapha.membership.domain.port.ClanInviteRedemptionRepository;

/**
 * Hai kho dữ liệu của luồng <b>mã mời dòng họ</b> trong bộ nhớ.
 *
 * <p>Hai ràng buộc của lược đồ V16 được mô phỏng thật, vì không có chúng thì test sẽ xanh trên một
 * trạng thái CSDL từ chối: {@code tryConsume} là một phép tăng <b>có điều kiện</b> (còn hạn · chưa
 * thu hồi · chưa chạm trần), và {@code ux_clan_redemption_once} chặn cặp (mã, tài khoản) trùng.</p>
 *
 * <p>Bộ test tích hợp {@code ClanInviteFlowIT} kiểm cùng các bất biến ấy trên Postgres thật; bản
 * này tồn tại để kiểm những ca cần dựng sẵn một realm Keycloak có tài khoản cũ — thứ môi trường
 * tích hợp không có (cổng danh tính ở đó chưa được cấu hình và trả 503).</p>
 */
public final class InMemoryClanInviteRepositories {

    private final Map<UUID, ClanInviteCode> codes = new LinkedHashMap<>();
    private final Map<UUID, Integer> useCounts = new LinkedHashMap<>();
    private final Set<String> redemptions = new LinkedHashSet<>();
    private final List<ClanInviteRedemption> log = new ArrayList<>();

    public final ClanInviteCodeRepository codeRepository = new Codes();
    public final ClanInviteRedemptionRepository redemptionRepository = new Redemptions();

    /** Số lượt đã tiêu của một mã — cùng con số Hội đồng nhìn thấy. */
    public int useCountOf(UUID codeId) {
        return useCounts.getOrDefault(codeId, 0);
    }

    public List<ClanInviteRedemption> redemptionsOf(UUID codeId) {
        return log.stream().filter(row -> row.codeId().equals(codeId)).toList();
    }

    private ClanInviteCode withUseCount(ClanInviteCode code) {
        return new ClanInviteCode(code.id(), code.codeHash(), code.label(), code.issuedBy(),
                code.status(), code.expiresAt(), code.maxUses(), useCountOf(code.id()),
                code.revokedAt(), code.revokedReason(), code.note(), code.createdAt(),
                code.version());
    }

    private final class Codes implements ClanInviteCodeRepository {

        @Override
        public Optional<ClanInviteCode> byId(UUID id) {
            return Optional.ofNullable(codes.get(id)).map(InMemoryClanInviteRepositories.this::withUseCount);
        }

        @Override
        public Optional<ClanInviteCode> byCodeHash(String codeHash) {
            return codes.values().stream()
                    .filter(code -> code.codeHash().equals(codeHash))
                    .findFirst()
                    .map(InMemoryClanInviteRepositories.this::withUseCount);
        }

        @Override
        public List<ClanInviteCode> all(int limit, int offset) {
            return codes.values().stream()
                    .map(InMemoryClanInviteRepositories.this::withUseCount)
                    .sorted(Comparator.comparing(ClanInviteCode::id))
                    .skip(Math.max(offset, 0))
                    .limit(Math.max(limit, 1))
                    .toList();
        }

        @Override
        public ClanInviteCode save(ClanInviteCode code) {
            codes.put(code.id(), code);
            useCounts.putIfAbsent(code.id(), code.useCount());
            return withUseCount(code);
        }

        @Override
        public boolean tryConsume(UUID codeId, Instant now) {
            ClanInviteCode code = codes.get(codeId);
            if (code == null) {
                return false;
            }
            // Dieu kien nam TRONG cau UPDATE o ban that — mo phong dung the, neu khong thi test se
            // xanh tren mot tran luot dung khong duoc canh.
            if (withUseCount(code).usabilityAt(now) != ClanInviteUsability.USABLE) {
                return false;
            }
            useCounts.merge(codeId, 1, Integer::sum);
            return true;
        }
    }

    private final class Redemptions implements ClanInviteRedemptionRepository {

        @Override
        public boolean record(UUID codeId, UUID appUserId, String clientKeyHash, Instant at) {
            // ux_clan_redemption_once: mot tai khoan dem mot luot tren mot ma.
            if (!redemptions.add(codeId + "/" + appUserId)) {
                return false;
            }
            log.add(new ClanInviteRedemption(log.size() + 1L, codeId, appUserId, at));
            return true;
        }

        @Override
        public List<ClanInviteRedemption> byCode(UUID codeId, int limit, int offset) {
            return redemptionsOf(codeId).stream()
                    .skip(Math.max(offset, 0))
                    .limit(Math.max(limit, 1))
                    .toList();
        }
    }
}

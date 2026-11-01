package vn.giapha.kinship.application;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.kinship.domain.KinshipRuleSet;
import vn.giapha.kinship.domain.Region;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.kinship.domain.RuleSetRepository;

/**
 * Bộ luật danh xưng <b>tầng {@code CLAN}</b> của dòng họ — named interface
 * {@code "bo-luat-dong-ho"}, mặt tiền duy nhất context {@code kinship} mở ra ngoài.
 *
 * <h2>Việc của lớp này: nối tầng CLAN vào đúng tầng REGION</h2>
 * Khi quản trị đặt/đổi vùng của dòng họ (hồ sơ ở context {@code clan}), bộ luật tầng
 * {@code CLAN} — gắn với chi gốc {@code DONG_HO}, đúng hình dạng {@code ck_rule_set_branch_shape}
 * của V3 — được <b>tạo</b> nếu chưa có, hoặc <b>nối lại cha</b> nếu vùng đổi:
 * <ul>
 *   <li>cha là bộ {@code REGION} <b>đang bật</b> của vùng ấy;</li>
 *   <li>vùng ấy chưa có bộ nào đang bật (hôm nay: cả ba vùng — bộ "kĩnh" của miền Trung còn tắt
 *       chờ Hội đồng chốt) ⇒ cha là bộ {@code DEFAULT} (lối miền Bắc). Không bịa bộ REGION rỗng:
 *       một bộ rỗng trông như "đã cấu hình miền Trung" trong khi thật ra không có gì.</li>
 * </ul>
 *
 * <h2>LUẬT HỘI ĐỒNG ĐÃ SỬA Ở TẦNG CLAN KHÔNG BAO GIỜ BỊ GHI ĐÈ</h2>
 * Nếu bộ tầng {@code CLAN} đã có luật riêng (Hội đồng sửa qua {@code /kinship-rules}), lớp này
 * <b>chỉ đổi cha kế thừa</b> bằng {@link RuleSetRepository#reparent} — không xoá, không chèn lại,
 * không đụng mô tả. Luật của Hội đồng vẫn ghi đè luật của tầng cha theo {@code relation_code}
 * như cũ; chỉ phần "không khai thì lấy của ai" là đổi theo vùng mới. Đó là toàn bộ nghĩa của
 * "đổi vùng": đổi <i>mặc định</i>, không đổi <i>quyết định</i>.
 *
 * <h2>Hệ quả đã biết, cần nói ra</h2>
 * {@code RuleSetJpaRepository.resolveFor} chọn {@code BRANCH → CLAN → REGION(theo vùng của chi)}.
 * Có bộ tầng {@code CLAN} rồi thì một chi khai vùng khác với dòng họ (ví dụ Chi Đinh di cư vào
 * Trung Bộ) <b>không còn</b> tự rơi về bộ REGION của vùng chi nữa — nó theo bộ của dòng họ, trừ
 * khi có bộ tầng {@code BRANCH} riêng. Hôm nay chưa có bộ REGION nào đang bật nên hành vi thật
 * không đổi; ngày bật bộ "kĩnh" của miền Trung, chi nào muốn theo nó thì cần một bộ
 * {@code BRANCH} kế thừa bộ ấy.
 *
 * <h2>Bật bộ REGION sau khi đã khởi tạo</h2>
 * Cha được chọn <b>lúc lưu hồ sơ</b>. Bật một bộ REGION về sau không tự nối lại — lưu lại hồ sơ
 * dòng họ một lần là đủ (phép nối lặp lại được, không đổi gì nếu đã đúng).
 *
 * <p>Không kiểm quyền ở đây: bên gọi duy nhất là lượt lưu hồ sơ của {@code clan}, vốn đã đòi vai
 * {@code ADMIN}; mọi phương thức ghi chạy trong transaction của bên gọi.</p>
 */
@org.springframework.modulith.NamedInterface("bo-luat-dong-ho")
@Service
public class ClanRuleSetService {

    private static final Logger log = LoggerFactory.getLogger(ClanRuleSetService.class);

    /** Giới hạn của cột {@code kinship_rule_set.code}. */
    private static final int CODE_MAX = 64;

    private final RuleSetRepository ruleSets;
    private final RuleAdminService ruleAdmin;

    public ClanRuleSetService(RuleSetRepository ruleSets, RuleAdminService ruleAdmin) {
        this.ruleSets = ruleSets;
        this.ruleAdmin = ruleAdmin;
    }

    /** Bộ tầng {@code CLAN} đang bật của chi gốc {@code rootBranchId}, nếu có. */
    @Transactional(readOnly = true)
    public Optional<ClanRuleSetView> current(UUID rootBranchId) {
        return activeClanSet(rootBranchId).flatMap(set -> ruleSets.byId(set.id())).map(this::view);
    }

    /**
     * Tạo hoặc nối lại bộ tầng {@code CLAN} theo vùng {@code region}. Lặp lại được: gọi hai lần
     * với cùng tham số thì lần hai không ghi gì.
     *
     * @param rootBranchId id chi gốc {@code DONG_HO}
     * @param rootLabel    nhãn gốc — dùng để đặt mã {@code CLAN_<NHÃN>}
     * @param clanName     tên dòng họ — dùng cho tên hiển thị của bộ luật khi tạo mới
     * @param region       {@code BAC/TRUNG/NAM}
     */
    @Transactional
    public ClanRuleSetView align(UUID rootBranchId, String rootLabel, String clanName,
                                 String region) {
        Objects.requireNonNull(rootBranchId, "rootBranchId");
        Region wanted = Region.fromDb(region);
        if (wanted == null) {
            throw new IllegalArgumentException("Thiếu vùng miền cho bộ luật tầng CLAN");
        }
        KinshipRuleSet parent = parentFor(wanted);

        Optional<KinshipRuleSet> existing = activeClanSet(rootBranchId)
                .flatMap(set -> ruleSets.byId(set.id()));
        KinshipRuleSet result;
        if (existing.isEmpty()) {
            result = ruleSets.replace(new RuleSetRepository.RuleSetCommand(null,
                    freeCode(rootLabel), "Danh xưng dòng họ — " + clanName, RuleScope.CLAN,
                    wanted, rootBranchId, parent.id(),
                    "Bộ luật của dòng họ, kế thừa " + parent.code() + ". Hội đồng Tộc biểu sửa"
                            + " luật riêng ở đây; đổi vùng miền chỉ đổi cha kế thừa.",
                    List.of(), null));
            log.info("Tao bo luat CLAN {} cho chi goc {}, ke thua {}", result.code(), rootBranchId,
                    parent.code());
        } else {
            KinshipRuleSet set = existing.get();
            UUID currentParent = set.parent() == null ? null : set.parent().id();
            if (parent.id().equals(currentParent) && wanted == set.region()) {
                return view(set);
            }
            // CHI doi cha + vung. Luat rieng cua Hoi dong (neu co) giu nguyen — xem javadoc lop.
            result = ruleSets.reparent(set.id(), parent.id(), wanted);
            log.info("Noi lai bo luat CLAN {}: cha {} -> {} ({} luat rieng giu nguyen)",
                    set.code(), currentParent, parent.code(), set.ownRules().size());
        }
        // Chuoi ke thua doi thi moi danh xung da cache deu dang ngo.
        ruleAdmin.evictKinshipCaches();
        return view(result);
    }

    private Optional<KinshipRuleSet> activeClanSet(UUID rootBranchId) {
        if (rootBranchId == null) {
            return Optional.empty();
        }
        return ruleSets.findAll(RuleScope.CLAN, null, rootBranchId).stream()
                .filter(KinshipRuleSet::active)
                .findFirst();
    }

    /** Bộ REGION đang bật của vùng, không có thì DEFAULT. */
    private KinshipRuleSet parentFor(Region region) {
        return ruleSets.findAll(RuleScope.REGION, region, null).stream()
                .filter(KinshipRuleSet::active)
                .findFirst()
                .orElseGet(ruleSets::defaultRuleSet);
    }

    /** {@code CLAN_<NHÃN>} cắt vừa 64 ký tự; trùng (bộ cũ đã tắt) thì thêm hậu tố số. */
    private String freeCode(String rootLabel) {
        String base = ("CLAN_" + (rootLabel == null ? "DONG_HO" : rootLabel))
                .toUpperCase(Locale.ROOT);
        if (base.length() > CODE_MAX) {
            base = base.substring(0, CODE_MAX);
        }
        Set<String> taken = ruleSets.findAll(null, null, null).stream()
                .map(KinshipRuleSet::code)
                .collect(Collectors.toSet());
        String candidate = base;
        for (int i = 2; taken.contains(candidate); i++) {
            String suffix = "_" + i;
            candidate = base.substring(0, Math.min(base.length(), CODE_MAX - suffix.length()))
                    + suffix;
        }
        return candidate;
    }

    private ClanRuleSetView view(KinshipRuleSet set) {
        int own = set.ownRules().size();
        return new ClanRuleSetView(set.id(), set.code(),
                set.region() == null ? null : set.region().name(),
                set.parent() == null ? null : set.parent().code(),
                own > 0, own);
    }
}

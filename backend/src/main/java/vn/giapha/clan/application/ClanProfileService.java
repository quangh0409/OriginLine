package vn.giapha.clan.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.audit.application.AuditTrailService;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.clan.domain.AncestralHall;
import vn.giapha.clan.domain.ClanProfile;
import vn.giapha.clan.domain.ClanProfileContent;
import vn.giapha.clan.domain.ClanRegion;
import vn.giapha.clan.domain.GioTo;
import vn.giapha.clan.domain.RootBranchLabel;
import vn.giapha.clan.domain.port.ClanProfileRepository;
import vn.giapha.genealogy.application.ClanRootService;
import vn.giapha.genealogy.application.ClanRootView;
import vn.giapha.kinship.application.ClanRuleSetService;
import vn.giapha.kinship.application.ClanRuleSetView;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Đọc và ghi <b>hồ sơ dòng họ</b> — trái tim của "Khởi tạo dòng họ".
 *
 * <h2>Một lượt lưu = bốn việc, MỘT transaction</h2>
 * <ol>
 *   <li>kiểm và thay nội dung hồ sơ (nhãn gốc bị khoá khi phả đã có người — luật ở
 *       {@link ClanProfile#revise});</li>
 *   <li>dựng / đổi tên / đổi nhãn <b>chi gốc</b> {@code DONG_HO} qua mặt tiền
 *       {@code genealogy "goc-dong-ho"};</li>
 *   <li>tạo hoặc nối lại <b>bộ luật danh xưng tầng {@code CLAN}</b> theo vùng qua mặt tiền
 *       {@code kinship "bo-luat-dong-ho"} — luật Hội đồng đã sửa ở tầng ấy <b>không</b> bị ghi đè,
 *       chỉ cha kế thừa đổi (xem {@link ClanRuleSetService});</li>
 *   <li>ghi {@code audit_log}.</li>
 * </ol>
 * Hỏng ở bất kỳ bước nào thì không bước nào vào: một hồ sơ nói "miền Trung" mà bộ luật vẫn theo
 * miền Bắc là đúng loại lệch âm thầm mà không ai nhìn ra cho tới ngày có người gọi sai danh xưng.
 *
 * <h2>Khoá lạc quan — làm y như {@code PATCH /persons/{id}}</h2>
 * Hồ sơ đã có ⇒ {@code If-Match} bắt buộc: thiếu ⇒ {@code 412 PRECONDITION_REQUIRED}, lệch ⇒
 * {@code 409 OPTIMISTIC_LOCK_CONFLICT}. Chưa có ⇒ lượt {@code PUT} là <i>tạo</i> và không cần nó.
 * Đua nhau giữa hai transaction còn được {@code @Version} bắt ở lúc flush.
 *
 * <h2>Lần khởi tạo đầu trên một cây đã có gốc</h2>
 * Chưa có hồ sơ mà cây chi đã có <b>đúng một</b> gốc khác nhãn ⇒ đó là gốc của dòng họ này, và
 * lượt lưu <b>đổi nhãn</b> nó (khi phả chưa có người) thay vì dựng gốc thứ hai bên cạnh. Có nhiều
 * gốc mà không gốc nào khớp ⇒ {@code 409 CLAN_ROOT_LOCKED}: đoán gốc nào là của dòng họ là việc
 * con người phải làm, không phải máy.
 */
@Service
public class ClanProfileService {

    private static final Logger log = LoggerFactory.getLogger(ClanProfileService.class);

    static final String ENTITY = "ClanProfile";

    private final ClanProfileRepository profiles;
    private final ClanRootService roots;
    private final ClanRuleSetService ruleSets;
    private final AuditTrailService audit;

    public ClanProfileService(ClanProfileRepository profiles, ClanRootService roots,
                              ClanRuleSetService ruleSets, AuditTrailService audit) {
        this.profiles = profiles;
        this.roots = roots;
        this.ruleSets = ruleSets;
        this.audit = audit;
    }

    /** Hồ sơ cho khách — rỗng khi dòng họ chưa khởi tạo. Không kiểm quyền: dữ liệu công khai. */
    @Transactional(readOnly = true)
    public Optional<ClanProfileView> publicProfile() {
        return profiles.find().map(ClanProfileView::of);
    }

    /**
     * Hồ sơ cho quản trị.
     *
     * @throws NotFoundException {@code CLAN_NOT_INITIALIZED} khi chưa có hồ sơ
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ClanProfileView adminProfile() {
        return profiles.find().map(ClanProfileView::of).orElseThrow(ClanProfileService::notInitialized);
    }

    /** Tạo (chưa có hồ sơ) hoặc thay (đã có) hồ sơ dòng họ. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ClanProfileWriteResult write(WriteClanProfileCommand command) {
        ClanProfileContent content = toContent(Objects.requireNonNull(command, "command"));
        long personsEver = roots.countPersons(true);

        Optional<ClanProfile> existing = profiles.find();
        ClanProfile profile;
        ClanProfileContent before;
        String previousLabel;
        if (existing.isPresent()) {
            profile = existing.get();
            requireVersion(command.expectedVersion(), profile.version());
            before = profile.content();
            try {
                profile.revise(content, personsEver);
            } catch (ClanProfile.RootLockedException ex) {
                throw rootLocked(before.root().value(), personsEver);
            }
            previousLabel = before.root().value();
        } else {
            before = null;
            previousLabel = adoptableRoot(content.root(), personsEver);
            profile = ClanProfile.create(UUID.randomUUID(), content);
        }
        boolean created = existing.isEmpty();

        ClanRootView root = roots.establish(content.root().value(), content.name(),
                content.region().name(), previousLabel);
        Optional<ClanRuleSetView> rulesBefore = ruleSets.current(root.id());
        ClanRuleSetView rules = ruleSets.align(root.id(), root.label(), content.name(),
                content.region().name());
        ClanProfile saved = profiles.save(profile);

        List<String> changed = content.changedFrom(before);
        if (created || !changed.isEmpty()) {
            audit.record(ENTITY, saved.id().toString(),
                    created ? AuditAction.CREATE : AuditAction.UPDATE,
                    before == null ? null : before.snapshot(), content.snapshot(), changed,
                    created ? "Khoi tao dong ho" : "Sua ho so dong ho");
        }
        auditRuleSet(rulesBefore, rules);

        log.info("{} ho so dong ho {} (goc {}, vung {}, bo luat {} ke thua {}, {} luat rieng)",
                created ? "Khoi tao" : "Cap nhat", saved.id(), root.label(),
                content.region(), rules.code(), rules.parentCode(), rules.ownRuleCount());
        return new ClanProfileWriteResult(ClanProfileView.of(saved), created);
    }

    // -------------------------------------------------------------------------------------
    // Nội bộ
    // -------------------------------------------------------------------------------------

    private static void requireVersion(Long expected, long current) {
        if (expected == null) {
            throw new ClanPreconditionException("Thiếu header If-Match; hãy lấy ETag từ"
                    + " GET /admin/clan-profile trước khi sửa");
        }
        if (expected != current) {
            throw new ClanConflictException(ClanProblemCodes.OPTIMISTIC_LOCK_CONFLICT,
                    "Hồ sơ dòng họ đã được người khác sửa (phiên bản mong đợi " + expected
                            + ", hiện tại " + current + "); tải lại rồi sửa tiếp");
        }
    }

    /**
     * Nhãn gốc hiện có mà lần khởi tạo đầu tiên nên <b>nhận về</b> — xem javadoc lớp.
     *
     * @return {@code null} khi dựng gốc mới hoặc khi nhãn yêu cầu đã là một gốc sẵn có
     */
    private String adoptableRoot(RootBranchLabel wanted, long personsEver) {
        List<ClanRootView> existingRoots = roots.roots();
        if (existingRoots.stream().anyMatch(r -> r.label().equals(wanted.value()))) {
            return null;
        }
        if (existingRoots.isEmpty()) {
            return null;
        }
        if (existingRoots.size() > 1) {
            throw new ClanConflictException(ClanProblemCodes.CLAN_ROOT_LOCKED,
                    "Cây chi đang có " + existingRoots.size() + " gốc ("
                            + existingRoots.stream().map(ClanRootView::label).toList()
                            + "); hãy chọn một trong các nhãn ấy làm rootBranchPath");
        }
        String only = existingRoots.get(0).label();
        if (personsEver > 0) {
            throw rootLocked(only, personsEver);
        }
        return only;
    }

    private static ClanConflictException rootLocked(String current, long persons) {
        return new ClanConflictException(ClanProblemCodes.CLAN_ROOT_LOCKED,
                "Phả đã có " + persons + " nhân khẩu nên không đổi được nhãn gốc '" + current
                        + "'. Nhãn gốc là tiền tố của mọi chi và mọi phạm vi phân quyền.");
    }

    private void auditRuleSet(Optional<ClanRuleSetView> before, ClanRuleSetView after) {
        boolean same = before.isPresent()
                && before.get().ruleSetId().equals(after.ruleSetId())
                && Objects.equals(before.get().parentCode(), after.parentCode())
                && Objects.equals(before.get().region(), after.region());
        if (same) {
            return;
        }
        audit.record("KinshipRuleSet", after.ruleSetId().toString(),
                before.isEmpty() ? AuditAction.CREATE : AuditAction.UPDATE,
                before.map(ClanProfileService::ruleSnapshot).orElse(null), ruleSnapshot(after),
                List.of("parentCode", "region"),
                "Noi bo luat danh xung tang CLAN theo vung cua dong ho; luat rieng giu nguyen ("
                        + after.ownRuleCount() + " luat)");
    }

    private static Map<String, Object> ruleSnapshot(ClanRuleSetView view) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", view.code());
        map.put("parentCode", view.parentCode());
        map.put("region", view.region());
        map.put("ownRuleCount", view.ownRuleCount());
        return map;
    }

    /** Dựng value object — nơi DUY NHẤT luật dữ liệu được kiểm; mọi vi phạm ra 422. */
    private static ClanProfileContent toContent(WriteClanProfileCommand c) {
        try {
            AncestralHall hall = c.ancestralHall() == null
                    || (isBlank(c.ancestralHall().name()) && isBlank(c.ancestralHall().address()))
                    ? null
                    : new AncestralHall(c.ancestralHall().name(), c.ancestralHall().address());
            GioTo gioTo = toGioTo(c.gioTo());
            return new ClanProfileContent(c.name(), c.shortName(),
                    RootBranchLabel.of(c.rootBranchPath()), ClanRegion.parse(c.region()), hall,
                    c.ancestorName(), c.foundedNote(), gioTo);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new DomainException(ClanProblemCodes.VALIDATION_FAILED, ex.getMessage(), ex);
        }
    }

    private static GioTo toGioTo(WriteClanProfileCommand.LunarDayMonth raw) {
        if (raw == null || (raw.lunarDay() == null && raw.lunarMonth() == null)) {
            return null;
        }
        if (raw.lunarDay() == null || raw.lunarMonth() == null) {
            throw new IllegalArgumentException("gioTo phải có cả lunarDay lẫn lunarMonth");
        }
        return new GioTo(raw.lunarDay(), raw.lunarMonth());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static NotFoundException notInitialized() {
        return new NotFoundException(ClanProblemCodes.CLAN_NOT_INITIALIZED,
                "Dòng họ chưa được khởi tạo; hãy mở màn Khởi tạo dòng họ");
    }
}

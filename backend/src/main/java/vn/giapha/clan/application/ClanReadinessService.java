package vn.giapha.clan.application;

import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.clan.domain.ClanProfile;
import vn.giapha.clan.domain.ClanReadiness;
import vn.giapha.clan.domain.port.ClanProfileRepository;
import vn.giapha.genealogy.application.ClanRootService;
import vn.giapha.genealogy.application.ClanRootView;
import vn.giapha.kinship.application.ClanRuleSetService;
import vn.giapha.kinship.application.ClanRuleSetView;
import vn.giapha.membership.application.MembershipReadinessService;

/**
 * Đếm các sự kiện rải ở bốn context rồi giao cho {@link ClanReadiness} phán "bước nào xong".
 *
 * <p>Chỉ đọc, chỉ đếm. Mọi phép đếm đi qua mặt tiền công khai của context sở hữu dữ liệu — không
 * một câu SQL nào ở đây đọc bảng của người khác.</p>
 */
@Service
public class ClanReadinessService {

    private final ClanProfileRepository profiles;
    private final ClanRootService roots;
    private final ClanRuleSetService ruleSets;
    private final MembershipReadinessService membership;

    public ClanReadinessService(ClanProfileRepository profiles, ClanRootService roots,
                                ClanRuleSetService ruleSets, MembershipReadinessService membership) {
        this.profiles = profiles;
        this.roots = roots;
        this.ruleSets = ruleSets;
        this.membership = membership;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ClanReadinessView readiness() {
        Optional<ClanProfile> profile = profiles.find();
        String label = profile.map(p -> p.content().root().value()).orElse(null);
        Optional<ClanRootView> root = roots.root(label);
        Optional<ClanRuleSetView> rules = root.flatMap(r -> ruleSets.current(r.id()));
        int council = membership.countCouncilMembers();
        long persons = roots.countPersons(false);
        long branches = roots.countBranchesUnder(label);
        int invites = membership.countActiveClanInvites();

        ClanReadiness readiness = ClanReadiness.of(new ClanReadiness.Facts(profile.isPresent(),
                root.isPresent(), rules.isPresent(), council, persons, invites));

        return new ClanReadinessView(
                new ClanReadinessView.ProfilePart(readiness.profileDone(),
                        profile.map(p -> p.content().name()).orElse(null)),
                new ClanReadinessView.RootPart(root.isPresent(), root.map(ClanRootView::label)
                        .orElse(label)),
                new ClanReadinessView.RuleSetPart(rules.isPresent(),
                        rules.map(ClanRuleSetView::region).orElse(null),
                        rules.map(ClanRuleSetView::customized).orElse(false)),
                new ClanReadinessView.CouncilPart(readiness.councilDone(), council),
                persons, branches, invites, readiness.steps());
    }
}

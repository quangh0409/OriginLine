package vn.giapha.kinship.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.kinship.domain.PersonView;
import vn.giapha.shared.security.CurrentUser;
import vn.giapha.shared.security.CurrentUserProvider;

/**
 * Che tên trên đường quan hệ theo <b>phân tầng hiển thị</b> (BA v2 §10, Nghị định 13/2023).
 *
 * <p><b>Áp SAU cache, không phải trước.</b> Cache danh xưng dùng chung cho mọi người gọi, nên nếu
 * che trước rồi mới cache thì người có quyền sẽ nhận lại bản đã che của người trước đó — hoặc tệ
 * hơn, ngược lại. Mọi kết quả đi ra khỏi {@link ResolveKinshipTitleService} đều đi qua
 * {@link #apply(KinshipQueryResult, java.util.Map)} một lần cuối.</p>
 *
 * <p><b>TODO (W6 — nợ có ý thức):</b> đây mới là bản tối thiểu. Context {@code genealogy} đang viết
 * {@code PrivacyTierService} (lọc theo {@code is_alive} + role + {@code privacy_level}); khi nó
 * công bố application service, lớp này phải gọi sang <b>qua một port khai ở kinship</b> và bỏ phần
 * suy đoán bên dưới. Cố tình <b>không</b> import lớp nào của {@code genealogy} để giữ ranh giới
 * bounded context.</p>
 *
 * <p><b>Hạn chế đã biết:</b> {@link PersonView} — bản chiếu duy nhất mà context này có — không mang
 * cờ còn sống/đã khuất, nên ở đây <b>không</b> phân biệt được người sống với người đã khuất. Vì thế
 * quy tắc là <i>đóng trước, mở sau</i>: khách vãng lai bị che toàn bộ tên. Điều này khớp với
 * {@code SecurityConfig} hiện tại (mọi {@code /api/v1} ngoài {@code /public} đều đòi JWT nên khách
 * đã nhận 401 từ trước), và khớp với hợp đồng "khách không thấy người còn sống".</p>
 */
@Component
public class KinshipDisplayPolicy {

    /** Nhãn chung thay cho tên thật — hợp đồng {@code KinshipPathStep.displayName} quy định. */
    public static final String MASKED_LABEL = "—";

    private static final Set<String> FULL_VISIBILITY_ROLES = Set.of("ADMIN", "COUNCIL");

    /**
     * Che tên trong {@code path} và {@code lca} của một kết quả.
     *
     * @param people bản chiếu nhân khẩu trên đường đi, tra theo id dạng {@link java.util.UUID}
     */
    public KinshipQueryResult apply(KinshipQueryResult result, Map<UUID, PersonView> people) {
        Optional<CurrentUser> caller = CurrentUserProvider.current();
        boolean privileged = caller.map(user -> FULL_VISIBILITY_ROLES.stream().anyMatch(user::hasRole))
                .orElse(false);
        boolean authenticated = caller.isPresent();

        List<RelationPathStep> masked = new ArrayList<>(result.path().size());
        for (RelationPathStep step : result.path()) {
            masked.add(step.withDisplayName(
                    label(step.displayName(), people.get(step.personId()), authenticated, privileged)));
        }
        KinshipQueryResult.Lca lca = result.lca();
        if (lca != null) {
            lca = lca.withDisplayName(
                    label(lca.displayName(), people.get(lca.personId()), authenticated, privileged));
        }
        return result.withPath(masked, lca);
    }

    /**
     * Tên được phép hiện cho một chặng.
     *
     * <ul>
     *   <li>Khách vãng lai → nhãn chung cho tất cả (đóng trước, mở sau).</li>
     *   <li>Người đã xoá mềm → nhãn chung, trừ {@code ADMIN}/{@code COUNCIL}. Node vẫn ở lại trên
     *       đường đi vì xoá mềm không được làm đứt cây.</li>
     *   <li>Thành viên đã đăng nhập → tên hiển thị (Tầng 1: tên + đời + quan hệ lõi).</li>
     * </ul>
     */
    private String label(String rawName, PersonView view, boolean authenticated, boolean privileged) {
        if (!authenticated) {
            return MASKED_LABEL;
        }
        if (view != null && view.deleted() && !privileged) {
            return MASKED_LABEL;
        }
        return rawName == null || rawName.isBlank() ? MASKED_LABEL : rawName;
    }
}

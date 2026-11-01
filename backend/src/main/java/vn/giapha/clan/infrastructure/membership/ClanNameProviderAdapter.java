package vn.giapha.clan.infrastructure.membership;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.clan.domain.port.ClanProfileRepository;
import vn.giapha.membership.application.ClanNameProvider;

/**
 * Hiện thực cổng {@link ClanNameProvider} mà {@code membership} khai — <b>phụ thuộc đảo</b>.
 *
 * <p>Chỉ là bộ dịch: tra hồ sơ, so nhãn gốc, trả tên. Không luật nghiệp vụ, không kiểm quyền (tên
 * dòng họ là dữ liệu công khai), không SQL. Đặt ở {@code clan.infrastructure.membership} vì cạnh
 * {@code clan → membership} vốn đã có; cạnh ngược lại thì không được có — xem javadoc của cổng.</p>
 */
@Component
public class ClanNameProviderAdapter implements ClanNameProvider {

    private final ClanProfileRepository profiles;

    public ClanNameProviderAdapter(ClanProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> clanNameOfRoot(String rootLabel) {
        if (rootLabel == null) {
            return Optional.empty();
        }
        return profiles.find()
                .filter(profile -> profile.content().root().value().equals(rootLabel))
                .map(profile -> profile.content().name());
    }
}

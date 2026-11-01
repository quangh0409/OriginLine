package vn.giapha.clan.domain.port;

import java.util.Optional;
import vn.giapha.clan.domain.ClanProfile;

/**
 * Lưu trữ hồ sơ dòng họ.
 *
 * <p>{@link #find()} không nhận tham số — đây là <b>chỗ duy nhất</b> trong cả context giả định
 * "một bản cài = một dòng họ". Bước 2 đổi nó thành {@code byId(clanId)} / {@code byRoot(label)} và
 * trình biên dịch sẽ chỉ ra mọi nơi phải nghĩ lại; giả định ấy không được rải ra chỗ khác.</p>
 */
public interface ClanProfileRepository {

    Optional<ClanProfile> find();

    /**
     * Ghi hồ sơ và trả bản đọc lại từ CSDL (có {@code createdAt}/{@code updatedAt}/{@code version}
     * do CSDL và khoá lạc quan sinh ra).
     */
    ClanProfile save(ClanProfile profile);
}

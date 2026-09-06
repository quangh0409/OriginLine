package vn.giapha.genealogy.domain;

import java.util.Optional;
import java.util.UUID;
import vn.giapha.shared.domain.ValueObject;

/**
 * Kết quả tra tổ chung gần nhất (LCA) giữa hai nhân khẩu — đầu vào của rule engine danh xưng ở
 * context {@code kinship}.
 *
 * <p>{@code distanceFrom} / {@code distanceTo} là số bước đi <b>ngược</b> cạnh {@code PARENT}
 * ({@code <-[:PARENT*0..]-}) từ mỗi người lên tới tổ chung. Bằng 0 nghĩa là chính người đó là tổ
 * chung — tức người kia là hậu duệ trực hệ.</p>
 *
 * @param lcaPersonId  tổ chung gần nhất; {@code null} khi hai người không cùng một cây
 * @param distanceFrom số đời từ người thứ nhất lên tới tổ chung
 * @param distanceTo   số đời từ người thứ hai lên tới tổ chung
 */
public record LcaResult(UUID lcaPersonId, int distanceFrom, int distanceTo) implements ValueObject {

    public static final LcaResult NONE = new LcaResult(null, -1, -1);

    public boolean found() {
        return lcaPersonId != null;
    }

    public Optional<UUID> lca() {
        return Optional.ofNullable(lcaPersonId);
    }
}

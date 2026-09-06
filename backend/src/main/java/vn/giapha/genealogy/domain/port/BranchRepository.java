package vn.giapha.genealogy.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.shared.vo.BranchPath;

/** Kho chi/ngành. {@link BranchPath} vừa là cấu trúc cây vừa là phạm vi phân quyền. */
public interface BranchRepository {

    Optional<Branch> byId(UUID id);

    List<Branch> byIds(Collection<UUID> ids);

    Optional<Branch> byPath(BranchPath path);

    /** Toàn bộ chi nằm dưới (hoặc chính là) {@code path} — tương đương {@code path <@ :root}. */
    List<Branch> subtreeOf(BranchPath path);

    List<Branch> all();

    /** Tra nhanh id → path, dùng khi kiểm phạm vi cho cả một lô nhân khẩu. */
    Map<UUID, BranchPath> pathsOf(Collection<UUID> branchIds);
}

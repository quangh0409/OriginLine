package vn.giapha.membership.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.BranchSummary;
import vn.giapha.membership.domain.port.BranchLookupPort;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bản giả của {@code BranchLookupPort}: hai bảng tra cứu chi/ngành mà phân quyền theo phạm vi cần.
 *
 * <p>Giữ riêng {@link #deleted} vì bản thật lọc {@code b.is_deleted = FALSE}. Một chi đã xoá mềm
 * phải phân giải thành "không có path", và khi ấy chỉ vai toàn dòng họ mới đụng được — nếu bản giả
 * bỏ qua điều này thì luật "chi rỗng không phải chi công cộng" sẽ không có test nào canh.</p>
 */
public final class StubBranchLookup implements BranchLookupPort {

    private final Map<UUID, BranchPath> branchPaths = new LinkedHashMap<>();
    private final Map<UUID, UUID> personBranches = new LinkedHashMap<>();
    private final Map<UUID, Boolean> deleted = new LinkedHashMap<>();
    private final Map<UUID, Long> personVersions = new LinkedHashMap<>();

    /** Khai báo một chi/ngành với đường dẫn {@code ltree} của nó. */
    public UUID branch(UUID branchId, String ltreePath) {
        branchPaths.put(branchId, BranchPath.of(ltreePath));
        deleted.put(branchId, Boolean.FALSE);
        return branchId;
    }

    /** Gắn một nhân khẩu vào chi chính của người ấy. Phiên bản khởi điểm là 0. */
    public UUID person(UUID personId, UUID branchId) {
        personBranches.put(personId, branchId);
        personVersions.put(personId, 0L);
        return personId;
    }

    /**
     * Đặt {@code person.version} — mô phỏng "có người khác vừa sửa hồ sơ này".
     *
     * <p>Đây là điều kiện của ca ghi đè mù: đề nghị đóng dấu phiên bản {@code n} lúc gửi, hồ sơ
     * nhích lên {@code n+1} trong lúc chờ, và lúc duyệt phải bị chặn chứ không âm thầm đè.</p>
     */
    public void personVersion(UUID personId, long version) {
        personVersions.put(personId, version);
    }

    /** Xoá mềm một chi — path không còn phân giải được. */
    public void softDelete(UUID branchId) {
        deleted.put(branchId, Boolean.TRUE);
    }

    @Override
    public Optional<BranchPath> pathOfBranch(UUID branchId) {
        if (branchId == null || Boolean.TRUE.equals(deleted.get(branchId))) {
            return Optional.empty();
        }
        return Optional.ofNullable(branchPaths.get(branchId));
    }

    @Override
    public Optional<BranchPath> branchOfPerson(UUID personId) {
        return branchIdOfPerson(personId).flatMap(this::pathOfBranch);
    }

    @Override
    public Optional<UUID> branchIdOfPerson(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(personBranches.get(personId));
    }

    @Override
    public Optional<Long> versionOfPerson(UUID personId) {
        return personId == null ? Optional.empty() : Optional.ofNullable(personVersions.get(personId));
    }

    /**
     * Tên dòng họ = <b>tên của chi cấp 1</b>, đúng phép lấy mà adapter thật dùng
     * ({@code nlevel(path) = 1}).
     *
     * <p>Suy từ chính bảng chi đã khai thay vì giữ một trường riêng: hai nguồn chân lý cho cùng một
     * câu hỏi sẽ lệch nhau, và test sẽ xanh vì lý do sai. Bản giả không có cột {@code name} nên
     * dùng nhãn gốc của {@code ltree} — đủ để phân biệt "có dòng họ" với "chưa khởi tạo".</p>
     */
    /**
     * Chi ở mức đọc được. Bản giả không có cột {@code name}/{@code branch_kind} nên suy từ nhãn
     * cuối của {@code ltree} — đủ để phân biệt hai chi và đủ để dựng một chức danh khác rỗng.
     */
    @Override
    public Optional<BranchSummary> summaryOfBranch(UUID branchId) {
        return pathOfBranch(branchId).map(path -> {
            String nhan = path.value().substring(path.value().lastIndexOf('.') + 1);
            boolean laGoc = !path.value().contains(".");
            return new BranchSummary(branchId, nhan, laGoc ? "DONG_HO" : "CHI", path);
        });
    }

    @Override
    public Optional<String> clanName() {
        return branchPaths.entrySet().stream()
                .filter(entry -> !Boolean.TRUE.equals(deleted.get(entry.getKey())))
                .map(entry -> entry.getValue().value())
                .filter(path -> !path.contains("."))
                .sorted()
                .findFirst();
    }

    @Override
    public boolean branchExists(UUID branchId) {
        return branchId != null && branchPaths.containsKey(branchId)
                && !Boolean.TRUE.equals(deleted.get(branchId));
    }
}

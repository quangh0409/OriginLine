package vn.giapha.membership.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.ClanOffice;
import vn.giapha.membership.domain.Invitee;
import vn.giapha.membership.domain.port.InviteeLookupPort;
import vn.giapha.shared.vo.BranchPath;

/**
 * Bản giả của {@code InviteeLookupPort}.
 *
 * <p>Lấy chi/ngành từ {@link StubBranchLookup} thay vì giữ bản sao riêng: nếu hai bản giả nói hai
 * điều khác nhau về cùng một nhân khẩu thì test phân quyền sẽ xanh vì lý do sai.</p>
 */
public final class StubInviteeLookup implements InviteeLookupPort {

    private final StubBranchLookup branches;
    private final Map<UUID, Invitee> known = new LinkedHashMap<>();
    private final Map<UUID, ClanOffice> offices = new LinkedHashMap<>();

    public StubInviteeLookup(StubBranchLookup branches) {
        this.branches = branches;
    }

    /** Một nhân khẩu còn sống, chưa xoá — ca thông thường của luồng mời. */
    public UUID living(UUID personId, String displayName, Integer generation) {
        return register(personId, displayName, generation, true, false);
    }

    /** Người đã khuất — không mời được, hồ sơ người đã khuất vốn đã công khai. */
    public UUID deceased(UUID personId, String displayName) {
        return register(personId, displayName, null, false, false);
    }

    /** Nhân khẩu đã xoá mềm. */
    public UUID softDeleted(UUID personId, String displayName) {
        return register(personId, displayName, null, true, true);
    }

    /** Chức danh dòng tộc — {@code branch.head_person_id} ở bản thật. */
    public void clanOffice(UUID personId, String branchName, String branchKind) {
        offices.put(personId, new ClanOffice(branchName, branchKind));
    }

    private UUID register(UUID personId, String displayName, Integer generation,
                          boolean alive, boolean deleted) {
        known.put(personId, new Invitee(personId, displayName, generation, null, null, null, null,
                null, alive, deleted));
        return personId;
    }

    @Override
    public Optional<Invitee> byId(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        Invitee stored = known.get(personId);
        if (stored == null) {
            return Optional.empty();
        }
        // Doc lai chi tu StubBranchLookup moi lan goi: chi co the bi xoa mem GIUA hai lan tra.
        BranchPath path = branches.branchOfPerson(personId).orElse(null);
        UUID branchId = branches.branchIdOfPerson(personId).orElse(null);
        // Ten dong ho = nhan cap 1 cua ltree, dung phep lay ma adapter that dung.
        String clanName = path == null ? null : path.value().split("\\.")[0];
        return Optional.of(new Invitee(personId, stored.displayName(), stored.generation(),
                path == null ? null : branchId, path == null ? null : path.value(), path, null,
                clanName, stored.alive(), stored.deleted()));
    }

    /**
     * Mẫu số của bộ đếm mã mời — đếm đúng những nhân khẩu bản giả này biết, lọc theo cờ sống/xoá.
     *
     * <p>Suy từ chính bảng đã khai chứ không giữ một con số riêng: hai nguồn chân lý cho cùng một
     * câu hỏi sẽ lệch nhau, và test sẽ xanh vì lý do sai.</p>
     */
    @Override
    public int countLivingPersons() {
        return (int) known.values().stream()
                .filter(person -> person.alive() && !person.deleted())
                .count();
    }

    @Override
    public Optional<ClanOffice> clanOfficeOf(UUID personId) {
        return personId == null ? Optional.empty() : Optional.ofNullable(offices.get(personId));
    }
}

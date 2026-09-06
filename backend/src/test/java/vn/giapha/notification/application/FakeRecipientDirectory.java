package vn.giapha.notification.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.RecipientDirectory;

/**
 * Danh bạ người nhận trong bộ nhớ, <b>mô phỏng ngữ nghĩa {@code ltree}</b> của adapter thật.
 *
 * <p>Người nhận của một lượt nhắc là thành viên thuộc chi/ngành đích <b>và mọi nhánh con</b> — trong
 * SQL là toán tử "hậu duệ hoặc chính nó" trên {@code branch.path}. Ở đây tái hiện bằng so khớp tiền
 * tố đường dẫn: {@code root.chi1} phủ {@code root.chi1.nhanh1} nhưng không phủ {@code root.chi10}
 * (dấu chấm phân đoạn là thứ phân biệt hai trường hợp ấy, và quên nó là lỗi kinh điển của mọi cách
 * làm bằng {@code LIKE 'x%'}).</p>
 *
 * <p><b>Giới hạn đã biết:</b> fake này không kiểm được câu SQL thật. Truy vấn {@code ltree} và điều
 * kiện {@code app_user.status} chỉ có thể kiểm bằng test tích hợp trên Postgres — đó là món nợ đã
 * ghi nhận, không phải điều bài test này giả vờ đã trả.</p>
 */
public final class FakeRecipientDirectory implements RecipientDirectory {

    /** {@code personId -> (recipient, duong dan chi/nhanh)}. */
    private final Map<UUID, Entry> members = new LinkedHashMap<>();

    /** {@code branchId -> duong dan ltree}. */
    private final Map<UUID, String> branchPaths = new LinkedHashMap<>();

    private final Map<String, Recipient> byKeycloakSub = new LinkedHashMap<>();

    public FakeRecipientDirectory branch(UUID branchId, String path) {
        branchPaths.put(branchId, path);
        return this;
    }

    public FakeRecipientDirectory member(Recipient recipient, String branchPath) {
        members.put(recipient.personId(), new Entry(recipient, branchPath));
        return this;
    }

    public FakeRecipientDirectory linkAccount(String keycloakSub, Recipient recipient) {
        byKeycloakSub.put(keycloakSub, recipient);
        return this;
    }

    @Override
    public List<Recipient> membersOfBranch(UUID branchId, boolean clanLevel) {
        if (clanLevel) {
            return members.values().stream().map(Entry::recipient).toList();
        }
        String target = branchPaths.get(branchId);
        if (target == null) {
            return List.of();
        }
        List<Recipient> found = new ArrayList<>();
        for (Entry entry : members.values()) {
            if (isDescendantOrSelf(entry.branchPath(), target)) {
                found.add(entry.recipient());
            }
        }
        return List.copyOf(found);
    }

    @Override
    public Optional<Recipient> byPersonId(UUID personId) {
        return Optional.ofNullable(members.get(personId)).map(Entry::recipient);
    }

    @Override
    public Optional<Recipient> byKeycloakSub(String keycloakSub) {
        return Optional.ofNullable(byKeycloakSub.get(keycloakSub));
    }

    /** {@code path <@ target} của {@code ltree}: bằng nhau, hoặc là hậu duệ theo từng đoạn. */
    private static boolean isDescendantOrSelf(String path, String target) {
        return path != null && (path.equals(target) || path.startsWith(target + "."));
    }

    private record Entry(Recipient recipient, String branchPath) {
    }
}

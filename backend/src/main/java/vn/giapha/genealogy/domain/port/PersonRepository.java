package vn.giapha.genealogy.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.shared.vo.PersonId;

/**
 * Kho nhân khẩu.
 *
 * <p><b>Không có {@code delete}.</b> Sự vắng mặt đó là cố ý và là một quyết định kiến trúc: xoá
 * cứng một nhân khẩu làm đứt liên kết cây, nên cách duy nhất để "xoá" là
 * {@link Person#softDelete(String)} rồi {@link #save(Person)}.</p>
 */
public interface PersonRepository {

    /** Nạp một nhân khẩu, <b>kể cả</b> bản ghi đã xoá mềm — lọc là việc của tầng application. */
    Optional<Person> byId(PersonId id);

    /** Nạp nhiều nhân khẩu một lượt; dùng khi dựng projection cây để tránh N+1. */
    List<Person> byIds(Collection<UUID> ids);

    /** Ghi (thêm mới hoặc cập nhật). Khoá lạc quan theo {@code version} nằm ở adapter. */
    Person save(Person person);

    boolean exists(PersonId id);

    /** Đời thứ hiện tại của một người; rỗng khi người đó chưa được nối vào cây. */
    Optional<Integer> generationOf(PersonId id);

    /**
     * Số con (ruột + nuôi) chưa bị xoá mềm của mỗi người trong danh sách.
     *
     * <p>Phục vụ {@code TreeNode.childCount} và {@code hasMoreDescendants} — FE cần biết
     * "còn 12 người con chưa nạp" để vẽ nút mở rộng.</p>
     */
    java.util.Map<UUID, Integer> countChildren(Collection<UUID> personIds);
}

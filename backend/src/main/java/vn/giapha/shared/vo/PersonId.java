package vn.giapha.shared.vo;

import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.domain.ValueObject;

/**
 * Định danh nhân khẩu. Bọc {@link UUID} để không lẫn với id của bất kỳ thực thể nào khác khi
 * truyền qua nhiều tầng (đặc biệt là các truy vấn LCA nhận hai id cùng kiểu).
 *
 * <p>UUID được sinh ở tầng ứng dụng chứ không để CSDL sinh, vì cùng một id phải ghi đồng thời vào
 * bảng {@code person} và vào node của đồ thị {@code giapha_graph} trong <b>cùng một transaction</b>.</p>
 */
public record PersonId(UUID value) implements ValueObject {

    public PersonId {
        Objects.requireNonNull(value, "PersonId khong duoc null");
    }

    public static PersonId newId() {
        return new PersonId(UUID.randomUUID());
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }

    public static PersonId of(String value) {
        try {
            return new PersonId(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("PersonId khong hop le: " + value, ex);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

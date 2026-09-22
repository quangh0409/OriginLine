package vn.giapha.membership.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.PasswordResetGrant;

/**
 * Lưu trữ các lượt cấp lại liên kết đặt mật khẩu.
 *
 * <h2>Cổng này có một phương thức KHÔNG phải CRUD, và đó là phương thức quan trọng nhất</h2>
 * {@link #claim(UUID, Instant)} là nơi tính chất <b>"liên kết dùng được đúng một lần"</b> được
 * canh. Nó phải là <i>một</i> lệnh ghi có điều kiện ({@code UPDATE ... WHERE used_at IS NULL}), chứ
 * không phải "đọc lên, kiểm trong Java, rồi ghi xuống" — hai lượt bấm gần nhau trên cùng một liên
 * kết sẽ cùng đọc thấy {@code used_at IS NULL} và cùng đi tiếp.
 */
public interface PasswordResetGrantRepository {

    /** Ghi một lượt cấp mới. */
    PasswordResetGrant save(PasswordResetGrant grant);

    Optional<PasswordResetGrant> byId(UUID id);

    /**
     * Thu hồi mọi lượt cấp còn mở của một tài khoản.
     *
     * <p>Gọi <b>trước</b> khi ghi lượt mới, trong cùng transaction:
     * {@code ux_password_reset_grant_open} chỉ cho phép một lượt mở trên mỗi tài khoản, và ý nghĩa
     * của nó là "hai liên kết cùng mở được một tài khoản thì thu hồi một cái không đóng được cửa
     * nào".</p>
     *
     * @return số dòng bị thu hồi
     */
    int revokeOpenFor(UUID appUserId, Instant at, String reason);

    /**
     * <b>Đốt</b> một lượt cấp: đặt {@code used_at} khi và chỉ khi nó còn chưa dùng, chưa thu hồi và
     * còn hạn.
     *
     * @return {@code true} nếu lượt gọi này là lượt đốt được — nhiều nhất một lượt gọi trả
     *         {@code true} cho mỗi {@code id}, bất kể bao nhiêu luồng gọi song song
     */
    boolean claim(UUID id, Instant at);

    /**
     * Trả một lượt cấp vừa đốt về trạng thái chưa dùng.
     *
     * <h2>Chỉ dùng cho MỘT ca, và ca ấy phải chắc chắn Keycloak không ghi gì</h2>
     * Realm từ chối mật khẩu vì vi phạm chính sách ({@code PasswordRejectedException}, HTTP 400 của
     * Keycloak) là lỗi <b>của người gõ</b>, không phải của liên kết: không một credential nào bị
     * thay đổi. Đốt liên kết trong ca ấy là bắt một cụ 80 tuổi gõ sai một lần rồi đi xin lại liên
     * kết — tức là hỏng đúng nhóm người mà cả luồng này sinh ra để phục vụ.
     *
     * <p><b>Không</b> dùng cho lỗi mạng hay lỗi 5xx: ở đó không biết được Keycloak đã ghi hay chưa,
     * và thiếu dữ kiện thì chọn vế an toàn — để liên kết cháy.</p>
     */
    void release(UUID id);

    /** Số lượt một người đã cấp kể từ {@code since} — bộ đếm giới hạn tần suất. */
    int countIssuedBySince(UUID issuerAppUserId, Instant since);
}

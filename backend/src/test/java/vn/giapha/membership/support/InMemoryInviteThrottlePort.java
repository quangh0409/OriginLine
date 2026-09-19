package vn.giapha.membership.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import vn.giapha.membership.domain.port.InviteThrottlePort;

/**
 * Bản trong bộ nhớ của {@code invitation_attempt}.
 *
 * <p>Giữ <b>toàn bộ</b> lần thử kèm kết quả chứ không chỉ một bộ đếm: luật "chỉ đếm lần thất bại"
 * là thứ cần được kiểm, và một bộ đếm trần sẽ không phân biệt được.</p>
 */
public final class InMemoryInviteThrottlePort implements InviteThrottlePort {

    /** Một lần thử: khoá người gọi, kết quả, thời điểm. */
    public record Attempt(String clientKeyHash, String outcome, Instant at) {
    }

    private final List<Attempt> attempts = new ArrayList<>();

    public List<Attempt> attempts() {
        return List.copyOf(attempts);
    }

    public long failureCount() {
        return attempts.stream().filter(a -> "FAILED".equals(a.outcome())).count();
    }

    @Override
    public int failuresSince(String clientKeyHash, Instant since) {
        return (int) attempts.stream()
                .filter(a -> a.clientKeyHash().equals(clientKeyHash))
                .filter(a -> "FAILED".equals(a.outcome()))
                .filter(a -> !a.at().isBefore(since))
                .count();
    }

    @Override
    public void record(String clientKeyHash, String outcome) {
        attempts.add(new Attempt(clientKeyHash, outcome, Instant.now()));
    }
}

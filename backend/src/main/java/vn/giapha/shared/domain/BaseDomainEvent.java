package vn.giapha.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Lớp cơ sở tiện dụng cho {@link DomainEvent}: tự sinh {@code eventId} và {@code occurredAt}.
 */
public abstract class BaseDomainEvent implements DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredAt = Instant.now();

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return eventType() + "[" + eventId + " @ " + occurredAt + "]";
    }
}

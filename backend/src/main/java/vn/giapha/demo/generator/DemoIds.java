package vn.giapha.demo.generator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Sinh UUID <b>tất định</b> từ (seed, loại thực thể, khoá nghiệp vụ) theo kiểu UUID v5 (SHA-1).
 *
 * <p>Vì sao không dùng {@code UUID.randomUUID()}: plan §10 yêu cầu "seed cố định để mỗi lần sinh ra
 * đúng một bộ dữ liệu, nhờ đó test assert được trên id cụ thể". Sinh id từ khoá nghiệp vụ còn có
 * hai cái lợi nữa: chạy lại generator ra đúng id cũ (idempotent), và khi debug thì
 * {@code attributes->>'demo_key'} truy ngược được về đúng dòng code đã tạo ra nhân khẩu đó.</p>
 */
public final class DemoIds {

    private final UUID namespace;

    public DemoIds(long seed) {
        this.namespace = UUID.nameUUIDFromBytes(("vn.giapha.demo/seed=" + seed).getBytes(StandardCharsets.UTF_8));
    }

    public UUID person(String key) { return uuid5("person:" + key); }

    public UUID branch(String key) { return uuid5("branch:" + key); }

    public UUID name(String personKey, String nameType) { return uuid5("name:" + personKey + ":" + nameType); }

    public UUID relation(String fromKey, String toKey, String relType, int discriminator) {
        return uuid5("rel:" + relType + ":" + fromKey + "->" + toKey + ":" + discriminator);
    }

    private UUID uuid5(String name) {
        byte[] nsBytes = toBytes(namespace);
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM khong co SHA-1 — khong the sinh UUID tat dinh", e);
        }
        sha1.update(nsBytes);
        sha1.update(nameBytes);
        byte[] hash = sha1.digest();
        hash[6] = (byte) ((hash[6] & 0x0F) | 0x50); // version 5
        hash[8] = (byte) ((hash[8] & 0x3F) | 0x80); // variant RFC 4122
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) { msb = (msb << 8) | (hash[i] & 0xFF); }
        for (int i = 8; i < 16; i++) { lsb = (lsb << 8) | (hash[i] & 0xFF); }
        return new UUID(msb, lsb);
    }

    private static byte[] toBytes(UUID uuid) {
        byte[] out = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) { out[i] = (byte) (msb >>> (8 * (7 - i))); }
        for (int i = 8; i < 16; i++) { out[i] = (byte) (lsb >>> (8 * (15 - i))); }
        return out;
    }
}

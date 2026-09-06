package vn.giapha.genealogy.infrastructure.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.domain.port.TreeCachePort;

/**
 * Hiện thực {@link TreeCachePort} trên Redis.
 *
 * <h2>Dùng {@code StringRedisTemplate}, không phải {@code RedisTemplate<String,Object>}</h2>
 * Cổng này chỉ trao đổi chuỗi. Đi qua bộ tuần tự JSON chung sẽ bọc thêm một lớp nháy kép và
 * (với một số cấu hình) cả tên lớp Java vào giá trị — cache khi đó khoá chặt vào tên lớp, đổi tên
 * lớp là toàn bộ cache hỏng theo cách khó thấy. Chuỗi vào, chuỗi ra, soi bằng {@code redis-cli} vẫn
 * đọc được.
 *
 * <h2>Tiền tố khoá và phạm vi của {@link #evictAll()}</h2>
 * Mọi khoá được đặt dưới {@code giapha:tree:}. Tiền tố này <b>bao trùm</b> cả khoá do
 * {@code @Cacheable("tree")} sinh ra ({@code giapha:tree::…}, xem {@code RedisConfig}), nên một lần
 * {@code evictAll()} dọn sạch cả hai kiểu — đúng ý: cả hai đều là projection của cùng một cây, và
 * để sót một loại thì cây vẫn hiển thị người vừa bị xoá mềm.
 *
 * <p>Xoá bằng {@code SCAN} theo lô chứ không {@code KEYS}: {@code KEYS} khoá luồng đơn của Redis
 * trong suốt thời gian quét, và mutation phả hệ xảy ra ngay trong request path của người dùng.</p>
 *
 * <h2>Hỏng Redis thì suy giảm, không sập</h2>
 * {@link #get}/{@link #put} nuốt lỗi và ghi WARN — mất cache chỉ làm chậm, không làm sai.
 * {@link #evictAll()} thì ghi ERROR, vì đó là chế độ hỏng duy nhất có thể <b>trả về dữ liệu sai</b>.
 * Vẫn không ném ngoại lệ: mutation đã ghi vào Postgres rồi, đánh hỏng nó ở bước dọn cache là biến
 * một sự cố cache thành mất dữ liệu người dùng vừa nhập. Cửa sổ sai lệch bị chặn trên bởi TTL.
 */
@Component
public class RedisTreeCacheAdapter implements TreeCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisTreeCacheAdapter.class);

    /** Số khoá mỗi vòng SCAN / mỗi lệnh DEL — đủ lớn để ít vòng, đủ nhỏ để không nghẽn Redis. */
    private static final int SCAN_BATCH = 500;

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisTreeCacheAdapter(StringRedisTemplate redis,
                                 @Value("${giapha.cache.tree.key-prefix:giapha:tree:}") String keyPrefix,
                                 @Value("${giapha.cache.tree.ttl:PT10M}") Duration ttl) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    @Override
    public Optional<String> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redis.opsForValue().get(keyPrefix + key));
        } catch (RuntimeException ex) {
            log.warn("Doc cache cay that bai, chay tiep khong cache: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        try {
            redis.opsForValue().set(keyPrefix + key, value, ttl);
        } catch (RuntimeException ex) {
            log.warn("Ghi cache cay that bai, bo qua: {}", ex.getMessage());
        }
    }

    @Override
    public void evictAll() {
        long removed = 0;
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(keyPrefix + "*")
                .count(SCAN_BATCH)
                .build())) {
            List<String> batch = new ArrayList<>(SCAN_BATCH);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH) {
                    removed += deleteBatch(batch);
                    batch.clear();
                }
            }
            removed += deleteBatch(batch);
            log.debug("Da xoa {} khoa cache cay theo tien to {}", removed, keyPrefix);
        } catch (RuntimeException ex) {
            // Khong nem tiep: mutation da commit, huy no o buoc don cache la doi mot su co cache
            // lay mat du lieu nguoi dung vua nhap. TTL chan tren cua so hien thi du lieu cu.
            log.error("Khong xoa duoc cache cay (tien to {}). Cay co the hien thi du lieu cu"
                    + " toi khi het TTL {}.", keyPrefix, ttl, ex);
        }
    }

    private long deleteBatch(List<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redis.delete(keys);
        return deleted == null ? 0 : deleted;
    }
}

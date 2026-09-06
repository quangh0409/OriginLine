package vn.giapha.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis dùng cho <b>cache</b> và session, không hơn.
 *
 * <p>Nói rõ để tránh trôi kiến trúc: Redis <b>không</b> phải công cụ tìm kiếm. Tra cứu tên có dấu /
 * không dấu (FR-4.4) là full-text search của PostgreSQL với {@code unaccent}. Redis cũng không phải
 * nguồn chân lý — mọi thứ trong đây phải dựng lại được từ Postgres.</p>
 *
 * <p>Hai vùng cache đã biết trước:</p>
 * <ul>
 *   <li>{@code tree} — projection cây phả đồ đã render (W7). TTL ngắn và <b>bắt buộc</b> invalidate
 *       khi có mutation nhân khẩu/quan hệ, nếu không cây sẽ hiển thị người vừa bị xoá mềm.</li>
 *   <li>{@code kinship} — kết quả tra danh xưng theo khoá {@code A:B} (W3); rẻ để tính lại nên TTL
 *       có thể dài hơn.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String CACHE_TREE = "tree";
    public static final String CACHE_KINSHIP = "kinship";
    public static final String CACHE_KINSHIP_RULES = "kinshipRules";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Khoá dạng chuỗi để soi bằng redis-cli còn đọc được; giá trị dạng JSON để không khoá vào
        // Java serialization (đổi tên lớp là hỏng toàn bộ cache).
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .prefixCacheNameWith("giapha:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        CACHE_TREE, defaults.entryTtl(Duration.ofMinutes(10)),
                        CACHE_KINSHIP, defaults.entryTtl(Duration.ofHours(6)),
                        CACHE_KINSHIP_RULES, defaults.entryTtl(Duration.ofHours(12))))
                .transactionAware()
                .build();
    }
}

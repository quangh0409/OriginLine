package vn.giapha.demo;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;

/**
 * Chốt chặn: dữ liệu giả <b>không bao giờ</b> được chạm vào môi trường thật.
 *
 * <h2>Vì sao một cái {@code @Profile("demo")} là chưa đủ</h2>
 * <p>Profile là danh sách, và danh sách thì cộng dồn được. Một dòng
 * {@code SPRING_PROFILES_ACTIVE=prod,demo} trong biến môi trường của pipeline triển khai — do sao
 * chép nhầm từ file dev — là đủ để generator chạy trên CSDL thật. Với chế độ {@code REPLACE} thì nó
 * xoá sạch gia phả của cả dòng họ, và đó không phải sự cố kỹ thuật mà là mất dữ liệu không phục hồi
 * được.</p>
 *
 * <p>Vì vậy quy tắc ở đây là <b>chặn bằng cách làm ứng dụng không khởi động nổi</b>, không phải ghi
 * log rồi bỏ qua: một cảnh báo trong log là thứ không ai đọc lúc 2 giờ sáng. Thà chết ngay lúc
 * khởi động, với đúng một câu nói rõ phải sửa ở đâu.</p>
 */
final class DemoEnvironmentGuard {

    /** Tên profile của môi trường thật — có mặt bất kỳ cái nào là cấm tuyệt đối. */
    private static final Set<String> FORBIDDEN_PROFILES = Set.of(
            "prod", "production", "prd", "live", "staging", "stg", "uat", "preprod");

    private DemoEnvironmentGuard() {
    }

    /**
     * @throws IllegalStateException nếu profile {@code demo} chạy chung với một profile môi trường thật
     */
    static void requireNonProduction(Environment environment) {
        Set<String> active = new LinkedHashSet<>();
        Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .forEach(active::add);

        List<String> offenders = active.stream().filter(FORBIDDEN_PROFILES::contains).toList();
        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    ("Profile 'demo' dang chay cung profile moi truong that %s. Du lieu gia chi duoc "
                     + "phep o dev/test. Bo 'demo' khoi spring.profiles.active roi khoi dong lai.")
                            .formatted(offenders));
        }
    }
}

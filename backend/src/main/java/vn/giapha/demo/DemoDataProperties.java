package vn.giapha.demo;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import vn.giapha.demo.generator.DemoSeedConfig;

/**
 * Cấu hình việc nạp dữ liệu giả, tiền tố {@code giapha.demo}.
 *
 * <p>Mọi giá trị mặc định nằm ngay trong code chứ không trong file yaml, vì module này phải chạy
 * được chỉ bằng một cờ profile: {@code -Dspring.profiles.active=dev,demo}.</p>
 *
 * <p><b>Không có thuộc tính nào bật được module ở môi trường thật.</b> Chốt chặn là profile
 * {@code demo} cộng với {@link DemoEnvironmentGuard}; đặt {@code giapha.demo.*} ở prod không có
 * tác dụng gì cả.</p>
 *
 * @param seed          hạt giống cố định — đổi giá trị này là đổi toàn bộ UUID của bộ dữ liệu
 * @param referenceDate "hôm nay" của bộ dữ liệu; quyết định tỉ lệ còn sống / đã khuất
 * @param onExistingData xử sự khi bảng {@code person} đã có dữ liệu
 */
@ConfigurationProperties(prefix = "giapha.demo")
public record DemoDataProperties(long seed, LocalDate referenceDate, ExistingData onExistingData) {

    /** Xử sự khi CSDL đã có nhân khẩu. */
    public enum ExistingData {
        /** Bỏ qua, không đụng vào dữ liệu sẵn có. Mặc định — an toàn nhất. */
        SKIP,
        /** Xoá sạch phả hệ rồi nạp lại. Chỉ dùng khi biết rõ mình đang ở máy dev. */
        REPLACE,
        /** Ném lỗi cho ứng dụng không khởi động được — dùng khi CSDL demo phải luôn sạch. */
        FAIL
    }

    public DemoDataProperties {
        if (referenceDate == null) {
            referenceDate = DemoSeedConfig.DEFAULT_REFERENCE_DATE;
        }
        if (onExistingData == null) {
            onExistingData = ExistingData.SKIP;
        }
        if (seed == 0L) {
            seed = DemoSeedConfig.DEFAULT_SEED;
        }
    }

    /** Tham số sinh dữ liệu: quy mô lấy từ plan §10, chỉ seed và ngày mốc là chỉnh được. */
    public DemoSeedConfig toSeedConfig() {
        DemoSeedConfig defaults = DemoSeedConfig.defaults();
        return new DemoSeedConfig(seed, referenceDate, defaults.rootBirthYear(),
                defaults.bloodlinePlan(), defaults.spousePlan(),
                defaults.chiCount(), defaults.nhanhPerChi());
    }
}

package vn.giapha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Điểm khởi động của monolith module hoá "Hệ Thống Quản Lý Gia Phả &amp; Cổng Thông Tin Dòng Họ".
 *
 * <p>Mỗi package con trực tiếp của {@code vn.giapha} là một <b>bounded context</b> (Spring Modulith
 * application module). Ranh giới được kiểm chứng bằng test
 * {@code vn.giapha.ModularityTests} chứ không trông vào kỷ luật của người viết code.</p>
 *
 * <p>{@code shared} (kernel dùng chung) và {@code config} (cấu hình cross-cutting) được khai báo là
 * <i>shared module</i>: mọi context được phép phụ thuộc vào chúng, chiều ngược lại thì không.</p>
 */
@SpringBootApplication
@Modulithic(
        systemName = "Gia Pha Dong Ho",
        sharedModules = {"shared", "config"})
public class GiaPhaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GiaPhaApplication.class, args);
    }
}

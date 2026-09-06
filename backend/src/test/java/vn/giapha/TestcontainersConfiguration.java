package vn.giapha;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Ha tang that cho test tich hop.
 *
 * <p><b>Bat buoc dung image co Apache AGE</b> ({@code apache/age}), khong dung {@code postgres:16}
 * thuan: migration V1 co {@code CREATE EXTENSION age}, va Hikari chay
 * {@code connection-init-sql: LOAD 'age'} tren tung connection - image khong co thu vien AGE thi
 * toan bo pool hong ngay tu connection dau tien.</p>
 *
 * <p><b>Endpoint Docker</b> duoc phan giai boi {@link DockerContextClientProviderStrategy} (dang ky
 * qua {@code META-INF/services}). Doc javadoc lop do truoc khi di tim nguyen nhan cho loi
 * "Could not find a valid Docker environment" - tren Docker Desktop ban moi no khong phai loi
 * Docker.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static {
        normalizeVietnamTimeZone();
    }

    /**
     * Doi mui gio mac dinh cua JVM tu {@code Asia/Saigon} sang {@code Asia/Ho_Chi_Minh}.
     *
     * <h2>Bay README §6.2 - no giet ket noi CSDL chu khong chi lam lech gio</h2>
     * Tren Windows, JVM co the suy ra mui gio mac dinh la {@code Asia/Saigon} (mot bi danh cu cua
     * tzdata). pgjdbc gui thang ten do trong goi khoi tao ket noi, va PostgreSQL <b>tu choi</b>:
     *
     * <pre>
     *   FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
     * </pre>
     *
     * <p>Flyway chet ngay o buoc lay connection dau tien, va thong bao khong he nhac toi mui gio
     * cua may - nguoi doc log se di tim loi o cau hinh Testcontainers. Hai ten chi cung mot vung
     * ({@code Asia/Ho_Chi_Minh} la ten chinh tac), nen phep doi nay khong lam lech bat ky moc thoi
     * gian nao; no chi doi mot cai nhan ma PostgreSQL khong nhan ra.</p>
     */
    public static void normalizeVietnamTimeZone() {
        if ("Asia/Saigon".equals(java.util.TimeZone.getDefault().getID())) {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
    }

    // Tag phai khop infra/docker-compose.yml. LUU Y: khong co tag "PG16_latest" tren
    // Docker Hub - cac tag PG16 that su ton tai chi gom release_PG16_1.6.0,
    // release_PG16_1.5.0 va dev_snapshot_PG16. AGE 1.7/1.8 chi phat hanh cho PG17/PG18,
    // nen 1.6.0 la ban AGE moi nhat con khop Postgres 16 ma BA/TDD da chot.
    private static final DockerImageName AGE_IMAGE = DockerImageName
            .parse("apache/age:release_PG16_1.6.0")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(AGE_IMAGE)
                .withDatabaseName("giapha")
                .withUsername("giapha")
                .withPassword("giapha");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        // Khop infra/docker-compose.yml de test khong lech phien ban voi moi truong dev.
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine")
                .asCompatibleSubstituteFor("rabbitmq"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    }
}

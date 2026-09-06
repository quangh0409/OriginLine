package vn.giapha;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;

/**
 * Smoke test: ung dung khoi dong duoc voi ha tang that (Postgres+AGE, RabbitMQ, Redis).
 *
 * <p>Test nay tu bo qua khi may khong co Docker de {@code mvn test} van chay duoc o may chi lam
 * viec voi domain thuan. Khong co Keycloak trong bo container - va dung la khong can: JWT decoder
 * duoc cau hinh lazy qua {@code jwk-set-uri} nen ung dung len duoc du Keycloak chua chay.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIf("dockerAvailable")
class GiaPhaApplicationTests {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Test
    void contextLoads() {
        // Khong assert gi them: chi can Spring context dung duoc la du cho tieu chi ra cua W0.
    }
}

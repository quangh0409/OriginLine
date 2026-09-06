package vn.giapha.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tài liệu OpenAPI cho phần REST của {@code /api/v1}.
 *
 * <p>Nhớ hai điều khi đọc file này: (1) truy vấn cây lồng sâu đi bằng GraphQL nên sẽ không xuất
 * hiện ở đây — schema GraphQL nằm ở {@code contracts/}; (2) đặc tả trong {@code contracts/} là
 * <b>nguồn chung chốt trước</b> để frontend mock bằng MSW, còn tài liệu sinh tự động ở đây chỉ để
 * đối chiếu xem hiện thực có trôi khỏi hợp đồng hay không.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI giaPhaOpenApi(
            @Value("${giapha.openapi.server-url:http://localhost:8080}") String serverUrl,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {

        return new OpenAPI()
                .info(new Info()
                        .title("Gia Pha Dong Ho API")
                        .version("v1")
                        .description("""
                                API cua He Thong Quan Ly Gia Pha & Cong Thong Tin Dong Ho.

                                Loi tra ve theo chuan RFC 7807 Problem Details (media type
                                application/problem+json), kem thuoc tinh mo rong `code` va `timestamp`.

                                Luu y rieng tu (Nghi dinh 13/2023): du lieu nguoi con song bi loc theo
                                tang hien thi truoc khi serialize. Khach vang lai khong nhan duoc bat ky
                                truong nao cua nguoi con song.""")
                        .contact(new Contact().name("Nhom phat trien Gia Pha"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url(serverUrl).description("Moi truong hien tai")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token do Keycloak cap. Issuer: "
                                + (issuerUri.isBlank() ? "(chua cau hinh)" : issuerUri))))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

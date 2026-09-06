package vn.giapha.shared.api;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * Resolver tối thiểu cho schema GraphQL.
 *
 * <p><b>Vì sao tồn tại:</b> {@code spring-boot-starter-graphql} từ chối khởi động nếu không tìm
 * thấy file schema nào trong {@code classpath:graphql/}. Ở W0 chưa có schema nghiệp vụ nên
 * {@code schema.graphqls} chỉ khai báo trường {@code ping}; resolver này giữ cho schema có đủ
 * data fetcher. Khi W2 bổ sung schema cây phả đồ, {@code ping} vẫn hữu ích để smoke-test.</p>
 */
@Controller
public class HealthGraphQlController {

    @QueryMapping
    public String ping() {
        return "pong";
    }
}

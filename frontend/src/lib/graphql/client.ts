import { GraphQLClient } from "graphql-request";
import { API_BASE_URL, getApiLocale } from "@/lib/api/http";
import { MOCKING_ENABLED, getDevRole } from "@/lib/api/dev-role";
import { getAuthToken } from "@/lib/auth/token-bridge";

/**
 * Single GraphQL endpoint per plan §5: POST /api/v1/graphql. Used for deep
 * nested tree queries where the client chooses depth/fields; everything else
 * goes through the REST layer (src/lib/api).
 *
 * F8: cổng GraphQL đi qua đúng bộ lọc bảo mật với REST, nên nó cũng phải mang
 * `Authorization: Bearer`. `requestMiddleware` (bất đồng bộ) chứ không phải
 * `headers` (đồng bộ): token phải chờ Keycloak khởi tạo xong mới lấy được.
 *
 * Không có lớp thử-lại-sau-401 như `apiFetch`: `graphql-request` không phơi
 * điểm móc tương đương, và mọi truy vấn GraphQL hiện tại đều là đọc dữ liệu
 * nền (danh sách chi/ngành) — React Query sẽ tự gọi lại ở lần render sau khi
 * phiên đã ổn định.
 *
 * <h2>`request.headers` là một `Headers`, KHÔNG phải một object thường</h2>
 * Bản đầu của tệp này viết `{ ...request.headers }`. Trải một `Headers` bằng
 * toán tử spread cho ra `{}` — `Headers` không có thuộc tính riêng liệt kê
 * được, mọi giá trị nằm sau một cặp getter/setter. Nên dòng ấy **xoá sạch mọi
 * header mặc định**, trong đó có `Content-Type: application/json`.
 *
 * <p>Hệ quả đo được: Spring GraphQL khai tuyến của nó kèm điều kiện
 * `contentType(application/json)`. Thiếu header ấy thì tuyến **không khớp**,
 * yêu cầu rơi xuống `DispatcherServlet` và trả **404** — trong khi nhật ký
 * khởi động vẫn in rành rành `GraphQL endpoint HTTP POST /api/v1/graphql`. Đó
 * là lý do lỗi này sống lâu: mọi dấu hiệu đều nói endpoint có thật, và nó có
 * thật; thứ hỏng là yêu cầu gửi đi.</p>
 *
 * <p>Triệu chứng người dùng: ô lọc <b>Chi / Ngành</b> ở màn tìm kiếm quay mãi
 * rồi rỗng. Không có câu lỗi nào, vì một danh sách rỗng trông y hệt một dòng
 * họ chưa chia chi.</p>
 *
 * <p>Nay dựng một `Headers` MỚI từ cái cũ rồi `set` thêm — giữ nguyên mặc định
 * của `graphql-request` thay vì tự liệt kê lại chúng, vì liệt kê lại là hẹn
 * ngày thư viện thêm một header nữa mà ta không biết.</p>
 */
export const graphqlClient = new GraphQLClient(
  `${API_BASE_URL}/api/v1/graphql`,
  {
    requestMiddleware: async (request) => {
      const token = await getAuthToken();
      const headers = new Headers(request.headers);
      // Backend soạn sẵn chữ hiển thị theo header này, y như REST.
      headers.set("Accept-Language", getApiLocale());
      if (MOCKING_ENABLED) headers.set("x-mock-role", getDevRole());
      if (token) headers.set("Authorization", `Bearer ${token}`);
      return { ...request, headers };
    },
  }
);

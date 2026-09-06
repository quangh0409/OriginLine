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
 */
export const graphqlClient = new GraphQLClient(
  `${API_BASE_URL}/api/v1/graphql`,
  {
    requestMiddleware: async (request) => {
      const token = await getAuthToken();
      return {
        ...request,
        headers: {
          ...request.headers,
          // Backend soạn sẵn chữ hiển thị theo header này, y như REST.
          "Accept-Language": getApiLocale(),
          ...(MOCKING_ENABLED ? { "x-mock-role": getDevRole() } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      };
    },
  }
);

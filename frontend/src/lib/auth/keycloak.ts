import Keycloak from "keycloak-js";

/**
 * Keycloak adapter placeholder (F0 scope — "chưa cần luồng đăng nhập hoàn
 * chỉnh"). This wires the client configuration only; nothing calls
 * `keycloak.init()` yet. Sprint 4 (F8) implements the actual login flow,
 * token refresh, and React context/provider around this instance.
 *
 * keycloak-js touches `window`, so this must only be instantiated in client
 * components / browser code — never at module scope in a file that could be
 * imported during SSR. Use `getKeycloakInstance()` lazily instead of a
 * top-level singleton export.
 */
export interface KeycloakConfig {
  url: string;
  realm: string;
  clientId: string;
}

export const keycloakConfig: KeycloakConfig = {
  url: process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? "http://localhost:8081",
  realm: process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? "giapha",
  // Assumption (contracts/ did not define this yet): a public SPA client
  // named "giapha-frontend" with PKCE, no client secret. Confirm with
  // backend/infra when the Keycloak realm export lands.
  clientId: process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "giapha-frontend",
};

let instance: Keycloak | null = null;

export function getKeycloakInstance(): Keycloak {
  if (typeof window === "undefined") {
    throw new Error("Keycloak can only be instantiated in the browser");
  }
  if (!instance) {
    instance = new Keycloak(keycloakConfig);
  }
  return instance;
}

/**
 * Có chạy luồng đăng nhập THẬT hay không.
 *
 * Tắt khi `NEXT_PUBLIC_API_MOCKING=enabled`: bản chạy MSW không có Keycloak
 * nào để nói chuyện, `init()` sẽ treo ở lần gọi mạng đầu tiên và cả ứng dụng
 * đứng im. Ở chế độ ấy vai trò do bộ chuyển vai dev giả lập
 * (`src/lib/api/dev-role.ts`) — hai cơ chế loại trừ nhau, không bao giờ cùng
 * sống trong một bản chạy.
 */
export const AUTH_ENABLED = process.env.NEXT_PUBLIC_API_MOCKING !== "enabled";

/**
 * Trang tĩnh rỗng dùng cho `check-sso` trong iframe.
 *
 * Không có nó, mỗi lần tải trang Keycloak sẽ ĐIỀU HƯỚNG cả tab sang trang
 * đăng nhập rồi quay lại — người dùng thấy một cú nháy trắng ở mọi lần F5, và
 * mọi trạng thái phía client (viewport phả đồ, nhánh đang mở) mất sạch.
 * Xem `public/silent-check-sso.html`.
 */
export const SILENT_CHECK_SSO_PATH = "/silent-check-sso.html";

let initPromise: Promise<boolean> | null = null;

/**
 * Khởi tạo phiên Keycloak. **Bất biến (idempotent)** — gọi bao nhiêu lần cũng
 * trả về cùng một promise.
 *
 * Vì sao phải chốt điều này: `keycloak-js` ném lỗi nếu `init()` chạy lần thứ
 * hai trên cùng instance, mà React 18 StrictMode lại gắn effect HAI LẦN ở chế
 * độ dev. Không có bộ nhớ đệm promise thì mọi lần chạy dev đều nổ.
 *
 * `onLoad: "check-sso"` chứ KHÔNG phải `login-required`: khách chưa đăng nhập
 * vẫn phải mở được cổng thông tin (BA v2 §10) — `login-required` sẽ đá thẳng
 * mọi người lạ sang trang đăng nhập của Keycloak.
 */
export async function initKeycloak(): Promise<boolean> {
  if (!AUTH_ENABLED) return false;
  if (initPromise) return initPromise;

  const keycloak = getKeycloakInstance();

  initPromise = keycloak
    .init({
      onLoad: "check-sso",
      silentCheckSsoRedirectUri: `${window.location.origin}${SILENT_CHECK_SSO_PATH}`,
      // PKCE S256 — client `giapha-frontend` là public client, không có
      // client secret, nên PKCE là thứ duy nhất chặn được việc đánh cắp mã
      // uỷ quyền. Khớp với cấu hình realm.
      pkceMethod: "S256",
      // Iframe kiểm tra phiên định kỳ của Keycloak dựa vào cookie bên thứ ba;
      // trình duyệt hiện đại chặn, khiến nó báo "đã đăng xuất" nhầm rồi đá
      // người dùng ra giữa chừng. Việc theo dõi hạn token đã do
      // `onTokenExpired` + `updateToken` đảm nhiệm.
      checkLoginIframe: false,
    })
    .catch((error: unknown) => {
      // Keycloak chết hoặc sai cấu hình KHÔNG được phép làm trắng cả ứng
      // dụng: rơi về chế độ khách, phần công khai của cổng thông tin vẫn xem
      // được.
      console.warn("[auth] khong khoi tao duoc Keycloak, chay o che do khach", error);
      return false;
    });

  return initPromise;
}

/** Chỉ dùng trong kiểm thử — xoá bộ nhớ đệm instance/promise giữa các ca test. */
export function __resetKeycloakForTests(): void {
  instance = null;
  initPromise = null;
}

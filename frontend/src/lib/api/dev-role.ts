/**
 * Bộ giả lập vai trò — **chỉ sống ở chế độ MSW**, là nửa client của
 * `src/mocks/handlers/role.ts`.
 *
 * F8 đã lên: bản chạy nói chuyện với backend thật lấy vai từ claim
 * `realm_access.roles` của JWT Keycloak (`src/lib/auth/roles.ts`), và
 * `src/lib/api/http.ts` gắn `Authorization: Bearer` thay cho header giả lập.
 *
 * <b>Vì sao tệp này vẫn còn:</b> `npm run dev:mock` và toàn bộ bộ test Vitest
 * chạy không cần Keycloak. Không có bộ giả lập vai thì mọi yêu cầu ở đó đều là
 * KHÁCH, và không một hành vi phân tầng riêng tư hay RBAC nào (thứ mà F3/F4
 * sinh ra để trình diễn) còn nhìn thấy được. Xoá tệp này là mất khả năng phát
 * triển ngoại tuyến — chỉ xoá khi có một bộ đồ giả cho Keycloak thay thế.
 *
 * <b>Bất biến phải giữ:</b> `MOCKING_ENABLED` và `AUTH_ENABLED`
 * (`src/lib/auth/keycloak.ts`) được định nghĩa để LOẠI TRỪ NHAU, nên header
 * `x-mock-role` không bao giờ đi tới backend thật — backend không đọc nó, và
 * đọc mới là sai. Bất biến ấy được chốt bằng test ở
 * `tests/unit/api/http-auth.test.ts` và kiểm lại trên hệ thống thật ở
 * `e2e/real-auth/keycloak-login.spec.ts`.
 */
export type DevRole = "guest" | "member" | "branch-head" | "admin";

export const DEV_ROLES: readonly DevRole[] = ["guest", "member", "branch-head", "admin"];

export const MOCKING_ENABLED =
  process.env.NEXT_PUBLIC_API_MOCKING === "enabled" &&
  process.env.NODE_ENV !== "production";

const STORAGE_KEY = "giapha.dev-role";

/** Default matches the legal default: an anonymous visitor sees no living person. */
export const DEFAULT_DEV_ROLE: DevRole = "guest";

export function getDevRole(): DevRole {
  if (!MOCKING_ENABLED || typeof window === "undefined") return DEFAULT_DEV_ROLE;
  const stored = window.localStorage.getItem(STORAGE_KEY);
  return DEV_ROLES.includes(stored as DevRole) ? (stored as DevRole) : DEFAULT_DEV_ROLE;
}

export function setDevRole(role: DevRole): void {
  if (!MOCKING_ENABLED || typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, role);
}

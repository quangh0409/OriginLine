/**
 * Ánh xạ vai trò từ JWT thật (F8).
 *
 * Nguồn chân lý là claim `realm_access.roles` của Keycloak realm `giapha` —
 * đúng thứ mà backend đọc trong `KeycloakRealmRoleConverter`. Giao diện KHÔNG
 * được đọc vai từ localStorage, từ query string, hay từ bất cứ chỗ nào người
 * dùng ghi được: đó chỉ là gợi ý hiển thị, còn quyết định thật vẫn ở backend.
 *
 * Vai trò ở đây thuần tuý dùng để BẬT/TẮT các nút bấm (affordance). Ẩn nút
 * "Duyệt" không phải là kiểm soát truy cập; backend vẫn kiểm vai × phạm vi
 * `ltree` trên mọi yêu cầu.
 *
 * Lưu ý: chức danh dòng tộc (Tộc trưởng, Trưởng chi — theo huyết thống/đích
 * tôn) là chuyện KHÁC với vai kỹ thuật ở đây; một người có thể giữ cả hai.
 */

/** Bốn vai ứng dụng do Keycloak cấp. Mọi vai khác trong token bị bỏ qua. */
export const APP_ROLES = ["ADMIN", "COUNCIL", "BRANCH_HEAD", "MEMBER"] as const;

export type AppRole = (typeof APP_ROLES)[number];

/**
 * Thứ tự quyền lực giảm dần — dùng để chọn vai "chính" đem đi hiển thị khi
 * một tài khoản mang nhiều vai (tài khoản dev `admin.giapha` mang cả ADMIN
 * lẫn COUNCIL).
 */
const ROLE_PRECEDENCE: readonly AppRole[] = ["ADMIN", "COUNCIL", "BRANCH_HEAD", "MEMBER"];

/** Hình dạng tối thiểu của phần payload token mà giao diện quan tâm. */
export interface TokenClaims {
  realm_access?: { roles?: string[] };
  resource_access?: Record<string, { roles?: string[] } | undefined>;
  preferred_username?: string;
  name?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  sub?: string;
}

function isAppRole(value: string): value is AppRole {
  return (APP_ROLES as readonly string[]).includes(value);
}

/**
 * Lọc ra các vai ứng dụng từ token.
 *
 * Keycloak nhét thêm `offline_access`, `uma_authorization`,
 * `default-roles-giapha` vào cùng mảng ấy — chúng không có nghĩa gì với gia
 * phả nên bị loại, tránh việc một vai lạ vô tình lọt vào phép so sánh.
 *
 * Cũng đọc `resource_access[clientId].roles` vì realm có thể cấp vai ở cấp
 * client, giống hệt cách backend làm.
 */
export function extractRoles(
  claims: TokenClaims | undefined | null,
  clientId?: string
): AppRole[] {
  if (!claims) return [];
  const raw = [
    ...(claims.realm_access?.roles ?? []),
    ...(clientId ? (claims.resource_access?.[clientId]?.roles ?? []) : []),
  ];
  const found = new Set<AppRole>();
  for (const role of raw) {
    // Keycloak cho phép vai viết thường hoặc có dấu gạch ngang; chuẩn hoá về
    // dạng backend dùng (HOA + gạch dưới) trước khi so.
    const normalized = role.toUpperCase().replace(/-/g, "_");
    if (isAppRole(normalized)) found.add(normalized);
  }
  return ROLE_PRECEDENCE.filter((role) => found.has(role));
}

/** Vai cao nhất, hoặc `null` với khách. Dùng để hiển thị nhãn trên thanh đầu trang. */
export function primaryRole(roles: readonly AppRole[]): AppRole | null {
  return ROLE_PRECEDENCE.find((role) => roles.includes(role)) ?? null;
}

export function hasRole(roles: readonly AppRole[], role: AppRole): boolean {
  return roles.includes(role);
}

/**
 * Tên hiển thị của người đang đăng nhập.
 *
 * Ưu tiên `name` (Keycloak ghép họ + tên theo đúng thứ tự người Việt nếu realm
 * được cấu hình đúng), rồi tới `given_name family_name`, cuối cùng mới tới
 * `preferred_username`. Không bao giờ hiện `sub` — chuỗi UUID với người dùng
 * là vô nghĩa.
 */
export function displayNameOf(claims: TokenClaims | undefined | null): string {
  if (!claims) return "";
  const full = claims.name?.trim();
  if (full) return full;
  const composed = [claims.family_name, claims.given_name]
    .filter((part) => part && part.trim().length > 0)
    .join(" ")
    .trim();
  if (composed) return composed;
  return claims.preferred_username?.trim() ?? "";
}

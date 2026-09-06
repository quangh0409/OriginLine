/**
 * Cầu nối giữa phiên Keycloak (sống trong cây React) và lớp API (không sống
 * trong cây React).
 *
 * VÌ SAO PHẢI CÓ: `apiFetch` được gọi từ `queryFn` của React Query, tức là
 * ngoài phạm vi render, nên nó KHÔNG đọc được context. Cách duy nhất để nó
 * biết token hiện tại là một biến ở phạm vi module — giống hệt cách
 * `setApiLocale` trong `lib/api/http.ts` đã làm với `Accept-Language`.
 *
 * Cầu nối này cố ý KHÔNG giữ chuỗi token. Nó chỉ giữ một tham chiếu tới
 * `keycloak-js`, và mỗi lần lấy token đều hỏi lại instance ấy. Lưu bản sao là
 * đường ngắn nhất tới việc gắn một token đã hết hạn sau khi
 * `keycloak.updateToken()` đã lặng lẽ đổi token mới.
 */

export interface AuthBridge {
  /**
   * Token còn hạn dùng được ngay, tự làm mới nếu sắp hết hạn.
   * `null` nghĩa là KHÁCH — người gọi vẫn phải gửi được yêu cầu, chỉ là không
   * kèm header `Authorization` (chế độ khách là yêu cầu nghiệp vụ, BA v2 §10).
   */
  getFreshToken(): Promise<string | null>;
  /**
   * Ép làm mới sau khi backend đã trả 401 với một token mà phía client vẫn
   * tưởng là còn hạn (lệch đồng hồ, phiên bị thu hồi, Keycloak khởi động lại).
   * Trả `null` khi không cứu được — lúc đó phải rơi về chế độ khách.
   */
  forceRefresh(): Promise<string | null>;
  /** Phiên đã chết hẳn: dọn trạng thái, đưa giao diện về chế độ khách. */
  onSessionLost(): void;
}

let bridge: AuthBridge | null = null;

/** Do `AuthProvider` gọi sau khi `keycloak.init()` xong. */
export function setAuthBridge(next: AuthBridge | null): void {
  bridge = next;
}

export function getAuthBridge(): AuthBridge | null {
  return bridge;
}

/**
 * Token để gắn vào yêu cầu kế tiếp, hoặc `null` với khách.
 *
 * Nuốt mọi lỗi: một lần làm mới thất bại không được phép làm hỏng lời gọi API
 * — nó chỉ có nghĩa là yêu cầu này đi với tư cách khách và backend sẽ tự trả
 * 401/404 theo đúng luật phân tầng.
 */
export async function getAuthToken(): Promise<string | null> {
  if (!bridge) return null;
  try {
    return await bridge.getFreshToken();
  } catch {
    return null;
  }
}

/** Trả token mới sau 401, hoặc `null` nếu phiên không cứu được. */
export async function refreshAuthToken(): Promise<string | null> {
  if (!bridge) return null;
  try {
    const token = await bridge.forceRefresh();
    if (!token) bridge.onSessionLost();
    return token;
  } catch {
    bridge.onSessionLost();
    return null;
  }
}

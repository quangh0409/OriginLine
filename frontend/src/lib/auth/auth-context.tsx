"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  AUTH_ENABLED,
  getKeycloakInstance,
  initKeycloak,
  keycloakConfig,
} from "./keycloak";
import { setAuthBridge, type AuthBridge } from "./token-bridge";
import {
  displayNameOf,
  extractRoles,
  primaryRole,
  type AppRole,
  type TokenClaims,
} from "./roles";

/**
 * F8 — phiên đăng nhập thật qua Keycloak.
 *
 * BA v2 §10: **chế độ khách là yêu cầu nghiệp vụ**, không phải trạng thái lỗi.
 * Vì thế provider này KHÔNG chặn việc dựng giao diện: nó dựng con ngay lập
 * tức, còn lớp API tự chờ phiên ổn định qua `token-bridge`. Người chưa đăng
 * nhập vẫn mở được cổng thông tin; chỉ là các yêu cầu của họ đi mà không mang
 * header `Authorization`.
 */

/** Thời gian tối thiểu (giây) mà token còn phải sống thì mới đem dùng. */
const MIN_TOKEN_VALIDITY_SECONDS = 30;

export type AuthStatus = "loading" | "authenticated" | "guest";

export interface AuthContextValue {
  status: AuthStatus;
  isAuthenticated: boolean;
  /** Tên hiển thị lấy từ claim của token, không bao giờ là `sub`. */
  displayName: string;
  username: string;
  roles: AppRole[];
  /** Vai cao nhất — dùng cho nhãn trên thanh đầu trang. */
  role: AppRole | null;
  hasRole: (role: AppRole) => boolean;
  login: () => void;
  logout: () => void;
}

const GUEST: Omit<AuthContextValue, "hasRole" | "login" | "logout"> = {
  status: "guest",
  isAuthenticated: false,
  displayName: "",
  username: "",
  roles: [],
  role: null,
};

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Do provider gán; cầu nối ở phạm vi module gọi ngược lên khi phiên chết giữa
 * chừng để giao diện rơi về chế độ khách thay vì đứng hình.
 */
let sessionLostHandler: (() => void) | null = null;

/**
 * Cầu nối cho lớp API. Nằm ở phạm vi module chứ không trong state React vì
 * `apiFetch` chạy ngoài cây React (xem `token-bridge.ts`).
 *
 * `getFreshToken` CHỜ `initKeycloak()` trước khi trả lời. Đó là mấu chốt giúp
 * provider không phải chặn render: truy vấn đầu tiên của React Query có thể
 * khởi động trước khi `check-sso` xong, và nó sẽ tự đợi đúng chỗ cần đợi —
 * lớp mạng — thay vì bắt cả màn hình trắng chờ theo.
 */
const keycloakBridge: AuthBridge = {
  async getFreshToken() {
    if (!AUTH_ENABLED) return null;
    const authenticated = await initKeycloak();
    if (!authenticated) return null;
    const keycloak = getKeycloakInstance();
    try {
      // Trả `false` khi token còn hạn — không phát sinh lượt gọi mạng nào.
      await keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS);
    } catch {
      return null;
    }
    return keycloak.token ?? null;
  },

  async forceRefresh() {
    if (!AUTH_ENABLED) return null;
    const authenticated = await initKeycloak();
    if (!authenticated) return null;
    const keycloak = getKeycloakInstance();
    try {
      // -1 ép làm mới bất kể còn hạn bao lâu: ta tới đây vì backend đã trả
      // 401, nghĩa là niềm tin "còn hạn" phía client đã sai.
      await keycloak.updateToken(-1);
    } catch {
      return null;
    }
    return keycloak.token ?? null;
  },

  onSessionLost() {
    sessionLostHandler?.();
  },
};

function readIdentity(): Omit<AuthContextValue, "hasRole" | "login" | "logout"> {
  const keycloak = getKeycloakInstance();
  if (!keycloak.authenticated) return GUEST;
  const claims = (keycloak.tokenParsed ?? {}) as TokenClaims;
  const roles = extractRoles(claims, keycloakConfig.clientId);
  return {
    status: "authenticated",
    isAuthenticated: true,
    displayName: displayNameOf(claims),
    username: claims.preferred_username ?? "",
    roles,
    role: primaryRole(roles),
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [identity, setIdentity] = useState(() => ({
    ...GUEST,
    // Ở chế độ MSW không có Keycloak nào để hỏi, nên trạng thái đã ngã ngũ
    // ngay từ đầu: khách + bộ chuyển vai dev.
    status: (AUTH_ENABLED ? "loading" : "guest") as AuthStatus,
  }));

  // Đăng ký cầu nối NGAY TRONG THÂN RENDER, không đợi effect.
  // Effect của component cha chạy SAU effect của con, nên nếu đăng ký trong
  // useEffect thì truy vấn React Query đầu tiên của một trang đã kịp bay đi
  // với tư cách khách rồi. Cùng lý do và cùng khuôn mẫu với `setApiLocale`
  // trong `src/app/providers.tsx`.
  if (typeof window !== "undefined" && AUTH_ENABLED) {
    setAuthBridge(keycloakBridge);
  }

  useEffect(() => {
    if (!AUTH_ENABLED) return;

    let cancelled = false;

    const toGuest = () => {
      if (cancelled) return;
      setIdentity(GUEST);
      // Mọi phản hồi đã nằm trong cache đều được định hình bởi vai cũ. Giữ
      // lại là để một người vừa mất phiên vẫn nhìn thấy dữ liệu của tầng
      // riêng tư cao hơn — đúng thứ phân tầng sinh ra để chặn.
      queryClient.clear();
    };

    sessionLostHandler = toGuest;

    const keycloak = getKeycloakInstance();
    // Keycloak gọi khi token hết hạn trong lúc người dùng đang ngồi im. Làm
    // mới ngầm ở đây để lần bấm tiếp theo không phải chờ một vòng mạng.
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS).catch(toGuest);
    };
    // Refresh token cũng hết hạn / phiên bị thu hồi -> không cứu được nữa.
    keycloak.onAuthRefreshError = toGuest;
    keycloak.onAuthLogout = toGuest;

    void initKeycloak().then(() => {
      if (cancelled) return;
      setIdentity(readIdentity());
    });

    return () => {
      cancelled = true;
      if (sessionLostHandler === toGuest) sessionLostHandler = null;
    };
  }, [queryClient]);

  const login = useCallback(() => {
    if (!AUTH_ENABLED) return;
    // Quay lại đúng trang đang đứng (kể cả tiền tố ngôn ngữ và query string)
    // — người dùng bấm "Đăng nhập" giữa phả đồ thì phải về lại phả đồ.
    void getKeycloakInstance().login({ redirectUri: window.location.href });
  }, []);

  const logout = useCallback(() => {
    if (!AUTH_ENABLED) return;
    queryClient.clear();
    void getKeycloakInstance().logout({ redirectUri: window.location.origin });
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...identity,
      hasRole: (role: AppRole) => identity.roles.includes(role),
      login,
      logout,
    }),
    [identity, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Trả về trạng thái phiên. Ngoài cây provider (ví dụ trong test component
 * dựng lẻ một widget) thì rơi về "khách" thay vì ném lỗi — không màn hình nào
 * đáng sập chỉ vì thiếu thông tin đăng nhập.
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (context) return context;
  return {
    ...GUEST,
    hasRole: () => false,
    login: () => undefined,
    logout: () => undefined,
  };
}

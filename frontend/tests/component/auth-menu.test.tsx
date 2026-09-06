import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import type { AuthContextValue } from "@/lib/auth/auth-context";

/**
 * F8 — cụm đăng nhập trên thanh đầu trang.
 *
 * Bộ test Vitest chạy với `NEXT_PUBLIC_API_MOCKING=enabled`, tức là
 * `AUTH_ENABLED` tắt, nên phải giả lập module cấu hình để dựng được ba trạng
 * thái của bản chạy thật. Chính việc phải làm vậy là bằng chứng cho bất biến
 * quan trọng nhất: hai chế độ loại trừ nhau ở mức module, không phải ở mức
 * một câu `if` ai đó có thể quên.
 */

vi.mock("@/lib/auth/keycloak", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth/keycloak")>();
  return { ...actual, AUTH_ENABLED: true };
});

const authState = vi.hoisted(() => ({ current: null as AuthContextValue | null }));

vi.mock("@/lib/auth/auth-context", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth/auth-context")>();
  return { ...actual, useAuth: () => authState.current };
});

const { AuthMenu } = await import("@/components/layout/auth-menu");

const login = vi.fn();
const logout = vi.fn();

function authValue(overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  return {
    status: "guest",
    isAuthenticated: false,
    displayName: "",
    username: "",
    roles: [],
    role: null,
    hasRole: () => false,
    login,
    logout,
    ...overrides,
  };
}

beforeEach(() => {
  login.mockClear();
  logout.mockClear();
});

describe("chưa xác định được phiên", () => {
  it("không nháy nút 'Đăng nhập' trong lúc check-sso còn chạy", () => {
    authState.current = authValue({ status: "loading" });
    renderWithProviders(<AuthMenu />);

    expect(screen.queryByTestId("login-button")).toBeNull();
    expect(screen.queryByTestId("account-button")).toBeNull();
  });
});

describe("khách", () => {
  it("hiện nút Đăng nhập và gọi đúng luồng Keycloak khi bấm", async () => {
    authState.current = authValue();
    const { user } = renderWithProviders(<AuthMenu />);

    const button = screen.getByTestId("login-button");
    expect(button).toBeInTheDocument();
    expect(button).toHaveTextContent("Đăng nhập");

    await user.click(button);
    expect(login).toHaveBeenCalledTimes(1);
  });

  it("song ngữ — bản tiếng Anh hiện 'Log in'", () => {
    authState.current = authValue();
    renderWithProviders(<AuthMenu />, { locale: "en" });

    expect(screen.getByTestId("login-button")).toHaveTextContent("Log in");
  });
});

describe("đã đăng nhập", () => {
  it("hiện tên người đang đăng nhập, không hiện nút Đăng nhập nữa", () => {
    authState.current = authValue({
      status: "authenticated",
      isAuthenticated: true,
      displayName: "Nguyễn Đình Bách",
      username: "admin.giapha",
      roles: ["ADMIN", "COUNCIL"],
      role: "ADMIN",
    });
    renderWithProviders(<AuthMenu />);

    expect(screen.getByTestId("account-name")).toHaveTextContent("Nguyễn Đình Bách");
    expect(screen.queryByTestId("login-button")).toBeNull();
  });

  it("rơi về tên đăng nhập khi realm không có claim `name`", () => {
    authState.current = authValue({
      status: "authenticated",
      isAuthenticated: true,
      displayName: "",
      username: "thanhvien",
      roles: ["MEMBER"],
      role: "MEMBER",
    });
    renderWithProviders(<AuthMenu />);

    expect(screen.getByTestId("account-name")).toHaveTextContent("thanhvien");
  });

  it("menu mở ra có vai đã dịch và nút Đăng xuất chạy được", async () => {
    authState.current = authValue({
      status: "authenticated",
      isAuthenticated: true,
      displayName: "Nguyễn Văn Quản",
      username: "admin.giapha",
      roles: ["ADMIN"],
      role: "ADMIN",
    });
    const { user } = renderWithProviders(<AuthMenu />);

    await user.click(screen.getByTestId("account-button"));

    // Nhãn vai lấy từ `messages/*.json`, không hard-code trong component.
    await waitFor(() => {
      expect(screen.getByText("Quản trị hệ thống")).toBeInTheDocument();
    });
    const logoutItem = await screen.findByText("Đăng xuất");
    await user.click(logoutItem);

    expect(logout).toHaveBeenCalledTimes(1);
  });

  it("không rò rỉ `sub` của token ra màn hình", () => {
    authState.current = authValue({
      status: "authenticated",
      isAuthenticated: true,
      displayName: "Nguyễn Hữu Nguyên",
      username: "truongchi",
      roles: ["BRANCH_HEAD"],
      role: "BRANCH_HEAD",
    });
    const { container } = renderWithProviders(<AuthMenu />);

    expect(container.textContent).not.toMatch(
      /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i
    );
  });
});

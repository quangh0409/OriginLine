import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * F8 — bộ tiếp hợp Keycloak.
 *
 * Ở Giai đoạn 1 đây mới là phần cấu hình: chưa có luồng đăng nhập nào gọi
 * `init()`. Nhưng ba tính chất dưới đây là thứ sẽ hỏng âm thầm nếu ai đó
 * "dọn dẹp" file này, nên chốt lại bằng test ngay từ bây giờ:
 *
 *  1. `keycloak-js` chạm vào `window`. Nếu module tự tạo instance ở phạm vi
 *     module, mọi trang render phía máy chủ sẽ nổ. Vì thế phải là hàm lấy
 *     lười (lazy getter), và phải NÉM LỖI RÕ RÀNG khi không có `window` —
 *     thay vì một "ReferenceError: window is not defined" khó lần.
 *  2. Phải là singleton. Hai instance Keycloak trong cùng một tab sẽ tự làm
 *     mới token chồng lên nhau và đăng xuất lẫn nhau.
 *  3. Realm/clientId đọc từ biến môi trường, có giá trị mặc định cho máy dev.
 *     Không được nhúng cứng URL của môi trường nào.
 */

const KeycloakCtor = vi.fn();

vi.mock("keycloak-js", () => ({
  // keycloak-js xuất mặc định một lớp; ở đây chỉ cần đếm số lần khởi tạo.
  default: class {
    config: unknown;
    constructor(config: unknown) {
      this.config = config;
      KeycloakCtor(config);
    }
  },
}));

beforeEach(() => {
  vi.resetModules();
  KeycloakCtor.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("cấu hình", () => {
  it("dùng realm 'giapha' và client SPA công khai mặc định cho máy dev", async () => {
    const { keycloakConfig } = await import("@/lib/auth/keycloak");

    expect(keycloakConfig.realm).toBe("giapha");
    expect(keycloakConfig.clientId).toBe("giapha-frontend");
    expect(keycloakConfig.url).toMatch(/^https?:\/\//);
  });

  it("không nhúng cứng bí mật client — SPA dùng PKCE, không có client secret", async () => {
    const keycloak = await import("@/lib/auth/keycloak");

    expect(Object.keys(keycloak.keycloakConfig).sort()).toEqual([
      "clientId",
      "realm",
      "url",
    ]);
  });
});

describe("khởi tạo lười, chỉ ở trình duyệt", () => {
  it("ném lỗi có ý nghĩa khi được gọi lúc không có window (SSR)", async () => {
    const { getKeycloakInstance } = await import("@/lib/auth/keycloak");
    vi.stubGlobal("window", undefined);

    expect(() => getKeycloakInstance()).toThrowError(
      /only be instantiated in the browser/i
    );
    expect(KeycloakCtor).not.toHaveBeenCalled();
  });

  it("chỉ tạo MỘT instance dù được gọi nhiều lần", async () => {
    const { getKeycloakInstance } = await import("@/lib/auth/keycloak");

    const first = getKeycloakInstance();
    const second = getKeycloakInstance();

    expect(first).toBe(second);
    expect(KeycloakCtor).toHaveBeenCalledTimes(1);
  });

  it("truyền đúng url/realm/clientId xuống keycloak-js", async () => {
    const { getKeycloakInstance, keycloakConfig } = await import("@/lib/auth/keycloak");

    getKeycloakInstance();

    expect(KeycloakCtor).toHaveBeenCalledWith(keycloakConfig);
  });

  it("không tự khởi tạo lúc import — nạp module phải là việc không có tác dụng phụ", async () => {
    await import("@/lib/auth/keycloak");

    expect(KeycloakCtor).not.toHaveBeenCalled();
  });
});

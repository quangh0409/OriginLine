import { describe, expect, it } from "vitest";
import {
  APP_ROLES,
  displayNameOf,
  extractRoles,
  primaryRole,
  type TokenClaims,
} from "@/lib/auth/roles";

/**
 * F8 — vai trò đọc từ JWT thật.
 *
 * Trước bản vá này giao diện lấy vai từ `localStorage` (bộ chuyển vai dev):
 * bất kỳ ai mở DevTools cũng tự phong mình làm Quản trị. Nay nguồn duy nhất
 * là claim `realm_access.roles` của Keycloak — đúng chỗ backend đọc trong
 * `KeycloakRealmRoleConverter`.
 *
 * Nhắc lại cho rõ: những phép kiểm ở đây chỉ quyết định NÚT NÀO HIỆN. Quyền
 * thật vẫn do backend kiểm (vai × phạm vi `ltree`), nên một token bị sửa cũng
 * chỉ đổi được giao diện chứ không lấy thêm được dữ liệu.
 */

/** Bản sao thật của token tài khoản dev `admin.giapha` (đã lược bớt). */
const ADMIN_CLAIMS: TokenClaims = {
  realm_access: {
    roles: ["COUNCIL", "offline_access", "uma_authorization", "ADMIN", "default-roles-giapha"],
  },
  resource_access: { account: { roles: ["manage-account", "view-profile"] } },
  preferred_username: "admin.giapha",
  name: "Quan tri He thong",
  sub: "4717d021-84e2-4538-a3e5-563c8de95f6f",
};

describe("extractRoles", () => {
  it("chỉ giữ bốn vai ứng dụng, bỏ mọi vai nội bộ của Keycloak", () => {
    expect(extractRoles(ADMIN_CLAIMS)).toEqual(["ADMIN", "COUNCIL"]);
  });

  it("không nhặt nhầm vai từ `resource_access.account` của Keycloak", () => {
    const roles = extractRoles(ADMIN_CLAIMS, "giapha-frontend");
    expect(roles).not.toContain("MEMBER");
    expect(roles).toEqual(["ADMIN", "COUNCIL"]);
  });

  it("đọc thêm vai cấp client khi realm cấp ở đó", () => {
    const claims: TokenClaims = {
      resource_access: { "giapha-frontend": { roles: ["BRANCH_HEAD"] } },
    };
    expect(extractRoles(claims, "giapha-frontend")).toEqual(["BRANCH_HEAD"]);
    // Không truyền clientId thì không được tự ý mò sang resource_access.
    expect(extractRoles(claims)).toEqual([]);
  });

  it("chuẩn hoá cách viết: 'branch-head' hay chữ thường vẫn ra BRANCH_HEAD", () => {
    expect(extractRoles({ realm_access: { roles: ["branch-head"] } })).toEqual(["BRANCH_HEAD"]);
    expect(extractRoles({ realm_access: { roles: ["member"] } })).toEqual(["MEMBER"]);
  });

  it("khách (không token) là mảng rỗng, không phải lỗi", () => {
    expect(extractRoles(null)).toEqual([]);
    expect(extractRoles(undefined)).toEqual([]);
    expect(extractRoles({})).toEqual([]);
  });

  it("trả về theo thứ tự quyền lực giảm dần, không theo thứ tự trong token", () => {
    const claims: TokenClaims = {
      realm_access: { roles: ["MEMBER", "ADMIN", "BRANCH_HEAD", "COUNCIL"] },
    };
    expect(extractRoles(claims)).toEqual([...APP_ROLES]);
  });

  it("không nhân đôi khi một vai xuất hiện ở cả realm lẫn client", () => {
    const claims: TokenClaims = {
      realm_access: { roles: ["MEMBER"] },
      resource_access: { "giapha-frontend": { roles: ["MEMBER"] } },
    };
    expect(extractRoles(claims, "giapha-frontend")).toEqual(["MEMBER"]);
  });
});

describe("primaryRole", () => {
  it("chọn vai cao nhất khi một người mang nhiều vai", () => {
    expect(primaryRole(["COUNCIL", "ADMIN"])).toBe("ADMIN");
    expect(primaryRole(["MEMBER", "BRANCH_HEAD"])).toBe("BRANCH_HEAD");
  });

  it("khách không có vai nào", () => {
    expect(primaryRole([])).toBeNull();
  });
});

describe("displayNameOf", () => {
  it("ưu tiên claim `name`", () => {
    expect(displayNameOf({ name: "Nguyễn Đình Bách", preferred_username: "bach" })).toBe(
      "Nguyễn Đình Bách"
    );
  });

  it("ghép họ trước tên khi chỉ có given/family — đúng thứ tự tên người Việt", () => {
    expect(displayNameOf({ family_name: "Nguyễn", given_name: "Hữu Nguyên" })).toBe(
      "Nguyễn Hữu Nguyên"
    );
  });

  it("cuối cùng mới tới tên đăng nhập, và KHÔNG BAO GIỜ hiện `sub`", () => {
    const claims: TokenClaims = { preferred_username: "thanhvien", sub: "4717d021-84e2" };
    expect(displayNameOf(claims)).toBe("thanhvien");
    expect(displayNameOf(claims)).not.toContain("4717d021");
  });

  it("token rỗng cho chuỗi rỗng, không phải 'undefined'", () => {
    expect(displayNameOf({})).toBe("");
    expect(displayNameOf(null)).toBe("");
  });
});

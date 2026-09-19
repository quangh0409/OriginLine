import { beforeEach, describe, expect, it } from "vitest";
import { clearDraft, readDraft } from "@/hooks/use-form-draft";

/**
 * Kho nháp cục bộ — phần **quyết định riêng tư** của nó.
 *
 * Nháp của biểu mẫu nhân khẩu có thể chứa dữ liệu Tầng 3 của một người còn
 * sống (điện thoại, thư điện tử, địa chỉ đầy đủ, ngày sinh chính xác — BA v2
 * §10 / Nghị định 13/2023), và máy ở nhà thờ họ là máy dùng chung. Bài này
 * khoá đúng những tính chất khiến điều đó không thành một vụ lộ dữ liệu.
 */

const KEY = "person:p-001:u-admin";
const STORAGE_KEY = `giapha.draft.v1:${KEY}`;

beforeEach(() => {
  window.sessionStorage.clear();
  window.localStorage.clear();
});

describe("nháp sống trong sessionStorage", () => {
  it("đọc được thứ đã ghi trong CÙNG phiên", () => {
    window.sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ version: 1, savedAt: "2026-09-01T10:00:00Z", values: { occupation: "Thầy đồ" } })
    );
    expect(readDraft<{ occupation: string }>(KEY)?.values.occupation).toBe("Thầy đồ");
  });

  it("KHÔNG đọc localStorage, kể cả khi ở đó có đúng khoá ấy", () => {
    // Đây là chốt chống hồi quy: đổi sang localStorage "cho tiện" sẽ khiến bản
    // nháp bỏ dở của Trưởng chi nằm lại chờ người kế tiếp mở máy.
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ version: 1, savedAt: "2026-09-01T10:00:00Z", values: { occupation: "Rò rỉ" } })
    );
    expect(readDraft(KEY)).toBeNull();
  });

  it("xoá hẳn khỏi máy khi được yêu cầu", () => {
    window.sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ version: 1, savedAt: "2026-09-01T10:00:00Z", values: {} })
    );
    clearDraft(KEY);
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});

describe("khoá tách bạch theo người và theo đối tượng", () => {
  it("nháp của tài khoản khác không lọt sang", () => {
    window.sessionStorage.setItem(
      "giapha.draft.v1:person:p-001:u-branch-head",
      JSON.stringify({ version: 1, savedAt: "2026-09-01T10:00:00Z", values: { occupation: "Của người khác" } })
    );
    expect(readDraft("person:p-001:u-admin")).toBeNull();
  });

  it("nháp của nhân khẩu khác không lọt sang", () => {
    window.sessionStorage.setItem(
      "giapha.draft.v1:person:p-010:u-admin",
      JSON.stringify({ version: 1, savedAt: "2026-09-01T10:00:00Z", values: { occupation: "Hồ sơ khác" } })
    );
    expect(readDraft("person:p-001:u-admin")).toBeNull();
  });
});

describe("nháp cũ hoặc hỏng", () => {
  it("nháp thuộc phiên bản cũ bị bỏ VÀ bị xoá, không cố đọc bừa", () => {
    window.sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ version: 0, savedAt: "2026-09-01T10:00:00Z", values: { cu: true } })
    );
    expect(readDraft(KEY)).toBeNull();
    // Xoá luôn: dữ liệu không dùng được thì không có lý do gì nằm lại trên máy.
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("JSON hỏng không làm sập màn hình sửa", () => {
    window.sessionStorage.setItem(STORAGE_KEY, "{khong-phai-json");
    expect(() => readDraft(KEY)).not.toThrow();
    expect(readDraft(KEY)).toBeNull();
  });
});

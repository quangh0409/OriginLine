import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders, FORBIDDEN_PLACEHOLDER_PATTERNS } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import viMessages from "../../messages/vi.json";
import enMessages from "../../messages/en.json";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/",
}));

const { GuestModeNotice } = await import("@/components/auth/guest-mode-notice");

/**
 * Lối vào chế độ khách — thiết kế 06 §8.
 *
 * <h2>Vì sao khối này cần bộ kiểm riêng dù đã có `privacy-tier-notice.test.tsx`</h2>
 * Phép quét ở tệp ấy tự tìm mọi thành phần gắn `useTranslations("privacy")`.
 * Khối này nằm ở nhánh `auth.*` — vì nó nói về **quyền truy cập của người
 * đọc**, không về **một hồ sơ cụ thể** — nên phép quét kia không với tới. Bất
 * biến "không nêu con số nào về phần bị ẩn" thì vẫn y nguyên, nên nó được ghim
 * lại ở đây thay vì để hở.
 */

const flatten = (value: unknown, out: string[] = []): string[] => {
  if (typeof value === "string") out.push(value);
  else if (value && typeof value === "object") {
    for (const nested of Object.values(value as Record<string, unknown>)) flatten(nested, out);
  }
  return out;
};

describe("nói rõ xem được gì, không mời đăng nhập mù mờ", () => {
  it("hai cột cân nhau: xem được gì, và cái gì phải là người trong họ", () => {
    const { container } = renderWithProviders(<GuestModeNotice />);

    expect(container.querySelector('[data-guest-mode="notice"]')).not.toBeNull();
    expect(container.textContent).toContain("Xem được ngay, không cần tài khoản");
    expect(container.textContent).toContain("Phải là người trong họ mới xem được");
  });

  it("chỉ hứa những thứ cổng công khai THẬT SỰ trả được", () => {
    const { container } = renderWithProviders(<GuestModeNotice />);
    const text = container.textContent ?? "";

    // Ba dòng có đường đi thật: /api/v1/public/tree và GET /persons/{id} của
    // một người đã khuất.
    expect(text).toContain("các cụ đã khuất");
    expect(text).toContain("Phả đồ từ Thuỷ tổ");
    // Cố ý KHÔNG hứa tìm kiếm: màn tìm kiếm hiện gọi bản thành viên, nên hứa ở
    // đây là in sẵn một lời nói dối lên màn hình đầu tiên (06 §8).
    expect(text).not.toMatch(/tìm kiếm/i);
  });

  it("dẫn tới một việc làm được ngay, không phải một bức tường", () => {
    renderWithProviders(<GuestModeNotice />);
    const link = screen.getByRole("link", { name: /phả đồ công khai/i });
    expect(link).toHaveAttribute("href", "/tree");
  });

  it("nói rõ đây là luật, không phải lựa chọn của dòng họ hay của người dùng", () => {
    const { container } = renderWithProviders(<GuestModeNotice />);
    expect(container.textContent).toContain("không phải thiết lập riêng");
    expect(container.textContent).toContain("quy định của pháp luật");
  });
});

describe("không rò rỉ gì về phần bị ẩn", () => {
  it("KHÔNG một con số nào — 'có 320 người đang sống bị ẩn' tự nó là phép đếm dân số", () => {
    const { container } = renderWithProviders(<GuestModeNotice />);
    expect(container.textContent ?? "").not.toMatch(/\d/);
  });

  it("không câu nào trong nhánh auth.guestMode có chỗ chèn số lượng", () => {
    const strings = [
      ...flatten((viMessages.auth as Record<string, unknown>).guestMode),
      ...flatten((enMessages.auth as Record<string, unknown>).guestMode),
    ];
    expect(strings.length).toBeGreaterThan(0);
    for (const text of strings) {
      expect(text, `"${text}" có chỗ chèn`).not.toMatch(/\{\s*\w+\s*\}/);
      expect(text, `"${text}" có con số`).not.toMatch(/\d/);
    }
  });

  it("không mượn từ ngữ của một ô trống bị che, và không có ổ khoá", () => {
    const { container } = renderWithProviders(<GuestModeNotice />);
    const text = container.textContent ?? "";
    for (const pattern of FORBIDDEN_PLACEHOLDER_PATTERNS) {
      if (pattern.source.startsWith("^")) continue;
      expect(pattern.test(text), `khớp mẫu cấm ${pattern}`).toBe(false);
    }
    // Ổ khoá bị cấm trên toàn sản phẩm: nó dạy người đọc rằng mọi khoảng trắng
    // trên hồ sơ đều là dữ liệu bị giấu.
    expect(container.querySelector(".anticon-lock")).toBeNull();
  });
});

describe("song ngữ", () => {
  it("bản tiếng Anh đủ khoá và giữ nguyên thuật ngữ dòng họ", () => {
    const { container } = renderWithProviders(<GuestModeNotice />, { locale: "en" });
    const text = container.textContent ?? "";

    expect(text).not.toContain("MISSING_MESSAGE");
    // 00 §2.6: một người Mỹ gốc Việt cần hiểu "danh xưng" là gì, không cần một
    // từ tiếng Anh xoá mất phân biệt.
    expect(text).toContain("phả đồ");
    expect(text).toContain("Danh xưng");
    expect(text).toContain("giỗ");
  });

  it("hai ngôn ngữ có cùng bộ khoá ở nhánh auth mới", () => {
    for (const nhanh of ["invitation", "accountState", "guestMode"] as const) {
      const vi = Object.keys(viMessages.auth[nhanh] as Record<string, unknown>).sort();
      const en = Object.keys(enMessages.auth[nhanh] as Record<string, unknown>).sort();
      expect(en, `nhánh auth.${nhanh} lệch khoá`).toEqual(vi);
    }
  });
});

import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-100",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-100",
}));

const { PersonProfile } = await import("@/components/person/person-profile");

/**
 * NT-TV-07 — thành viên mở hồ sơ **người khác**: không sửa thẳng được, nên lối duy nhất là
 * "Đề nghị đính chính". Bấm thật ngày 24/09: nút ấy không có trên hồ sơ, trong ngăn phả đồ, hay ở
 * bất cứ đâu người dùng tới được — nó chỉ nằm trên màn SỬA (người không sửa được thì không tới)
 * và trang `/persons/{id}/correction` (không liên kết nào dẫn tới). Máy chủ thì vẫn mở cửa
 * (`canRequestCorrection = true`). Ca kiểm này ghim nút vào chính hồ sơ.
 *
 * Mock: `member` là p-102; p-100 là một người bà con còn sống khác (xem `mocks/identity.ts`).
 */
async function renderProfile(personId: string, role: "member" | "branch-head" | "admin", compact = false) {
  renderWithProviders(<PersonProfile personId={personId} compact={compact} />, { role });
  await waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument(), {
    timeout: 5000,
  });
  await screen.findByRole("heading", { level: 1 }, { timeout: 5000 });
}

describe("hồ sơ người khác, thành viên thường", () => {
  it("có nút 'Đề nghị đính chính' ngay trên trang hồ sơ, và KHÔNG có 'Sửa hồ sơ'", async () => {
    await renderProfile("p-100", "member");
    expect(await screen.findByRole("button", { name: /Đề nghị đính chính/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa hồ sơ/ })).not.toBeInTheDocument();
  });

  it("cả trong ngăn mở từ phả đồ, kèm lối ra trang hồ sơ đầy đủ", async () => {
    await renderProfile("p-100", "member", true);
    expect(await screen.findByRole("button", { name: /Đề nghị đính chính/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Mở trang hồ sơ đầy đủ/ })).toHaveAttribute(
      "href",
      "/persons/p-100"
    );
  });
});

describe("người sửa thẳng được thì không bị đẩy qua hàng đợi", () => {
  it("chính chủ: có 'Sửa hồ sơ', không có 'Đề nghị đính chính'", async () => {
    await renderProfile("p-102", "member");
    expect(screen.getByRole("link", { name: /Sửa hồ sơ/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Đề nghị đính chính/ })).not.toBeInTheDocument();
  });
});

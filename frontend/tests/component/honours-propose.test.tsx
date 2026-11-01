import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/vinh-danh",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/vinh-danh",
}));

const { HonoursListScreen } = await import("@/components/honours/honours-list-screen");

/**
 * NT-TV-14 — gửi đề nghị vinh danh xong, hộp thoại đóng lại. Bản trước chỉ đóng hộp thoại: người
 * dùng thấy một hộp thoại biến mất và phải tự đoán đã gửi hay đã huỷ. Nay có lời xác nhận nói rõ
 * mục ấy nằm ở đâu và đang chờ ai.
 */
describe("Vinh danh — đề nghị cho chính mình", () => {
  it("gửi xong: hộp thoại đóng, có lời xác nhận, mục mới nằm ở 'Đang chờ duyệt'", async () => {
    const { user } = renderWithProviders(<HonoursListScreen />, { role: "member" });

    await user.click(await screen.findByRole("button", { name: "Đề nghị vinh danh" }));
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect(within(dialog).getByText(/Đề nghị vinh danh cho chính ông\/bà/)).toBeInTheDocument());

    await user.type(within(dialog).getByLabelText("Tiêu đề"), "Thạc sĩ Quản trị");
    await user.click(within(dialog).getByRole("button", { name: "Gửi đề nghị" }));

    expect(await screen.findByText("Đã gửi đề nghị vinh danh")).toBeInTheDocument();
    expect(screen.getByText(/«Thạc sĩ Quản trị» đang nằm ở mục Đang chờ duyệt/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    const pending = await screen.findByText("Đang chờ duyệt", { selector: "h2" });
    expect(pending).toBeInTheDocument();
  });
});

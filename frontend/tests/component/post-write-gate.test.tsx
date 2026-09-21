import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/bai-viet/moi",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/bai-viet/moi",
}));

const { PostComposeScreen } = await import("@/components/posts/post-compose-screen");

/**
 * **Cổng "viết bài là tiếng nói của người trong họ"** — quyết định đã chốt
 * (design 07 §2). Ba vai, ba câu trả lời, và cả ba đều KHÔNG được để lộ ô
 * tiêu đề/nội dung của biểu mẫu soạn bài.
 *
 * `role: "admin"` đứng cho ca "đã đăng nhập nhưng chưa gắn nhân khẩu nào"
 * (`identityOf("admin").personId === null` trong bộ giả lập — System Admin kỹ
 * thuật, tách khỏi danh xưng dòng tộc theo đúng CLAUDE.md) — bộ chuyển vai dev
 * không có trạng thái nào khác biểu diễn đúng ca này.
 */
describe("PostWriteGate — ai viết được bài", () => {
  it("khách chưa đăng nhập: thấy lời mời đăng nhập, KHÔNG thấy biểu mẫu", async () => {
    renderWithProviders(<PostComposeScreen />, { role: "guest" });

    await waitFor(() => {
      expect(screen.getByText("Cần đăng nhập để viết bài")).toBeInTheDocument();
    });
    expect(screen.queryByLabelText("Tiêu đề")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Nội dung")).not.toBeInTheDocument();
  });

  it("khách chưa đăng nhập: 'Đăng ký' là một liên kết THẬT tới /dang-ky, không gộp vào nút Đăng nhập", async () => {
    // Trước bản sửa này, nút DUY NHẤT ở màn này ghi "Đăng nhập / Đăng ký" mà
    // chỉ gọi `auth.login()` — thẳng tới Keycloak, nơi
    // `registrationAllowed: false` (`infra/keycloak/realm-giapha.json`) khiến
    // trang ấy không có lối đăng ký nào. Nút phải hứa đúng thứ nó làm được.
    renderWithProviders(<PostComposeScreen />, { role: "guest" });

    await waitFor(() => {
      expect(screen.getByText("Cần đăng nhập để viết bài")).toBeInTheDocument();
    });

    const registerLink = screen.getByRole("link", { name: /Đăng ký bằng mã mời dòng họ/ });
    expect(registerLink).toHaveAttribute("href", "/dang-ky");

    // Nút "Đăng nhập" vẫn còn, tách riêng — không còn chữ "Đăng ký" dính vào nó.
    expect(screen.getByRole("button", { name: "Đăng nhập" })).toBeInTheDocument();
  });

  it("đã đăng nhập nhưng chưa được duyệt vào phả: thấy lý do rõ ràng, KHÔNG thấy biểu mẫu", async () => {
    renderWithProviders(<PostComposeScreen />, { role: "admin" });

    await waitFor(() => {
      expect(screen.getByText("Chưa được duyệt vào phả")).toBeInTheDocument();
    });
    expect(screen.queryByLabelText("Tiêu đề")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Nội dung")).not.toBeInTheDocument();
    // Không giấu nút rồi để người dùng tự đoán — cả hai lối đi tiếp đều hiện ra.
    expect(screen.getByText("Nhận diện mình trong phả")).toBeInTheDocument();
    expect(screen.getByText("Xem trạng thái đơn đã gửi")).toBeInTheDocument();
  });

  it("thành viên đã được duyệt vào phả: thấy biểu mẫu soạn bài", async () => {
    renderWithProviders(<PostComposeScreen />, { role: "member" });

    await waitFor(() => {
      expect(screen.getByLabelText("Tiêu đề")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("Nội dung")).toBeInTheDocument();
  });
});

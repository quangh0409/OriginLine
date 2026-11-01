import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { claimRoutes } from "@/components/claim";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/tree",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/tree",
}));

const { PersonProfileDrawer } = await import("@/components/person/person-profile-drawer");

/**
 * **Nút "Đây là tôi" ở chân ngăn hồ sơ mở từ phả đồ** — NT-MOI-05/07, lỗi đo được bằng trình
 * duyệt thật ngày 24/09: nút trên dải dưới ô tìm chỉ hiện khi một node được chọn, mà chọn node
 * chính là mở ngăn này — ngăn có lớp phủ nên nút kia luôn nằm dưới lớp phủ (máy tính: bấm vào
 * là đóng ngăn; điện thoại: bị che hẳn). Ca kiểm này ghim lối đi thay thế.
 *
 * Tài khoản chưa gắn phả được dựng bằng cách ghi đè `/me` với đúng hình dạng máy chủ thật trả
 * cho người vừa đăng ký bằng mã dòng họ: có `appUserId`, `personId: null`, vai MEMBER.
 */
function asUnlinkedAccount() {
  server.use(
    http.get(`${API_BASE_URL}/api/v1/me`, () =>
      HttpResponse.json({
        appUserId: "u-moi",
        personId: null,
        role: "MEMBER",
        clanWide: false,
        managedBranches: [],
        homeBranch: null,
        linkedToTree: false,
      })
    )
  );
}

describe("tài khoản chưa gắn phả mở hồ sơ một người từ phả đồ", () => {
  it("người còn sống: chân ngăn có nút 'Đây là tôi — {tên}' dẫn tới đúng claimRoutes", async () => {
    asUnlinkedAccount();
    renderWithProviders(<PersonProfileDrawer personId="p-100" onClose={() => {}} />, {
      role: "member",
    });
    const nut = await screen.findByTestId("person-claim-this-is-me", {}, { timeout: 5000 });
    expect(nut).toHaveTextContent(/^Đây là tôi — .+/);
    expect(nut).toHaveAttribute("href", claimRoutes.forPerson("p-100"));
  });

  it("người đã khuất: không có nút, chỉ đúng câu vì sao — chặn ngay lúc chọn", async () => {
    asUnlinkedAccount();
    renderWithProviders(<PersonProfileDrawer personId="p-001" onClose={() => {}} />, {
      role: "member",
    });
    expect(
      await screen.findByText("Ô này là của một cụ đã khuất, nên không nhận được.", {}, { timeout: 5000 })
    ).toBeInTheDocument();
    expect(screen.queryByTestId("person-claim-this-is-me")).not.toBeInTheDocument();
  });
});

describe("ai KHÔNG thấy chân ngăn", () => {
  it("thành viên đã gắn phả — với họ nút này là nhiễu", async () => {
    renderWithProviders(<PersonProfileDrawer personId="p-100" onClose={() => {}} />, {
      role: "member",
    });
    // Đợi hồ sơ tải xong rồi mới khẳng định vắng mặt, kẻo "chưa kịp hiện" bị đọc thành "không hiện".
    await waitFor(() => expect(screen.getAllByText(/Đời thứ/).length).toBeGreaterThan(0), {
      timeout: 5000,
    });
    expect(screen.queryByTestId("person-claim-this-is-me")).not.toBeInTheDocument();
    expect(screen.queryByTestId("person-claim-deceased-note")).not.toBeInTheDocument();
  });
});

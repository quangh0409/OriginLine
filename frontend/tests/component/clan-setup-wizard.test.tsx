import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { resetClanMockState } from "@/mocks/handlers/clan";
import type { Problem } from "@/types/api";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/quan-tri",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/quan-tri",
}));

const { ClanSetupWizard } = await import("@/components/clan-setup/clan-setup-wizard");

/**
 * Vài ca ở đây gõ nhiều ký tự thật (`user.type`) qua HAI lượt gửi liên tiếp,
 * cộng một lượt tải đồ thị giả lập ~4.000 người cho `readiness` — cùng lý do
 * `membership-password-reset.test.tsx` đã đặt hằng số này: sàn 20 giây mặc
 * định của Vitest sát nút trên máy đang chạy, không phải một dấu hiệu treo.
 */
const SLOW = 45_000;

/**
 * **Màn "Khởi tạo dòng họ"** (`/quan-tri`) — quyết định 26/09/2026.
 *
 * Bốn điều khoá ở đây:
 *  1. cổng vai — CHỈ `ADMIN` vào được, mọi vai khác (kể cả Hội đồng/Trưởng chi)
 *     thấy một câu từ chối, không thấy biểu mẫu;
 *  2. Bước 1 lưu được (tạo mới lẫn cập nhật), gợi ý `rootBranchPath` từ tên,
 *     và một `409 CLAN_ROOT_LOCKED` bị ép qua `server.use` phải hiện đúng câu;
 *  3. Bước 2 tạo tài khoản Hội đồng, liên kết đặt mật khẩu CHỈ hiện một lần,
 *     và gửi lại đúng định danh lần hai bị chặn bởi `422 IDENTITY_ALREADY_REGISTERED`;
 *  4. thanh tiến độ đọc `readiness.steps` — Bước 1 chuyển "Đã xong" ngay sau
 *     khi lưu, không cần tải lại trang.
 */
describe("Khởi tạo dòng họ (/quan-tri)", () => {
  beforeEach(() => {
    resetRouterMock();
    resetClanMockState();
  });

  it("thành viên thường không vào được — chỉ thấy lời từ chối, không thấy biểu mẫu", async () => {
    renderWithProviders(<ClanSetupWizard />, { role: "member" });
    expect(
      await screen.findByText("Chỉ Quản trị hệ thống mới vào được màn này.")
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Khởi tạo dòng họ" })).not.toBeInTheDocument();
  });

  it("Trưởng chi cũng không vào được — cùng câu từ chối như thành viên thường", async () => {
    renderWithProviders(<ClanSetupWizard />, { role: "branch-head" });
    expect(
      await screen.findByText("Chỉ Quản trị hệ thống mới vào được màn này.")
    ).toBeInTheDocument();
  });

  it("Quản trị hệ thống vào được, thấy tiêu đề và cả năm bước", async () => {
    renderWithProviders(<ClanSetupWizard />, { role: "admin" });
    expect(await screen.findByRole("heading", { name: "Khởi tạo dòng họ" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Hồ sơ dòng họ/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Hội đồng đầu tiên/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Nhập phả từ Excel/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Phát mã mời/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Sẵn sàng/ })).toBeInTheDocument();
  });

  it("chưa khởi tạo: Bước 1 tự mở, và mục 'Hồ sơ dòng họ' hiện 'Đang ở bước này'", async () => {
    renderWithProviders(<ClanSetupWizard />, { role: "admin" });
    await screen.findByLabelText("Tên dòng họ");

    const profileButton = screen.getByRole("button", { name: /Hồ sơ dòng họ/ });
    expect(within(profileButton).getByText("Đang ở bước này")).toBeInTheDocument();
  });

  it(
    "gõ tên dòng họ thì gợi ý gốc phả tự điền, lưu xong thì bước 1 chuyển 'Đã xong'",
    async () => {
      const { user } = renderWithProviders(<ClanSetupWizard />, { role: "admin" });

      const nameBox = await screen.findByLabelText("Tên dòng họ");
      await user.type(nameBox, "Nguyễn Đình");

      const rootBox = screen.getByTestId("root-branch-path-input") as HTMLInputElement;
      await waitFor(() => expect(rootBox.value).toBe("ho_nguyen_dinh"));

      await user.click(screen.getByRole("radio", { name: "Bắc" }).closest("label")!);
      await user.click(screen.getByRole("button", { name: "Lưu hồ sơ" }));

      expect(await screen.findByText("Đã lưu hồ sơ dòng họ.")).toBeInTheDocument();

      const profileButton = screen.getByRole("button", { name: /Hồ sơ dòng họ/ });
      await waitFor(() => expect(within(profileButton).getByText("Đã xong")).toBeInTheDocument());
    },
    SLOW
  );

  it("gõ gốc phả tay rồi mới đổi tên: KHÔNG bị gợi ý ghi đè lên gốc đã tự sửa", async () => {
    const { user } = renderWithProviders(<ClanSetupWizard />, { role: "admin" });

    const nameBox = await screen.findByLabelText("Tên dòng họ");
    await user.type(nameBox, "Nguyễn Đình");

    const rootBox = screen.getByTestId("root-branch-path-input") as HTMLInputElement;
    await waitFor(() => expect(rootBox.value).toBe("ho_nguyen_dinh"));

    await user.clear(rootBox);
    await user.type(rootBox, "nguyen_dinh_ha_noi");

    await user.type(nameBox, " (chi Hà Nội)");
    // Gợi ý không được kéo ngược ô gốc về giá trị suy từ tên nữa.
    expect(rootBox.value).toBe("nguyen_dinh_ha_noi");
  });

  it("bỏ trống tên dòng họ rồi lưu: báo lỗi tại chỗ, KHÔNG gọi mạng", async () => {
    const { user } = renderWithProviders(<ClanSetupWizard />, { role: "admin" });
    await screen.findByLabelText("Tên dòng họ");

    const calls: string[] = [];
    server.events.on("request:start", ({ request }) => {
      if (request.method === "PUT") calls.push(request.url);
    });

    await user.click(screen.getByRole("button", { name: "Lưu hồ sơ" }));

    expect(await screen.findByText("Nhập tên dòng họ.")).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it(
    "409 CLAN_ROOT_LOCKED (ép qua server.use): hiện đúng câu, không phải lỗi chung chung",
    async () => {
      const { user } = renderWithProviders(<ClanSetupWizard />, { role: "admin" });

      const nameBox = await screen.findByLabelText("Tên dòng họ");
      await user.type(nameBox, "Nguyễn Đình");
      await user.click(screen.getByRole("radio", { name: "Bắc" }).closest("label")!);
      await user.click(screen.getByRole("button", { name: "Lưu hồ sơ" }));
      await screen.findByText("Đã lưu hồ sơ dòng họ.");

      // Hồ sơ đã tồn tại (đã lưu ở trên) — buộc lượt CẬP NHẬT kế tiếp trả 409,
      // mô phỏng một Quản trị khác vừa khoá gốc phả giữa hai lượt gọi.
      server.use(
        http.put(`${API_BASE_URL}/api/v1/admin/clan-profile`, () =>
          HttpResponse.json(
            {
              type: "about:blank",
              title: "Gốc phả đã khoá",
              status: 409,
              code: "CLAN_ROOT_LOCKED",
            } satisfies Problem,
            { status: 409 }
          )
        )
      );

      await user.click(screen.getByRole("button", { name: "Lưu hồ sơ" }));

      expect(
        await screen.findByText(
          "Gốc phả đã khoá vì dòng họ đã có nhân khẩu. Tải lại trang rồi sửa các trường khác."
        )
      ).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "Bước 2: tạo tài khoản Hội đồng hiện liên kết một lần, và gửi trùng định danh bị chặn",
    async () => {
      const { user } = renderWithProviders(<ClanSetupWizard />, { role: "admin" });
      await screen.findByLabelText("Tên dòng họ");

      await user.click(screen.getByRole("button", { name: /Hội đồng đầu tiên/ }));

      await user.type(await screen.findByLabelText("Họ và tên"), "Nguyễn Văn Cả");
      await user.type(screen.getByLabelText("Số điện thoại hoặc email đăng nhập"), "0900000001");
      await user.click(screen.getByRole("button", { name: "Tạo tài khoản Hội đồng" }));

      expect(await screen.findByText("Đã tạo tài khoản Hội đồng")).toBeInTheDocument();
      expect(screen.getByText("Đây là một quyền lực thật")).toBeInTheDocument();
      expect(screen.getByTestId("ma-mot-lan")).toHaveTextContent("dat-mat-khau");

      await user.click(screen.getByRole("button", { name: "Tạo tài khoản khác" }));

      await user.type(await screen.findByLabelText("Họ và tên"), "Nguyễn Văn Hai");
      // Cùng định danh vừa dùng ở trên.
      await user.type(screen.getByLabelText("Số điện thoại hoặc email đăng nhập"), "0900000001");
      await user.click(screen.getByRole("button", { name: "Tạo tài khoản Hội đồng" }));

      // Bộ giả lập trả `detail` trùng chữ với thông điệp — `Alert` vẽ nó ở CẢ
      // hai chỗ (`message` và `description`), nên khẳng định có mặt chứ không
      // khẳng định DUY NHẤT.
      const matches = await screen.findAllByText(
        "Số điện thoại/email này đã có tài khoản trong hệ thống."
      );
      expect(matches.length).toBeGreaterThan(0);
    },
    SLOW
  );
});

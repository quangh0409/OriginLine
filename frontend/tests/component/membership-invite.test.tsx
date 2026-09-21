import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import {
  findClanInviteMock,
  resetMembershipAdminMockState,
} from "@/mocks/handlers/membership-admin";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/quan-ly/phat-ma",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/quan-ly/phat-ma",
}));

const { InviteScreen } = await import("@/components/membership/invite-screen");

/**
 * **Màn phát mã và phát lời mời** — một màn, hai việc, hai vai.
 *
 * Ba luật được khoá ở đây, và cả ba đều đến thẳng từ design 07 §1.2–§1.3:
 *
 *  1. **bộ đếm lượt dùng phải hiện, và hiện cho nổi bật.** Nó là "chốt quan
 *     trọng nhất và dễ bị bỏ qua nhất": Hội đồng thấy 400 lượt trên một dòng
 *     họ 600 người thì *biết* mà thu hồi. Không có bộ đếm thì mã rò ra và mọi
 *     thứ trông vẫn bình thường;
 *  2. **mã chỉ hiện một lần** — CSDL lưu băm, không endpoint nào đọc lại được;
 *     giao diện phải nói điều ấy **trước khi** người dùng rời màn;
 *  3. **hai vai thấy hai thứ khác nhau** — Trưởng chi không thấy nửa mã dòng
 *     họ, vì bấm vào là chắc chắn `403`.
 *
 * Dữ liệu mồi: `ci-001` là chính ví dụ của §1.2 — **400 lượt / 600 người**.
 */

interface CapturedRequest {
  method: string;
  url: string;
}

function captureRequests(): CapturedRequest[] {
  const captured: CapturedRequest[] = [];
  server.events.on("request:start", ({ request }) => {
    captured.push({ method: request.method, url: request.url });
  });
  return captured;
}

/** Bộ test này chờ MSW + antd nhiều lần; 45s là mức an toàn trên máy chậm. */
const SLOW = 45_000;

beforeEach(() => {
  resetRouterMock();
  resetMembershipAdminMockState();
  server.events.removeAllListeners("request:start");
});

describe("bộ đếm lượt dùng của mã dòng họ", () => {
  it(
    "hiện đúng số lượt đã dùng, và đối chiếu với số người trong họ",
    async () => {
      renderWithProviders(<InviteScreen />, { role: "admin" });

      const the = await waitFor(
        () => {
          const nodes = screen.getAllByTestId("ma-dong-ho");
          const found = nodes.find((n) => n.textContent?.includes("Nhóm Zalo họ Nguyễn 2026"));
          if (!found) throw new Error("chưa thấy mã ci-001");
          return found;
        },
        { timeout: 15_000 }
      );

      // Con số trần trụi, ở đúng chỗ mắt nhìn vào đầu tiên.
      expect(within(the).getByTestId("bo-dem-luot-dung")).toHaveTextContent("400");
      expect(within(the).getByText(/trên 600 lượt cho phép/)).toBeInTheDocument();

      // Và câu biến nó thành một phán đoán được — 400 trên 600 người đang sống.
      // Không có dòng này thì "400" một mình không nói lên điều gì.
      const doiChieu = within(the).getByTestId("doi-chieu-nhan-khau");
      expect(doiChieu.textContent).toContain("400");
      expect(doiChieu.textContent).toContain("600");

      // Hạn dùng đi kèm bộ đếm: 400 lượt của một mã sắp hết hạn khác hẳn 400
      // lượt của một mã còn hai tháng.
      expect(within(the).getByText("Hết hạn")).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "hiện `usability` chứ không phải `status`, và cảnh báo mã không có trần",
    async () => {
      renderWithProviders(<InviteScreen />, { role: "admin" });
      await screen.findAllByTestId("ma-dong-ho", {}, { timeout: 15_000 });

      const khongTran = screen
        .getAllByTestId("ma-dong-ho")
        .find((n) => n.textContent?.includes("Phiếu phát tại lễ giỗ tổ"));
      expect(khongTran).toBeTruthy();
      expect(within(khongTran as HTMLElement).getByText(/chưa đặt trần lượt dùng/)).toBeTruthy();

      // Mã đã thu hồi VẪN ở lại danh sách, kèm bộ đếm của nó — con số ấy là
      // bằng chứng rằng việc thu hồi là đúng.
      const daThuHoi = screen
        .getAllByTestId("ma-dong-ho")
        .find((n) => n.textContent?.includes("Mã cũ — đã thu hồi"));
      expect(daThuHoi).toBeTruthy();
      expect(within(daThuHoi as HTMLElement).getByTestId("bo-dem-luot-dung")).toHaveTextContent(
        "87"
      );
      expect(within(daThuHoi as HTMLElement).getByText("Đã thu hồi")).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "thu hồi một mã thì mã ấy đóng lại ở máy chủ",
    async () => {
      const { user } = renderWithProviders(<InviteScreen />, { role: "admin" });
      await screen.findAllByTestId("ma-dong-ho", {}, { timeout: 15_000 });

      const the = screen
        .getAllByTestId("ma-dong-ho")
        .find((n) => n.textContent?.includes("Nhóm Zalo họ Nguyễn 2026")) as HTMLElement;

      await user.click(within(the).getByRole("button", { name: /Thu hồi mã/ }));
      const dialog = await screen.findByRole("dialog");
      expect(
        within(dialog).getByText(/Những người đã vào bằng mã không bị ảnh hưởng/)
      ).toBeInTheDocument();

      await user.type(within(dialog).getByRole("textbox"), "Mã bị chuyển tiếp ra ngoài.");
      await user.click(within(dialog).getByRole("button", { name: /^Thu hồi mã$/ }));

      await waitFor(() => expect(findClanInviteMock("ci-001")?.status).toBe("REVOKED"), {
        timeout: 15_000,
      });
    },
    SLOW
  );
});

describe("mã chỉ hiện một lần", () => {
  it(
    "hiện mã vừa phát kèm lời cảnh báo, rồi không còn cách nào đọc lại",
    async () => {
      const captured = captureRequests();
      const { user } = renderWithProviders(<InviteScreen />, { role: "admin" });
      await screen.findAllByTestId("ma-dong-ho", {}, { timeout: 15_000 });

      await user.click(screen.getByRole("button", { name: /Phát mã/ }));

      const ma = await screen.findByTestId("ma-mot-lan", {}, { timeout: 15_000 });
      const chuoiMa = ma.textContent ?? "";
      // Khuôn Crockford Base32 chia hai nhóm năm ký tự, đọc được qua điện thoại.
      expect(chuoiMa).toMatch(/^[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$/);

      // Lời cảnh báo phải có mặt NGAY LÚC NÀY, không phải sau khi đóng.
      expect(screen.getByText(/Mã chỉ hiện một lần/)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Chép mã/ })).toBeInTheDocument();

      // Đóng khối phát mã.
      await user.click(screen.getByRole("button", { name: /Tôi đã chép xong/ }));
      await waitFor(() => expect(screen.queryByTestId("ma-mot-lan")).toBeNull());

      // Mã biến mất khỏi CẢ màn hình lẫn mọi phản hồi về sau: bản ghi mã mới có
      // mặt trong danh sách, nhưng không mang theo chuỗi mã.
      await waitFor(
        () => {
          const danhSach = screen.getAllByTestId("ma-dong-ho");
          expect(danhSach.length).toBeGreaterThan(3);
        },
        { timeout: 15_000 }
      );
      expect(document.body.textContent).not.toContain(chuoiMa);

      // Và không có lối gọi nào để đọc lại — chỉ đúng một `POST` sinh ra mã ấy.
      const phat = captured.filter(
        (r) => r.method === "POST" && r.url.includes("/clan-invites")
      );
      expect(phat).toHaveLength(1);
    },
    SLOW
  );
});

describe("hai vai thấy hai thứ khác nhau", () => {
  it(
    "Hội đồng thấy cả hai việc",
    async () => {
      renderWithProviders(<InviteScreen />, { role: "admin" });
      expect(
        await screen.findByRole("tab", { name: "Mã mời dòng họ" }, { timeout: 15_000 })
      ).toBeInTheDocument();
      expect(screen.getByRole("tab", { name: "Lời mời cá nhân" })).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "Trưởng chi chỉ thấy lời mời cá nhân, và không biết nửa kia tồn tại",
    async () => {
      renderWithProviders(<InviteScreen />, { role: "branch-head" });

      expect(
        await screen.findByText(/Chọn đúng một cụ trong chi mình/, {}, { timeout: 15_000 })
      ).toBeInTheDocument();
      expect(screen.queryByRole("tab", { name: "Mã mời dòng họ" })).toBeNull();
      expect(document.body.textContent).not.toContain("Nhóm Zalo họ Nguyễn 2026");
    },
    SLOW
  );

  it(
    "thành viên thường được nói thẳng là không phát mã được",
    async () => {
      renderWithProviders(<InviteScreen />, { role: "member" });
      expect(
        await screen.findByText(
          /Chỉ Trưởng chi và Hội đồng Tộc biểu mới phát được mã mời/,
          {},
          { timeout: 15_000 }
        )
      ).toBeInTheDocument();
    },
    SLOW
  );
});

describe("lời mời cá nhân", () => {
  it(
    "phát mã cho một cụ được chọn, và mã ấy cũng chỉ hiện một lần",
    async () => {
      const { user } = renderWithProviders(<InviteScreen />, { role: "branch-head" });
      await screen.findByText(/Chọn đúng một cụ trong chi mình/, {}, { timeout: 15_000 });

      // Bộ chọn chạy trên `/persons/search` — tìm không dấu ở MÁY CHỦ, nên test
      // gõ không dấu và KHÔNG tự đoán trước tên nào sẽ ra: bộ dữ liệu giả sinh
      // tên theo hạt giống, và ghim một cái tên cụ thể ở đây sẽ biến bài kiểm
      // này thành bài kiểm bộ sinh dữ liệu.
      const box = screen.getAllByRole("combobox")[0] as HTMLElement;
      await user.click(box);
      await user.type(box, "Nguyen");

      const option = await waitFor(
        () => {
          const nodes = Array.from(
            document.querySelectorAll(".ant-select-item-option")
          ) as HTMLElement[];
          if (nodes.length === 0) throw new Error("chưa có kết quả tìm kiếm");
          return nodes[0] as HTMLElement;
        },
        { timeout: 15_000 }
      );
      const tenDuocChon = option.getAttribute("title") ?? "";
      expect(tenDuocChon.length).toBeGreaterThan(0);
      await user.click(option);

      await user.click(screen.getByRole("button", { name: /Phát lời mời/ }));

      const ma = await screen.findByTestId("ma-mot-lan", {}, { timeout: 15_000 });
      expect(ma.textContent ?? "").toMatch(/^[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$/);

      // Mã hiện kèm TÊN người được mời: trong một buổi phát mười mã, mã thứ bảy
      // rất dễ đi nhầm người, và một mã cá nhân đi nhầm người là một tài khoản
      // gắn nhầm hồ sơ.
      expect(screen.getByText(`Mã dành cho ${tenDuocChon}`)).toBeInTheDocument();
      expect(screen.getByText(/Mã chỉ hiện một lần/)).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "không gửi gì khi chưa chọn người, và nói rõ thiếu gì",
    async () => {
      const captured = captureRequests();
      const { user } = renderWithProviders(<InviteScreen />, { role: "branch-head" });
      await screen.findByText(/Chọn đúng một cụ trong chi mình/, {}, { timeout: 15_000 });

      await user.click(screen.getByRole("button", { name: /Phát lời mời/ }));

      expect(await screen.findByRole("alert")).toHaveTextContent(/Xin chọn người được mời/);
      expect(
        captured.filter((r) => r.method === "POST" && r.url.endsWith("/invitations"))
      ).toHaveLength(0);
    },
    SLOW
  );

  it(
    "không để lọt một MISSING_MESSAGE nào lên màn hình",
    async () => {
      renderWithProviders(<InviteScreen />, { role: "admin" });
      await screen.findAllByTestId("ma-dong-ho", {}, { timeout: 15_000 });
      expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
    },
    SLOW
  );
});

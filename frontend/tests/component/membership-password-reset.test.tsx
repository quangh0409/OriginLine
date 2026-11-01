import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { resolvePersonMock, toSummaryMock } from "@/mocks/person-detail";
import { resetPasswordResetMockState } from "@/mocks/handlers/password-reset";
import type { PersonSummaryPage } from "@/types/api";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/quan-ly/cap-lai-mat-khau",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/quan-ly/cap-lai-mat-khau",
}));

const { PasswordResetScreen } = await import("@/components/membership/password-reset-screen");

/**
 * **Cấp lại liên kết đặt mật khẩu** — ô thứ sáu của `/quan-ly`, gọi tới
 * `POST /api/v1/password-resets` lần đầu tiên từ giao diện.
 *
 * Bốn ca bị chặn của dịch vụ thật (đọc `PasswordResetGrantService` javadoc):
 * phạm vi `ltree` · không tự cấp cho mình · tài khoản đích phải `ACTIVE` ·
 * tài khoản đích không được có địa chỉ thư. Bộ giả lập
 * (`src/mocks/handlers/password-reset.ts`) chạy đúng bốn phép kiểm ấy trên
 * dữ liệu thật của đồ thị/danh tính — không một cờ fixture riêng nào bịa ra
 * cho bài kiểm này dễ xanh hơn.
 */

const SLOW = 45_000;

/** Ép `/persons/search` trả về ĐÚNG một người, bất kể gõ gì — cho một ca cần chọn đích danh. */
function ghimKetQuaTraCuu(personId: string) {
  const person = resolvePersonMock(personId);
  if (!person) throw new Error(`Không dựng được hồ sơ giả cho ${personId}`);
  const page: PersonSummaryPage = {
    items: [toSummaryMock(person)],
    page: { page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false },
  };
  server.use(
    http.get(`${API_BASE_URL}/api/v1/persons/search`, () => HttpResponse.json(page))
  );
}

/** Một người sống thật trong đồ thị, không phải p-100/p-101, dùng cho các ca cần một chi/đời cụ thể. */
function timNguoiSong(duKien: (path: string | undefined) => boolean, tranhIds: string[]): string {
  for (const raw of getMockGraph().personsById.values()) {
    if (!raw.isAlive) continue;
    if (tranhIds.includes(raw.id)) continue;
    if (duKien(raw.primaryBranch?.path)) return raw.id;
  }
  throw new Error("Không tìm được người sống phù hợp trong đồ thị giả lập");
}

async function chonNguoiDuyNhat(user: ReturnType<typeof renderWithProviders>["user"]) {
  const box = screen.getByRole("combobox");
  await user.click(box);
  await user.type(box, "a");
  const option = await waitFor(
    () => {
      const nodes = Array.from(document.querySelectorAll(".ant-select-item-option")) as HTMLElement[];
      if (nodes.length === 0) throw new Error("chưa có kết quả tìm kiếm");
      return nodes[0]!;
    },
    { timeout: 15_000 }
  );
  await user.click(option);
}

beforeEach(() => {
  resetRouterMock();
  resetPasswordResetMockState();
  server.events.removeAllListeners("request:start");
});

describe("ô thứ sáu chỉ hiện cho người có quyền", () => {
  it(
    "thành viên thường được nói thẳng là không cấp lại được",
    async () => {
      renderWithProviders(<PasswordResetScreen />, { role: "member" });
      expect(
        await screen.findByText(
          /Chỉ Trưởng chi và Hội đồng Tộc biểu mới cấp lại được liên kết/,
          {},
          { timeout: 15_000 }
        )
      ).toBeInTheDocument();
      // Và không có ô tra người nào để bấm nhầm vào.
      expect(screen.queryByRole("combobox")).toBeNull();
    },
    SLOW
  );

  it(
    "Trưởng chi và Hội đồng thấy được biểu mẫu",
    async () => {
      renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      expect(await screen.findByRole("combobox", {}, { timeout: 15_000 })).toBeInTheDocument();
    },
    SLOW
  );
});

describe("chọn người bằng ô tra, không phải gõ tay personId", () => {
  it(
    "không có ô nhập personId nào trên màn hình",
    async () => {
      renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      await screen.findByRole("combobox", {}, { timeout: 15_000 });
      // Chỉ đúng một combobox (bộ chọn nhân khẩu) và một ô ghi chú — không có
      // ô nào tên/placeholder gợi ý gõ tay một UUID.
      expect(screen.getAllByRole("combobox")).toHaveLength(1);
      expect(document.querySelector("input[placeholder*='p-']")).toBeNull();
    },
    SLOW
  );
});

describe("bốn ca bị chặn — đúng câu, không phải tên mã lỗi", () => {
  it(
    "người ngoài phạm vi chi KHÔNG được đưa ra để chọn, và màn nói ra là đã ẩn",
    async () => {
      // Đo trên hệ thật 24/09: ô chọn cũ đưa ra cả người chi khác với dòng phụ
      // y hệt ("Đời thứ 7 · Ngành Trưởng"), Trưởng chi chọn nhầm và nhận 403.
      // Nay họ không có trong danh sách — và màn nói vì sao không thấy họ.
      const chi2Id = timNguoiSong((p) => p?.startsWith("root.chi_nhi") ?? false, []);
      ghimKetQuaTraCuu(chi2Id);

      const { user } = renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      const box = await screen.findByRole("combobox", {}, { timeout: 15_000 });
      await user.click(box);
      await user.type(box, "a");

      expect(
        await screen.findByTestId("an-ngoai-pham-vi", {}, { timeout: 15_000 })
      ).toHaveTextContent(/ở chi khác không hiện ở đây/);
      expect(document.querySelectorAll(".ant-select-item-option")).toHaveLength(0);
    },
    SLOW
  );

  it(
    "không ai tự cấp cho chính mình được, kể cả khi tự chọn được mình",
    async () => {
      // p-103 (Trưởng chi giả lập) KHÔNG nằm trong đồ thị sinh tự động — ghim
      // kết quả tra cứu để dựng đúng ca "tự chọn mình", việc mà ô tra thật
      // không tình cờ làm được (xem javadoc `password-reset.ts`).
      ghimKetQuaTraCuu("p-103");

      const { user } = renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      await screen.findByRole("combobox", {}, { timeout: 15_000 });
      await chonNguoiDuyNhat(user);

      await user.click(screen.getByRole("button", { name: /Cấp liên kết/ }));

      expect(
        await screen.findByText(/Không tự cấp cho chính mình được/, {}, { timeout: 15_000 })
      ).toBeInTheDocument();
      expect(screen.queryByTestId("ma-mot-lan")).toBeNull();
    },
    SLOW
  );

  it(
    "tài khoản có địa chỉ thư thì bảo tự bấm Quên mật khẩu, không cấp",
    async () => {
      // p-100 là fixture DUY NHẤT có địa chỉ thư trong bộ giả lập.
      ghimKetQuaTraCuu("p-100");

      const { user } = renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      await screen.findByRole("combobox", {}, { timeout: 15_000 });
      await chonNguoiDuyNhat(user);

      await user.click(screen.getByRole("button", { name: /Cấp liên kết/ }));

      const canhBao = await screen.findByText(
        /đã có địa chỉ thư nên tự lấy lại mật khẩu được/,
        {},
        { timeout: 15_000 }
      );
      expect(canhBao).toBeInTheDocument();
      expect(canhBao.textContent).toContain("Quên mật khẩu");
      expect(screen.queryByTestId("ma-mot-lan")).toBeNull();
    },
    SLOW
  );

  it(
    "người chưa có tài khoản (ca phổ biến nhất) được nói rõ, không cấp",
    async () => {
      const chuaCoTaiKhoan = timNguoiSong(
        (p) => p === "root.chi_nhat",
        ["p-100", "p-101"]
      );
      ghimKetQuaTraCuu(chuaCoTaiKhoan);

      const { user } = renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      await screen.findByRole("combobox", {}, { timeout: 15_000 });
      await chonNguoiDuyNhat(user);

      await user.click(screen.getByRole("button", { name: /Cấp liên kết/ }));

      expect(
        await screen.findByText(/chưa có tài khoản/, {}, { timeout: 15_000 })
      ).toBeInTheDocument();
      expect(screen.queryByTestId("ma-mot-lan")).toBeNull();
    },
    SLOW
  );
});

describe("cấp thành công: liên kết hiện đúng một lần, và cả hai vế của đánh đổi", () => {
  it(
    "hiện liên kết kèm cảnh báo TRƯỚC, câu đã báo chính chủ SAU, rồi biến mất khi đóng",
    async () => {
      const captured: string[] = [];
      server.events.on("request:start", ({ request }) => {
        if (request.method === "POST" && request.url.includes("/password-resets")) {
          captured.push(request.url);
        }
      });

      // p-101 (Nguyễn Thị Bé) là fixture DUY NHẤT có tài khoản mà KHÔNG có địa
      // chỉ thư — tên không đụng hàng với bảng tên sinh tự động
      // (MALE_GIVEN/FEMALE_GIVEN không có "Bé"), nên tìm bằng ô tra THẬT, không
      // ghim kết quả — đúng đường mà một Trưởng chi thật sẽ đi.
      const { user } = renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      const box = await screen.findByRole("combobox", {}, { timeout: 15_000 });
      await user.click(box);
      await user.type(box, "Be");
      const option = await waitFor(
        () => {
          const nodes = Array.from(
            document.querySelectorAll(".ant-select-item-option")
          ) as HTMLElement[];
          const found = nodes.find((n) => n.textContent?.includes("Nguyễn Thị Bé"));
          if (!found) throw new Error("chưa thấy Nguyễn Thị Bé trong kết quả");
          return found;
        },
        { timeout: 15_000 }
      );
      await user.click(option);

      await user.click(screen.getByRole("button", { name: /Cấp liên kết/ }));

      const link = await screen.findByTestId("ma-mot-lan", {}, { timeout: 15_000 });
      const url = link.textContent ?? "";
      expect(url).toContain("token=");

      // Cảnh báo quyền lực thật phải có mặt cùng lúc với liên kết.
      expect(screen.getByText(/Đây là một quyền lực thật/)).toBeInTheDocument();
      // Và câu "đã báo chính chủ" — vế thứ hai của đánh đổi.
      expect(screen.getByText(/Chính chủ đã được báo/)).toBeInTheDocument();
      expect(screen.getByText(/Liên kết dành cho Nguyễn Thị Bé/)).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /Tôi đã chép xong/ }));
      await waitFor(() => expect(screen.queryByTestId("ma-mot-lan")).toBeNull());
      expect(document.body.textContent).not.toContain(url);

      expect(captured).toHaveLength(1);
    },
    SLOW
  );

  it(
    "không gửi gì khi chưa chọn người, và nói rõ thiếu gì",
    async () => {
      const captured: string[] = [];
      server.events.on("request:start", ({ request }) => {
        if (request.method === "POST" && request.url.includes("/password-resets")) {
          captured.push(request.url);
        }
      });

      const { user } = renderWithProviders(<PasswordResetScreen />, { role: "branch-head" });
      await screen.findByRole("combobox", {}, { timeout: 15_000 });

      await user.click(screen.getByRole("button", { name: /Cấp liên kết/ }));

      // KHÔNG `findByRole("alert")`: Ant Design gắn `role="alert"` lên MỌI
      // `<Alert>`, kể cả khối cảnh báo "quyền lực thật" luôn hiện trên màn —
      // nên ở đây có hai phần tử cùng vai, và truy vấn ấy sẽ ném lỗi mơ hồ.
      expect(
        await screen.findByText(/Xin chọn người quên mật khẩu/, {}, { timeout: 15_000 })
      ).toBeInTheDocument();
      expect(captured).toHaveLength(0);
    },
    SLOW
  );
});

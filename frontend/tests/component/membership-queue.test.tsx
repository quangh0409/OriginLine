import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { findClaimMock, resetMembershipAdminMockState } from "@/mocks/handlers/membership-admin";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/quan-ly/don-gia-nhap",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/quan-ly/don-gia-nhap",
}));

const { MembershipQueueScreen } = await import(
  "@/components/membership/membership-queue-screen"
);

/**
 * **Hàng chờ duyệt đơn xin vào phả** — phía Trưởng chi và Hội đồng.
 *
 * Bốn luật mà màn hình này sống chết theo, và cả bốn đều được khoá ở đây:
 *
 *  1. **hai người cùng nhận một nhân khẩu thì hiện CẢ HAI cạnh nhau**, không
 *     ưu tiên người gửi trước — trùng tên trong dòng họ là chuyện thường, và
 *     người gửi trước chưa chắc đúng (design 07 §1.4);
 *  2. **từ chối bắt buộc kèm lý do**, và lý do ấy đi tới người gửi — họ chỉ
 *     còn ít lần gửi lại, nên một chữ "không" làm lần sau sai y như lần này;
 *  3. **phạm vi theo chi cắt ở MÁY CHỦ** — Trưởng chi `root.chi_nhat` không
 *     nhận về đơn của `root.chi_nhi`, chứ không phải "nhận rồi client lọc";
 *  4. **hai loại đơn hiện khác nhau vì hệ quả khác nhau** — đơn "chưa có trong
 *     phả" là lối ghi vào phả, và bộ dò trùng của nó phải hiện ra trước khi
 *     người duyệt bấm.
 *
 * Dữ liệu mồi ở `src/mocks/handlers/membership-admin.ts`:
 *   `pc-001` + `pc-002` cùng nhận `p-100` (chi_nhat) → cụm tranh chấp
 *   `pc-003` NEW_PERSON, dò trùng CÓ ứng viên `p-100`
 *   `pc-004` NEW_PERSON, đã dò mà không thấy ai
 *   `pc-005` EXISTING của chi_nhi → Trưởng chi chi_nhat KHÔNG thấy
 *   `pc-006` NEW_PERSON CHƯA dò trùng
 */

interface CapturedRequest {
  method: string;
  url: string;
  body: unknown;
}

function captureWrites(): CapturedRequest[] {
  const captured: CapturedRequest[] = [];
  server.events.on("request:start", async ({ request }) => {
    if (request.method === "GET") return;
    let body: unknown;
    try {
      body = await request.clone().json();
    } catch {
      body = undefined;
    }
    captured.push({ method: request.method, url: request.url, body });
  });
  return captured;
}

const reviews = (captured: CapturedRequest[]) => captured.filter((r) => r.url.includes("/review"));

/** Bộ test này chờ MSW + antd nhiều lần; 45s là mức an toàn trên máy chậm. */
const SLOW = 45_000;

function card(id: string): HTMLElement {
  const node = document.querySelector(`[data-request-id="${id}"]`);
  if (!node) throw new Error(`Không tìm thấy thẻ đơn ${id}`);
  return node as HTMLElement;
}

async function openQueue(role: "member" | "branch-head" | "admin") {
  return renderWithProviders(<MembershipQueueScreen />, { role });
}

beforeEach(() => {
  resetRouterMock();
  resetMembershipAdminMockState();
  server.events.removeAllListeners("request:start");
});

describe("hai người cùng nhận một nhân khẩu", () => {
  it(
    "hiện CẢ HAI lá đơn trong một cụm, kèm số máy của từng người",
    async () => {
      await openQueue("branch-head");

      const cum = await screen.findByTestId("cum-tranh-chap", {}, { timeout: 15_000 });

      // Cả hai, không phải một. Đây là toàn bộ điểm của ca biên này.
      const don = within(cum).getAllByTestId("don-nhan-minh");
      expect(don).toHaveLength(2);

      // Hai số máy khác nhau — bằng chứng đây là hai người, và là thứ Trưởng
      // chi thật sự dùng để đối chiếu (họ gọi điện, không đọc màn hình).
      expect(within(cum).getByText("0912 000 111")).toBeInTheDocument();
      expect(within(cum).getByText("0912 000 222")).toBeInTheDocument();

      // Bài tự giới thiệu của từng người cũng phải có mặt: đó mới là thứ đối
      // chiếu được, số máy chỉ để gọi kiểm chứng.
      expect(within(cum).getByText(/con cụ Nguyễn Văn Tư, sinh năm 1981/)).toBeInTheDocument();
      expect(within(cum).getByText(/con thứ hai của ông Nguyễn Văn Tư/)).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "không ưu tiên người gửi trước — giữ nguyên thứ tự máy chủ trả về",
    async () => {
      await openQueue("branch-head");
      const cum = await screen.findByTestId("cum-tranh-chap", {}, { timeout: 15_000 });

      const ids = within(cum)
        .getAllByTestId("don-nhan-minh")
        .map((el) => el.getAttribute("data-request-id"));

      // Bộ giả lập trả `pc-002` (gửi SAU) trước `pc-001` (gửi TRƯỚC). Nếu màn
      // hình âm thầm sắp theo `createdAt` thì thứ tự này sẽ đảo lại.
      expect(ids).toEqual(["pc-002", "pc-001"]);
    },
    SLOW
  );

  it(
    "nói rõ rằng duyệt một đơn là đóng các đơn kia",
    async () => {
      const { user } = await openQueue("branch-head");
      await screen.findByTestId("cum-tranh-chap", {}, { timeout: 15_000 });

      await user.click(within(card("pc-001")).getByRole("button", { name: /Duyệt/ }));

      expect(
        await screen.findByText(/Duyệt đơn này là từ chối các đơn kia/)
      ).toBeInTheDocument();
    },
    SLOW
  );
});

describe("từ chối bắt buộc kèm lý do", () => {
  it(
    "không gửi gì lên máy chủ khi bấm Từ chối mà bỏ trống lý do",
    async () => {
      const captured = captureWrites();
      const { user } = await openQueue("branch-head");
      await screen.findByTestId("cum-tranh-chap", {}, { timeout: 15_000 });

      await user.click(within(card("pc-001")).getByRole("button", { name: /Từ chối/ }));

      const dialog = await screen.findByRole("dialog");
      // Nút xác nhận trong hộp thoại, không phải nút trên thẻ.
      await user.click(within(dialog).getByRole("button", { name: /^Từ chối$/ }));

      expect(
        await within(dialog).findByText(/Xin nêu lý do từ chối/)
      ).toBeInTheDocument();
      expect(reviews(captured)).toHaveLength(0);

      // Đơn vẫn còn nguyên ở phía máy chủ.
      expect(findClaimMock("pc-001")?.status).toBe("PENDING");
    },
    SLOW
  );

  it(
    "gửi lý do lên máy chủ và nói rõ rằng người gửi sẽ đọc được",
    async () => {
      const captured = captureWrites();
      const { user } = await openQueue("branch-head");
      await screen.findByTestId("cum-tranh-chap", {}, { timeout: 15_000 });

      await user.click(within(card("pc-002")).getByRole("button", { name: /Từ chối/ }));
      const dialog = await screen.findByRole("dialog");

      // Câu này phải có mặt TRƯỚC khi người duyệt gõ — nó đổi cách họ viết.
      expect(within(dialog).getByText(/sẽ được gửi tới người khai/)).toBeInTheDocument();

      const lyDo = "Tôi đã gọi số này, người nghe máy không phải con cụ Tư.";
      await user.type(within(dialog).getByRole("textbox"), lyDo);
      await user.click(within(dialog).getByRole("button", { name: /^Từ chối$/ }));

      await waitFor(() => expect(reviews(captured)).toHaveLength(1), { timeout: 15_000 });
      expect(reviews(captured)[0]?.body).toMatchObject({ approve: false, note: lyDo });

      await waitFor(() => expect(findClaimMock("pc-002")?.status).toBe("REJECTED"), {
        timeout: 15_000,
      });
      expect(findClaimMock("pc-002")?.reviewNote).toBe(lyDo);
    },
    SLOW
  );
});

describe("phạm vi theo chi", () => {
  it(
    "Trưởng chi chi Nhất không nhận về đơn của chi Nhì",
    async () => {
      await openQueue("branch-head");
      await screen.findByTestId("cum-tranh-chap", {}, { timeout: 15_000 });

      expect(document.querySelector('[data-request-id="pc-005"]')).toBeNull();
      expect(document.body.textContent).not.toContain("Nguyễn Thị Hoa");
    },
    SLOW
  );

  it(
    "Hội đồng thấy cả họ, kể cả đơn của chi Nhì",
    async () => {
      await openQueue("admin");
      await waitFor(() => expect(card("pc-005")).toBeInTheDocument(), { timeout: 15_000 });
      expect(within(card("pc-005")).getByText("0966 777 888")).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "thành viên thường không mở được hàng đợi, và được nói rõ vì sao",
    async () => {
      await openQueue("member");
      expect(
        await screen.findByText(/Chỉ Trưởng chi và Hội đồng Tộc biểu mới duyệt được/, {}, {
          timeout: 15_000,
        })
      ).toBeInTheDocument();
    },
    SLOW
  );
});

describe("đơn 'chưa có trong phả' hiện khác vì hệ quả khác", () => {
  it(
    "nói thẳng rằng duyệt là ghi thêm một người vào phả, và nút cũng nói thế",
    async () => {
      await openQueue("branch-head");
      const the = await waitFor(() => card("pc-003"), { timeout: 15_000 });

      expect(
        within(the).getByText("Duyệt đơn này là ghi thêm một người vào gia phả")
      ).toBeInTheDocument();
      expect(
        within(the).getByRole("button", { name: /Duyệt — tạo nhân khẩu mới/ })
      ).toBeInTheDocument();

      // Người thân đã có trong phả là bắt buộc, và nó quyết định ai duyệt.
      expect(within(the).getByText(/Cha của người khai/)).toBeInTheDocument();
      // Tên người thân đến từ MỘT LƯỢT GỌI RIÊNG: `PersonClaim` chỉ chở
      // `relativePersonId`, và bộ lọc phân tầng riêng tư chỉ chạy ở
      // `GET /persons/{id}` — bằng phiên của NGƯỜI DUYỆT, không của người gửi.
      // Vì thế phải chờ, y như khối dò trùng ở dưới.
      expect(
        await within(the).findByText("Nguyễn Văn Cẩn", {}, { timeout: 15_000 })
      ).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "hiện kết quả dò trùng với TÊN tra từ /persons/{id}, không đọc từ ảnh chụp",
    async () => {
      await openQueue("branch-head");
      const the = await waitFor(() => card("pc-003"), { timeout: 15_000 });

      const doTrung = within(the).getByTestId("do-trung");
      expect(within(doTrung).getByText(/Có thể đây là người này/)).toBeInTheDocument();
      expect(within(doTrung).getByText("Giống 84%")).toBeInTheDocument();
      expect(within(doTrung).getByText("Trùng tên không dấu")).toBeInTheDocument();

      // Ảnh chụp KHÔNG chứa tên — nó chỉ có `personId: "p-100"`. Cái tên này
      // chỉ xuất hiện được nếu giao diện thật sự gọi `GET /persons/p-100` bằng
      // phiên của người duyệt, đúng như `person_claim.screening` của V16 đòi.
      expect(
        await within(doTrung).findByText("Nguyễn Văn An", {}, { timeout: 15_000 })
      ).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "phân biệt 'đã dò mà không thấy ai' với 'chưa dò'",
    async () => {
      await openQueue("branch-head");
      await waitFor(() => card("pc-004"), { timeout: 15_000 });

      // Hai câu khác nhau cho hai tình huống khác nhau. Im lặng ở một trong hai
      // chỗ này là để người duyệt tưởng máy đã kiểm giúp mình.
      expect(
        within(card("pc-004")).getByText(/đã dò khắp dòng họ và không thấy ai/)
      ).toBeInTheDocument();
      expect(
        within(card("pc-006")).getByText(/chưa dò trùng cho đơn này/)
      ).toBeInTheDocument();
    },
    SLOW
  );

  it(
    "liệt kê hệ quả trước khi tạo người, thay vì hỏi 'bạn chắc chứ'",
    async () => {
      const { user } = await openQueue("branch-head");
      const the = await waitFor(() => card("pc-003"), { timeout: 15_000 });

      await user.click(within(the).getByRole("button", { name: /Duyệt — tạo nhân khẩu mới/ }));
      const dialog = await screen.findByRole("dialog");

      expect(within(dialog).getByText(/Nguyễn Văn Ân/)).toBeInTheDocument();
      expect(
        within(dialog).getByText(/không xoá cứng bao giờ, nên người này sẽ ở lại trong cây/)
      ).toBeInTheDocument();
    },
    SLOW
  );
});

describe("câu chữ", () => {
  it(
    "không để lọt một MISSING_MESSAGE nào lên màn hình",
    async () => {
      await openQueue("branch-head");
      await screen.findByTestId("cum-tranh-chap", {}, { timeout: 15_000 });
      expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
    },
    SLOW
  );
});

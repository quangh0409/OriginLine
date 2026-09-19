import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/new",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/new",
}));

const { PersonForm } = await import("@/components/person-form/person-form");

/**
 * NGHI TRÙNG NHÂN KHẨU (`409 DUPLICATE_PERSON_SUSPECTED`) — hộp thoại song sinh
 * với kỵ húy, và cùng một ranh giới riêng tư:
 *
 *   <b>được phép nói trường nào của CHÍNH người dùng vừa nhập đã khớp; không
 *   bao giờ nói một giá trị đọc từ phả.</b>
 *
 * <p>Bộ dò quét toàn dòng họ và không biết người gọi là ai, nên ứng viên hoàn
 * toàn có thể là một người còn sống ở một chi khác. Vì vậy thân lỗi chỉ chở
 * `personId` + `score` + `signals` + `hint`, còn danh tính phải nạp riêng qua
 * `GET /persons/{id}`.</p>
 *
 * <p>Ba hình dạng được khoá ở đây, và cả ba đều là ca <b>bình thường</b>:</p>
 * <ol>
 *   <li><b>`personId` tra được</b> — người đã khuất, công khai. Ca thường gặp.</li>
 *   <li><b>`personId` trả 404</b> — người gọi không được biết bản ghi có tồn
 *       tại hay không. Không phải lỗi kỹ thuật.</li>
 *   <li><b>`personId === null`</b> — một dòng chưa ghi trong cùng lô nhập liệu;
 *       định danh duy nhất là `ref`, và <b>không được gọi `GET`</b>.</li>
 * </ol>
 */

/** Gieo cả ba hình dạng trong một phản hồi (src/mocks/handlers/persons.ts). */
const DUP_ALL_THREE = "Nguyễn Văn Nghi Trùng";
/** Chỉ ứng viên KHÔNG tra được — để kiểm tra hộp thoại im lặng đúng chỗ. */
const DUP_HIDDEN_ONLY = "Nguyễn Văn Nghi Trùng Ẩn";
/** Chỉ ứng viên chưa ghi trong lô nhập liệu. */
const DUP_IN_BATCH_ONLY = "Nguyễn Văn Nghi Trùng Lô";

interface CapturedRequest {
  method: string;
  url: string;
  body: unknown;
}

function captureRequests(): CapturedRequest[] {
  const captured: CapturedRequest[] = [];
  server.events.on("request:start", async ({ request }) => {
    const clone = request.clone();
    let body: unknown;
    try {
      body = await clone.json();
    } catch {
      body = undefined;
    }
    captured.push({ method: request.method, url: request.url, body });
  });
  return captured;
}

async function typeName(user: ReturnType<typeof renderWithProviders>["user"], value: string) {
  const input = screen.getByLabelText(/Họ và tên/);
  await user.clear(input);
  await user.type(input, value);
}

async function submitWith(name: string) {
  const rendered = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });
  await typeName(rendered.user, name);
  await rendered.user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));
  return rendered;
}

beforeEach(() => {
  resetRouterMock();
  server.events.removeAllListeners("request:start");
});

describe("nghi trùng · vế ĐƯỢC PHÉP nói, đến thẳng từ 409", () => {
  it("dừng lại bằng một hộp thoại, không phải một câu lỗi chung chung", async () => {
    await submitWith(DUP_ALL_THREE);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Nghi trùng với hồ sơ đã có")).toBeInTheDocument();
  });

  it("nói điểm và các tín hiệu — chúng chỉ mô tả ô người dùng vừa nhập", async () => {
    await submitWith(DUP_ALL_THREE);

    const dialog = await screen.findByRole("dialog");
    const top = within(dialog).getByTestId("duplicate-candidate-p-001");
    expect(within(top).getByText("Điểm nghi ngờ 88")).toBeInTheDocument();
    expect(within(top).getByText("Trùng họ tên đủ dấu")).toBeInTheDocument();
    expect(within(top).getByText("Cùng chi")).toBeInTheDocument();
    expect(within(top).getByText(/trùng năm sinh, cùng chi/)).toBeInTheDocument();
  });

  it("KHÔNG gửi confirmDuplicateOverride ở lần gửi đầu", async () => {
    const captured = captureRequests();
    await submitWith(DUP_ALL_THREE);

    await screen.findByRole("dialog");
    const posts = captured.filter((r) => r.method === "POST");
    expect(posts).toHaveLength(1);
    expect(
      (posts[0]!.body as Record<string, unknown>).confirmDuplicateOverride
    ).toBeUndefined();
  });

  it("gửi lại kèm cờ xác nhận khi người dùng khẳng định đây là người khác", async () => {
    const captured = captureRequests();
    const { user } = await submitWith(DUP_ALL_THREE);

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Đây là người khác, vẫn thêm" }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    const posts = captured.filter((r) => r.method === "POST");
    expect(posts).toHaveLength(2);
    expect((posts[1]!.body as Record<string, unknown>).confirmDuplicateOverride).toBe(true);
    // Payload giữ nguyên: người dùng xác nhận, họ không sửa gì cả.
    expect((posts[1]!.body as Record<string, unknown>).names).toEqual(
      (posts[0]!.body as Record<string, unknown>).names
    );
  });

  it("không gửi gì thêm khi người dùng quay lại kiểm tra", async () => {
    const captured = captureRequests();
    const { user } = await submitWith(DUP_ALL_THREE);

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Quay lại kiểm tra" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(captured.filter((r) => r.method === "POST")).toHaveLength(1);
    expect(routerMock.push).not.toHaveBeenCalled();
  });
});

describe("nghi trùng · hình dạng 1: ứng viên tra được (200)", () => {
  it("dựng danh tính từ GET /persons, không từ thân lỗi 409", async () => {
    await submitWith(DUP_ALL_THREE);

    const dialog = await screen.findByRole("dialog");
    const card = within(dialog).getByTestId("duplicate-candidate-p-001");
    const party = await within(card).findByTestId("duplicate-party-p-001-visible");

    // `p-001` là cụ Thuỷ Tổ đã khuất — người đã khuất công khai (BA v2 §10),
    // nên hộp thoại hiện đủ như trước khi hợp đồng cắt trường.
    expect(within(party).getByText("Nguyễn Văn Thủy Tổ")).toBeInTheDocument();
    expect(within(party).getByText("Đời thứ 1")).toBeInTheDocument();
  });

  it("thật sự đi hỏi hồ sơ theo khoá", async () => {
    const captured = captureRequests();
    await submitWith(DUP_ALL_THREE);

    await screen.findByTestId("duplicate-party-p-001-visible");
    expect(
      captured.some((r) => r.method === "GET" && r.url.includes("/api/v1/persons/p-001"))
    ).toBe(true);
  });
});

describe("nghi trùng · hình dạng 2: ứng viên KHÔNG được phép xem (404)", () => {
  it("không hiện một cái tên nào", async () => {
    await submitWith(DUP_HIDDEN_ONLY);

    const dialog = await screen.findByRole("dialog");
    const card = within(dialog).getByTestId("duplicate-candidate-p-781");
    await within(card).findByTestId("duplicate-party-p-781-not-visible");

    expect(within(card).queryByTestId("duplicate-party-p-781-visible")).not.toBeInTheDocument();
    expect(within(card).queryByText(/Nghi Trùng/)).not.toBeInTheDocument();
    expect(within(card).queryByText(/Đời thứ/)).not.toBeInTheDocument();
  });

  it("nói 'bạn không có quyền xem', không nói 'không tìm thấy', và chỉ ra chỗ để đi tiếp", async () => {
    await submitWith(DUP_HIDDEN_ONLY);

    const blocked = await screen.findByTestId("duplicate-party-p-781-not-visible");
    expect(within(blocked).getByText(/không có quyền xem hồ sơ bị nghi trùng/i)).toBeInTheDocument();
    expect(
      within(blocked).getByText(/không cho biết hồ sơ đó có tồn tại hay không/i)
    ).toBeInTheDocument();
    expect(within(blocked).getByText(/Hội đồng Tộc biểu/i)).toBeInTheDocument();

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).queryByText(/Không tra được hồ sơ/i)).not.toBeInTheDocument();
    expect(within(dialog).queryByText(/không tìm thấy/i)).not.toBeInTheDocument();
  });

  it("404 không phải lỗi: mục ứng viên không mang vai trò alert nào", async () => {
    await submitWith(DUP_HIDDEN_ONLY);

    const card = await screen.findByTestId("duplicate-candidate-p-781");
    await within(card).findByTestId("duplicate-party-p-781-not-visible");
    expect(within(card).queryByRole("alert")).not.toBeInTheDocument();
  });

  it("vẫn nói được VÌ SAO nghi — điểm và tín hiệu thuộc về ô người dùng vừa nhập", async () => {
    await submitWith(DUP_HIDDEN_ONLY);

    const card = await screen.findByTestId("duplicate-candidate-p-781");
    expect(within(card).getByText("Điểm nghi ngờ 80")).toBeInTheDocument();
    expect(within(card).getByText("Ngày giỗ trùng khít")).toBeInTheDocument();
  });

  it("không rò rỉ qua bố cục: ca 404 không in một bảng nhãn–giá trị rỗng", async () => {
    await submitWith(DUP_HIDDEN_ONLY);

    const card = await screen.findByTestId("duplicate-candidate-p-781");
    const blocked = await within(card).findByTestId("duplicate-party-p-781-not-visible");
    // Ba câu văn xuôi, không một ô trống nào để đếm ra "có bao nhiêu trường bị giấu".
    expect(blocked.querySelectorAll("dt, dd, td, th")).toHaveLength(0);
    expect(blocked.querySelectorAll("p")).toHaveLength(3);
  });
});

describe("nghi trùng · hình dạng 3: ứng viên chưa ghi trong lô (personId === null)", () => {
  it("hiện mã tham chiếu dòng — dữ liệu của chính người nhập", async () => {
    await submitWith(DUP_IN_BATCH_ONLY);

    const dialog = await screen.findByRole("dialog");
    const card = within(dialog).getByTestId("duplicate-candidate-Dòng 7 · chi-nhi-2026.xlsx");
    expect(within(card).getByText(/Một dòng chưa ghi trong lô nhập liệu/)).toBeInTheDocument();
    expect(within(card).getByText(/Dòng 7 · chi-nhi-2026\.xlsx/)).toBeInTheDocument();
  });

  it("KHÔNG gọi GET /persons — không có khoá nào để tra", async () => {
    const captured = captureRequests();
    await submitWith(DUP_IN_BATCH_ONLY);

    await screen.findByText(/Một dòng chưa ghi trong lô nhập liệu/);
    expect(
      captured.filter((r) => r.method === "GET" && /\/api\/v1\/persons\/[^s]/.test(r.url))
    ).toHaveLength(0);
  });

  it("không mượn câu 'bạn không có quyền xem' — đây là ca khác hẳn", async () => {
    await submitWith(DUP_IN_BATCH_ONLY);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByText(/không có quyền xem/i)).not.toBeInTheDocument();
  });
});

describe("nghi trùng · ba hình dạng cùng một hộp thoại", () => {
  it("hiện đủ ba, xếp theo điểm giảm dần", async () => {
    await submitWith(DUP_ALL_THREE);

    const dialog = await screen.findByRole("dialog");
    const cards = within(dialog).getAllByTestId(/^duplicate-candidate-/);
    expect(cards).toHaveLength(3);
    expect(cards.map((c) => c.getAttribute("data-testid"))).toEqual([
      "duplicate-candidate-p-001",
      "duplicate-candidate-p-781",
      "duplicate-candidate-Dòng 42 · chi-nhat-2026.xlsx",
    ]);

    await within(cards[0]!).findByTestId("duplicate-party-p-001-visible");
    await within(cards[1]!).findByTestId("duplicate-party-p-781-not-visible");
    expect(within(cards[2]!).getByText(/Một dòng chưa ghi trong lô nhập liệu/)).toBeInTheDocument();
  });
});

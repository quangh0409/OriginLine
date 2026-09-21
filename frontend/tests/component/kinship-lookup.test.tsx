import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/kinship",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/kinship",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/kinship",
}));

const { KinshipLookup } = await import("@/components/kinship/kinship-lookup");

/**
 * F5 — tra cứu danh xưng. The pair lives in `?from=&to=` so an answer can be
 * shared in the clan group chat, which is also what makes it testable without
 * driving two comboboxes for every case.
 *
 * Everything asserted here is server-derived (FR-1.3a: danh xưng is
 * configurable DATA per clan/region, never client code). The component's job
 * is to render four things: the title, the reciprocal (a Vietnamese speaker
 * needs both halves), the reverse direction, and the LCA ladder that lets an
 * elder verify the answer rather than take it on faith.
 *
 * Fixture pair: p-010 (đời 2, chi head) -> p-001 (thủy tổ) = "Cha"/"Con",
 * pinned in tests/unit/kinship/kinship-engine-fixtures.test.ts.
 */

function renderLookup(query: string, role: "guest" | "member" | "admin" = "member") {
  searchParams = new URLSearchParams(query);
  return renderWithProviders(<KinshipLookup />, { role });
}

beforeEach(() => {
  resetRouterMock();
  searchParams = new URLSearchParams();
});

describe("before two people are chosen", () => {
  it("prompts for a pair instead of showing an error", async () => {
    renderLookup("");
    expect(await screen.findByText("Chọn đủ hai người để xem danh xưng.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("offers a picker for each side of the address pair", async () => {
    renderLookup("");
    // `await find*`, not `getBy*`: the screen now gates on `useMe()` first
    // (see `<KinshipGuestNotice>`), so the pickers appear one tick later than
    // the initial render, not synchronously with it.
    expect(await screen.findByText("Người xưng hô (tôi là…)")).toBeInTheDocument();
    expect(screen.getByText("Người được gọi")).toBeInTheDocument();
  });
});

describe("a resolved danh xưng", () => {
  it("shows the title the caller uses, prominently", async () => {
    renderLookup("from=p-010&to=p-001");
    expect(await screen.findByText("Cha")).toBeInTheDocument();
  });

  it("shows how the caller must refer to THEMSELVES, which is half the answer", async () => {
    // Knowing to say "bác" is useless without knowing to call yourself "cháu".
    renderLookup("from=p-010&to=p-001");
    await screen.findByText("Cha");
    expect(screen.getByText("và xưng là")).toBeInTheDocument();
    expect(screen.getAllByText("Con").length).toBeGreaterThan(0);
  });

  it("spells out the reverse direction as its own sentence", async () => {
    renderLookup("from=p-010&to=p-001");
    await screen.findByText("Cha");
    const reverse = screen.getByRole("heading", { name: "Chiều ngược lại" }).closest("section")!;
    expect(within(reverse).getByText(/gọi/)).toBeInTheDocument();
    expect(within(reverse).getByText(/Con\.?$/)).toBeInTheDocument();
  });

  it("draws the LCA path as verifiable evidence, not hidden behind a details toggle", async () => {
    renderLookup("from=p-010&to=p-001");
    await screen.findByText("Cha");

    const path = screen.getByRole("heading", { name: "Đường quan hệ" }).closest("section")!;
    expect(within(path).getByText("Tổ chung")).toBeInTheDocument();
    // The ladder must contain one rung per step: the caller, then the ancestor.
    expect(within(path).getAllByRole("listitem").length).toBeGreaterThanOrEqual(2);
    expect(within(path).getByText("Điểm xuất phát")).toBeInTheDocument();
    expect(within(path).getByText("Lên một đời")).toBeInTheDocument();
  });

  it("shows the normalised facts the rule engine matched on, degree included", async () => {
    // collateralDegree is the ONLY thing separating bác ruột from bác họ, so
    // it has to be on screen for an elder to check the answer.
    renderLookup("from=p-010&to=p-001");
    await screen.findByText("Cha");

    expect(screen.getByText("Chênh lệch đời")).toBeInTheDocument();
    expect(screen.getByText("Trên 1 đời")).toBeInTheDocument();
    expect(screen.getByText("Bên nội")).toBeInTheDocument();
    expect(screen.getByText("Bậc bàng hệ")).toBeInTheDocument();
    expect(
      screen.getByText("Trực hệ (đời trên – đời dưới thẳng dòng)")
    ).toBeInTheDocument();
  });

  it("names which rule set answered, so the council knows what to edit", async () => {
    renderLookup("from=p-010&to=p-001");
    await screen.findByText("Cha");
    expect(screen.getByText("Bộ luật áp dụng")).toBeInTheDocument();
    expect(screen.getByText(/Mặc định của hệ thống/)).toBeInTheDocument();
  });

  it("resolves a collateral pair through the shared ancestor, going up then down", async () => {
    renderLookup("from=p-010&to=p-011");
    const path = await screen.findByRole("heading", { name: "Đường quan hệ" });
    const section = path.closest("section")!;

    expect(within(section).getByText("Tổ chung")).toBeInTheDocument();
    expect(within(section).getByText("Lên một đời")).toBeInTheDocument();
    expect(within(section).getByText("Xuống một đời")).toBeInTheDocument();
    expect(screen.getByText("Ruột")).toBeInTheDocument();
  });
});

describe("non-RESOLVED statuses are answers, not errors", () => {
  it("says the two pickers hold the same person, without a red error banner", async () => {
    renderLookup("from=p-001&to=p-001");
    expect(
      await screen.findByText("Hai ô đang chọn cùng một người")
    ).toBeInTheDocument();
    // contracts/README §7.7: never a generic error banner.
    const alert = screen.getByRole("alert");
    expect(alert.className).not.toMatch(/ant-alert-error/);
    expect(screen.queryByText("Không tra được danh xưng. Vui lòng thử lại.")).not.toBeInTheDocument();
  });
});

describe("privacy on the kinship screen", () => {
  /**
   * Trước bản sửa này, một khách gõ thẳng
   * `/kinship?from=p-001&to=p-100` vào thanh địa chỉ sẽ THẤY được các ô chọn
   * và nhận về "Không tìm thấy một trong hai người" — một câu đúng riêng tư
   * (không xác nhận ai đang sống) nhưng SAI so với bản thật: `GET
   * /api/v1/kinship` không có lối `permitAll` nào, nó rơi vào
   * `anyRequest().authenticated()` ở `SecurityConfig`, nên một khách nhận
   * `401` chứ không phải `404` — bất kể cả hai người có đã khuất hay không.
   * Bộ giả lập cũ mô phỏng một API "mềm" hơn bản thật, đúng cái bẫy mà
   * `tests/unit/privacy/field-visibility-api.test.ts` đã đặt tên.
   *
   * Ca ấy không còn xảy ra được nữa: `<KinshipGuestNotice>` chặn khách lại
   * TRƯỚC khi bất kỳ id nào trong URL được dùng tới — xem javadoc
   * `kinship-lookup.tsx`. Ca "một người còn sống bị giấu trả về 404, không
   * phải 403" vẫn đúng và vẫn được enforce ở máy chủ; nó không còn kiểm được
   * qua vai "khách" ở màn này nữa vì khách không bao giờ chạm tới bước gọi
   * API — thuộc phạm vi kiểm của backend, không phải màn hình này.
   */
  it("khách mở thẳng một liên kết đã điền sẵn hai người vẫn thấy lời mời đăng nhập, không thấy ô chọn hay danh xưng", async () => {
    renderLookup("from=p-001&to=p-100", "guest");

    expect(await screen.findByText("Cần đăng nhập để tra danh xưng")).toBeInTheDocument();
    expect(screen.queryByText("Người xưng hô (tôi là…)")).not.toBeInTheDocument();
    expect(screen.queryByText("Không tìm thấy một trong hai người.")).not.toBeInTheDocument();
    // Không tên nào của p-001/p-100 rò ra màn hình — dù tên có ở trong URL,
    // khách không bao giờ nhận được một phản hồi RIÊNG cho cặp id ấy: khối
    // này là một câu chung cho MỌI khách, không phải câu trả lời cho câu hỏi
    // "hai người này có quan hệ gì". (Không kiểm `/quyền/i` như bản test cũ —
    // câu hợp lệ ở đây có nhắc "quyền đọc phả đồ" theo nghĩa quyền truy cập
    // TÍNH NĂNG nói chung, không phải một lời từ chối về MỘT người cụ thể.)
    expect(document.body.textContent).not.toContain("Nguyễn Văn An");
  });

  it("never renders a MISSING_MESSAGE placeholder for any status", async () => {
    renderLookup("from=p-010&to=p-001");
    await screen.findByText("Cha");
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  });
});

describe("choosing two people through the pickers", () => {
  it("resolves the danh xưng after both are picked by search", async () => {
    const { user } = renderLookup("");

    // Wait for the `useMe()` gate to resolve before the comboboxes exist.
    await screen.findByText("Người xưng hô (tôi là…)");
    const [fromBox, toBox] = screen.getAllByRole("combobox");
    await user.click(fromBox!);
    await user.type(fromBox!, "Nguyen Van Hien"); // unaccented on purpose: FTS is server-side

    const fromOption = await screen.findByTitle("Nguyễn Văn Hiển", {}, { timeout: 5000 });
    await user.click(fromOption);

    await user.click(toBox!);
    await user.type(toBox!, "Thuy To");
    const toOption = await screen.findByTitle("Nguyễn Văn Thủy Tổ", {}, { timeout: 5000 });
    await user.click(toOption);

    await waitFor(() => expect(screen.getByText("Cha")).toBeInTheDocument(), { timeout: 8000 });
  });

  it("keeps the chosen pair in the URL so the answer can be shared", async () => {
    renderLookup("from=p-010&to=p-001");
    await screen.findByText("Cha");
    await waitFor(() => expect(routerMock.replace).toHaveBeenCalled());
    const lastCall = routerMock.replace.mock.calls.at(-1);
    expect(String(lastCall?.[0])).toContain("from=p-010");
    expect(String(lastCall?.[0])).toContain("to=p-001");
  });
});

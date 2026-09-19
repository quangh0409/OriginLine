import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
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
 * The thủy tổ's recorded name (src/mocks/tree-graph, id p-001, đời 1). Naming
 * a newborn this is the gravest kỵ húy collision the fixture data can express.
 */
const TABOO_NAME = "Nguyễn Văn Thủy Tổ";
/** Nobody in the ~4,000-node mock graph is called this. */
const FREE_NAME = "Nguyễn Văn Khôngtrùngvớiai";
/**
 * Va chạm kỵ húy với một bậc trên mà người gọi KHÔNG được phép xem
 * (`src/mocks/handlers/persons.ts` — khoá `p-781` cố ý không tra được, nên
 * `GET /persons/{id}` trả 404 y như máy chủ thật trả cho một người còn sống ở
 * chi khác).
 */
const TABOO_NAME_HIDDEN_ANCESTOR = "Nguyễn Văn Bậc Trên Ẩn";

/**
 * F4 — form thêm/sửa nhân khẩu, exercised end to end against MSW: validation,
 * then the kỵ húy (FR-1.6) two-call flow.
 *
 * The kỵ húy flow is the part worth this much machinery. Naming a descendant
 * after an ancestor's tên húy is a serious breach, so the contract is a
 * deliberate stop: POST WITHOUT `confirmTabooOverride` -> 409 with the
 * colliding ancestors -> the user reads who they collide with -> only then a
 * second, identical POST carrying `confirmTabooOverride: true` and an audited
 * reason. Defaulting the flag on the first call would silently delete the
 * whole feature, so the outgoing request bodies are asserted, not just the
 * screen.
 */

interface CapturedRequest {
  method: string;
  url: string;
  body: unknown;
}

function captureRequests(): CapturedRequest[] {
  const captured: CapturedRequest[] = [];
  server.events.on("request:start", async ({ request }) => {
    if (request.method === "GET") return;
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
  // Regex, not an exact string: <FormField required> appends an asterisk span
  // to the <label>, so the accessible name is "Họ và tên*".
  const input = screen.getByLabelText(/Họ và tên/);
  await user.clear(input);
  await user.type(input, value);
}

beforeEach(() => {
  resetRouterMock();
  server.events.removeAllListeners("request:start");
});

describe("validation before anything reaches the server", () => {
  it("refuses to submit a person with no name and says why", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    expect(await screen.findByText("Xin nhập họ và tên.")).toBeInTheDocument();
    expect(captured.filter((r) => r.method === "POST")).toHaveLength(0);
  });

  /**
   * Luật cũ "người còn sống thì không có ngày mất" đã bị Hội đồng đảo: nhập ngày mất tức là suy
   * ra người đó đã mất. Ca này giữ lại chiều KHÔNG được phép làm phiền — thêm mới một cụ đã
   * khuất là việc thường ngày của gia phả, không có hồ sơ người sống nào bị phát tán, nên không
   * hộp thoại nào được chen vào. Chiều phải hỏi nằm ở person-form-death.test.tsx.
   */
  it("adds a deceased ancestor with a death date, with no confirmation in the way", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, FREE_NAME);
    const deathSolar = within(screen.getByRole("group", { name: /Ngày mất/ })).getByLabelText(
      "Ngày dương lịch"
    );
    fireEvent.change(deathSolar, { target: { value: "1952-01-01" } });

    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    await waitFor(() => expect(captured.filter((r) => r.method === "POST").length).toBe(1));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const body = captured.find((r) => r.method === "POST")!.body as Record<string, unknown>;
    // Ngày mất suy ra trạng thái đã mất, chứ không bị mapper vứt đi.
    expect(body.isAlive).toBe(false);
    expect(body.death).toMatchObject({ solar: "1952-01-01" });
  });

  it("rejects a malformed email without ever contacting the server", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, FREE_NAME);
    await user.type(screen.getByLabelText("Thư điện tử"), "khong-phai-email");
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    expect(await screen.findByText("Thư điện tử không hợp lệ.")).toBeInTheDocument();
    expect(captured.filter((r) => r.method === "POST")).toHaveLength(0);
  });

  it("rejects a lunar day entered without its month, because a giỗ needs both", async () => {
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, FREE_NAME);
    const birth = screen.getByRole("group", { name: /Ngày sinh/ });
    await user.type(within(birth).getByLabelText("Ngày"), "22");
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    expect(
      await screen.findByText("Đã nhập ngày thì phải nhập cả tháng âm lịch.")
    ).toBeInTheDocument();
  });
});

describe("kỵ húy warning (FR-1.6)", () => {
  it("does NOT send confirmTabooOverride on the first attempt", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME); // the thủy tổ's recorded name, p-001
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    await waitFor(() => expect(captured.filter((r) => r.method === "POST").length).toBe(1));
    const first = captured.find((r) => r.method === "POST")!;
    expect((first.body as Record<string, unknown>).confirmTabooOverride).toBeUndefined();
  });

  it("stops with a dialog naming the ancestor, their đời and how the names collide", async () => {
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Cảnh báo trùng tên húy (kỵ húy)")).toBeInTheDocument();
    // WHO
    expect(within(dialog).getAllByText(TABOO_NAME).length).toBeGreaterThan(0);
    // WHERE — which generation the colliding ancestor sits in
    expect(within(dialog).getByText(/Đời thứ/)).toBeInTheDocument();
    // HOW — an exact collision is graver than an unaccented coincidence
    expect(within(dialog).getByText("Trùng khít")).toBeInTheDocument();
  });

  it("will not let the user push past the warning without an audited reason", async () => {
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    const confirm = within(dialog).getByRole("button", { name: "Vẫn giữ tên này" });
    expect(confirm).toBeDisabled();

    await user.type(within(dialog).getByRole("textbox"), "abcd"); // 4 chars, below the minimum
    expect(confirm).toBeDisabled();
  });

  it("resends the identical payload plus the override flag and the reason once confirmed", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    const reason = "Hội đồng Tộc biểu đã chấp thuận trong phiên họp ngày 12/3";
    await user.type(within(dialog).getByRole("textbox"), reason);
    await user.click(within(dialog).getByRole("button", { name: "Vẫn giữ tên này" }));

    await waitFor(() => expect(captured.filter((r) => r.method === "POST").length).toBe(2));
    const [first, second] = captured.filter((r) => r.method === "POST");
    const firstBody = first!.body as Record<string, unknown>;
    const secondBody = second!.body as Record<string, unknown>;

    expect(secondBody.confirmTabooOverride).toBe(true);
    expect(secondBody.note).toBe(reason);
    // Identical payload otherwise — the user accepted the name, they did not
    // change it.
    expect(secondBody.names).toEqual(firstBody.names);
    expect(secondBody.gender).toEqual(firstBody.gender);
    expect(secondBody.isAlive).toEqual(firstBody.isAlive);
  });

  it("navigates to the saved profile after an accepted override", async () => {
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    await user.type(
      within(dialog).getByRole("textbox"),
      "Đã được Hội đồng Tộc biểu chấp thuận"
    );
    await user.click(within(dialog).getByRole("button", { name: "Vẫn giữ tên này" }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(String(routerMock.push.mock.calls[0]?.[0])).toMatch(/^\/persons\/p-new-/);
  });

  it("sends nothing further when the user backs out to rename", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Quay lại đổi tên" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(captured.filter((r) => r.method === "POST")).toHaveLength(1);
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("saves straight through when the new name collides with nobody", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, FREE_NAME);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const posts = captured.filter((r) => r.method === "POST");
    expect(posts).toHaveLength(1);
    expect((posts[0]!.body as Record<string, unknown>).confirmTabooOverride).toBeUndefined();
  });
});

/**
 * Ca bậc trên KHÔNG tra được — hệ quả trực tiếp của việc hợp đồng bỏ bốn trường
 * `ancestorDisplayName` / `ancestorGeneration` / `tabooName` / `relationHint`.
 *
 * <p>Truy vấn dò kỵ húy chọn bậc trên theo <b>đời thứ</b>, không theo sống/mất
 * và không theo chi, nên "bậc trên" có thể là một ông bác còn sống ở một chi
 * khác mà người đang thêm nhân khẩu không có quyền biết gì về họ — kể cả việc
 * họ tồn tại. Thân lỗi 409 đi thẳng ra HTTP, không qua bộ lọc phân tầng riêng
 * tư, nên danh tính phải nạp riêng qua `GET /persons/{id}`.</p>
 *
 * <p>Đây <b>không</b> phải ca thường gặp: bậc trên gần như luôn là người đã
 * khuất, mà người đã khuất công khai (xem ca "hiện y như trước" ngay trên).
 * Nhưng khi nó xảy ra thì hộp thoại phải im lặng đúng chỗ và ồn ào đúng chỗ.</p>
 */
describe("kỵ húy · bậc trên mà người gọi không được phép xem", () => {
  it("không hiện một cái tên nào — kể cả tên người dùng vừa gõ, dưới dạng 'tên húy bên kia'", async () => {
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME_HIDDEN_ANCESTOR);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    const item = await within(dialog).findByTestId("taboo-conflict-p-781");
    await within(item).findByTestId("taboo-ancestor-p-781-not-visible");

    // Không có khối danh tính nào được dựng.
    expect(within(item).queryByTestId("taboo-ancestor-p-781-visible")).not.toBeInTheDocument();
    // Và không một mẩu tên nào lọt ra: 409 không chở tên, GET không trả tên.
    expect(within(item).queryByText(/Bậc Trên Ẩn/)).not.toBeInTheDocument();
    expect(within(item).queryByText(/Tên húy:/)).not.toBeInTheDocument();
    expect(within(item).queryByText(/Đời thứ/)).not.toBeInTheDocument();
  });

  it("nói 'bạn không có quyền xem', không nói 'không tìm thấy', và chỉ ra chỗ để đi tiếp", async () => {
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME_HIDDEN_ANCESTOR);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    const blocked = await within(dialog).findByTestId("taboo-ancestor-p-781-not-visible");

    expect(within(blocked).getByText(/không có quyền xem bậc trên này/i)).toBeInTheDocument();
    // Không rò rỉ việc bản ghi có tồn tại hay không.
    expect(
      within(blocked).getByText(/không cho biết hồ sơ đó có tồn tại hay không/i)
    ).toBeInTheDocument();
    // Một chỗ để đi: người nhìn được cả hai bên.
    expect(within(blocked).getByText(/Hội đồng Tộc biểu/i)).toBeInTheDocument();
    // Tuyệt đối không phải câu lỗi kỹ thuật.
    expect(within(dialog).queryByText(/Không tra được hồ sơ bậc trên/i)).not.toBeInTheDocument();
    expect(within(dialog).queryByText(/không tìm thấy/i)).not.toBeInTheDocument();
  });

  it("404 không phải lỗi: mục va chạm không mang vai trò alert nào", async () => {
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME_HIDDEN_ANCESTOR);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    const item = await within(dialog).findByTestId("taboo-conflict-p-781");
    await within(item).findByTestId("taboo-ancestor-p-781-not-visible");

    // `role="alert"` DUY NHẤT trong hộp thoại là lời dẫn cảnh báo kỵ húy — thứ
    // người dùng thật sự phải hành động. Ca 404 thì không: nó chỉ là câu trả
    // lời đúng của hệ thống, nên nó không được cướp lượt đọc của trình đọc màn
    // hình.
    expect(within(item).queryByRole("alert")).not.toBeInTheDocument();
  });

  it("vẫn nói được MỨC KHỚP và vẫn cho ghi đè — mức khớp nói về ô người dùng vừa gõ", async () => {
    const captured = captureRequests();
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "admin" });

    await typeName(user, TABOO_NAME_HIDDEN_ANCESTOR);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Trùng khít")).toBeInTheDocument();

    await user.type(within(dialog).getByRole("textbox"), "Hội đồng Tộc biểu đã đối chiếu hộ");
    await user.click(within(dialog).getByRole("button", { name: "Vẫn giữ tên này" }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
    const posts = captured.filter((r) => r.method === "POST");
    expect((posts[1]!.body as Record<string, unknown>).confirmTabooOverride).toBe(true);
  });
});

describe("RBAC affordance on submit", () => {
  it("reports a 403 as a permission problem rather than a validation failure", async () => {
    // Phase 1: a MEMBER cannot add a nhân khẩu at all (the change-request
    // flow is Phase 2), so the server answers 403 and the form must say so.
    const { user } = renderWithProviders(<PersonForm mode="create" />, { role: "member" });

    await typeName(user, FREE_NAME);
    await user.click(screen.getByRole("button", { name: "Thêm vào gia phả" }));

    expect(await screen.findByText("Bạn không có quyền sửa hồ sơ này.")).toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();
  });
});

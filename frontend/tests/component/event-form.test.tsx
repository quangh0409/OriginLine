import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import type { EventCreateRequest } from "@/types/api";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/events/new",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/events/new",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/events/new",
}));

const { EventForm } = await import("@/components/events/event-form");
const { EventCreateScreen } = await import("@/components/events/event-create-screen");

/**
 * F7 Đợt 2 — form tạo việc họ.
 *
 * <h2>Việc quan trọng nhất bài này kiểm</h2>
 * Form CHỈ có ô nhập ngày ÂM. Không có ô ngày dương nào để tự quy đổi trong
 * trường hợp thường (lặp lại hằng năm) — đúng yêu cầu "đừng bắt người dùng
 * nhập dương rồi tự quy đổi ở client". `lunarDate` gửi lên máy chủ phải khớp
 * Y NGUYÊN những gì đã gõ, không bị làm tròn/suy diễn thêm bất cứ gì.
 */

interface CapturedRequest {
  method: string;
  body: unknown;
}

/**
 * Chỉ bắt lời gọi tới `/api/v1/events` — bỏ qua GraphQL (`useBranches` gửi
 * `POST` tới `/graphql` khi form nạp danh sách chi, và đó KHÔNG phải lượt
 * gửi biểu mẫu đang được kiểm ở đây).
 */
function captureWrites(): CapturedRequest[] {
  const captured: CapturedRequest[] = [];
  server.events.on("request:start", async ({ request }) => {
    if (request.method === "GET") return;
    if (!request.url.includes("/api/v1/events")) return;
    let body: unknown;
    try {
      body = await request.clone().json();
    } catch {
      body = undefined;
    }
    captured.push({ method: request.method, body });
  });
  return captured;
}

/**
 * Chỉ những `.ant-select-item-option` nằm trong dropdown ĐANG MỞ.
 *
 * Ant Design giữ nguyên popup đã đóng trong DOM (chỉ gắn thêm lớp
 * `ant-select-dropdown-hidden`) để mở lại cho mượt, thay vì gỡ hẳn. Một biểu
 * mẫu chạm tới HAI ô `<Select>` trở lên trong cùng một bài kiểm (loại việc họ
 * rồi tới chi/ngành) vì thế để lại NHIỀU popup cùng lúc trong `document`; dò
 * `.ant-select-item-option` không giới hạn phạm vi sẽ bắt nhầm một dòng của
 * popup ĐÃ ĐÓNG trước đó — bấm "thành công" nhưng không đổi gì trên popup
 * thật đang mở, và trường tương ứng lặng lẽ giữ nguyên giá trị cũ.
 */
function openDropdownOptions(): HTMLElement[] {
  return Array.from(
    document.querySelectorAll(".ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option")
  ) as HTMLElement[];
}

/**
 * Mở & chọn mục đầu của một `<Select>` bằng `fireEvent` thay vì `userEvent`.
 *
 * Ant Design mở dropdown ở sự kiện `mousedown` trên `.ant-select-selector`
 * (không phải `click` trên ô nhập ẩn `role="combobox"`), và dropdown lại dựng
 * bằng danh sách ảo (`rc-virtual-list`) — chuỗi kiểm tra "phần tử có thật sự
 * bấm được" của `userEvent` (di chuột, `pointer-events`, kích thước) không ăn
 * khớp với cặp đôi này trong JSDOM và có thể treo. `fireEvent` gửi thẳng sự
 * kiện DOM cần thiết, không suy luận gì thêm.
 */
async function pickFirstBranch(labelText: string) {
  const input = screen.getByLabelText(labelText);
  const selector = input.closest(".ant-select")?.querySelector(".ant-select-selector");
  if (!selector) throw new Error(`không thấy .ant-select-selector cho "${labelText}"`);
  fireEvent.mouseDown(selector);

  const option = await waitFor(
    () => {
      const nodes = openDropdownOptions();
      if (nodes.length === 0) throw new Error("chưa có chi nào trong danh sách");
      return nodes[0]!;
    },
    { timeout: 15_000 }
  );
  fireEvent.click(option);
}

/**
 * Chọn LOẠI VIỆC HỌ đầu tiên trong danh sách (`GIO_TO` — "Giỗ Tổ") — cùng cơ
 * chế với `pickFirstBranch`, cố ý KHÔNG nhắm một lựa chọn theo tên ở vị trí
 * khác. `rc-virtual-list` (danh sách ảo antd dùng cho dropdown) có thể dựng
 * lại/định vị lại các dòng ngay sau khi mở — bắt đúng dòng đầu tiên là ổn
 * định; tìm một dòng THEO VĂN BẢN rồi bấm sau một `waitFor` từng đo được là
 * bấm trúng dòng khác, vì tham chiếu DOM đã bị danh sách ảo tái dùng cho một
 * dòng khác vào đúng lúc đó. Đủ dùng cho các bài kiểm chỉ cần "một loại việc
 * họ KHÔNG bắt buộc chọn người", không cần đúng loại cụ thể nào.
 */
async function chooseFirstEventType(labelText: string) {
  const input = screen.getByLabelText(labelText);
  const selector = input.closest(".ant-select")?.querySelector(".ant-select-selector");
  if (!selector) throw new Error(`không thấy .ant-select-selector cho "${labelText}"`);
  fireEvent.mouseDown(selector);

  const option = await waitFor(
    () => {
      const nodes = openDropdownOptions();
      if (nodes.length === 0) throw new Error("chưa có loại việc họ nào trong danh sách");
      return nodes[0]!;
    },
    { timeout: 15_000 }
  );
  fireEvent.click(option);
}

/**
 * `<PersonPicker>` bọc cả nhãn LẪN toàn bộ ô `<Select>` trong một `<label>`
 * duy nhất — tên khả truy cập của nó vì thế gồm cả chữ đặt-chỗ bên trong
 * ("Tìm theo tên…"), nên `getByLabelText("Người của giỗ này")` trượt dù mắt
 * người đọc thấy đúng (cùng bẫy đã gặp ở `search-filters`). Tìm bằng
 * `getByText` trên đúng nhãn rồi đi xuống ô nhập thật.
 */
function personPickerCombobox(labelText: string): HTMLElement {
  const label = screen.getByText(labelText).closest("label");
  if (!label) throw new Error(`không thấy <label> cho "${labelText}"`);
  const input = label.querySelector('input[role="combobox"]');
  if (!input) throw new Error(`không thấy ô nhập cho "${labelText}"`);
  return input as HTMLElement;
}

/**
 * Chọn người qua `<PersonPicker>` — cùng cách `membership-invite.test.tsx` đã
 * dùng: gõ vài chữ để bộ tìm (`/persons/search`) trả kết quả, rồi bấm mục đầu.
 * Không đoán trước một cái tên cụ thể vì dữ liệu giả sinh theo hạt giống.
 */
async function pickAnyPerson(
  user: ReturnType<typeof renderWithProviders>["user"],
  labelText: string
) {
  const box = personPickerCombobox(labelText);
  await user.click(box);
  await user.type(box, "Nguyen");

  const option = await waitFor(
    () => {
      const nodes = openDropdownOptions();
      if (nodes.length === 0) throw new Error("chưa có người nào khớp");
      return nodes[0]!;
    },
    { timeout: 15_000 }
  );
  await user.click(option);
}

/**
 * Chọn một loại việc họ theo ĐÚNG TÊN hiển thị — khác `chooseFirstEventType`
 * (vốn cố ý bấm dòng đầu vì không quan tâm loại nào). Các bài kiểm bên dưới
 * quan tâm chính xác `GIO_HO`/`GIO_CHI`, nên phải tìm đúng dòng bằng văn bản.
 */
async function chooseEventTypeByLabel(labelText: string, optionText: string) {
  const input = screen.getByLabelText(labelText);
  const selector = input.closest(".ant-select")?.querySelector(".ant-select-selector");
  if (!selector) throw new Error(`không thấy .ant-select-selector cho "${labelText}"`);
  fireEvent.mouseDown(selector);

  const option = await waitFor(
    () => {
      const nodes = openDropdownOptions();
      const match = nodes.find((n) => n.textContent === optionText);
      if (!match) throw new Error(`chưa thấy lựa chọn "${optionText}"`);
      return match;
    },
    { timeout: 15_000 }
  );
  fireEvent.click(option);
}

beforeEach(() => {
  resetRouterMock();
});

describe("EventForm — Giỗ họ/Giỗ chi tự ấn định phạm vi (khớp EventTypeApiMapper ở backend)", () => {
  it("Trưởng chi không thấy 'Giỗ họ' trong danh sách: họ không bao giờ thoả được clanWide=true mà loại đó đòi", async () => {
    renderWithProviders(<EventForm mode="create" />, { role: "branch-head" });

    const input = screen.getByLabelText("Loại việc họ");
    const selector = input.closest(".ant-select")?.querySelector(".ant-select-selector")!;
    fireEvent.mouseDown(selector);

    const texts = await waitFor(() => {
      const nodes = openDropdownOptions();
      if (nodes.length === 0) throw new Error("danh sách loại việc họ chưa mở");
      return nodes.map((n) => n.textContent);
    });
    expect(texts).not.toContain("Giỗ họ");
    expect(texts).toContain("Giỗ chi");

    // Và câu giải thích vì sao phải nói ra, không chỉ lặng lẽ bớt một dòng.
    expect(
      screen.getByText(/Danh sách này không có "Giỗ họ"/)
    ).toBeInTheDocument();
  });

  it("chọn 'Giỗ chi' thì phạm vi tự khoá vào 'Một chi/ngành', không bấm 'Cả dòng họ' được nữa", async () => {
    renderWithProviders(<EventForm mode="create" />, { role: "admin" });

    await chooseEventTypeByLabel("Loại việc họ", "Giỗ chi");

    const branchRadio = screen.getByRole("radio", { name: "Một chi/ngành" });
    const clanRadio = screen.getByRole("radio", { name: "Cả dòng họ" });
    await waitFor(() => expect(branchRadio).toBeChecked());
    expect(clanRadio).toBeDisabled();
    expect(
      screen.getByText(/Loại việc họ “Giỗ chi” luôn thuộc phạm vi này/)
    ).toBeInTheDocument();
  });

  it("Hội đồng/Admin chọn 'Giỗ họ' thì phạm vi tự khoá vào 'Cả dòng họ', và gửi clanWide=true", async () => {
    const captured = captureWrites();
    const { user } = renderWithProviders(<EventForm mode="create" />, { role: "admin" });

    await chooseEventTypeByLabel("Loại việc họ", "Giỗ họ");

    const branchRadio = screen.getByRole("radio", { name: "Một chi/ngành" });
    const clanRadio = screen.getByRole("radio", { name: "Cả dòng họ" });
    await waitFor(() => expect(clanRadio).toBeChecked());
    expect(branchRadio).toBeDisabled();
    // Không còn ô chọn chi — phạm vi đã là cả họ, không phải "một chi".
    expect(screen.queryByLabelText("Chi/ngành nhận nhắc")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Tiêu đề"), "Giỗ Tổ cả họ");
    await user.clear(screen.getByLabelText("Ngày (âm)"));
    await user.type(screen.getByLabelText("Ngày (âm)"), "10");
    await user.clear(screen.getByLabelText("Tháng (âm)"));
    await user.type(screen.getByLabelText("Tháng (âm)"), "3");
    await user.click(screen.getByRole("button", { name: "Tạo" }));

    await waitFor(() => {
      expect(captured.some((r) => r.method === "POST")).toBe(true);
    });
    const body = captured.find((r) => r.method === "POST")!.body as EventCreateRequest;
    // Chính phép kiểm khoá lại lỗi thật: trước bản sửa, `scope` mặc định vẫn
    // ở "BRANCH" trong khi `eventType` là `GIO_HO` — máy chủ ném
    // `IllegalArgumentException` (422 VALIDATION_FAILED) vì `clanWideFlag`
    // gửi lên (`false`) khác với giá trị `GIO_HO` đã ấn định (`true`).
    expect(body.eventType).toBe("GIO_HO");
    expect(body.clanWide).toBe(true);
  });
});

describe("EventForm — không có ô ngày dương để tự quy đổi (mặc định lặp lại hằng năm)", () => {
  it("gửi lên máy chủ ĐÚNG các trường đã chốt, ngày âm y nguyên như đã gõ", async () => {
    const captured = captureWrites();
    const { user } = renderWithProviders(<EventForm mode="create" />, { role: "branch-head" });

    expect(screen.queryByLabelText("Ngày dương (tham chiếu)")).not.toBeInTheDocument();

    // Đổi khỏi mặc định GIO_THUONG (đòi chọn người, xem describe riêng bên
    // dưới) — bài này kiểm ngày âm, không kiểm ràng buộc gắn người.
    await chooseFirstEventType("Loại việc họ");

    await user.type(screen.getByLabelText("Tiêu đề"), "Giỗ thử Đợt 2");
    await user.clear(screen.getByLabelText("Ngày (âm)"));
    await user.type(screen.getByLabelText("Ngày (âm)"), "15");
    await user.clear(screen.getByLabelText("Tháng (âm)"));
    await user.type(screen.getByLabelText("Tháng (âm)"), "8");

    await pickFirstBranch("Chi/ngành nhận nhắc");
    await user.click(screen.getByRole("button", { name: "Tạo" }));

    await waitFor(() => {
      expect(captured.some((r) => r.method === "POST")).toBe(true);
    });

    const body = captured.find((r) => r.method === "POST")!.body as EventCreateRequest;

    // Không trường nào ngoài danh sách PO đã chốt (cập nhật sau khi backend
    // xây xong — thêm `personId`/`location`, không đổi bảy trường gốc).
    expect(Object.keys(body).sort()).toEqual(
      [
        "clanWide",
        "description",
        "eventType",
        "location",
        "lunarDate",
        "personId",
        "recurringAnnually",
        "scopeBranchId",
        "solarDate",
        "title",
      ].sort()
    );
    // Ngày âm y hệt đã gõ — không có phép quy đổi/làm tròn nào chạy ở client.
    expect(body.lunarDate).toEqual({ day: 15, month: 8 });
    expect(body.title).toBe("Giỗ thử Đợt 2");
    expect(body.recurringAnnually).toBe(true);
    expect(body.solarDate).toBeNull();
    expect(body.personId).toBeNull();
    expect(body.clanWide).toBe(false);
    expect(body.scopeBranchId).toBeTruthy();
  });

  it("chỉ hiện ô ngày dương (tham chiếu) khi tắt 'lặp lại hằng năm', và gửi đi ĐÚNG như đã gõ", async () => {
    const captured = captureWrites();
    const { user } = renderWithProviders(<EventForm mode="create" />, { role: "branch-head" });

    await chooseFirstEventType("Loại việc họ");

    await user.type(screen.getByLabelText("Tiêu đề"), "Khánh thành nhà thờ chi");
    await user.clear(screen.getByLabelText("Ngày (âm)"));
    await user.type(screen.getByLabelText("Ngày (âm)"), "3");
    await user.clear(screen.getByLabelText("Tháng (âm)"));
    await user.type(screen.getByLabelText("Tháng (âm)"), "11");

    // Tắt lặp lại hằng năm — bây giờ ô ngày dương tham chiếu VÀ năm âm xuất hiện
    // (năm âm bắt buộc cho một việc chỉ diễn ra một lần — `ck_event_oneoff_lunar_year`).
    await user.click(screen.getByLabelText("Lặp lại hằng năm"));
    const solarInput = await screen.findByLabelText("Ngày dương (tham chiếu)");
    // `fireEvent.change`, không phải `user.type`: `<input type="date">` chia
    // thành từng đoạn (năm/tháng/ngày) mà bộ gõ phím giả lập của userEvent
    // không mô phỏng đúng, gõ từng ký tự vào đó dễ treo test.
    fireEvent.change(solarInput, { target: { value: "2027-01-20" } });
    await user.clear(screen.getByLabelText("Năm (âm)"));
    await user.type(screen.getByLabelText("Năm (âm)"), "2027");

    await pickFirstBranch("Chi/ngành nhận nhắc");
    await user.click(screen.getByRole("button", { name: "Tạo" }));

    await waitFor(() => {
      expect(captured.some((r) => r.method === "POST")).toBe(true);
    });

    const body = captured.find((r) => r.method === "POST")!.body as EventCreateRequest;
    expect(body.recurringAnnually).toBe(false);
    // Gõ gì thì gửi đúng cái đó — client không suy ra ngày dương từ ngày âm
    // (3 tháng 11 âm không "tính ra" 20/01 dương bằng phép nào ở đây cả).
    expect(body.solarDate).toBe("2027-01-20");
    expect(body.lunarDate).toEqual({ day: 3, month: 11, year: 2027 });
  });
});

describe("EventForm — GIO_THUONG luôn gắn một người (ck_event_gio_has_person)", () => {
  it("mặc định là GIO_THUONG và đòi chọn người trước khi cho gửi", async () => {
    const captured = captureWrites();
    const { user } = renderWithProviders(<EventForm mode="create" />, { role: "branch-head" });

    expect(screen.getByText("Người của giỗ này")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Tiêu đề"), "Giỗ chưa chọn người");
    await user.clear(screen.getByLabelText("Ngày (âm)"));
    await user.type(screen.getByLabelText("Ngày (âm)"), "1");
    await user.clear(screen.getByLabelText("Tháng (âm)"));
    await user.type(screen.getByLabelText("Tháng (âm)"), "1");
    await pickFirstBranch("Chi/ngành nhận nhắc");

    await user.click(screen.getByRole("button", { name: "Tạo" }));

    // Validate ở CLIENT chặn luôn — không một lượt POST nào được gửi đi.
    await waitFor(() => {
      expect(screen.getByText("Chọn người mà giỗ này thuộc về.")).toBeInTheDocument();
    });
    expect(captured.some((r) => r.method === "POST")).toBe(false);
  });

  it("chọn người xong thì gửi kèm personId", async () => {
    const captured = captureWrites();
    const { user } = renderWithProviders(<EventForm mode="create" />, { role: "branch-head" });

    await user.type(screen.getByLabelText("Tiêu đề"), "Giỗ có chọn người");
    await user.clear(screen.getByLabelText("Ngày (âm)"));
    await user.type(screen.getByLabelText("Ngày (âm)"), "1");
    await user.clear(screen.getByLabelText("Tháng (âm)"));
    await user.type(screen.getByLabelText("Tháng (âm)"), "1");
    await pickAnyPerson(user, "Người của giỗ này");
    await pickFirstBranch("Chi/ngành nhận nhắc");

    await user.click(screen.getByRole("button", { name: "Tạo" }));

    await waitFor(() => {
      expect(captured.some((r) => r.method === "POST")).toBe(true);
    });
    const body = captured.find((r) => r.method === "POST")!.body as EventCreateRequest;
    expect(body.personId).toBeTruthy();
  });
});

describe("EventForm — phạm vi nổi bật, và 'cả dòng họ' chỉ dành cho Hội đồng/Tộc trưởng", () => {
  it("Trưởng chi không bấm được 'Cả dòng họ'", () => {
    renderWithProviders(<EventForm mode="create" />, { role: "branch-head" });

    expect(screen.getByRole("radio", { name: "Cả dòng họ" })).toBeDisabled();
    expect(
      screen.getByText("Chỉ Hội đồng Tộc biểu / Tộc trưởng mới phát được lời nhắc cho cả dòng họ.")
    ).toBeInTheDocument();
  });

  it("Quản trị/Hội đồng bấm được 'Cả dòng họ', và khi đó không còn ô chọn chi", async () => {
    const captured = captureWrites();
    const { user } = renderWithProviders(<EventForm mode="create" />, { role: "admin" });

    await chooseFirstEventType("Loại việc họ");

    await user.type(screen.getByLabelText("Tiêu đề"), "Giỗ Tổ toàn họ");
    await user.clear(screen.getByLabelText("Ngày (âm)"));
    await user.type(screen.getByLabelText("Ngày (âm)"), "10");
    await user.clear(screen.getByLabelText("Tháng (âm)"));
    await user.type(screen.getByLabelText("Tháng (âm)"), "3");

    const clanRadio = screen.getByRole("radio", { name: "Cả dòng họ" });
    expect(clanRadio).not.toBeDisabled();
    // Ant Design đặt `pointer-events: none` lên chính thẻ <input> và vẽ vùng
    // chạm thật lên <label> bọc ngoài — bấm đúng như một người dùng thật sẽ
    // bấm (vào cái vỏ nhìn thấy được), không bấm thẳng vào input ẩn.
    await user.click(clanRadio.closest("label")!);

    expect(screen.queryByLabelText("Chi/ngành nhận nhắc")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Tạo" }));

    await waitFor(() => {
      expect(captured.some((r) => r.method === "POST")).toBe(true);
    });
    const body = captured.find((r) => r.method === "POST")!.body as EventCreateRequest;
    expect(body.clanWide).toBe(true);
    expect(body.scopeBranchId).toBeNull();
  });
});

describe("EventCreateScreen — chỉ Trưởng cành/chi/họ vào được", () => {
  it("thành viên thường thấy lời giải thích, không thấy biểu mẫu", async () => {
    renderWithProviders(<EventCreateScreen />, { role: "member" });

    expect(
      await screen.findByText("Chỉ Trưởng cành/chi/họ mới tạo được việc họ mới.")
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Tiêu đề")).not.toBeInTheDocument();
  });

  it("Trưởng chi thấy biểu mẫu thật", async () => {
    renderWithProviders(<EventCreateScreen />, { role: "branch-head" });

    expect(await screen.findByLabelText("Tiêu đề")).toBeInTheDocument();
  });
});

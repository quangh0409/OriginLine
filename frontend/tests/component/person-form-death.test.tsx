import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-100/edit",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-100/edit",
}));

const { PersonEditScreen } = await import("@/components/person-form/person-edit-screen");

/**
 * F4 — hộp thoại xác nhận khi ghi nhận một người đang sống là đã mất.
 *
 * <h2>Vì sao đáng một tệp test riêng</h2>
 * Backend nay SUY DIỄN: nhập ngày mất cho người đang sống thì hệ thống hiểu là người đó đã mất và
 * lưu luôn, thay vì từ chối. Suy diễn ấy tiện, nhưng nó khiến một cú gõ nhầm ngày đủ sức lật
 * trạng thái riêng tư của một người ĐANG SỐNG: theo BA v2 §10, hồ sơ người đã khuất chuyển từ
 * "ẩn mặc định" sang công khai với cả Khách chưa đăng nhập, đồng thời sinh lịch nhắc giỗ gửi cho
 * cả chi/nhánh. Sửa lại thì được, nhưng thứ đã lộ thì không thu về được — nên hộp thoại này là
 * lớp bảo vệ duy nhất còn lại, và nó phải hiện ĐÚNG LÚC, không hiện sai lúc.
 *
 * Vì thế các ca dưới đây kiểm cả hai chiều: phải hỏi khi thật sự chuyển sống → mất, và tuyệt đối
 * không hỏi lại khi chỉ sửa ngày giỗ của người vốn đã mất.
 */

/** Nguyễn Văn An — người CÒN SỐNG trong dữ liệu giả (src/mocks/data.ts, p-100). */
const LIVING_ID = "p-100";
const LIVING_NAME = "Nguyễn Văn An";
/** Thủy tổ — người ĐÃ KHUẤT (p-001). */
const DECEASED_ID = "p-001";

interface CapturedRequest {
  method: string;
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
    captured.push({ method: request.method, body });
  });
  return captured;
}

const patches = (captured: CapturedRequest[]) => captured.filter((r) => r.method === "PATCH");

/** Chờ form nạp xong hồ sơ rồi mới thao tác. */
async function openEditor(personId: string) {
  const rendered = renderWithProviders(<PersonEditScreen personId={personId} />, {
    role: "admin",
  });
  await screen.findByRole("button", { name: "Lưu thay đổi" }, { timeout: 15_000 });
  return rendered;
}

function deathSolarInput() {
  return within(screen.getByRole("group", { name: /Ngày mất/ })).getByLabelText("Ngày dương lịch");
}

async function save(user: ReturnType<typeof renderWithProviders>["user"]) {
  await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
}

beforeEach(() => {
  resetRouterMock();
  server.events.removeAllListeners("request:start");
});

describe("ghi nhận một người đang sống là đã mất", () => {
  it("hỏi lại trước khi gửi, nêu đích danh người và ngày vừa nhập", async () => {
    const captured = captureWrites();
    const { user } = await openEditor(LIVING_ID);

    fireEvent.change(deathSolarInput(), { target: { value: "2026-03-12" } });
    await save(user);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Bạn đang nhập thông tin người mất")).toBeInTheDocument();
    // AI — không được hỏi trống không. Tên xuất hiện cả ở câu dẫn lẫn ở dòng hệ quả.
    expect(within(dialog).getAllByText(new RegExp(LIVING_NAME)).length).toBeGreaterThan(0);
    // NGÀY — chiếu lại đúng thứ sắp lưu, để người dùng soi ra mình gõ nhầm.
    expect(within(dialog).getByText("12/03/2026")).toBeInTheDocument();

    // Và chưa có gì rời khỏi máy.
    expect(patches(captured)).toHaveLength(0);
  });

  it("nói rõ hệ quả: hồ sơ thành công khai và sẽ có nhắc giỗ", async () => {
    const { user } = await openEditor(LIVING_ID);

    fireEvent.change(deathSolarInput(), { target: { value: "2026-03-12" } });
    await save(user);

    const dialog = await screen.findByRole("dialog");
    const text = dialog.textContent ?? "";
    expect(text).toMatch(/công khai/);
    expect(text).toMatch(/Khách chưa đăng nhập/);
    expect(text).toMatch(/nhắc giỗ/);
    // Và nói cả điều không thu hồi được, chứ không chỉ hỏi "chắc chưa?".
    expect(text).toMatch(/không thu về được/);
  });

  it("huỷ thì không gửi gì cả và giữ nguyên form", async () => {
    const captured = captureWrites();
    const { user } = await openEditor(LIVING_ID);

    fireEvent.change(deathSolarInput(), { target: { value: "2026-03-12" } });
    await save(user);

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Quay lại kiểm tra" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(patches(captured)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
    // Ngày vừa nhập còn nguyên: người dùng quay lại để KIỂM TRA, không phải để gõ lại từ đầu.
    expect(deathSolarInput()).toHaveValue("2026-03-12");
  });

  it("hỏi cả khi người dùng tự gạt công tắc sang 'đã khuất' mà chưa nhập ngày", async () => {
    // Hệ quả y hệt đường nhập ngày mất, nên không có lý do gì bỏ qua đường này.
    const captured = captureWrites();
    const { user } = await openEditor(LIVING_ID);

    await user.click(screen.getByRole("switch"));
    await save(user);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Bạn đang nhập thông tin người mất")).toBeInTheDocument();
    expect(patches(captured)).toHaveLength(0);
  });

  it("chỉ gửi sau khi được xác nhận, và gửi kèm trạng thái đã mất", async () => {
    const captured = captureWrites();
    const { user } = await openEditor(LIVING_ID);

    fireEvent.change(deathSolarInput(), { target: { value: "2026-03-12" } });
    await save(user);

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Đúng, người này đã mất" }));

    await waitFor(() => expect(patches(captured)).toHaveLength(1));
    const body = patches(captured)[0]!.body as Record<string, unknown>;
    // Ngày mất suy ra trạng thái đã mất — nếu công tắc trên form thắng thì ngày giỗ vừa nhập sẽ
    // bị mapper vứt đi trong im lặng.
    expect(body.isAlive).toBe(false);
    expect(body.death).toMatchObject({ solar: "2026-03-12" });
    await waitFor(() => expect(routerMock.push).toHaveBeenCalled());
  });
});

describe("người vốn đã mất", () => {
  it("KHÔNG hỏi lại khi chỉ sửa ngày mất — hồ sơ đã công khai từ trước", async () => {
    const captured = captureWrites();
    const { user } = await openEditor(DECEASED_ID);

    fireEvent.change(deathSolarInput(), { target: { value: "1852-11-04" } });
    await save(user);

    await waitFor(() => expect(patches(captured)).toHaveLength(1));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const body = patches(captured)[0]!.body as Record<string, unknown>;
    expect(body.death).toMatchObject({ solar: "1852-11-04" });
  });
});

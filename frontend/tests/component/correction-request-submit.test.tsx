import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { resetChangeRequestsMock } from "@/mocks/change-requests";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-010/correction",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-010/correction",
}));

const { PersonCorrectionScreen } = await import(
  "@/components/correction/person-correction-screen"
);

/**
 * **Phía người gửi** của luồng đính chính.
 *
 * <h2>Vì sao bộ test này tồn tại</h2>
 * Trước đó `canRequestCorrection` bị ghi cứng `false` ở cả ba tệp dữ liệu giả,
 * nên luồng này không có một điểm bắt đầu nào trên toàn giao diện — thành viên
 * là người phát hiện sai sót nhiều nhất lại là người không sửa được gì, và
 * không có cầu nối. Các ca dưới đây khoá chặt cây cầu ấy: nút phải hiện đúng
 * chỗ, đề nghị phải đi tới máy chủ đúng hình dạng, và **không** hiện cho người
 * vốn đã sửa thẳng được.
 *
 * Dữ liệu giả: `p-010` (Nguyễn Văn Hiển) đã khuất, thuộc `root.chi_nhat`, nên
 * hồ sơ là công khai — thành viên đọc được nhưng không ghi được.
 */

const DECEASED_IN_CHI1 = "p-010";

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

const submissions = (captured: CapturedRequest[]) =>
  captured.filter((r) => r.method === "POST" && r.url.includes("/change-requests"));

/** Bộ test này chờ MSW + antd nhiều lần; 45s là mức an toàn trên máy chậm. */
const SLOW = 45_000;

/**
 * `fireEvent.change` chứ không phải `user.type`: react-hook-form đọc đúng sự
 * kiện `change` của React, còn `user.type` gõ từng phím qua cả lớp Ant Design
 * trong jsdom và tốn hàng giây cho một câu ngắn.
 */
function fill(field: HTMLElement, value: string) {
  fireEvent.change(field, { target: { value } });
}

async function openFor(personId: string, role: "member" | "branch-head" | "admin") {
  const rendered = renderWithProviders(<PersonCorrectionScreen personId={personId} />, { role });
  return rendered;
}

beforeEach(() => {
  resetRouterMock();
  resetChangeRequestsMock();
  server.events.removeAllListeners("request:start");
});

describe("thành viên đề nghị đính chính một hồ sơ họ không sửa được", () => {
  it("mở thẳng hộp thoại và chỉ ra ngày mất đang được ghi", async () => {
    await openFor(DECEASED_IN_CHI1, "member");

    expect(
      await screen.findByRole("button", { name: "Gửi đề nghị" }, { timeout: 15_000 })
    ).toBeInTheDocument();

    // Mặc định là ngày mất — ngày giỗ, thứ sai lệch nặng nhất một cuốn gia phả
    // có thể mắc, nên nó phải là mục đầu tiên chứ không nằm cuối danh sách.
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("Đang ghi trong gia phả")).toBeInTheDocument();
    // 20/01/1878 là ngày mất dương lịch của cụ trong dữ liệu giả.
    expect(within(dialog).getByText("20/01/1878")).toBeInTheDocument();
  }, SLOW);

  it("gửi đúng hình dạng POST /change-requests mà backend chờ", async () => {
    const captured = captureWrites();
    const { user } = await openFor(DECEASED_IN_CHI1, "member");
    await screen.findByRole("button", { name: "Gửi đề nghị" }, { timeout: 15_000 });

    const dialog = screen.getByRole("dialog");
    fill(within(dialog).getByLabelText("Ngày"), "18");
    fill(within(dialog).getByLabelText("Tháng"), "12");
    fill(within(dialog).getByLabelText("Năm"), "1877");
    fill(
      within(dialog).getByLabelText("Vì sao bạn cho là như vậy?"),
      "Gia phả chép tay nhà cháu ghi cụ mất ngày 18 tháng Chạp."
    );
    await user.click(screen.getByRole("button", { name: "Gửi đề nghị" }));

    await waitFor(() => expect(submissions(captured)).toHaveLength(1), { timeout: 10_000 });

    const body = submissions(captured)[0]?.body as Record<string, unknown>;
    expect(body.requestType).toBe("UPDATE_PERSON");
    expect(body.personId).toBe(DECEASED_IN_CHI1);
    expect(body.reason).toContain("18 tháng Chạp");

    // Khoá payload trùng tên trường của `UpdatePersonRequest` — quy ước duy
    // nhất khiến bước ghép W2×W6 sau này áp dụng được mà không phải dịch khoá.
    const payload = body.payload as Record<string, unknown>;
    expect(Object.keys(payload)).toEqual(["death"]);
    const death = payload.death as { lunar: { day: number; month: number; leap: boolean } };
    expect(death.lunar.day).toBe(18);
    expect(death.lunar.month).toBe(12);
    // Tháng nhuận phải đi cùng ngày âm: bỏ sót nó là lệch giỗ trọn một tháng.
    expect(death.lunar.leap).toBe(false);
  }, SLOW);

  it("không gửi gì khi lý do quá sơ sài — người duyệt cần bối cảnh mới quyết được", async () => {
    const captured = captureWrites();
    const { user } = await openFor(DECEASED_IN_CHI1, "member");
    await screen.findByRole("button", { name: "Gửi đề nghị" }, { timeout: 15_000 });

    const dialog = screen.getByRole("dialog");
    fill(within(dialog).getByLabelText("Ngày"), "18");
    fill(within(dialog).getByLabelText("Tháng"), "12");
    fill(within(dialog).getByLabelText("Vì sao bạn cho là như vậy?"), "sai");
    await user.click(screen.getByRole("button", { name: "Gửi đề nghị" }));

    expect(
      await screen.findByText(/Xin nêu lý do rõ hơn/, {}, { timeout: 10_000 })
    ).toBeInTheDocument();
    expect(submissions(captured)).toHaveLength(0);
  }, SLOW);

  it("không suy diễn về dữ liệu bị ẩn: hồ sơ thiếu quê quán thì không có ô 'đang ghi'", async () => {
    // p-021 (Trần Văn Khoa, con rể) không có `nativePlace` trong dữ liệu giả.
    const { user } = await openFor("p-021", "member");
    await screen.findByRole("button", { name: "Gửi đề nghị" }, { timeout: 15_000 });

    const dialog = screen.getByRole("dialog");
    const [topicBox] = within(dialog).getAllByRole("combobox");
    await user.click(topicBox!);
    await user.click(await screen.findByTitle("Quê quán", {}, { timeout: 5000 }));

    // Vắng mặt là cách hiển thị ĐÚNG: hợp đồng cố ý không cho phân biệt "chưa
    // ai ghi" với "bạn không được phép thấy", nên một dòng "chưa có" hay một
    // gạch ngang đều là tự bịa ra câu trả lời.
    await waitFor(() =>
      expect(within(dialog).getByLabelText("Đề nghị sửa thành")).toBeInTheDocument()
    );
    expect(within(dialog).queryByText("Đang ghi trong gia phả")).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  }, SLOW);
});

describe("người đã sửa thẳng được thì không bị đẩy qua hàng đợi của chính mình", () => {
  it("Quản trị thấy lời chỉ đường sang màn hình sửa, không thấy nút đề nghị", async () => {
    await openFor(DECEASED_IN_CHI1, "admin");

    expect(
      await screen.findByText("Bạn sửa trực tiếp được hồ sơ này", {}, { timeout: 15_000 })
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đề nghị đính chính" })).not.toBeInTheDocument();
  }, SLOW);

  it("Trưởng chi vẫn phải đề nghị với hồ sơ thuộc chi KHÁC", async () => {
    // p-011 (Nguyễn Văn Hoà) thuộc `root.chi_nhi`, ngoài phạm vi `root.chi_nhat`.
    await openFor("p-011", "branch-head");

    expect(
      await screen.findByRole("button", { name: "Gửi đề nghị" }, { timeout: 15_000 })
    ).toBeInTheDocument();
    expect(screen.queryByText("Bạn sửa trực tiếp được hồ sơ này")).not.toBeInTheDocument();
  }, SLOW);
});

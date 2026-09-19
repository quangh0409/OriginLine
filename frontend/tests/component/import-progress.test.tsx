import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { resetImportMockDb, seedMockBatch } from "@/mocks/data-import";
import { setDevRole } from "@/lib/api/dev-role";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/nhap-lieu/tien-do",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/nhap-lieu/tien-do",
}));

const { BranchProgressScreen } = await import("@/components/import/branch-progress-screen");
const { ImportTemplateScreen } = await import("@/components/import/import-template-screen");

/**
 * MÀN TIẾN ĐỘ THEO CHI, và màn MẪU EXCEL.
 *
 * <p>Ràng buộc nặng nhất của màn tiến độ không phải kỹ thuật: nó <b>không được
 * biến thành bảng xếp hạng giữa các chi</b>. Một dòng họ không phải một bảng
 * thi đua, và cách dễ nhất để vi phạm điều đó không phải là thêm một cột "hạng"
 * — mà là <b>sắp các chi theo số người đã nhập</b>, thứ trông vô hại và dựng ra
 * đúng một bảng xếp hạng bằng thứ tự dòng.</p>
 *
 * <p>Ràng buộc thứ hai đến từ hợp đồng: các con số tiến độ đến từ một endpoint
 * <b>chưa tồn tại</b>. Màn này phải dựng được phần khung từ `GET /import/branches`
 * (có thật) và <b>nói ra</b> phần nào đang chờ — vẽ một bảng toàn số 0 thì người
 * đọc sẽ tin là thật.</p>
 */

beforeEach(() => {
  resetImportMockDb();
  resetRouterMock();
  setDevRole("admin");
});

afterEach(() => {
  resetImportMockDb();
});

describe("tiến độ theo chi · không phải bảng xếp hạng", () => {
  it("nói thẳng ra rằng đây không phải bảng xếp hạng", async () => {
    renderWithProviders(<BranchProgressScreen />, { role: "admin" });
    expect(await screen.findByText(/Đây không phải bảng xếp hạng/i)).toBeInTheDocument();
  });

  it("giữ nguyên thứ tự `ltree` của máy chủ", async () => {
    renderWithProviders(<BranchProgressScreen />, { role: "admin" });

    await screen.findByTestId("branch-progress-b-chi1");
    // Chỉ đọc tiêu đề CỦA CÁC THẺ CHI: khối "đang chờ" cũng là một `h2`.
    const names = screen
      .getAllByTestId(/^branch-progress-/)
      .map((card) => within(card).getByRole("heading", { level: 2 }).textContent);
    expect(names).toEqual(["Chi Nhất", "Chi Nhị", "Chi Tam", "Chi Tứ"]);
  });

  it("không hiện phần trăm hoàn thành — mẫu số nhiều chi còn chưa đếm", async () => {
    renderWithProviders(<BranchProgressScreen />, { role: "admin" });
    await screen.findByTestId("branch-progress-b-chi4");
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });
});

describe("tiến độ theo chi · chi ngoài phạm vi bị cắt trường", () => {
  it("chi NGOÀI phạm vi không vẽ ô trống ở chỗ năm trường bị cắt", async () => {
    setDevRole("branch-head");
    seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<BranchProgressScreen />, { role: "branch-head" });

    const other = await screen.findByTestId("branch-progress-b-chi2");
    // "Còn lại" rỗng đọc ra là "chi ấy không còn việc gì" — ngược hẳn sự thật,
    // mà sự thật là "bạn không được biết".
    expect(within(other).queryByText(/^Còn lại$/i)).not.toBeInTheDocument();
    expect(within(other).queryByText(/Người phụ trách/i)).not.toBeInTheDocument();
    expect(within(other).queryByText(/Chưa có người nhận chi này/i)).not.toBeInTheDocument();
    expect(within(other).getByText(/Bạn không nhập liệu cho chi này/i)).toBeInTheDocument();
  });

  it("chi TRONG phạm vi hiện đủ việc còn lại", async () => {
    setDevRole("branch-head");
    seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<BranchProgressScreen />, { role: "branch-head" });

    const mine = await screen.findByTestId("branch-progress-b-chi1");
    expect(within(mine).getByText(/Bạn được giao nhập chi này/i)).toBeInTheDocument();
    expect(within(mine).getByText(/6 lỗi phải sửa/i)).toBeInTheDocument();
    expect(within(mine).getByText(/11 mục cần xem lại/i)).toBeInTheDocument();
    expect(within(mine).getByText(/3 cặp nghi trùng chưa quyết/i)).toBeInTheDocument();
    expect(within(mine).getByText(/4 người đã mất chưa có ngày giỗ/i)).toBeInTheDocument();
  });

  it("chi chưa có người phụ trách hiện LÝ DO, không hiện một con số 0 trông như lỗi", async () => {
    renderWithProviders(<BranchProgressScreen />, { role: "admin" });
    const card = await screen.findByTestId("branch-progress-b-chi4");
    expect(within(card).getByText(/Chưa có người nhận chi này/i)).toBeInTheDocument();
    expect(within(card).getByText(/của Hội đồng|thuộc về Hội đồng/i)).toBeInTheDocument();
  });

  it("`expectedPersons` vắng mặt thì nói 'chưa ai đếm', KHÔNG vẽ thành 0", async () => {
    renderWithProviders(<BranchProgressScreen />, { role: "admin" });

    const counted = await screen.findByTestId("branch-progress-b-chi1");
    expect(within(counted).getByText(/Bản phả gốc có 380 người/i)).toBeInTheDocument();

    const uncounted = screen.getByTestId("branch-progress-b-chi3");
    expect(within(uncounted).getByText(/Chưa ai đếm bản phả giấy/i)).toBeInTheDocument();
    expect(within(uncounted).queryByText(/có 0 người/i)).not.toBeInTheDocument();
  });

  it("giải thích vì sao thiếu ngày giỗ là con số đáng đọc nhất", async () => {
    renderWithProviders(<BranchProgressScreen />, { role: "admin" });
    expect(await screen.findByText(/không bao giờ được nhắc giỗ/i)).toBeInTheDocument();
  });
});

describe("mẫu Excel · một mẫu cho mỗi chi", () => {
  it("Trưởng chi chỉ tải được mẫu của chi mình", async () => {
    renderWithProviders(<ImportTemplateScreen />, { role: "branch-head" });

    expect(
      await screen.findByRole("button", { name: /Tải mẫu của Chi Nhất/i })
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Tải mẫu của Chi Nhị/i })).not.toBeInTheDocument();
  });

  it("Quản trị tải được mẫu của cả bốn chi — phép kiểm là QUYỀN GHI, không phải vai", async () => {
    renderWithProviders(<ImportTemplateScreen />, { role: "admin" });
    expect(
      await screen.findByRole("button", { name: /Tải mẫu của Chi Tứ/i })
    ).toBeInTheDocument();
  });

  it("KHÔNG dạy một quy tắc tiền tố mã không tồn tại", async () => {
    renderWithProviders(<ImportTemplateScreen />, { role: "branch-head" });
    await screen.findByRole("button", { name: /Tải mẫu của Chi Nhất/i });
    expect(screen.queryByText(/Tiền tố mã/i)).not.toBeInTheDocument();
    // Cái hiện ra là `ltree` — căn cứ phạm vi thật.
    expect(screen.getByText("root.chi_nhat")).toBeInTheDocument();
  });

  it("cảnh báo trước cái bẫy ngày âm — chỗ tốn thời gian nhất của cả tệp", async () => {
    renderWithProviders(<ImportTemplateScreen />, { role: "branch-head" });
    expect(await screen.findByText(/Ngày mất âm gõ dạng chữ/i)).toBeInTheDocument();
    expect(screen.getByText(/Excel sẽ tự nuốt 15\/8/i)).toBeInTheDocument();
  });

  it("nói rõ việc không điền hộ người còn sống là QUYẾT ĐỊNH, không phải thiếu sót", async () => {
    renderWithProviders(<ImportTemplateScreen />, { role: "branch-head" });
    expect(
      await screen.findByText(/Đây là quyết định, không phải thiếu sót/i)
    ).toBeInTheDocument();
  });

  it("Thành viên thường được chỉ tới Hội đồng, không nhận một trang trống", async () => {
    renderWithProviders(<ImportTemplateScreen />, { role: "member" });
    expect(await screen.findByText(/chưa được giao chi nào để nhập liệu/i)).toBeInTheDocument();
    expect(screen.getByText(/Hội đồng Tộc biểu giao từng chi/i)).toBeInTheDocument();
  });
});

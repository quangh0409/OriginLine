import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import {
  markEditedAfterCommit,
  markNewWarningAfterAck,
  resetImportMockDb,
  seedMockBatch,
} from "@/mocks/data-import";
import { dataImportApi } from "@/lib/api/data-import";
import { setDevRole } from "@/lib/api/dev-role";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/nhap-lieu",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/nhap-lieu",
}));

const { ImportReconcileScreen } = await import("@/components/import/import-reconcile-screen");
const { ImportIntroScreen } = await import("@/components/import/import-intro-screen");

/**
 * MÀN ĐỐI SOÁT — màn quyết định thành bại của cả đợt nhập liệu.
 *
 * <p>Năm điều được khoá ở đây, và cả năm đều là <b>quyết định thiết kế</b> chứ
 * không phải chi tiết cài đặt:</p>
 *
 * <ol>
 *   <li><b>Câu "chưa ghi gì vào phả" có mặt trên màn hình.</b> Người nhập là
 *       Trưởng chi 45–65 tuổi và nỗi sợ lớn nhất của họ là làm hỏng phả.</li>
 *   <li><b>Hai nhóm lỗi nằm ở hai khối riêng biệt.</b> "6 lỗi phải sửa" là một
 *       việc làm được; "17 vấn đề" nghe như hỏng cả tệp.</li>
 *   <li><b>Nút duyệt khoá theo `canApprove` của MÁY CHỦ</b>, không theo phép suy
 *       của client — và vẫn nói ra lý do.</li>
 *   <li><b>Lỗi của cả lô không in ra "Dòng undefined".</b></li>
 *   <li><b>Màn xác nhận gỡ lô hiện `blockers[]`.</b> Một hộp thoại "Bạn chắc
 *       chứ?" không nói gì về 12 người đã được sửa là một hộp thoại nói dối.</li>
 * </ol>
 */

beforeEach(() => {
  resetImportMockDb();
  resetRouterMock();
  // Các lời gọi API TRỰC TIẾP trong ca kiểm xảy ra trước `renderWithProviders`,
  // nên phải tự ghim vai — nếu không chúng đi với vai mặc định là Khách.
  setDevRole("branch-head");
});

afterEach(() => {
  resetImportMockDb();
});

function blockingSection() {
  return screen.getByRole("region", { name: /lỗi phải sửa/i });
}

function warningSection() {
  return screen.getByRole("region", { name: /cần xem lại/i });
}

describe("đối soát · lời trấn an", () => {
  it("nói thẳng rằng chưa có gì được ghi vào phả", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    expect(await screen.findByText(/Chưa có gì được ghi vào phả/i)).toBeInTheDocument();
    expect(screen.getByText(/chỉ đổi khi bạn bấm nút duyệt/i)).toBeInTheDocument();
  });

  it("lời trấn an BIẾN MẤT sau khi đã ghi — lúc đó nó không còn đúng nữa", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    await dataImportApi.commit(batch.id);
    // Bộ giả lập đưa lô về đích khi hết thời lượng ghi nền.
    await new Promise((resolve) => setTimeout(resolve, 2_000));

    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await screen.findByText(/người vào phả/i);
    expect(screen.queryByText(/Chưa có gì được ghi vào phả/i)).not.toBeInTheDocument();
  });
});

describe("đối soát · hai nhóm lỗi tách bạch", () => {
  it("sáu lỗi phải sửa và mười một mục cần xem lại nằm ở hai khối khác nhau", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());

    expect(within(blockingSection()).getByText(/6 lỗi phải sửa/i)).toBeInTheDocument();
    expect(
      within(warningSection()).getByRole("heading", { name: /11 mục cần xem lại/i })
    ).toBeInTheDocument();

    // Không có chỗ nào gộp hai con số thành "17 vấn đề".
    expect(screen.queryByText(/17 vấn đề/i)).not.toBeInTheDocument();

    // Và một mục cảnh báo không được lọt sang khối lỗi chặn.
    expect(within(blockingSection()).queryByText(/Thiếu ngày giỗ/i)).not.toBeInTheDocument();
    expect(within(warningSection()).getAllByText(/Thiếu ngày giỗ/i).length).toBeGreaterThan(0);
  });

  it("mỗi dòng lỗi chỉ đúng TRANG và DÒNG trong tệp Excel", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());
    expect(within(blockingSection()).getByText(/Nhân khẩu · dòng 37/i)).toBeInTheDocument();
  });

  it("lỗi của CẢ LÔ hiện 'Cả lô', không in ra một số dòng không tồn tại", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());
    expect(within(blockingSection()).getByText(/^Cả lô$/)).toBeInTheDocument();
    expect(within(blockingSection()).queryByText(/undefined/i)).not.toBeInTheDocument();
  });

  it("lỗi 'mã cha không tìm thấy' hiện GỢI Ý mã gần giống ngay cạnh nó", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());
    const region = blockingSection();
    expect(within(region).getByText(/Có phải bạn định ghi/i)).toBeInTheDocument();
    expect(within(region).getByText("AT-04-003")).toBeInTheDocument();
  });

  it("lỗi vòng lặp hiện đường đi thật, đủ để mở sổ giấy ra tra", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());
    const region = blockingSection();
    expect(within(region).getByText(/Đường vòng trong tệp/i)).toBeInTheDocument();
    expect(within(region).getAllByText("AT-05-021").length).toBeGreaterThanOrEqual(2);
  });

  it("mã trùng chỉ ra ĐỦ các dòng cùng dính, không chỉ một dòng", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());
    expect(
      within(blockingSection()).getByText(/Trùng với các dòng 58, 59, 61/i)
    ).toBeInTheDocument();
  });

  /**
   * Nút tải bản lỗi ra Excel phải nằm <b>ngay cạnh bảng lỗi</b>.
   *
   * <p>Đây không phải một tiện ích: người dùng thật là Trưởng chi 45–65 tuổi, và
   * chỗ họ sửa được là chính tệp Excel chứ không phải một bảng trên web bắt sửa
   * từng dòng. Giấu nút ấy trong một menu phụ là để đường thoát tồn tại mà
   * không ai tìm thấy.</p>
   */
  it("nút tải bản lỗi ra Excel nằm NGAY TRONG khối lỗi chặn, không ở một menu phụ", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());
    const region = blockingSection();
    expect(within(region).getByRole("button", { name: /Tải lại tệp đã sửa/i })).toBeInTheDocument();
    expect(
      within(region).getByRole("button", { name: /Tải danh sách lỗi ra Excel/i })
    ).toBeInTheDocument();
    // Khối "chờ backend" đã biến mất cùng với lý do tồn tại của nó.
    expect(screen.queryByTestId("pending-issuesWorkbook")).not.toBeInTheDocument();
  });

  it("chỉ có MỘT nút tải, không phải một nút cho mỗi nhóm lỗi", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(blockingSection()).toBeInTheDocument());
    // Tệp có ba trang và endpoint KHÔNG nhận `severity`: bộ sinh tự tách hai
    // nhóm. Một bản xuất chỉ có cảnh báo là tệp mang tên "Danh sách cần sửa"
    // mà thiếu đúng phần phải sửa.
    expect(screen.getAllByTestId("issues-workbook")).toHaveLength(1);
  });

  it("hết lỗi chặn thì nút tải chuyển sang đứng cạnh bảng cần-xem-lại, vẫn chỉ một nút", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-da-sua.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await screen.findByRole("region", { name: /cần xem lại/i });
    const warning = screen.getByRole("region", { name: /cần xem lại/i });
    expect(screen.getAllByTestId("issues-workbook")).toHaveLength(1);
    expect(
      within(warning).getByRole("button", { name: /Tải danh sách lỗi ra Excel/i })
    ).toBeInTheDocument();
  });

  it("lô sạch hoàn toàn KHÔNG có nút tải — không có gì để sửa thì tệp ấy là tiếng ồn", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await screen.findByText(/Không còn lỗi phải sửa/i);
    expect(screen.queryByTestId("issues-workbook")).not.toBeInTheDocument();
  });
});

describe("đối soát · cửa duyệt", () => {
  it("còn lỗi chặn thì nút duyệt bị khoá VÀ nói ra lý do", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    const commit = await screen.findByRole("button", { name: /Duyệt và ghi .* vào phả/i });
    expect(commit).toBeDisabled();
    expect(within(screen.getByTestId("commit-blockers")).getByText(/6 lỗi/i)).toBeInTheDocument();
  });

  it("sạch lỗi nhưng còn cảnh báo và cặp nghi trùng thì vẫn khoá, và nêu ĐỦ lý do", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-da-sua.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    const commit = await screen.findByRole("button", { name: /Duyệt và ghi .* vào phả/i });
    expect(commit).toBeDisabled();
    const blockers = screen.getByTestId("commit-blockers");
    expect(within(blockers).getByText(/Xác nhận đã xem/i)).toBeInTheDocument();
    expect(within(blockers).getByText(/3 cặp nghi trùng chưa quyết/i)).toBeInTheDocument();
  });

  it("tệp sạch hoàn toàn thì nút duyệt MỞ, theo canApprove của máy chủ", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    const commit = await screen.findByRole("button", { name: /Duyệt và ghi 318 người vào phả/i });
    await waitFor(() => expect(commit).toBeEnabled());
    expect(screen.queryByTestId("commit-blockers")).not.toBeInTheDocument();
  });

  it("tick 'đã xem' ghi lại được, và ô tick nhường chỗ cho dòng lịch sử", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    // Gieo một cảnh báo để có gì mà xác nhận, rồi xác nhận.
    markNewWarningAfterAck(batch.id);
    const { user } = renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, {
      role: "branch-head",
    });

    await waitFor(() => expect(warningSection()).toBeInTheDocument());
    await user.click(within(warningSection()).getByRole("checkbox"));

    expect(await within(warningSection()).findByText(/Đã xác nhận xem lúc/i)).toBeInTheDocument();
    const commit = await screen.findByRole("button", { name: /Duyệt và ghi .* vào phả/i });
    await waitFor(() => expect(commit).toBeEnabled());
  });

  it("xác nhận HẾT HIỆU LỰC sau khi bộ kiểm sinh mục mới: dấu thời gian còn, cửa đóng lại", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    markNewWarningAfterAck(batch.id);
    await dataImportApi.acknowledgeWarnings(batch.id);
    // Lượt kiểm sau sinh thêm một mục nữa — lời khai cũ chỉ tính cho đúng danh
    // sách người ta đã đọc.
    markNewWarningAfterAck(batch.id);

    renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, { role: "branch-head" });

    await waitFor(() => expect(warningSection()).toBeInTheDocument());
    // Dấu thời gian VẪN hiện — đã có người thật bấm nút, đó là lịch sử.
    expect(within(warningSection()).getByText(/Đã xác nhận xem lúc/i)).toBeInTheDocument();
    // Nhưng ô tick quay lại, kèm lý do, và nút duyệt vẫn khoá.
    expect(within(warningSection()).getByRole("checkbox")).toBeInTheDocument();
    expect(within(warningSection()).getByText(/Cửa duyệt vẫn chưa mở/i)).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: /Duyệt và ghi .* vào phả/i })
    ).toBeDisabled();
    expect(
      within(screen.getByTestId("commit-blockers")).getByText(/sinh thêm mục cần xem lại/i)
    ).toBeInTheDocument();
  });
});

describe("đối soát · xem trước các dòng đang chờ", () => {
  it("trả lời 'tải lại có sinh người trùng không' bằng cột Sẽ làm gì", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    const { user } = renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, {
      role: "branch-head",
    });

    const summary = await screen.findByText(/Xem trước các dòng/i);
    await user.click(summary);

    const row = await screen.findByTestId("staged-row-12");
    expect(within(row).getByText(/Cập nhật người đã có/i)).toBeInTheDocument();
  });
});

describe("đối soát · gỡ lô", () => {
  async function renderCommitted(fileName = "chi-nhat-sach.xlsx") {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName });
    await dataImportApi.commit(batch.id);
    await new Promise((resolve) => setTimeout(resolve, 2_000));
    const result = renderWithProviders(<ImportReconcileScreen batchId={batch.id} />, {
      role: "branch-head",
    });
    return { batch, ...result };
  }

  it("KHÔNG hứa một hạn 30 ngày — hợp đồng không có rollbackDeadline", async () => {
    await renderCommitted();
    await screen.findByText(/người vào phả/i);
    expect(screen.queryByText(/30 ngày/i)).not.toBeInTheDocument();
  });

  it("hộp xác nhận hiện ĐÍCH DANH những vướng mắc, không hỏi trống không", async () => {
    const { batch, user } = await renderCommitted();
    // 12 người đã được sửa sau khi ghi — thứ mà một hộp thoại "Bạn chắc chứ?"
    // sẽ giấu đi.
    markEditedAfterCommit(batch.id, 12);

    await screen.findByText(/người vào phả/i);
    await user.click(screen.getByRole("button", { name: /Gỡ toàn bộ lô này/i }));

    const blockers = await screen.findByTestId("rollback-blockers");
    expect(within(blockers).getByText(/12 hồ sơ/i)).toBeInTheDocument();
    // Và nút gỡ phải khoá: không mời người ta bấm vào một việc máy chủ sẽ từ chối.
    expect(screen.getByRole("button", { name: /^Gỡ lô$/ })).toBeDisabled();
  });

  it("không vướng gì thì gỡ được, và lý do là TUỲ CHỌN", async () => {
    const { user } = await renderCommitted();
    await screen.findByText(/người vào phả/i);
    await user.click(screen.getByRole("button", { name: /Gỡ toàn bộ lô này/i }));

    const confirm = await screen.findByRole("button", { name: /^Gỡ lô$/ });
    await waitFor(() => expect(confirm).toBeEnabled());
    // Không gõ một chữ lý do nào.
    await user.click(confirm);

    expect(await screen.findByText(/Trạng thái vẫn là/i)).toBeInTheDocument();
  });
});

describe("màn dẫn nhập · lô đang dở", () => {
  it("chưa có lô nào thì KHÔNG mời làm tiếp — mảng rỗng ở đây có đúng một nghĩa", async () => {
    renderWithProviders(<ImportIntroScreen />, { role: "branch-head" });

    await screen.findByText(/Không có thao tác nào ở đây làm hỏng phả/i);
    expect(screen.queryByTestId("resume-batch-link")).not.toBeInTheDocument();
  });

  it("có lô đang dở thì mời làm tiếp, đọc từ danh sách lô của máy chủ", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    renderWithProviders(<ImportIntroScreen />, { role: "branch-head" });

    const resume = await screen.findByTestId("resume-batch-link");
    expect(resume).toHaveAttribute("href", expect.stringContaining(`/nhap-lieu/${batch.id}`));
  });

  it("vẫn nói rõ không thao tác nào ở đây làm hỏng phả", async () => {
    renderWithProviders(<ImportIntroScreen />, { role: "branch-head" });
    expect(
      await screen.findByText(/Không có thao tác nào ở đây làm hỏng phả/i)
    ).toBeInTheDocument();
  });
});

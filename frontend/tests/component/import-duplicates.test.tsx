import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import {
  DUP_PAIR_IN_FILE,
  DUP_PAIR_TREE_HIDDEN,
  DUP_PAIR_TREE_VISIBLE,
  resetImportMockDb,
  seedConflictingMergePair,
  seedMockBatch,
} from "@/mocks/data-import";
import { setDevRole } from "@/lib/api/dev-role";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/nhap-lieu",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/nhap-lieu",
}));

const { DuplicateReviewScreen } = await import("@/components/import/duplicate-review-screen");

/**
 * MÀN ĐỐI CHIẾU NGƯỜI NGHI TRÙNG.
 *
 * <p>Sáu điều được khoá ở đây, và cả sáu đều là hệ quả trực tiếp của hợp đồng
 * thật — không phải sở thích trình bày:</p>
 * <ol>
 *   <li><b>Bên "đã có trong phả" không có dữ liệu nào ngoài một khoá.</b> Màn
 *       này phải tự đi hỏi `GET /persons/{id}`, và <b>`404` là ca bình thường</b>:
 *       bộ dò quét cả dòng họ nên người bị nghi có thể còn sống ở một chi khác.
 *       Câu chữ khi ấy phải đọc ra "bạn không có quyền xem", không phải "hỏng"
 *       và không phải một ô trống.</li>
 *   <li><b>Không có bảng bằng chứng cho cặp `TREE`.</b> `evidence` rỗng theo
 *       bất biến riêng tư — vẽ một bảng bảy dòng trống là rò rỉ sự tồn tại của
 *       dữ liệu đang giấu.</li>
 *   <li><b>Cặp `TREE` vẫn nói được VÌ SAO NGHI</b>, qua `signals`. Đó là ô duy
 *       nhất có nội dung ở loại cặp ấy — `hint` luôn vắng — nên đọc nhầm khoá
 *       làm ô ấy trống ở đúng loại cặp quan trọng nhất.</li>
 *   <li><b>Ngày giỗ trên năm sinh</b> ở cặp `FILE`↔`FILE`, nơi có bảng thật.</li>
 *   <li><b>Quyết một cặp đổi HAI CON SỐ KẾ HOẠCH</b>, và chúng đến từ lô đã tính
 *       lại trong chính phản hồi — màn hình không cộng trừ gì.</li>
 *   <li><b>"Hoãn" KHÔNG mở khoá nút duyệt.</b> Nếu nó mở, nó là nút "cho tôi
 *       qua" và cả cơ chế dò trùng thành trang trí.</li>
 * </ol>
 */

beforeEach(() => {
  resetImportMockDb();
  resetRouterMock();
  setDevRole("branch-head");
});

afterEach(() => {
  resetImportMockDb();
});

function seedAndRender() {
  const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
  const result = renderWithProviders(<DuplicateReviewScreen batchId={batch.id} />, {
    role: "branch-head",
  });
  return { batch, ...result };
}

describe("đối chiếu nghi trùng · bên phả chỉ có một khoá", () => {
  it("cặp có bên phả tra được thì dựng cột từ GET /persons, không từ kết quả dò trùng", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_VISIBLE}`);
    // `p-001` là cụ Thuỷ Tổ đã khuất — tên đến từ hồ sơ, nơi bộ lọc phân tầng
    // riêng tư là luật DUY NHẤT quyết định trường nào được trả.
    expect(await within(card).findByText(/Nguyễn Văn Thủy Tổ/i)).toBeInTheDocument();
  });

  it("cặp có bên phả KHÔNG được xem thì nói 'bạn không có quyền', không nói 'không tìm thấy'", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_HIDDEN}`);
    const blocked = await within(card).findByTestId("tree-party-not-visible");

    expect(within(blocked).getByText(/không có quyền xem người này/i)).toBeInTheDocument();
    // Không rò rỉ việc bản ghi có tồn tại hay không.
    expect(within(blocked).getByText(/không cho biết hồ sơ đó có tồn tại hay không/i)).toBeInTheDocument();
    // Và chỉ đúng chỗ đi tiếp: người nhìn được cả hai bên.
    expect(within(blocked).getByText(/Hội đồng Tộc biểu/i)).toBeInTheDocument();
    // Tuyệt đối không phải một câu lỗi.
    expect(within(card).queryByText(/Không tải được hồ sơ bên phả/i)).not.toBeInTheDocument();
  });

  it("cặp TREE KHÔNG có bảng bằng chứng — và nói ra vì sao, thay vì một bảng rỗng", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_VISIBLE}`);
    expect(within(card).queryByTestId(/^evidence-[A-Z]/)).not.toBeInTheDocument();
    expect(within(card).getByTestId("evidence-withheld")).toBeInTheDocument();
  });

  it("nói trước cho người đối chiếu biết vì sao một bên có thể trống", async () => {
    seedAndRender();
    expect(
      await screen.findByText(/chỉ nhận được một khoá — không tên, không năm sinh/i)
    ).toBeInTheDocument();
  });
});

describe("đối chiếu nghi trùng · cặp hai dòng trong cùng một tệp", () => {
  it("có đủ bảng bằng chứng: không có gì để giấu với chính tác giả của nó", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_IN_FILE}`);
    expect(within(card).getAllByTestId(/^evidence-[A-Z]/).length).toBe(7);
  });

  it("ngày giỗ là dòng ĐẦU, năm sinh là dòng CUỐI", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_IN_FILE}`);
    const rows = within(card)
      .getAllByTestId(/^evidence-[A-Z]/)
      .map((row) => row.getAttribute("data-testid"));

    expect(rows[0]).toBe("evidence-DEATH_LUNAR");
    expect(rows[rows.length - 1]).toBe("evidence-BIRTH_YEAR");
  });

  it("nói ra VÌ SAO ngày giỗ đáng tin hơn năm sinh — kiến thức gia phả, không phải mẹo giao diện", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_IN_FILE}`);
    expect(
      within(within(card).getByTestId("evidence-DEATH_LUNAR")).getByText(/cả họ cúng hằng năm/i)
    ).toBeInTheDocument();
    expect(
      within(within(card).getByTestId("evidence-BIRTH_YEAR")).getByText(/chép theo trí nhớ/i)
    ).toBeInTheDocument();
  });

  it("chỗ tệp bỏ trống được gọi tên, kèm cảnh báo về việc mất lớp tên", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_IN_FILE}`);
    const gioRow = within(card).getByTestId("evidence-DEATH_LUNAR");
    expect(within(gioRow).getByText(/Tệp bỏ trống/i)).toBeInTheDocument();
    expect(within(card).getByText(/không ghi đè bằng chỗ trống/i)).toBeInTheDocument();
  });

  it("KHÔNG hiện cột điểm cho từng dấu hiệu — máy chủ không gửi điểm nào", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_IN_FILE}`);
    expect(within(card).queryByText(/\d+ điểm$/)).not.toBeInTheDocument();
  });
});

describe("đối chiếu nghi trùng · máy nghi ngờ, người quyết định", () => {
  it("ngưỡng hiện lên từ MÁY CHỦ, không phải một con số ghi cứng", async () => {
    seedAndRender();
    // 70 và 85 đến từ `GET /import/duplicate-policy`.
    expect(await screen.findByText(/từ 70 điểm trở lên.*từ 85 điểm/i)).toBeInTheDocument();
  });

  it("ba nút bấm được — endpoint quyết định đã có thật", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_VISIBLE}`);

    for (const name of [/hợp nhất/i, /hai người khác nhau/i, /Chưa rõ — để lại sau/i]) {
      expect(within(card).getByRole("button", { name })).toBeEnabled();
    }
    // Khối "chờ backend" đã biến mất cùng với lý do tồn tại của nó.
    expect(screen.queryByTestId("pending-duplicateDecision")).not.toBeInTheDocument();
  });

  it("KHÔNG có nút quyết hàng loạt — một cú bấm không được phép gộp hàng chục người", async () => {
    seedAndRender();
    await screen.findByRole("heading", { name: /Đối chiếu người nghi trùng/i });
    expect(screen.queryByRole("button", { name: /tất cả/i })).not.toBeInTheDocument();
  });

  it("nói thẳng rằng máy chỉ xếp thứ tự, người quyết", async () => {
    seedAndRender();
    expect(
      await screen.findByText(/Hệ thống chỉ xếp thứ tự xem xét. Quyết định là của bạn/i)
    ).toBeInTheDocument();
  });
});

describe("đối chiếu nghi trùng · vì sao nghi", () => {
  /**
   * Ô "vì sao nghi" đọc `signals`, KHÔNG đọc `hint`.
   *
   * <p>`hint` là câu tự do có thể nhắc tới giá trị trường của bên kia ("trùng
   * năm sinh 1975"), nên nó <b>vắng mặt với cặp `TREE`</b> — bên kia có thể là
   * một người còn sống ở một chi khác. Một màn hình đọc `hint` sẽ để ô ấy trống
   * ở đúng loại cặp mà người đối chiếu <b>không có gì khác</b> để dựa vào, vì
   * bảng bằng chứng cũng rỗng theo bất biến riêng tư.</p>
   */
  it("cặp TREE nói được vì sao nghi, dù không có bảng bằng chứng và không có hint", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_VISIBLE}`);

    const why = within(card).getByTestId("pair-signals");
    expect(why).toHaveTextContent(/Trùng ngày giỗ 15\/8/i);
    // Và đúng là không có gì khác để dựa vào ở cặp này.
    expect(within(card).getByTestId("evidence-withheld")).toBeInTheDocument();
  });

  it("cặp TREE thứ hai cũng có nhãn tín hiệu — không phải may mắn của một cặp", async () => {
    seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_HIDDEN}`);
    expect(within(card).getByTestId("pair-signals")).toHaveTextContent(/Trùng tên huý/i);
  });
});

describe("đối chiếu nghi trùng · quyết định thật sự được ghi", () => {
  it("quyết một cặp làm HAI CON SỐ KẾ HOẠCH đổi — máy chủ tính lại, màn hình chỉ vẽ", async () => {
    const { user } = seedAndRender();

    const plan = await screen.findByTestId("duplicate-plan");
    const createBefore = within(plan).getByTestId("plan-create").textContent!;
    const updateBefore = within(plan).getByTestId("plan-update").textContent!;

    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_VISIBLE}`);
    await user.click(within(card).getByRole("button", { name: /hợp nhất/i }));

    // Gộp một cặp TREE: dòng ấy chuyển từ "thêm mới" sang "cập nhật", nên CẢ
    // HAI con số đổi. Hiện được điều đó là cách duy nhất để người đối chiếu
    // thấy hệ quả của việc mình vừa làm.
    await waitFor(() => {
      expect(within(plan).getByTestId("plan-create").textContent).not.toBe(createBefore);
    });
    expect(within(plan).getByTestId("plan-update").textContent).not.toBe(updateBefore);
  });

  it("sau khi quyết, thẻ hiện AI QUYẾT, LÚC NÀO và ghi chú — không im lặng", async () => {
    const { user } = seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_IN_FILE}`);

    await user.type(
      within(card).getByRole("textbox"),
      "đối chiếu với sổ chi Giáp bản 1998"
    );
    await user.click(within(card).getByRole("button", { name: /hai người khác nhau/i }));

    const decision = await within(card).findByTestId("pair-decision");
    expect(within(decision).getByText(/hai người khác nhau/i)).toBeInTheDocument();
    expect(within(decision).getByText(/sổ chi Giáp bản 1998/i)).toBeInTheDocument();
    // Gộp hai hồ sơ là thao tác có hệ quả vĩnh viễn lên phả, nên nó phải có chủ
    // và có mốc thời gian.
    expect(within(decision).getByText(/đã quyết lúc/i)).toBeInTheDocument();
  });

  it('"HOÃN" không mở khoá cửa duyệt — số cặp chưa quyết KHÔNG giảm', async () => {
    const { user } = seedAndRender();

    const gateBefore = (await screen.findByTestId("duplicate-gate")).textContent;
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_HIDDEN}`);
    await user.click(within(card).getByRole("button", { name: /Chưa rõ — để lại sau/i }));

    // Quyết định ĐƯỢC ghi: thẻ đổi trạng thái.
    const decision = await within(card).findByTestId("pair-decision");
    // Nhưng nhãn nói thẳng rằng nó vẫn tính là chưa quyết.
    expect(within(decision).getByText(/vẫn tính là chưa quyết/i)).toBeInTheDocument();

    // Và cửa duyệt y nguyên. Bù trừ ở client biến "hoãn" thành "cho tôi qua".
    expect(screen.getByTestId("duplicate-gate").textContent).toBe(gateBefore);
    expect(screen.getByTestId("duplicate-gate")).toHaveTextContent(/chưa duyệt được/i);
    expect(screen.getByTestId("plan-undecided")).toHaveTextContent(/3 cặp chưa quyết/i);
  });

  it("quyết xong vẫn đổi lại được, nhưng phải bấm thêm một bậc", async () => {
    const { user } = seedAndRender();
    const card = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_IN_FILE}`);

    await user.click(within(card).getByRole("button", { name: /hai người khác nhau/i }));
    await within(card).findByTestId("pair-decision");
    // Ba nút biến mất: không ai vô tình gộp nhầm khi đang cuộn trang.
    expect(within(card).queryByRole("button", { name: /hợp nhất/i })).not.toBeInTheDocument();

    await user.click(within(card).getByRole("button", { name: /Đổi quyết định/i }));
    expect(within(card).getByRole("button", { name: /hợp nhất/i })).toBeEnabled();
  });

  it("KHÔNG có nút quyết hàng loạt, kể cả sau khi endpoint đã có thật", async () => {
    seedAndRender();
    await screen.findByTestId("duplicate-plan");
    for (const name of [/tất cả/i, /toàn bộ/i]) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
  });
});

describe("đối chiếu nghi trùng · ca từ chối hợp nhất", () => {
  /**
   * Một nhóm gộp dính tới <b>hai người khác nhau đã có trong phả</b>.
   *
   * <p>Người dùng vừa làm một việc hợp lý, nên câu trả lời đúng không phải "sai
   * rồi" mà là <b>"đúng việc, nhầm chỗ"</b>: hợp nhất hai hồ sơ đã nằm trong
   * phả là thao tác của màn quản lý nhân khẩu. Không nói ra ranh giới ấy thì
   * người ta thử lại ba lần rồi kết luận phần mềm hỏng.</p>
   */
  it("nói ra rằng đây là việc của màn quản lý nhân khẩu, không phải của màn nhập liệu", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-da-sua.xlsx" });
    const second = seedConflictingMergePair(batch.id);
    const { user } = renderWithProviders(<DuplicateReviewScreen batchId={batch.id} />, {
      role: "branch-head",
    });

    const first = await screen.findByTestId(`duplicate-pair-${DUP_PAIR_TREE_VISIBLE}`);
    await user.click(within(first).getByRole("button", { name: /hợp nhất/i }));
    await within(first).findByTestId("pair-decision");

    const other = await screen.findByTestId(`duplicate-pair-${second}`);
    await user.click(within(other).getByRole("button", { name: /hợp nhất/i }));

    const notice = await screen.findByTestId("merge-not-applicable", undefined, {
      timeout: 5000,
    });
    expect(within(notice).getByText(/hai người đã có trong phả/i)).toBeInTheDocument();
    // RANH GIỚI — câu quan trọng nhất của cả khối.
    expect(within(notice).getAllByText(/màn quản lý nhân khẩu/i).length).toBeGreaterThan(0);
    // LÀM GÌ TIẾP — không có nó thì hai câu trên chỉ là một lời xin lỗi dài.
    expect(within(notice).getAllByText(/Chọn lại/i).length).toBeGreaterThan(0);
    // Và nói rõ lô đang dừng, chưa có gì vào phả.
    expect(within(notice).getByText(/Chưa có gì được ghi vào phả/i)).toBeInTheDocument();
  });
});

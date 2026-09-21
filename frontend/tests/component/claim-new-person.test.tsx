import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import {
  MOCK_NEW_PERSON_DUPLICATE_NAME,
  pendingClaimFixture,
  rejectedClaimFixture,
  resetClaimMockState,
  seedClaimMockState,
} from "@/mocks/handlers/claim";
import { claimRoutes } from "@/components/claim/routes";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/nhan-dien/chua-co",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/nhan-dien/chua-co",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/nhan-dien/chua-co",
}));

const { ClaimNewPersonScreen } = await import("@/components/claim/claim-new-person-screen");

/**
 * LỐI "TÔI CHƯA CÓ TRONG PHẢ".
 *
 * Đây là lối **ghi vào phả**, không phải một biểu mẫu liên hệ — nó cho một
 * người **chưa được duyệt** khởi tạo việc thêm người vào gia phả. Hai trong năm
 * ràng buộc của checklist §1.5 nhìn thấy được trên màn hình, và cả hai được
 * ghim ở đây:
 *
 *  · **không tạo nhân khẩu lúc gửi đơn** — màn phải NÓI RA điều đó, vì một
 *    người tưởng mình đã vào phả sẽ không gọi cho ai cả và sẽ chờ mãi;
 *  · **bắt buộc chỉ ra người thân đã có trong phả** — và màn phải GIẢI THÍCH
 *    VÌ SAO thay vì gắn một dấu sao, vì đây là chỗ duy nhất trong biểu mẫu mà
 *    một lời từ chối cụt lủn sẽ làm người dùng bỏ cuộc.
 */

function renderNewPerson(locale: "vi" | "en" = "vi") {
  resetRouterMock();
  return renderWithProviders(<ClaimNewPersonScreen />, { role: "member", locale });
}

async function choTaiXong(container: HTMLElement): Promise<void> {
  await waitFor(() =>
    expect(container.querySelector('[data-claim-state="LOADING"]')).toBeNull()
  );
}

beforeEach(() => {
  resetClaimMockState();
});

describe("người thân là mục BẮT BUỘC, và màn phải giải thích vì sao", () => {
  it("chặn việc gửi khi chưa chỉ ra người thân", async () => {
    const { container, user } = renderNewPerson();
    await choTaiXong(container);

    await user.type(await screen.findByLabelText("Họ và tên"), "Trần Thị Lan");
    await user.type(screen.getByLabelText("Năm sinh"), "1996");
    await user.type(screen.getByLabelText("Số điện thoại"), "0903111222");
    await user.type(
      screen.getByLabelText("Vài dòng tự giới thiệu"),
      "Cháu là con dâu nhà bác Cẩn, mới về làm dâu tháng trước, quê Nam Định."
    );
    // Cố ý KHÔNG chọn người thân.
    await user.click(screen.getByRole("button", { name: /Gửi đơn cho Trưởng chi/ }));

    const loi = await screen.findAllByRole("alert");
    const chu = loi.map((n) => n.textContent ?? "").join(" ");
    expect(chu).toContain("bố, mẹ, hoặc vợ/chồng");
    expect(routerMock.replace).not.toHaveBeenCalled();
  });

  it("câu báo lỗi nói HỆ QUẢ, không nói 'trường bắt buộc'", async () => {
    const { container, user } = renderNewPerson();
    await choTaiXong(container);

    await user.click(await screen.findByRole("button", { name: /Gửi đơn cho Trưởng chi/ }));

    const loi = await screen.findAllByRole("alert");
    const chu = loi.map((n) => n.textContent ?? "").join(" ");
    expect(chu).toContain("không nối được vào cây");
    expect(chu).not.toMatch(/trường bắt buộc/i);
    expect(chu).not.toMatch(/required/i);
  });

  it("giải thích vì sao BẮT BUỘC, ngay cạnh ô nhập, trước khi người dùng sai", async () => {
    const { container } = renderNewPerson();
    await choTaiXong(container);

    expect(await screen.findByText("Vì sao bắt buộc phải có")).toBeInTheDocument();
    const chu = container.textContent ?? "";
    // Lý do thật, bằng ngôn ngữ của dòng họ: không tính được đời, không tra
    // được danh xưng, không chi nào biết đơn là của mình.
    expect(chu).toContain("không tính được đời");
    expect(chu).toContain("danh xưng");
  });

  it("chỉ nhận ba quan hệ đã chốt — bố, mẹ, vợ/chồng", async () => {
    const { container } = renderNewPerson();
    await choTaiXong(container);

    expect(await screen.findByText("Bố của tôi")).toBeInTheDocument();
    expect(screen.getByText("Mẹ của tôi")).toBeInTheDocument();
    expect(screen.getByText("Vợ / chồng của tôi")).toBeInTheDocument();
    // Ông/bà, anh/chị, cô/dì không có ở đây: mỗi giá trị thêm vào là một cạnh
    // nữa mà bước duyệt phải biết cách ghi.
    expect(screen.queryByText(/Ông của tôi/)).not.toBeInTheDocument();
  });
});

describe("nói rõ rằng NHÂN KHẨU CHƯA ĐƯỢC TẠO", () => {
  it("khối ấy đứng TRƯỚC biểu mẫu, không phải sau nút Gửi", async () => {
    const { container } = renderNewPerson();
    await choTaiXong(container);

    const khoi = container.querySelector<HTMLElement>('[data-claim-state="NOT_CREATED_YET"]');
    expect(khoi).not.toBeNull();
    expect(khoi?.textContent).toContain("chưa tạo hồ sơ nào");
    expect(khoi?.textContent).toContain("Trưởng chi duyệt");

    // Đứng trước biểu mẫu trong thứ tự DOM — tức người dùng đọc nó trước khi gõ.
    const form = container.querySelector("form");
    expect(form).not.toBeNull();
    expect(khoi?.compareDocumentPosition(form as Node)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING
    );
  });

  it("vẽ bằng sắc hổ phách — đây là quy trình đang chạy, không phải sự cố", async () => {
    const { container } = renderNewPerson();
    await choTaiXong(container);

    const khoi = container.querySelector<HTMLElement>('[data-claim-state="NOT_CREATED_YET"]');
    expect(khoi?.style.background).toBe("var(--color-warning-bg)");
  });
});

describe("gọi tên ba tình huống có thật mà lối này sinh ra để giải", () => {
  it("con dâu mới về · cháu mới sinh · nhánh ở xa nhiều đời", async () => {
    const { container } = renderNewPerson();
    await choTaiXong(container);

    const chu = container.textContent ?? "";
    expect(chu).toContain("Con dâu, con rể mới về");
    expect(chu).toContain("Cháu mới sinh");
    expect(chu).toContain("Nhánh ở xa nhiều đời");
  });
});

describe("lối này KHÔNG phải cửa sau đi vòng qua giới hạn số lần", () => {
  it("đang có một đơn mở thì cũng chặn ở đây", async () => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });

    const { container } = renderNewPerson();
    await choTaiXong(container);

    expect(await screen.findByText("Ông/bà đã gửi một đơn rồi")).toBeInTheDocument();
    expect(screen.queryByLabelText("Họ và tên")).not.toBeInTheDocument();
  });

  it("hết lượt thì cũng chặn ở đây", async () => {
    seedClaimMockState({
      claims: [
        rejectedClaimFixture(),
        rejectedClaimFixture({ id: "claim-tu-choi-2" }),
        rejectedClaimFixture({ id: "claim-tu-choi-3" }),
      ],
    });

    const { container } = renderNewPerson();
    await choTaiXong(container);

    expect(
      await screen.findByText("Ông/bà đã dùng hết số lần gửi đơn")
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Họ và tên")).not.toBeInTheDocument();
  });
});

/**
 * Chọn "Nguyễn Văn Hiển" (`p-010`, `MOCK_CLAIM_RELATIVE_OK`) làm người thân —
 * một tổ tiên có thật, có chi ("Chi Nhất"), nên đơn qua được phép kiểm
 * `CLAIM_RELATIVE_UNUSABLE` mà không cần biết trước ai khác sẽ ra trong kết
 * quả tìm. Gõ không dấu, đúng cách tìm không dấu ở máy chủ.
 */
async function chonNguoiThan(user: ReturnType<typeof renderNewPerson>["user"]): Promise<void> {
  // AntD `Select` không đặt placeholder làm thuộc tính DOM thật (nó vẽ bằng một
  // `<span>` riêng) — cùng cách `membership-invite.test.tsx` đã chọn ô này.
  const box = screen.getByRole("combobox");
  await user.click(box);
  await user.type(box, "Nguyen Van Hien");
  const option = await waitFor(
    () => {
      const nodes = Array.from(
        document.querySelectorAll(".ant-select-item-option")
      ) as HTMLElement[];
      if (nodes.length === 0) throw new Error("chưa có kết quả tìm kiếm");
      return nodes[0]!;
    },
    { timeout: 15_000 }
  );
  await user.click(option);
}

/** Điền đủ một lá đơn hợp lệ (trừ những trường ghi đè) rồi bấm gửi. */
async function dienDayDuVaGui(
  user: ReturnType<typeof renderNewPerson>["user"],
  overrides: { fullName?: string; phone?: string; introduction?: string } = {}
): Promise<void> {
  await user.type(await screen.findByLabelText("Họ và tên"), overrides.fullName ?? "Trần Thị Mai");
  await user.type(screen.getByLabelText("Năm sinh"), "1996");
  await chonNguoiThan(user);
  await user.click(screen.getByText("Bố của tôi"));
  await user.type(
    screen.getByLabelText("Số điện thoại"),
    overrides.phone ?? "0903111222"
  );
  await user.type(
    screen.getByLabelText("Vài dòng tự giới thiệu"),
    overrides.introduction ??
      "Cháu là con dâu nhà bác Cẩn, mới về làm dâu tháng trước, quê Nam Định."
  );
  await user.click(screen.getByRole("button", { name: /Gửi đơn cho Trưởng chi/ }));
}

describe("`VALIDATION_FAILED` trần: hiện lý do THẬT, không phải 'thử lại sau'", () => {
  it("có `detail` → hiện đúng câu máy chủ nói, ở lại trong biểu mẫu", async () => {
    const { container, user } = renderNewPerson();
    await choTaiXong(container);

    // 40 chữ số vượt trần 32 ký tự của `SubmitNewPersonClaimRequest.phone` —
    // biểu mẫu KHÔNG chặn trước (không có `maxLength` phía client), nên lượt
    // gửi này chạm thật tới máy chủ (ở đây là bộ giả lập chép đúng luật ấy).
    await dienDayDuVaGui(user, { phone: "0".repeat(40) });

    const loi = await screen.findByText(/vượt quá 32 ký tự/);
    expect(loi.getAttribute("role")).toBe("alert");

    // KHÔNG đổi cả màn: đây là lỗi hình dạng dữ liệu, người dùng sửa được ngay
    // tại ô — không phải một luật nghiệp vụ về ô đã chọn hay người thân.
    expect(screen.getByLabelText("Họ và tên")).toBeInTheDocument();
    // Và KHÔNG phải câu "thử lại sau" — đó chính là lời nói dối đợt này sửa.
    expect(screen.queryByText(/thử lại/i)).not.toBeInTheDocument();
    expect(routerMock.replace).not.toHaveBeenCalled();
  });

  it("không có `detail` → rơi về câu chung, KHÔNG bịa một câu mới", async () => {
    server.use(
      http.post(`${API_BASE_URL}/api/v1/person-claims/new-person`, () =>
        HttpResponse.json(
          { type: "about:blank", title: "...", status: 400, code: "VALIDATION_FAILED" },
          { status: 400 }
        )
      )
    );

    const { container, user } = renderNewPerson();
    await choTaiXong(container);
    await dienDayDuVaGui(user);

    // Câu dự phòng là ĐÚNG câu `block.unavailable.body` đã có sẵn — không viết
    // câu thứ hai cho cùng một tình huống "máy chủ không nói được vì sao".
    expect(
      await screen.findByText(
        "Đường truyền tới máy chủ đang trục trặc. Đơn của ông/bà chưa bị mất gì cả — xin thử lại."
      )
    ).toBeInTheDocument();
    expect(routerMock.replace).not.toHaveBeenCalled();
  });
});

describe("nghi trùng sau khi gửi: hiện SỐ LƯỢNG, KHÔNG hiện DANH TÍNH", () => {
  it("gửi thành công và có nghi vấn → dừng lại, nói số lượng, mở lối quay lại phả đồ", async () => {
    const { container, user } = renderNewPerson();
    await choTaiXong(container);

    await dienDayDuVaGui(user, { fullName: MOCK_NEW_PERSON_DUPLICATE_NAME });

    expect(
      await screen.findByText(/có thể ông\/bà đã có trong phả/)
    ).toBeInTheDocument();
    expect(container.textContent).toContain("2 hồ sơ trong phả có thể chính là ông/bà");

    // KHÔNG tự động sang màn "đang chờ duyệt" — người dùng cần đọc câu này
    // trước, bằng đúng một cú bấm.
    expect(routerMock.replace).not.toHaveBeenCalled();

    expect(
      screen.getByRole("link", { name: "Quay lại phả đồ để chọn đúng ô" })
    ).toHaveAttribute("href", "/tree");

    await user.click(screen.getByRole("button", { name: "Xem đơn đang chờ duyệt" }));
    expect(routerMock.replace).toHaveBeenCalledWith(claimRoutes.pending);
  });

  it("không một tên, năm sinh, đời hay tên chi nào của nghi phạm lọt ra DOM", async () => {
    const { container, user } = renderNewPerson();
    await choTaiXong(container);

    await dienDayDuVaGui(user, { fullName: MOCK_NEW_PERSON_DUPLICATE_NAME });
    await screen.findByText(/có thể ông\/bà đã có trong phả/);

    const chu = container.textContent ?? "";
    // Hai nghi phạm giả lập là `MOCK_CLAIM_TARGET_OK` (p-100) và
    // `MOCK_CLAIM_RELATIVE_OK` (p-010) — cả hai đều có tên và chi thật trong đồ
    // thị giả lập. Không cái nào trong số đó được phép xuất hiện ở đây.
    expect(chu).not.toContain("Chi Nhất");
    expect(chu).not.toMatch(/\b19\d{2}\b/); // không năm sinh bốn chữ số
    expect(chu).not.toContain("p-100");
    expect(chu).not.toContain("p-010");
  });

  it("gửi thành công và KHÔNG ai bị nghi thì đi thẳng sang màn đang chờ duyệt, như trước", async () => {
    const { container, user } = renderNewPerson();
    await choTaiXong(container);

    await dienDayDuVaGui(user);

    await waitFor(() => expect(routerMock.replace).toHaveBeenCalledWith(claimRoutes.pending));
  });
});

describe("song ngữ", () => {
  it("bản tiếng Anh giữ nguyên thuật ngữ dòng họ và không thiếu khoá dịch", async () => {
    const { container } = renderNewPerson("en");
    await choTaiXong(container);

    expect(await screen.findByText("Why this one is required")).toBeInTheDocument();
    const chu = container.textContent ?? "";
    expect(chu).not.toMatch(/MISSING_MESSAGE/);
    // Thuật ngữ giữ nguyên tiếng Việt kèm giải thích ngắn (00 §2.6): "uncle"
    // xoá mất phân biệt chú/bác/cậu, và "family tree" xoá mất phả đồ.
    expect(chu).toContain("phả");
    expect(chu).toContain("danh xưng");
  });
});

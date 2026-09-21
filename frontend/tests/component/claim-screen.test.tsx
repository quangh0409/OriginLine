import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import {
  MOCK_CLAIM_TARGET_DECEASED,
  MOCK_CLAIM_TARGET_OK,
  MOCK_CLAIM_TARGET_UNAVAILABLE,
  pendingClaimFixture,
  rejectedClaimFixture,
  resetClaimMockState,
  seedClaimMockState,
} from "@/mocks/handlers/claim";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/nhan-dien",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/nhan-dien",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/nhan-dien",
}));

const { ClaimScreen } = await import("@/components/claim/claim-screen");

/**
 * MÀN "TÔI LÀ AI TRONG PHẢ".
 *
 * Bốn ca của checklist §1.4 được kiểm ở đây, và điều quan trọng nhất về cả bốn
 * là: **không ca nào là sự cố**. Chúng là bốn câu trả lời bình thường của một
 * hệ thống đang chạy đúng, nên mỗi ca phải có câu chữ riêng và một lối đi tiếp.
 *
 * Bốn ca ấy chặn ở **hai thời điểm khác nhau**, và ranh giới giữa hai thời điểm
 * chính là thiết kế riêng tư của màn:
 *
 *  · *đã khuất* chặn **trước** khi dựng biểu mẫu, đọc từ `isAlive` — hồ sơ
 *    người đã khuất vốn công khai nên không lộ thêm gì;
 *  · *đã gửi đơn rồi* và *hết lượt* chặn trước, đọc từ `/claims/mine` — cả hai
 *    nói về chính tài khoản đang gọi;
 *  · *ô đã có người nhận* **chỉ lộ ra khi gửi**, vì một lối hỏi trước trả lời
 *    được câu ấy chính là công cụ dò xem ai đã vào hệ thống. Ca kiểm tương ứng
 *    không khẳng định "có hiện đúng câu này" — nó khẳng định **chuỗi "tài
 *    khoản" không xuất hiện ở bất kỳ đâu trên màn**, vì đó mới là thứ hỏng được
 *    bởi một lần "cải thiện câu chữ cho rõ hơn".
 */

function renderClaim(personId: string | null, locale: "vi" | "en" = "vi") {
  searchParams = new URLSearchParams(personId === null ? "" : `nguoi=${personId}`);
  resetRouterMock();
  return renderWithProviders(<ClaimScreen />, { role: "member", locale });
}

/** Chờ tới khi màn hình rời khỏi trạng thái đang tải. */
async function choTaiXong(container: HTMLElement): Promise<void> {
  await waitFor(() =>
    expect(container.querySelector('[data-claim-state="LOADING"]')).toBeNull()
  );
}

/** Điền đủ một lá đơn hợp lệ rồi bấm gửi. */
async function dienVaGui(user: ReturnType<typeof renderClaim>["user"]): Promise<void> {
  await user.type(await screen.findByLabelText("Số điện thoại"), "0903111222");
  await user.type(
    screen.getByLabelText("Vài dòng tự giới thiệu"),
    "Cháu là con thứ hai của ông Cẩn và bà Lan, quê Đại Lan, Nam Định."
  );
  await user.click(screen.getByRole("button", { name: /Gửi đơn cho Trưởng chi/ }));
}

beforeEach(() => {
  resetClaimMockState();
});

describe("ca 1 · ô đã có người nhận — nói chung chung, KHÔNG nói 'đã có tài khoản'", () => {
  it("không chặn trước: biểu mẫu vẫn dựng, vì một lối hỏi trước là một bề mặt dò", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_UNAVAILABLE);
    await choTaiXong(container);

    // Đây là CÁI GIÁ đã được cân nhắc và chấp nhận ở máy chủ: người ngay tình
    // gõ xong rồi mới bị từ chối. Đổi lại, không ai đọc được danh sách người đã
    // vào hệ thống bằng cách bấm lần lượt từng ô trên phả đồ.
    expect(await screen.findByLabelText("Vài dòng tự giới thiệu")).toBeInTheDocument();
  });

  it("chặn lúc gửi bằng một câu chung chung và mở hai lối đi tiếp", async () => {
    const { container, user } = renderClaim(MOCK_CLAIM_TARGET_UNAVAILABLE);
    await choTaiXong(container);
    await dienVaGui(user);

    expect(
      await screen.findByText("Không gửi đơn cho người này được")
    ).toBeInTheDocument();
    expect(
      container.querySelector('[data-claim-state="PERSON_UNAVAILABLE"]')
    ).not.toBeNull();

    // Hai lối đi tiếp, vì rất nhiều người bấm nhầm một ô gần đúng CHÍNH VÌ họ
    // không có trong phả (checklist §1.5).
    expect(screen.getByRole("link", { name: /phả đồ/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tôi chưa có trong phả" })).toBeInTheDocument();
    expect(routerMock.replace).not.toHaveBeenCalled();
  });

  it("KHÔNG để lộ rằng người ấy đã có tài khoản — ràng buộc riêng tư, không phải câu chữ", async () => {
    const { container, user } = renderClaim(MOCK_CLAIM_TARGET_UNAVAILABLE);
    await choTaiXong(container);
    await dienVaGui(user);
    await screen.findByText("Không gửi đơn cho người này được");

    const chu = container.textContent ?? "";
    // Bốn cách nói cùng một điều. Nếu một trong bốn lọt ra thì màn này thành
    // cách dò danh sách người đã vào hệ thống.
    expect(chu).not.toMatch(/tài khoản/i);
    expect(chu).not.toMatch(/đã đăng ký/i);
    expect(chu).not.toMatch(/đã nhận/i);
    expect(chu).not.toMatch(/người khác/i);
  });

  it("thay cả biểu mẫu bằng câu trả lời mới, không chỉ thêm một dòng đỏ", async () => {
    const { container, user } = renderClaim(MOCK_CLAIM_TARGET_UNAVAILABLE);
    await choTaiXong(container);
    await dienVaGui(user);
    await screen.findByText("Không gửi đơn cho người này được");

    // Người dùng cần biết trạng thái MỚI, không chỉ biết "bấm không được".
    expect(screen.queryByLabelText("Vài dòng tự giới thiệu")).not.toBeInTheDocument();
  });
});

describe("ca 2 · ô của người đã khuất — chặn NGAY LÚC CHỌN", () => {
  it("chặn trước khi dựng biểu mẫu, và nêu đích danh người đã khuất", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_DECEASED);
    await choTaiXong(container);

    expect(
      await screen.findByText("Ô này là của một người đã khuất")
    ).toBeInTheDocument();
    expect(container.querySelector('[data-claim-state="PERSON_DECEASED"]')).not.toBeNull();

    // Nêu tên là CỐ Ý và không mở thêm cửa nào: hồ sơ người đã khuất vốn công
    // khai với cả khách, nên chặn ở bước này không lộ thêm gì (checklist §1.4).
    expect(container.textContent).toContain("Nguyễn Văn Thủy Tổ");
    // KHÔNG có biểu mẫu: không ai gõ xong một bài tự giới thiệu rồi mới biết
    // mình vừa nhận mình là một cụ tổ.
    expect(screen.queryByLabelText("Số điện thoại")).not.toBeInTheDocument();
  });

  it("vẽ ca này bằng sắc hổ phách, không bằng sắc đỏ — đây là luật chạy đúng", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_DECEASED);
    await choTaiXong(container);
    await screen.findByText("Ô này là của một người đã khuất");

    const khoi = container.querySelector<HTMLElement>('[data-claim-state="PERSON_DECEASED"]');
    expect(khoi).not.toBeNull();
    // Màu đi qua biến CSS, không bao giờ qua hex — nên phép kiểm soi đúng tên
    // biến chứ không soi giá trị màu (giá trị đổi theo chế độ sáng/tối).
    expect(khoi?.style.background).toBe("var(--color-warning-bg)");
  });
});

describe("ca 3 · đã gửi đơn rồi — hiện trạng thái, không cho gửi trùng", () => {
  beforeEach(() => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });
  });

  it("chặn mọi ô khác, kể cả một ô hoàn toàn hợp lệ", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);

    expect(await screen.findByText("Ông/bà đã gửi một đơn rồi")).toBeInTheDocument();
    expect(
      container.querySelector('[data-claim-state="CLAIM_ALREADY_PENDING"]')
    ).not.toBeNull();
    expect(screen.queryByLabelText("Vài dòng tự giới thiệu")).not.toBeInTheDocument();
  });

  it("dẫn sang màn đang chờ duyệt — không có lối đó thì họ gửi lại lần hai, lần ba", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);
    await screen.findByText("Ông/bà đã gửi một đơn rồi");

    expect(screen.getByRole("link", { name: "Xem đơn đang chờ duyệt" })).toHaveAttribute(
      "href",
      "/nhan-dien/cho-duyet"
    );
  });

  it("màn mở đầu (chưa chọn ai) cũng nói ngay rằng đã có đơn đang chờ", async () => {
    const { container } = renderClaim(null);

    await waitFor(() =>
      expect(container.querySelector('[data-claim-state="HAS_PENDING"]')).not.toBeNull()
    );
    expect(screen.getByRole("link", { name: "Xem đơn đang chờ duyệt" })).toBeInTheDocument();
  });
});

describe("ca 4 · bị từ chối rồi — gửi lại được, và phải nói rõ CÒN MẤY LẦN", () => {
  it("vẫn dựng biểu mẫu, và nói số lần còn lại TRƯỚC khi người dùng gõ", async () => {
    seedClaimMockState({ claims: [rejectedClaimFixture()] });
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);

    // Biểu mẫu có mặt: từ chối không phải là cấm.
    expect(await screen.findByLabelText("Vài dòng tự giới thiệu")).toBeInTheDocument();

    // Và bộ đếm nói thành lời. Biết sau khi đã viết xong là biết quá muộn.
    const dong = container.querySelector<HTMLElement>("[data-claim-quota]");
    expect(dong).not.toBeNull();
    expect(dong?.getAttribute("data-claim-quota")).toBe("2");
    expect(dong?.textContent).toContain("còn 2 lần");
  });

  it("đổi hẳn câu chữ ở lần cuối cùng, không chỉ đổi con số", async () => {
    seedClaimMockState({
      claims: [rejectedClaimFixture(), rejectedClaimFixture({ id: "claim-tu-choi-2" })],
    });
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);
    await screen.findByLabelText("Vài dòng tự giới thiệu");

    const dong = container.querySelector<HTMLElement>("[data-claim-quota]");
    expect(dong?.getAttribute("data-claim-quota")).toBe("1");
    expect(dong?.textContent).toContain("lần gửi đơn cuối cùng");
  });

  it("KHÔNG hiện bộ đếm với người chưa bị từ chối lần nào", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);
    await screen.findByLabelText("Vài dòng tự giới thiệu");

    // "Ông/bà còn 3 lần" chào một người chưa làm gì sai thì đọc như lời cảnh cáo.
    expect(container.querySelector("[data-claim-quota]")).toBeNull();
  });

  it("hết lượt thì chặn hẳn, và KHÔNG bịa ra một lối đi tiếp trong ứng dụng", async () => {
    seedClaimMockState({
      claims: [
        rejectedClaimFixture(),
        rejectedClaimFixture({ id: "claim-tu-choi-2" }),
        rejectedClaimFixture({ id: "claim-tu-choi-3" }),
      ],
    });
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);

    expect(
      await screen.findByText("Ông/bà đã dùng hết số lần gửi đơn")
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Vài dòng tự giới thiệu")).not.toBeInTheDocument();
    // Việc cần làm là gọi điện cho một con người. Một nút dẫn về phả đồ ở đây
    // chỉ mời họ thử tiếp một việc chắc chắn hỏng.
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("đơn ĐÃ RÚT không tiêu lượt nào", async () => {
    seedClaimMockState({
      claims: [
        rejectedClaimFixture({ id: "claim-rut-1", status: "CANCELLED", reviewNote: null }),
        rejectedClaimFixture({ id: "claim-rut-2", status: "CANCELLED", reviewNote: null }),
        rejectedClaimFixture({ id: "claim-rut-3", status: "CANCELLED", reviewNote: null }),
      ],
    });
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);

    // Bộ đếm của máy chủ đếm đơn BỊ TỪ CHỐI. Ba đơn tự rút không chạm vào nó —
    // nếu chạm thì lời khuyên "hãy rút đơn ấy trước khi gửi đơn khác" trở thành
    // một cái bẫy.
    expect(await screen.findByLabelText("Vài dòng tự giới thiệu")).toBeInTheDocument();
    expect(container.querySelector("[data-claim-quota]")).toBeNull();
  });
});

describe("lối đi thuận", () => {
  it("chiếu lại ô đã chọn trước khi cho gõ — bắt cú chạm trượt sang ô trùng tên", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);
    await screen.findByLabelText("Vài dòng tự giới thiệu");

    const the = container.querySelector<HTMLElement>(
      `[data-claim-target="${MOCK_CLAIM_TARGET_OK}"]`
    );
    expect(the).not.toBeNull();
    expect(the?.textContent).toContain("Nguyễn Văn An");
  });

  it("gửi xong thì đi thẳng sang màn chờ duyệt, bằng replace chứ không push", async () => {
    const { container, user } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);
    await dienVaGui(user);

    await waitFor(() => expect(routerMock.replace).toHaveBeenCalledWith("/nhan-dien/cho-duyet"));
    // `push` sẽ để nút Quay lại dẫn ngược về một biểu mẫu đã gửi rồi.
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("không gửi khi phần tự giới thiệu quá ngắn — và câu báo lỗi nói VIẾT THÊM GÌ", async () => {
    const { container, user } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);

    await user.type(await screen.findByLabelText("Số điện thoại"), "0903111222");
    await user.type(screen.getByLabelText("Vài dòng tự giới thiệu"), "Cháu đây ạ");
    await user.click(screen.getByRole("button", { name: /Gửi đơn cho Trưởng chi/ }));

    const loi = await screen.findByRole("alert");
    expect(loi.textContent).toContain("con ông nào, bà nào, quê quán");
    // "Tối thiểu 30 ký tự" là ngôn ngữ của phần mềm, không phải của dòng họ.
    expect(loi.textContent).not.toMatch(/ký tự/);
    expect(routerMock.replace).not.toHaveBeenCalled();
  });
});

describe("`VALIDATION_FAILED` trần ở `/person-claims/existing`: hiện lý do THẬT", () => {
  it("điện thoại vượt 32 ký tự → hiện đúng câu máy chủ nói, không phải 'thử lại sau'", async () => {
    const { container, user } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);

    // Biểu mẫu KHÔNG chặn số điện thoại dài ở client (không `maxLength`) — thứ
    // duy nhất phán quyết là máy chủ, và đây là chỗ nó phán quyết ĐÚNG (400
    // `VALIDATION_FAILED`, kèm `detail` cụ thể), không phải một lời từ chối
    // nghiệp vụ về ô đã chọn.
    await user.type(screen.getByLabelText("Số điện thoại"), "0".repeat(40));
    await user.type(
      screen.getByLabelText("Vài dòng tự giới thiệu"),
      "Cháu là con thứ hai của ông Cẩn và bà Lan, quê Đại Lan, Nam Định."
    );
    await user.click(screen.getByRole("button", { name: /Gửi đơn cho Trưởng chi/ }));

    expect(await screen.findByText(/vượt quá 32 ký tự/)).toBeInTheDocument();
    // KHÔNG đổi cả màn — người dùng sửa được ngay tại ô, và những gì đã gõ
    // (đặc biệt là phần tự giới thiệu) phải còn nguyên.
    expect(screen.getByLabelText("Vài dòng tự giới thiệu")).toBeInTheDocument();
    expect(screen.queryByText(/thử lại/i)).not.toBeInTheDocument();
    expect(routerMock.replace).not.toHaveBeenCalled();
  });
});

describe("sàn tiếp cận", () => {
  /**
   * Bốn trạng thái của nhánh "đã chọn một ô", và cả bốn phải có **đúng một**
   * `h1`. Trước khi có khung chung thì ba trong bốn không có `h1` nào — tấm thẻ
   * ô đã chọn, khối từ chối và khối biểu mẫu đều là `h2`. Người dùng trình đọc
   * màn hình định vị bằng tiêu đề trước tiên, nên đó không phải lỗi thẩm mỹ.
   */
  it.each([
    ["ô hợp lệ", MOCK_CLAIM_TARGET_OK],
    ["ô của người đã khuất", MOCK_CLAIM_TARGET_DECEASED],
  ])("đúng một <h1> ở trạng thái: %s", async (_ten, id) => {
    const { container } = renderClaim(id);
    await choTaiXong(container);
    await waitFor(() =>
      expect(container.querySelectorAll("h1").length).toBe(1)
    );
  });

  it("đúng một <h1> ở màn mở đầu", async () => {
    const { container } = renderClaim(null);
    await waitFor(() => expect(container.querySelectorAll("h1").length).toBe(1));
  });

  it("mọi điều khiển mang hành động đều cao ít nhất 44px và có nhãn CHỮ", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_OK);
    await choTaiXong(container);
    await screen.findByLabelText("Vài dòng tự giới thiệu");

    const dieuKhien = [
      ...Array.from(container.querySelectorAll("a")),
      ...Array.from(container.querySelectorAll("button")),
    ];
    expect(dieuKhien.length).toBeGreaterThan(0);
    for (const el of dieuKhien) {
      // jsdom không bố cục nên không đo được chiều cao thật; kiểm ĐÚNG thứ
      // quyết định nó — lớp sàn dùng chung — thay vì đo một số luôn bằng 0.
      expect(el.className).toContain("min-h-[44px]");
      // Biểu tượng KHÔNG BAO GIỜ đứng một mình: nhãn chữ luôn có (00 §2.3).
      expect((el.textContent ?? "").trim().length).toBeGreaterThan(1);
    }
  });
});

describe("song ngữ", () => {
  it("bản tiếng Anh không để lọt khoá dịch thiếu", async () => {
    const { container } = renderClaim(MOCK_CLAIM_TARGET_DECEASED, "en");
    await choTaiXong(container);

    expect(
      await screen.findByText("This node belongs to someone who has died")
    ).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/MISSING_MESSAGE/);
  });
});

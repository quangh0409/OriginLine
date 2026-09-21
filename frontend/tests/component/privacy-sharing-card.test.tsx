import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import { resetPrivacySettingsStore } from "@/mocks/privacy-settings";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-102",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-102",
}));

const { PersonProfile } = await import("@/components/person/person-profile");

/**
 * MÀN "CHÍNH CHỦ TỰ ĐẶT MỨC CHIA SẺ".
 *
 * Ba điều được ghim ở đây, theo đúng thứ tự quan trọng:
 *
 *  1. **Khối này chỉ tồn tại với chính chủ.** Không phải "hiện mờ", không phải
 *     "hiện chỉ đọc" — biến mất hoàn toàn. Một bảng công tắc chỉ-đọc trên hồ sơ
 *     người khác sẽ tiết lộ chính xác mức chia sẻ của họ, tức là tiết lộ đúng
 *     thứ mà mức "Riêng tư" sinh ra để giữ.
 *  2. **Không câu nào đếm số trường.** "Bạn đang ẩn 3 mục" là kiểu rò rỉ đã
 *     được nêu đích danh trong thiết kế: đếm đã là tiết lộ. Ở khối này người
 *     đọc là chủ thể nên phép đếm không hại ai, nhưng câu chữ vẫn không được
 *     tập cho người dùng thói quen đọc-số-để-suy-ra-dữ-liệu, vì cùng một thói
 *     quen ấy sẽ được áp lên hồ sơ người khác.
 *  3. **Lời hứa về khách phải nằm trên màn hình**, không phải trong trang trợ
 *     giúp — nó là câu trả lời cho nỗi lo đầu tiên của người sắp điền.
 */

const SELF_ID = "p-102"; // hồ sơ của tài khoản vai "member"
const OTHER_ID = "p-100";

type Role = "guest" | "member" | "branch-head" | "admin";

async function renderProfile(personId: string, role: Role, locale: "vi" | "en" = "vi") {
  const view = renderWithProviders(<PersonProfile personId={personId} />, { role, locale });
  await waitFor(() => expect(view.container.querySelector(".ant-skeleton")).toBeNull(), {
    timeout: 8000,
  });
  return view;
}

function card(container: HTMLElement): HTMLElement | null {
  return container.querySelector('[data-privacy-sharing="self"]');
}

/**
 * Nhãn bọc ngoài của một nút chọn mức.
 *
 * `Radio.Button` của Ant Design đặt ô `<input type=radio>` thật ở
 * `pointer-events: none` rồi vẽ một cái nhãn đè lên — đúng khuôn mẫu radio
 * chuẩn của thư viện, và ô thật vẫn nhận được tiêu điểm bàn phím nên trình đọc
 * màn hình không mất gì. Nhưng người dùng chuột/ngón tay chạm vào NHÃN, nên
 * ca kiểm cũng phải chạm vào nhãn; gọi thẳng vào ô ẩn sẽ là một phép thử không
 * tương ứng với thao tác nào có thật.
 */
function muc(group: HTMLElement, label: RegExp): HTMLElement {
  const radio = within(group).getByRole("radio", { name: label });
  return radio.closest("label") as HTMLElement;
}

function nhom(block: HTMLElement, heading: string): HTMLElement {
  return within(block)
    .getByRole("heading", { name: heading })
    .closest("[role='group']") as HTMLElement;
}

beforeEach(() => {
  resetPrivacySettingsStore();
});

describe("khối chỉ hiện với chính chủ", () => {
  it("chính chủ thấy đủ sáu nhóm, mỗi nhóm ba mức", async () => {
    const { container } = await renderProfile(SELF_ID, "member");

    const block = card(container);
    expect(block).not.toBeNull();

    for (const label of [
      "Nghề nghiệp & nơi làm việc",
      "Nơi ở (tỉnh/thành)",
      "Địa chỉ đầy đủ",
      "Liên hệ: điện thoại · thư điện tử · Zalo",
      "Ngày sinh đầy đủ & ảnh",
      // Nhóm thứ sáu (V17) — trước bản vá này giao diện chỉ khai năm nhóm gốc,
      // nên KHÔNG có công tắc nào để chính chủ mở vinh danh của mình cho cả
      // họ xem, dù backend đã lọc `HonourDto.personDisplayName` theo đúng
      // nhóm này từ trước.
      "Vinh danh: đỗ đạt · chức tước · thành tích · khen thưởng",
    ]) {
      expect(within(block!).getByRole("heading", { name: label })).toBeInTheDocument();
    }

    // Ba mức × sáu nhóm = 18 nút chọn, và chúng là radio thật chứ không phải
    // nhãn tô màu — trình đọc màn hình phải đọc được trạng thái đã chọn.
    expect(within(block!).getAllByRole("radio")).toHaveLength(18);
  });

  it("người KHÁC mở cùng hồ sơ thì không có khối nào, kể cả Quản trị", async () => {
    for (const role of ["member", "branch-head", "admin"] as const) {
      const { container, unmount } = await renderProfile(OTHER_ID, role);
      expect(card(container), `vai ${role} không được thấy khối này`).toBeNull();
      unmount();
    }
  });

  it("khách không tới được hồ sơ người còn sống, nên cũng không có khối nào", async () => {
    const { container } = await renderProfile(OTHER_ID, "guest");
    expect(await screen.findByText("Không tìm thấy nhân khẩu này.")).toBeInTheDocument();
    expect(card(container)).toBeNull();
  });

  it("hồ sơ người đã khuất không có mức nào để đặt", async () => {
    const { container } = await renderProfile("p-001", "admin");
    expect(card(container)).toBeNull();
  });
});

describe("câu chữ không rò rỉ số lượng", () => {
  it("không có câu nào đếm số trường đang ẩn hay đang mở", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const text = card(container)?.textContent ?? "";

    // Mọi biến thể của "đang ẩn N mục" / "N trường".
    expect(text).not.toMatch(/\d+\s*(mục|trường|thông tin|field)/i);
    expect(text).not.toMatch(/(ẩn|giấu|hidden)\s*\d+/i);
    expect(text).not.toContain("MISSING_MESSAGE");
  });

  it("bảng xem thử nói bằng TÊN NHÓM, không bằng con số", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const preview = within(card(container)!).getByRole("heading", {
      name: "Bảng tóm tắt: ai xem được gì",
    }).parentElement!;

    expect(preview.textContent).not.toMatch(/\d/);
  });
});

describe("lời hứa về khách nằm ngay trên màn hình", () => {
  it("nói rõ người chưa đăng nhập không thấy gì, kể cả tên", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const text = card(container)?.textContent ?? "";

    expect(text).toContain("người chưa đăng nhập");
    expect(text).toContain("kể cả tên");
  });

  it("dòng đầu bảng xem thử luôn là Khách, và luôn là 'không thấy gì'", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const block = card(container)!;

    const guestRow = within(block).getByText("Người chưa đăng nhập").parentElement!;
    expect(guestRow.textContent).toContain("Không thấy gì về bạn, kể cả tên.");
  });
});

describe("cái giá của mức Riêng tư được nói TRƯỚC khi chọn", () => {
  it('lời giải thích mức Riêng tư nêu đích danh Hội đồng, không nói "chỉ mình tôi"', async () => {
    const { container, user } = await renderProfile(SELF_ID, "member");
    const block = card(container)!;

    // `PRIVATE` theo contract là "chỉ chính chủ VÀ Hội đồng Tộc biểu". Gọi nó
    // là "chỉ mình tôi" là một lời hứa hệ thống không giữ, và người dùng sẽ
    // phát hiện ra đúng vào lúc tệ nhất.
    //
    // Khẳng định trên ĐÚNG dòng giải thích của nhóm, không trên cả khối: câu
    // cảnh báo ở đầu khối cố ý TRÍCH DẪN cụm "chỉ mình tôi" để phủ định nó, và
    // một phép quét cả khối sẽ bắt nhầm chính câu đang làm đúng việc.
    const group = nhom(block, "Nghề nghiệp & nơi làm việc");
    await user.click(muc(group, /Riêng tư/));

    const hint = within(group).getByText(/xem được\. Mục này không hiện trong danh bạ\./);
    expect(hint.textContent).toContain("Hội đồng Tộc biểu");
    expect(hint.textContent).not.toMatch(/^Chỉ bạn\.|chỉ mình tôi/i);
  });

  it("nói thẳng rằng Hội đồng vẫn đọc được số điện thoại ở mức Riêng tư", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const text = card(container)?.textContent ?? "";
    expect(text).toContain("số điện thoại");
    expect(text).toContain("không phải trục trặc");
  });

  it("liên hệ là MỘT công tắc, không phải ba", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const block = card(container)!;

    // Điện thoại, thư điện tử và Zalo dẫn tới cùng một con người; tách lẻ chỉ
    // tạo ảo giác kiểm soát. Contract gộp chúng, giao diện phải gộp theo.
    expect(
      within(block).getByRole("heading", { name: "Liên hệ: điện thoại · thư điện tử · Zalo" })
    ).toBeInTheDocument();
    expect(within(block).queryByRole("heading", { name: /^Điện thoại$/ })).toBeNull();
    expect(within(block).queryByRole("heading", { name: /^Zalo$/ })).toBeNull();
  });
});

describe("lợi ích viết ngay cạnh ô chọn, không giấu trong trang trợ giúp", () => {
  it("mỗi nhóm có một câu nói vì sao nên mở", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const text = card(container)?.textContent ?? "";

    expect(text).toContain("bà con cùng nghề tìm được nhau");
    expect(text).toContain("người trong họ ở cùng tỉnh biết nhau");
    expect(text).toContain("Danh bạ dòng họ");
  });

  it("có lối đi thẳng sang danh bạ", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const link = within(card(container)!).getByRole("link", {
      name: /Xem Danh bạ dòng họ/,
    });
    expect(link).toHaveAttribute("href", "/danh-ba");
  });
});

describe("đổi mức rồi lưu", () => {
  it("nút Lưu chỉ bật sau khi có thay đổi thật", async () => {
    const { container, user } = await renderProfile(SELF_ID, "member");
    const block = card(container)!;

    const save = within(block).getByRole("button", { name: "Lưu mức chia sẻ" });
    expect(save).toBeDisabled();

    // Nhóm "Địa chỉ đầy đủ" đang ở Riêng tư — mở nó ra cho cùng chi.
    await user.click(muc(nhom(block, "Địa chỉ đầy đủ"), /Cùng chi/));

    await waitFor(() => expect(save).toBeEnabled());
    expect(block.textContent).toContain("Có thay đổi chưa lưu.");
  });

  it("lưu xong thì nút tắt lại và có thông báo cho trình đọc màn hình", async () => {
    const { container, user } = await renderProfile(SELF_ID, "member");
    const block = card(container)!;

    await user.click(muc(nhom(block, "Địa chỉ đầy đủ"), /Cùng chi/));

    const save = within(block).getByRole("button", { name: "Lưu mức chia sẻ" });
    await waitFor(() => expect(save).toBeEnabled());
    await user.click(save);

    await waitFor(() => expect(block.textContent).toContain("Đã lưu."), { timeout: 8000 });
    expect(save).toBeDisabled();
  });
});

describe("nhóm vinh danh (thứ sáu, V17) có công tắc thật", () => {
  const NHAN_NHOM = "Vinh danh: đỗ đạt · chức tước · thành tích · khen thưởng";

  it("mở vinh danh cho cả họ, lưu, và giá trị thật đã ghi xuống máy chủ — không chỉ ở state React", async () => {
    const { container, user } = await renderProfile(SELF_ID, "member");
    const block = card(container)!;

    await user.click(muc(nhom(block, NHAN_NHOM), /Cả họ xem/));

    const save = within(block).getByRole("button", { name: "Lưu mức chia sẻ" });
    await waitFor(() => expect(save).toBeEnabled());
    await user.click(save);
    await waitFor(() => expect(block.textContent).toContain("Đã lưu."), { timeout: 8000 });

    // Dựng lại toàn bộ màn — nếu giá trị chỉ nằm trong state của lượt dựng
    // trước thì một `GET /persons/{id}` mới sẽ trả lại `PRIVATE` như cũ.
    const { container: reloaded } = await renderProfile(SELF_ID, "member");
    const chosen = within(nhom(card(reloaded)!, NHAN_NHOM)).getByRole("radio", {
      name: /Cả họ xem/,
    });
    expect(chosen).toBeChecked();
  });

  it("không mời bấm vào một liên kết hỏng: nhóm vinh danh không hiện 'chưa điền, điền ngay'", async () => {
    const { container } = await renderProfile(SELF_ID, "member");
    const group = nhom(card(container)!, NHAN_NHOM);

    // `PrivacyGroupRow` chỉ vẽ lời mời này khi `filledIn === false`, và
    // `editHref` của nó LUÔN là `/persons/{id}/edit` — biểu mẫu ấy không có ô
    // nào để khai một vinh danh (đường đúng là `/vinh-danh`, qua
    // `HonourFormModal`). Nhóm vinh danh không được phép mời một cú bấm dẫn
    // vào ngõ cụt như vậy.
    expect(within(group).queryByText(/Điền ngay/)).toBeNull();
  });
});

describe("song ngữ", () => {
  it("đọc được bản tiếng Anh, không lộ khoá i18n nào", async () => {
    const { container } = await renderProfile(SELF_ID, "member", "en");
    const text = card(container)?.textContent ?? "";

    expect(text).toContain("Who can see what about me");
    expect(text).toContain("Whole clan");
    expect(text).not.toContain("MISSING_MESSAGE");
  });
});

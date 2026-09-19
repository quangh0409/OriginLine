import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import {
  MOCK_SET_PASSWORD_TOKEN,
  MOCK_SET_PASSWORD_TOKEN_EXPIRED,
  MOCK_SET_PASSWORD_TOKEN_PROVIDER_DOWN,
  MOCK_SET_PASSWORD_TOKEN_RATE_LIMITED,
  resetSetPasswordMockState,
} from "@/mocks/handlers/invitation";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/dat-mat-khau",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/dat-mat-khau",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: ({ href, locale }: { href: string; locale: string }) =>
    locale === "vi" ? href : `/${locale}${href}`,
}));

const { SetPasswordScreen } = await import("@/components/auth/set-password-screen");

/**
 * ĐẶT MẬT KHẨU QUA LIÊN KẾT MỘT LẦN — `/dat-mat-khau?token=…`.
 *
 * Mảnh cuối của luồng mời. Bộ kiểm này canh bốn thứ, theo thứ tự quan trọng:
 *
 *  1. **Ba ca hỏng là ba câu khác nhau, và không ca nào là ngõ cụt.** `410`
 *     (liên kết hết hạn — nghiệp vụ BÌNH THƯỜNG, hạn ba mươi phút), `422`
 *     (realm từ chối mật khẩu — ca DUY NHẤT người dùng sửa được ngay tại chỗ),
 *     `503` (Keycloak im lặng — không phải lỗi mật khẩu, và liên kết còn nguyên).
 *     Lẫn ba ca ấy vào nhau sẽ đẩy một người đi nghĩ ra mật khẩu mới trong khi
 *     mạng của họ vừa rớt, hoặc đi xin một mã mời mới mà họ không cần.
 *  2. **Vắng liên kết là một phép chặn có chủ ý, không phải một lỗi.** Backend
 *     không phát liên kết cho tài khoản đã có mật khẩu; nếu nó phát, ai cầm một
 *     mã mời cộng với việc đoán đúng thư điện tử của một thành viên cũ sẽ đổi
 *     được mật khẩu của người ta. Màn hình phải ĐƯA TỚI ĐĂNG NHẬP, không hiện
 *     lỗi.
 *  3. **Không có bản sao chính sách mật khẩu ở client.** Một mật khẩu yếu vẫn
 *     phải GỬI ĐI được — máy chủ mới là nơi phán quyết.
 *  4. **`token` không rò thêm chỗ nào.** Không lên màn hình, và bị gỡ khỏi
 *     thanh địa chỉ ngay sau lượt đọc đầu tiên.
 */

function state(container: HTMLElement): string | null {
  return (
    container
      .querySelector("[data-set-password-state]")
      ?.getAttribute("data-set-password-state") ?? null
  );
}

function renderSetPassword(token: string | null, locale: "vi" | "en" = "vi") {
  searchParams = new URLSearchParams(token === null ? "" : `token=${token}`);
  // Thanh địa chỉ THẬT của jsdom, để kiểm được phép gỡ token. `useSearchParams`
  // đã được giả lập ở trên nên hai nơi phải khớp nhau bằng tay.
  window.history.replaceState(
    null,
    "",
    token === null ? "/dat-mat-khau" : `/dat-mat-khau?token=${token}`
  );

  const goToLogin = vi.fn();
  const view = renderWithProviders(<SetPasswordScreen onGoToLogin={goToLogin} />, {
    locale,
    role: "guest",
  });
  return { ...view, goToLogin };
}

/** Điền hai ô rồi bấm gửi. Hai ô vì màn Keycloak cũng có hai ô. */
async function datMatKhau(
  view: Awaited<ReturnType<typeof renderSetPassword>>,
  matKhau: string,
  goLai = matKhau
) {
  const o = screen.getAllByLabelText(/Mật khẩu mới|New password|Gõ lại|type the new password/i);
  await view.user.clear(o[0]!);
  await view.user.type(o[0]!, matKhau);
  await view.user.clear(o[1]!);
  await view.user.type(o[1]!, goLai);
  await view.user.click(
    screen.getByRole("button", { name: /^(Đặt mật khẩu|Set password)$/ })
  );
}

beforeEach(() => {
  resetSetPasswordMockState();
});

describe("đặt mật khẩu thành công", () => {
  it("204 → nói xong việc, và chỉ ra chỗ đáng xem tiếp chứ không dừng ở một màn cụt", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    expect(state(view.container)).toBe("FORM");

    await datMatKhau(view, "con chau nha Nguyen");

    await waitFor(() => expect(state(view.container)).toBe("DONE"));
    const panel = view.container.querySelector('[data-set-password-state="DONE"]')!;
    expect(panel.textContent).toContain("mật khẩu đã đặt");
    // Không phải "Thành công." rồi thôi: màn này nói trước CÓ GÌ ở bên kia.
    expect(panel.textContent).toContain("phả đồ dòng họ");
    expect(panel.textContent).toContain("danh xưng");
    expect(panel.textContent).toContain("nhắc ngày giỗ");
  });

  it("bước kế tiếp là ĐĂNG NHẬP, và nó quay về phả đồ chứ không về chính trang này", async () => {
    // Quay lại `/dat-mat-khau` sau đăng nhập là một vòng tròn: liên kết đã tiêu,
    // và người dùng sẽ gặp một màn nói "tài khoản của ông/bà đã có mật khẩu rồi".
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "con chau nha Nguyen");
    await waitFor(() => expect(state(view.container)).toBe("DONE"));

    await view.user.click(
      screen.getByRole("button", { name: /Đăng nhập, xem phả đồ/ })
    );

    expect(view.goToLogin).toHaveBeenCalledTimes(1);
    expect(view.goToLogin.mock.calls[0]![0]).toBe("/tree");
    expect(view.goToLogin.mock.calls[0]![0]).not.toContain("dat-mat-khau");
  });

  it("liên kết dùng một lần là THẬT: gọi lần hai ra 410, không ra 'xong' lần nữa", async () => {
    const lanMot = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(lanMot, "con chau nha Nguyen");
    await waitFor(() => expect(state(lanMot.container)).toBe("DONE"));
    lanMot.unmount();

    const lanHai = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(lanHai, "mot mat khau khac");
    await waitFor(() => expect(state(lanHai.container)).toBe("LINK_INVALID"));
  });
});

describe("410 — liên kết hết hạn là ca BÌNH THƯỜNG, không phải sự cố", () => {
  it("nói rõ tài khoản KHÔNG mất, vì sao liên kết hết hạn, và việc cần làm", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN_EXPIRED);
    await datMatKhau(view, "con chau nha Nguyen");

    await waitFor(() => expect(state(view.container)).toBe("LINK_INVALID"));
    const panel = view.container.querySelector('[data-set-password-state="LINK_INVALID"]')!;

    // Điều quan trọng nhất: không phải làm lại từ đầu.
    expect(panel.textContent).toContain("vẫn còn nguyên");
    expect(panel.textContent).toContain("không phải xin lại mã mời");
    // Vì sao — một lý do người thường hiểu được, không phải mã lỗi.
    expect(panel.textContent).toContain("ba mươi phút");
    // Việc cần làm: một con người, và một quy trình có thật ở đầu bên kia.
    expect(panel.textContent).toContain("gọi cho người đã mời");
    expect(panel.textContent).toContain("mật khẩu tạm");
  });

  it("có lối đi tiếp — khối liên hệ người mời, và lối đăng nhập cho ca 'đã đặt rồi'", async () => {
    // `410` cũng là câu trả lời khi liên kết ĐÃ DÙNG rồi: mật khẩu đã đặt xong ở
    // một lần bấm trước và vẫn đăng nhập được. Với người ấy việc cần làm không
    // phải gọi điện.
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN_EXPIRED);
    await datMatKhau(view, "con chau nha Nguyen");
    await waitFor(() => expect(state(view.container)).toBe("LINK_INVALID"));

    expect(view.container.querySelector('[data-invitation-contact="fallback"]')).not.toBeNull();
    await view.user.click(
      screen.getByRole("button", { name: /Tôi đã đặt mật khẩu rồi/ })
    );
    expect(view.goToLogin).toHaveBeenCalledTimes(1);
  });

  it("KHÔNG dùng dải đỏ cho một luật chạy đúng", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN_EXPIRED);
    await datMatKhau(view, "con chau nha Nguyen");
    await waitFor(() => expect(state(view.container)).toBe("LINK_INVALID"));

    const panel = view.container.querySelector('[data-set-password-state="LINK_INVALID"]')!;
    // `status`, không `alert`: người đứng trước màn này không làm gì sai.
    expect(panel.getAttribute("role")).toBe("status");
    expect(view.container.querySelector('[role="alert"]')).toBeNull();
    expect(panel.textContent).not.toContain("Đã có lỗi xảy ra");
    expect(panel.textContent).not.toContain("MISSING_MESSAGE");
  });
});

describe("422 — máy chủ từ chối mật khẩu", () => {
  it("in đúng câu MÁY CHỦ nói phải sửa gì, không phải 'không hợp lệ'", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    // Bốn ký tự: dưới sàn của realm giả lập, và bộ giả lập trả về một `detail`
    // nói rõ thiếu bao nhiêu — đúng như contract đòi ở `422`.
    await datMatKhau(view, "1234");

    await waitFor(() =>
      expect(screen.getByRole("alert").textContent).toContain("ít nhất 8 ký tự")
    );
    const canhBao = screen.getByRole("alert");
    expect(canhBao.textContent).toContain("4 ký tự");
    expect(canhBao.textContent).not.toMatch(/không hợp lệ/i);
    expect(canhBao.textContent).not.toContain("422");
    expect(canhBao.textContent).not.toContain("VALIDATION_FAILED");
  });

  it("Ở LẠI biểu mẫu và GIỮ NGUYÊN chữ đã gõ — sửa một lỗi chính tả, không gõ lại từ đầu", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "1234");
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());

    expect(state(view.container)).toBe("FORM");
    const o = screen.getAllByLabelText(/Mật khẩu mới|Gõ lại/);
    expect((o[0] as HTMLInputElement).value).toBe("1234");
    expect(o[0]).toHaveAttribute("aria-invalid", "true");
  });

  it("một mật khẩu quá dễ đoán nhận câu khác hẳn — hai lý do là hai việc phải làm", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "12345678");

    await waitFor(() =>
      expect(screen.getByRole("alert").textContent).toContain("quá dễ đoán")
    );
    expect(screen.getByRole("alert").textContent).not.toContain("ít nhất 8 ký tự");
  });

  it("sửa lại rồi gửi tiếp thì đi qua — 422 KHÔNG tiêu liên kết", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "1234");
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());

    await datMatKhau(view, "con chau nha Nguyen");
    await waitFor(() => expect(state(view.container)).toBe("DONE"));
  });
});

describe("503 — Keycloak im lặng, và đó KHÔNG phải lỗi mật khẩu", () => {
  it("nói thẳng rằng mật khẩu chưa được lưu và liên kết vẫn còn dùng được", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN_PROVIDER_DOWN);
    await datMatKhau(view, "con chau nha Nguyen");

    await waitFor(() =>
      expect(
        view.container.querySelector('[data-set-password-banner="PROVIDER_DOWN"]')
      ).not.toBeNull()
    );
    const banner = view.container.querySelector('[data-set-password-banner="PROVIDER_DOWN"]')!;
    expect(banner.textContent).toContain("không phải do mật khẩu");
    expect(banner.textContent).toContain("chưa được lưu");
    expect(banner.textContent).toContain("Liên kết vẫn còn dùng được");
    // KHÔNG được đọc thành "liên kết hỏng": người dùng sẽ đi xin liên kết mới
    // trong khi liên kết cũ vẫn tốt.
    expect(state(view.container)).toBe("FORM");
    expect(banner.textContent).not.toContain("hết hạn");
  });

  it("biểu mẫu còn nguyên để bấm lại — nút gửi vẫn bấm được", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN_PROVIDER_DOWN);
    await datMatKhau(view, "con chau nha Nguyen");
    await waitFor(() =>
      expect(
        view.container.querySelector('[data-set-password-banner="PROVIDER_DOWN"]')
      ).not.toBeNull()
    );

    const nut = screen.getByRole("button", { name: /^Đặt mật khẩu$/ });
    expect(nut).toBeEnabled();
  });

  it("429 là một câu RIÊNG, không gộp vào 503 và không gộp vào 'mật khẩu sai'", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN_RATE_LIMITED);
    await datMatKhau(view, "con chau nha Nguyen");

    await waitFor(() =>
      expect(
        view.container.querySelector('[data-set-password-banner="RATE_LIMITED"]')
      ).not.toBeNull()
    );
    const banner = view.container.querySelector('[data-set-password-banner="RATE_LIMITED"]')!;
    expect(banner.textContent).toContain("chặn người lạ dò mật khẩu");
    expect(banner.textContent).toContain("không phải do ông/bà làm sai");
  });

  it("mất đường truyền hẳn cũng không bị đọc thành 'mật khẩu sai'", async () => {
    server.use(
      http.post(`${API_BASE_URL}/api/v1/invitations/set-password`, () => HttpResponse.error())
    );

    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "con chau nha Nguyen");

    await waitFor(() =>
      expect(
        view.container.querySelector('[data-set-password-banner="UNAVAILABLE"]')
      ).not.toBeNull()
    );
    expect(state(view.container)).toBe("FORM");
  });
});

describe("VẮNG liên kết — phép chặn có chủ ý, KHÔNG phải lỗi", () => {
  it("không có token trong đường dẫn → đưa tới đăng nhập, không một chữ lỗi nào", async () => {
    // Backend KHÔNG phát `setPasswordUrl` cho tài khoản đã có mật khẩu. Người
    // đứng ở đây hoặc đã đặt xong rồi, hoặc gõ tay địa chỉ trang. Cả hai chỉ
    // cần một việc: đăng nhập.
    const view = renderSetPassword(null);

    expect(state(view.container)).toBe("NO_LINK");
    const panel = view.container.querySelector('[data-set-password-state="NO_LINK"]')!;
    expect(panel.getAttribute("role")).toBe("status");
    expect(view.container.querySelector('[role="alert"]')).toBeNull();

    for (const cam of [/lỗi/i, /không hợp lệ/i, /thất bại/i, /không có quyền/i, /hết hạn/i]) {
      expect(panel.textContent ?? "", `câu chữ chạm phải mẫu cấm ${cam}`).not.toMatch(cam);
    }
    expect(panel.textContent).not.toContain("MISSING_MESSAGE");
  });

  it("nói VÌ SAO trang không mở — phép chặn ấy là thứ đang bảo vệ họ", async () => {
    const view = renderSetPassword(null);
    const panel = view.container.querySelector('[data-set-password-state="NO_LINK"]')!;

    expect(panel.textContent).toContain("đã có mật khẩu rồi");
    expect(panel.textContent).toContain("cố ý");
    expect(panel.textContent).toContain("mã mời");
  });

  it("nút đăng nhập là lối đi chính, và nó dẫn ra khỏi trang này", async () => {
    const view = renderSetPassword(null);

    // Tên khả truy cập của nút AntD gồm cả nhãn biểu tượng ("login"), nên
    // không neo hai đầu. Màn này chỉ có đúng một nút.
    await view.user.click(screen.getByRole("button", { name: /Đăng nhập/ }));

    expect(view.goToLogin).toHaveBeenCalledTimes(1);
    expect(view.goToLogin.mock.calls[0]![0]).toBe("/tree");
  });

  it("không có biểu mẫu nào để gõ vào — gõ mật khẩu ở đây là gõ vào hư không", async () => {
    const view = renderSetPassword(null);
    expect(view.container.querySelector("form")).toBeNull();
    expect(screen.queryByLabelText(/Mật khẩu mới/)).toBeNull();
  });

  it("token rỗng cũng rơi về đúng nhánh ấy, không rơi vào biểu mẫu chết", async () => {
    const view = renderSetPassword("");
    expect(state(view.container)).toBe("NO_LINK");
  });
});

describe("ô Hiện mật khẩu — bắt buộc, và giống hệt giao diện Keycloak", () => {
  it("nút mang NHÃN CHỮ đổi theo trạng thái, không phải một con mắt gạch chéo", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    const nut = screen.getAllByRole("button", { name: "Hiện" })[0]!;
    const o = screen.getAllByLabelText(/Mật khẩu mới/)[0] as HTMLInputElement;

    expect(o.type).toBe("password");
    expect(nut).toHaveAttribute("aria-pressed", "false");
    expect(nut).toHaveAttribute("aria-controls", o.id);

    await view.user.click(nut);

    expect(o.type).toBe("text");
    expect(nut.textContent).toBe("Ẩn");
    expect(nut).toHaveAttribute("aria-pressed", "true");
    // Con trỏ ở lại trong ô: người vừa bấm "Hiện" là để đọc tiếp chỗ đang gõ dở.
    expect(document.activeElement).toBe(o);
  });

  it("mỗi ô có nút riêng — màn Keycloak hai ô cũng vậy", () => {
    renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    expect(screen.getAllByRole("button", { name: "Hiện" })).toHaveLength(2);
  });

  it("vùng chạm 68×44 đi theo lớp, không theo cảm tính", () => {
    // jsdom không có bộ dựng bố cục nên mọi phép đo hình học đều trả 0; phép đo
    // thật nằm ở tầng E2E. Ở đây canh CHÍNH CON SỐ, và
    // `set-password-keycloak-parity.test.ts` canh nó khớp với theme Keycloak.
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    const nut = screen.getAllByRole("button", { name: "Hiện" })[0]!;
    expect(nut.className).toContain("h-[44px]");
    expect(nut.className).toContain("w-[68px]");
    expect(
      view.container.querySelector("input[type=password]")!.className
    ).toContain("min-h-[44px]");
  });
});

describe("chính sách mật khẩu thuộc REALM — client không dựng bản sao", () => {
  it("một mật khẩu yếu vẫn GỬI ĐI được: máy chủ mới là nơi phán quyết", async () => {
    let daGoi = false;
    server.use(
      http.post(`${API_BASE_URL}/api/v1/invitations/set-password`, async ({ request }) => {
        daGoi = true;
        const body = (await request.json()) as { newPassword?: string };
        expect(body.newPassword).toBe("a");
        return new HttpResponse(null, { status: 204 });
      })
    );

    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "a");

    await waitFor(() => expect(daGoi).toBe(true));
  });

  it("nút gửi KHÔNG bao giờ bị vô hiệu vì mật khẩu 'chưa đủ mạnh'", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    const o = screen.getAllByLabelText(/Mật khẩu mới|Gõ lại/);
    await view.user.type(o[0]!, "a");

    expect(screen.getByRole("button", { name: /^Đặt mật khẩu$/ })).toBeEnabled();
  });

  it("không thanh đo độ mạnh, không danh sách dấu tích — chỉ một câu GỢI Ý", () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    const text = view.container.textContent ?? "";

    expect(text).toContain("Ít nhất 8 ký tự");
    expect(text).not.toMatch(/độ mạnh|yếu|trung bình|mạnh vừa/i);
    expect(view.container.querySelector('[role="progressbar"]')).toBeNull();
  });

  it("gõ lệch hai lần thì CHẶN — đó là phép dò lỗi gõ, không phải một luật thứ hai", async () => {
    let daGoi = false;
    server.use(
      http.post(`${API_BASE_URL}/api/v1/invitations/set-password`, () => {
        daGoi = true;
        return new HttpResponse(null, { status: 204 });
      })
    );

    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "con chau nha Nguyen", "con chau nha Nguyeen");

    expect(screen.getByRole("alert").textContent).toContain("chưa giống nhau");
    // Câu ấy chỉ đúng một chỗ để đi tiếp, và nó là cái nút đã có sẵn.
    expect(screen.getByRole("alert").textContent).toContain("Hiện");
    expect(daGoi).toBe(false);
  });
});

describe("token không rò thêm một chỗ nào", () => {
  it("bị gỡ khỏi thanh địa chỉ ngay sau lượt đọc đầu tiên", async () => {
    renderSetPassword(MOCK_SET_PASSWORD_TOKEN);

    await waitFor(() => expect(window.location.search).toBe(""));
    expect(window.location.href).not.toContain(MOCK_SET_PASSWORD_TOKEN);
    // …mà biểu mẫu vẫn dùng được: token đã nằm trong bộ nhớ của thành phần.
    expect(screen.getByRole("button", { name: /^Đặt mật khẩu$/ })).toBeInTheDocument();
  });

  it("gỡ xong rồi mà gửi vẫn mang ĐÚNG token lên máy chủ", async () => {
    let nhanDuoc: string | null = null;
    server.use(
      http.post(`${API_BASE_URL}/api/v1/invitations/set-password`, async ({ request }) => {
        nhanDuoc = ((await request.json()) as { token?: string }).token ?? null;
        return new HttpResponse(null, { status: 204 });
      })
    );

    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await waitFor(() => expect(window.location.search).toBe(""));
    await datMatKhau(view, "con chau nha Nguyen");

    await waitFor(() => expect(nhanDuoc).toBe(MOCK_SET_PASSWORD_TOKEN));
  });

  it("không màn nào in token ra màn hình — ảnh chụp không mang theo bí mật", async () => {
    for (const token of [
      MOCK_SET_PASSWORD_TOKEN,
      MOCK_SET_PASSWORD_TOKEN_EXPIRED,
      MOCK_SET_PASSWORD_TOKEN_PROVIDER_DOWN,
    ]) {
      const view = renderSetPassword(token);
      await datMatKhau(view, "con chau nha Nguyen");
      await waitFor(() => expect(state(view.container)).not.toBeNull());
      expect(view.container.innerHTML).not.toContain(token);
      view.unmount();
    }
  });

  it("mật khẩu KHÔNG đi vào đường dẫn — nó chỉ đi trong thân POST", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN);
    await datMatKhau(view, "con chau nha Nguyen");
    await waitFor(() => expect(state(view.container)).toBe("DONE"));

    expect(window.location.href).not.toContain("con");
    expect(window.location.search).toBe("");
  });
});

describe("song ngữ", () => {
  it("bốn màn đều có bản tiếng Anh đầy đủ", async () => {
    const ca: ReadonlyArray<readonly [string | null, string]> = [
      [null, "already has a password"],
      [MOCK_SET_PASSWORD_TOKEN, "Set a password for your account"],
      [MOCK_SET_PASSWORD_TOKEN_EXPIRED, "can no longer be used"],
      [MOCK_SET_PASSWORD_TOKEN_PROVIDER_DOWN, "Could not reach the account system"],
    ];

    for (const [token, mong] of ca) {
      const view = renderSetPassword(token, "en");
      if (token !== null && token !== MOCK_SET_PASSWORD_TOKEN) {
        const o = screen.getAllByLabelText(/New password|type the new password again/i);
        await view.user.type(o[0]!, "con chau nha Nguyen");
        await view.user.type(o[1]!, "con chau nha Nguyen");
        await view.user.click(screen.getByRole("button", { name: /^Set password$/ }));
      }
      await waitFor(() => expect(view.container.textContent).toContain(mong));
      expect(view.container.textContent).not.toContain("MISSING_MESSAGE");
      view.unmount();
    }
  });

  it("câu từ chối của máy chủ đi theo Accept-Language, không bị dịch lại ở client", async () => {
    const view = renderSetPassword(MOCK_SET_PASSWORD_TOKEN, "en");
    const o = screen.getAllByLabelText(/New password|type the new password again/i);
    await view.user.type(o[0]!, "1234");
    await view.user.type(o[1]!, "1234");
    await view.user.click(screen.getByRole("button", { name: /^Set password$/ }));

    await waitFor(() =>
      expect(screen.getByRole("alert").textContent).toContain("at least 8 characters")
    );
  });
});

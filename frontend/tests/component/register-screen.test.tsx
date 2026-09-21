import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import {
  MOCK_CLAN_CODE,
  MOCK_CLAN_CODE_EXHAUSTED,
  MOCK_CLAN_CODE_EXPIRED,
  MOCK_CLAN_CODE_PROVIDER_DOWN,
  MOCK_CLAN_CODE_RATE_LIMITED,
  MOCK_CLAN_CODE_REVOKED,
  MOCK_CLAN_CODE_SERVER_DOWN,
  MOCK_CLAN_LOGIN_ID_TAKEN,
  resetClanInviteMockState,
} from "@/mocks/handlers/clan-invite";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/dang-ky",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: ({ href, locale }: { href: string; locale: string }) =>
    locale === "vi" ? href : `/${locale}${href}`,
}));

const { RegisterScreen } = await import("@/components/auth/register-screen");

/**
 * **Màn đăng ký bằng mã mời dòng họ** — `/dang-ky`, design 07 §1.3.
 *
 * Bộ kiểm này canh năm thứ, theo thứ tự hậu quả:
 *
 *  1. **Mã được kiểm TRƯỚC khi tài khoản được tạo.** Một mã hỏng không được
 *     phép để lại bất cứ lượt gọi nào tới `/register`. Gõ sai mà vẫn sinh ra
 *     một tài khoản rác là lỗi không ai đi dọn — và tệ hơn, realm đặt
 *     `duplicateEmailsAllowed: false` nên cái tài khoản rác ấy chặn chính lần
 *     thử lại của người vừa gõ sai.
 *  2. **Bốn ca hỏng của mã là bốn câu khác nhau**, và không hai câu nào trùng
 *     nhau. "Mã không hợp lệ" cho cả bốn là một ngõ cụt có màu: người dùng phải
 *     làm bốn việc khác hẳn nhau (gõ lại · xin mã mới · hỏi vì sao bị đóng ·
 *     nhắn để Hội đồng nâng trần).
 *  3. **Ba cách viết cùng một mã đều mở được** — phiếu in `K7M-2QD`, tin nhắn
 *     mang `K7M2QD`, cụ bảy mươi gõ `k7m 2qd` kèm khoảng trắng.
 *  4. **Cổng danh tính im lặng KHÔNG được đọc thành "mã sai"**, vì ở ca ấy mã
 *     *chưa bị tiêu* và việc đúng là bấm lại — không phải đi xin mã mới.
 *  5. **Xong rồi thì đi đâu.** Tài khoản chưa gắn nhân khẩu nào; màn phải nói
 *     ra điều đó bằng tiếng người và chỉ sang `/nhan-dien`.
 */

function state(container: HTMLElement): string | null {
  return (
    container.querySelector("[data-register-state]")?.getAttribute("data-register-state") ?? null
  );
}

function panel(container: HTMLElement, ten: string): HTMLElement {
  const el = container.querySelector(`[data-register-state="${ten}"]`);
  expect(el, `không tìm thấy khối [data-register-state="${ten}"]`).not.toBeNull();
  return el as HTMLElement;
}

function render(locale: "vi" | "en" = "vi") {
  const leave = vi.fn();
  // `login` là một spy: nút "Đăng nhập rồi nhập lại mã" phải gọi ĐÚNG lối đăng
  // nhập của ứng dụng, không phải một `router.push` tự chế tới một tuyến tưởng
  // tượng. Phiên vẫn là khách — đó là trạng thái thật của người đứng ở
  // `/dang-ky`.
  const login = vi.fn();
  const view = renderWithProviders(<RegisterScreen onLeaveForPasswordSetup={leave} />, {
    role: "guest",
    locale,
    auth: { login },
  });
  return { ...view, leave, login };
}

/** Gõ mã rồi bấm "Kiểm mã". */
async function goMa(view: ReturnType<typeof render>, ma: string) {
  const o = screen.getByLabelText(/Mã mời dòng họ|Clan invitation code/);
  await view.user.clear(o);
  await view.user.type(o, ma);
  await view.user.click(screen.getByRole("button", { name: /Kiểm mã|Check the code/ }));
}

/** Điền bước 2 rồi bấm "Lập tài khoản". */
async function lapTaiKhoan(
  view: ReturnType<typeof render>,
  dinhDanh: string,
  ten = "Nguyễn Thị Lan"
) {
  await view.user.type(
    screen.getByLabelText(/Thư điện tử hoặc số điện thoại|Email address or phone number/),
    dinhDanh
  );
  if (ten.length > 0) {
    await view.user.type(
      screen.getByLabelText(/Tên ông\/bà tự xưng|What we should call you/),
      ten
    );
  }
  await view.user.click(
    screen.getByRole("button", { name: /Lập tài khoản|Create my account/ })
  );
}

beforeEach(() => {
  resetClanInviteMockState();
});

// ══════════════════════════════════════════════════════════════════════════
// 1 · MÃ ĐƯỢC KIỂM TRƯỚC, TÀI KHOẢN LẬP SAU
// ══════════════════════════════════════════════════════════════════════════

describe("kiểm mã trước, lập tài khoản sau", () => {
  it("mã hỏng KHÔNG sinh ra một lượt gọi nào tới /register", async () => {
    const dangKy = vi.fn();
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/register`, () => {
        dangKy();
        return HttpResponse.json({});
      })
    );

    const view = render();
    await goMa(view, "SAIBET0T");

    await waitFor(() => expect(state(view.container)).toBe("NOT_FOUND"));
    // Đây là bất biến quan trọng nhất của cả màn: gõ sai mã không để lại gì.
    expect(dangKy).not.toHaveBeenCalled();
  });

  it("ô thư điện tử CHƯA hiện ra trước khi máy chủ nói mã dùng được", async () => {
    const view = render();
    expect(state(view.container)).toBe("CODE");
    expect(
      screen.queryByLabelText(/Thư điện tử hoặc số điện thoại/)
    ).toBeNull();

    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    expect(screen.getByLabelText(/Thư điện tử hoặc số điện thoại/)).toBeTruthy();
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 2 · BỐN CA HỎNG CỦA MÃ — BỐN CÂU KHÁC NHAU, MỖI CÂU MỘT LỐI ĐI TIẾP
// ══════════════════════════════════════════════════════════════════════════

describe("bốn ca mã hỏng — mỗi ca một câu và một lối đi tiếp", () => {
  it("mã sai: nói đúng nguyên nhân hay gặp (O/0, đường dẫn bị cắt) và mời gõ lại", async () => {
    const view = render();
    await goMa(view, "SAIBET0T");

    await waitFor(() => expect(state(view.container)).toBe("NOT_FOUND"));
    const p = panel(view.container, "NOT_FOUND");
    expect(p.textContent).toContain("không khớp với mã mời nào");
    // Vì sao — lý do người thường hiểu được, không phải mã lỗi.
    expect(p.textContent).toContain("chuyển tiếp tin nhắn");
    expect(p.textContent).toContain("chữ O và số 0");
    // Lối đi tiếp.
    expect(p.textContent).toContain("hỏi lại người đã đưa mã");
    expect(screen.getByRole("button", { name: /Gõ lại mã/ })).toBeTruthy();
    // Cửa 3 của 06 §5.4: người lạ mò vào được chỉ đường, không bị bức tường.
    expect(view.container.querySelector('[data-guest-mode="notice"]')).not.toBeNull();
  });

  it("mã hết hạn: nói rõ là mã CŨ, và gõ lại thì vô ích", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE_EXPIRED);

    await waitFor(() => expect(state(view.container)).toBe("EXPIRED"));
    const p = panel(view.container, "EXPIRED");
    expect(p.textContent).toContain("đã hết hạn");
    expect(p.textContent).toContain("Gõ lại không giúp được gì");
    expect(p.textContent).toContain("xin một mã mới");
  });

  it("mã bị thu hồi: KHÁC hết hạn — có người chủ động đóng nó", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE_REVOKED);

    await waitFor(() => expect(state(view.container)).toBe("REVOKED"));
    const p = panel(view.container, "REVOKED");
    expect(p.textContent).toContain("đã bị thu hồi");
    expect(p.textContent).toContain("Khác với hết hạn");
    // Và trấn an đúng chỗ: việc thu hồi không nhắm vào người đang đọc.
    expect(p.textContent).toContain("không nhắm vào riêng ông/bà");
  });

  it("mã hết lượt: còn hạn, chưa thu hồi — và là ca DUY NHẤT mở lại được không cần mã mới", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE_EXHAUSTED);

    await waitFor(() => expect(state(view.container)).toBe("EXHAUSTED"));
    const p = panel(view.container, "EXHAUSTED");
    expect(p.textContent).toContain("hết lượt dùng");
    expect(p.textContent).toContain("vẫn còn hạn và chưa bị thu hồi");
    expect(p.textContent).toContain("không cần mã mới");
  });

  it("bốn ca KHÔNG dùng chung một câu — không ca nào là “mã không hợp lệ”", async () => {
    const cau: string[] = [];
    for (const ma of [
      "SAIBET0T",
      MOCK_CLAN_CODE_EXPIRED,
      MOCK_CLAN_CODE_REVOKED,
      MOCK_CLAN_CODE_EXHAUSTED,
    ]) {
      const view = render();
      await goMa(view, ma);
      await waitFor(() => expect(state(view.container)).not.toBe("CODE"));
      const tieuDe = view.container.querySelector("#register-problem-title")!.textContent!;
      cau.push(tieuDe.trim());
      view.unmount();
    }
    expect(new Set(cau).size).toBe(4);
    for (const c of cau) expect(c).not.toMatch(/không hợp lệ/);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 3 · QUÁ TẦN SUẤT — KHÔNG PHẢI LỖI CỦA HỌ
// ══════════════════════════════════════════════════════════════════════════

describe("quá tần suất (429)", () => {
  it("nói rõ không phải lỗi của họ, mã còn nguyên, và đợi bao lâu", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE_RATE_LIMITED);

    await waitFor(() => expect(state(view.container)).toBe("RATE_LIMITED"));
    const p = panel(view.container, "RATE_LIMITED");
    expect(p.textContent).toContain("Không phải lỗi của ông/bà");
    expect(p.textContent).toContain("vẫn còn nguyên giá trị");
    // `retryAfterSeconds: 120` → "khoảng 2 phút". Con số đến TỪ MÁY CHỦ.
    expect(p.textContent).toContain("khoảng 2 phút");
  });

  it("KHÔNG có nút “Thử lại” — bấm lại chỉ đẩy thêm một lượt vào đúng bộ đếm đang chặn", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE_RATE_LIMITED);

    await waitFor(() => expect(state(view.container)).toBe("RATE_LIMITED"));
    expect(screen.queryByRole("button", { name: /Thử lại/ })).toBeNull();
    // Nhưng vẫn có việc để làm trong lúc chờ.
    expect(view.container.querySelector('[data-guest-mode="notice"]')).not.toBeNull();
  });

  it("máy chủ không nói số giây thì KHÔNG bịa ra một con số", async () => {
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/lookup`, () =>
        HttpResponse.json(
          { type: "about:blank", title: "t", status: 429, code: "RATE_LIMITED" },
          { status: 429 }
        )
      )
    );

    const view = render();
    await goMa(view, MOCK_CLAN_CODE);

    await waitFor(() => expect(state(view.container)).toBe("RATE_LIMITED"));
    const p = panel(view.container, "RATE_LIMITED");
    expect(p.textContent).toContain("Xin đợi ít phút");
    expect(p.textContent).not.toMatch(/khoảng \d+ phút/);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 4 · BA CÁCH VIẾT, MỘT MÃ
// ══════════════════════════════════════════════════════════════════════════

describe("chuẩn hoá mã ở phía client", () => {
  it("chữ thường kèm khoảng trắng và dấu gạch vẫn mở đúng mã", async () => {
    // Đây là cụ bảy mươi cầm tờ phiếu in `H0NG7-V2KDA` và gõ lại bằng một ngón.
    const view = render();
    await goMa(view, "  h0ng7-v2 kda ");

    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    expect(view.container.textContent).toContain("Dòng họ Nguyễn — Đại Lan");
  });

  it("gõ chữ O thay cho số 0 vẫn mở đúng mã", async () => {
    const view = render();
    await goMa(view, "hOng7v2kda");

    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
  });

  it("mã đi trong THÂN POST, không đi trong đường dẫn", async () => {
    let thanGui: unknown = null;
    let duongDan = "";
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/lookup`, async ({ request }) => {
        duongDan = new URL(request.url).pathname + new URL(request.url).search;
        thanGui = await request.json();
        return HttpResponse.json({ clanName: "X", expiresAt: "2026-10-21T17:00:00Z" });
      })
    );

    const view = render();
    await goMa(view, "h0ng7-v2kda");

    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    // Mã là bí mật: đường dẫn đi vào access log, lịch sử duyệt web và `Referer`.
    expect(duongDan).toBe("/api/v1/clan-invites/lookup");
    expect(thanGui).toEqual({ code: "H0NG7V2KDA" });
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 5 · Ô ĐỊNH DANH NHẬN CẢ SỐ ĐIỆN THOẠI
// ══════════════════════════════════════════════════════════════════════════

describe("ô định danh — nhất quán với ô đăng nhập", () => {
  it("số điện thoại ĐĂNG KÝ ĐƯỢC, và đi lên máy chủ NGUYÊN VĂN", async () => {
    // Hợp đồng đổi: `loginId` nhận email HOẶC số điện thoại Việt Nam. Khối giải
    // thích nền hổ phách từng đứng ở đây — nói rằng số máy đăng nhập được nhưng
    // chưa đăng ký được — đã gỡ. Nó đúng khi viết, và nay sai.
    let thanGui: unknown = null;
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/register`, async ({ request }) => {
        thanGui = await request.json();
        return HttpResponse.json({
          appUserId: "u-1",
          status: "PENDING",
          clanInviteId: "ci-1",
          setPasswordUrl: "/vi/dat-mat-khau?token=t",
        });
      })
    );

    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));

    await view.user.type(
      screen.getByLabelText(/Thư điện tử hoặc số điện thoại/),
      "0912 345 678"
    );
    // KHÔNG còn khối chặn tại chỗ.
    expect(view.container.querySelector('[data-register-identifier="PHONE"]')).toBeNull();

    await view.user.click(screen.getByRole("button", { name: /Lập tài khoản/ }));

    await waitFor(() => expect(state(view.container)).toBe("DONE"));
    // `loginId`, không `email` — tên cũ nói dối khi giá trị là một số máy. Và
    // gửi NGUYÊN VĂN: chuẩn hoá đầu số là luật của máy chủ, một bản sao thứ hai
    // ở client là một bản sẽ lệch.
    expect(thanGui).toMatchObject({ loginId: "0912 345 678" });
    expect(thanGui).not.toHaveProperty("email");
  });

  it("giới hạn về KHÔI PHỤC được nói ở khối đặt mật khẩu, không ở ô nhập", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "0912345678");

    await waitFor(() => expect(state(view.container)).toBe("DONE"));
    const ghiChu = view.container.querySelector('[data-register-recovery="PHONE"]')!;
    expect(ghiChu).not.toBeNull();
    // Câu ĐÚNG trong khối hổ phách cũ không mất, nó chuyển chỗ: thư đặt lại mật
    // khẩu là chuyện của khôi phục, không phải của đăng ký.
    expect(ghiChu.textContent).toContain("đặt lại mật khẩu");
    expect(ghiChu.textContent).toContain("Trưởng chi");
  });

  it("thư điện tử thì KHÔNG hiện ghi chú khôi phục ấy", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "ba.lan@example.com");

    await waitFor(() => expect(state(view.container)).toBe("DONE"));
    expect(view.container.querySelector('[data-register-recovery="PHONE"]')).toBeNull();
  });

  it("thứ đọc không ra thành email LẪN số máy thì lỗi Ở LẠI TRONG Ô", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "khong-phai-thu-dien-tu");

    // Một lỗi chính tả trong ô định danh không được bắt họ gõ lại mã.
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("khuôn"));
    expect(state(view.container)).toBe("ACCOUNT");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 6 · CỔNG DANH TÍNH IM LẶNG — MÃ CHƯA BỊ TIÊU
// ══════════════════════════════════════════════════════════════════════════

describe("cổng danh tính im lặng (503)", () => {
  it("nói rõ mã CHƯA bị tính là đã dùng, và cho một nút thử lại", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE_PROVIDER_DOWN);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "ba.lan@ho-nguyen.vn");

    await waitFor(() => expect(state(view.container)).toBe("PROVIDER_DOWN"));
    const p = panel(view.container, "PROVIDER_DOWN");
    expect(p.textContent).toContain("chưa bị tính là đã dùng");
    // Loi di tiep la BAM LAI, khong phai di xin ma moi.
    expect(p.textContent).toContain("Không cần xin mã mới");
    expect(screen.getByRole("button", { name: /Thử lại/ })).toBeTruthy();
  });

  it("“Thử lại” gửi lại ĐÚNG bước vừa hỏng, không bắt gõ lại mã từ đầu", async () => {
    let luot = 0;
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/register`, () => {
        luot += 1;
        if (luot === 1) {
          return HttpResponse.json(
            {
              type: "about:blank",
              title: "t",
              status: 503,
              code: "IDENTITY_PROVIDER_UNAVAILABLE",
            },
            { status: 503 }
          );
        }
        return HttpResponse.json({
          appUserId: "u-1",
          status: "PENDING",
          clanInviteId: "ci-1",
          setPasswordUrl: "/vi/dat-mat-khau?token=abc",
        });
      })
    );

    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "ba.lan@ho-nguyen.vn");

    await waitFor(() => expect(state(view.container)).toBe("PROVIDER_DOWN"));
    await view.user.click(screen.getByRole("button", { name: /Thử lại/ }));

    await waitFor(() => expect(state(view.container)).toBe("DONE"));
    expect(luot).toBe(2);
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 7 · MẤT ĐƯỜNG TRUYỀN KHÔNG ĐƯỢC ĐỌC THÀNH "MÃ SAI"
// ══════════════════════════════════════════════════════════════════════════

describe("mất đường truyền", () => {
  it("máy chủ 500 → “trục trặc đường truyền”, KHÔNG phải “mã sai”", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE_SERVER_DOWN);

    await waitFor(() => expect(state(view.container)).toBe("UNAVAILABLE"));
    const p = panel(view.container, "UNAVAILABLE");
    expect(p.textContent).toContain("không phải do mã mời sai");
    expect(p.textContent).toContain("vẫn còn nguyên giá trị");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 8 · XONG RỒI THÌ ĐI ĐÂU
// ══════════════════════════════════════════════════════════════════════════

describe("đăng ký xong", () => {
  it("nói rõ tài khoản CHƯA gắn ai trong phả, và vì sao đó là đúng", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "ba.lan@ho-nguyen.vn");

    await waitFor(() => expect(state(view.container)).toBe("DONE"));
    const p = panel(view.container, "DONE");
    expect(p.textContent).toContain("chưa gắn với ai trong phả");
    expect(p.textContent).toContain("đoán bừa theo trùng tên");
    // KHÔNG in ra chữ "PENDING" — người dùng sẽ đọc nó thành "đơn của tôi bị treo".
    expect(p.textContent).not.toContain("PENDING");
  });

  it("dẫn sang “tôi là ai trong phả” — bằng một LIÊN KẾT, không phải một cú chuyển hướng", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "ba.lan@ho-nguyen.vn");
    await waitFor(() => expect(state(view.container)).toBe("DONE"));

    const lien = view.container.querySelector('[data-register-next="NHAN_DIEN"] a')!;
    expect(lien.getAttribute("href")).toBe("/nhan-dien");
    // Tuyến ấy do một mạch việc khác dựng và có thể chưa tồn tại. Một liên kết
    // chưa có đích thì tệ nhất là một trang 404 bấm lùi được; một cú chuyển
    // hướng tự động thì ném họ vào đó ngay sau một thao tác không làm lại được.
    expect(routerMock.push).not.toHaveBeenCalled();
    expect(routerMock.replace).not.toHaveBeenCalled();
  });

  it("đi tới ĐÚNG liên kết đặt mật khẩu máy chủ trả về, không tự ghép URL", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "ba.lan@ho-nguyen.vn");
    await waitFor(() => expect(state(view.container)).toBe("DONE"));

    await view.user.click(screen.getByRole("button", { name: /Đặt mật khẩu/ }));
    expect(view.leave).toHaveBeenCalledWith("/vi/dat-mat-khau?token=sp-lien-ket-con-han");
  });

  it("vắng liên kết đặt mật khẩu vì ĐANG ĐĂNG NHẬP SẴN — không nói 'đã có mật khẩu từ trước'", async () => {
    // Ca này là toàn bộ nghĩa còn lại của "vắng `setPasswordUrl`" với máy chủ
    // hôm nay. Trước bản vá, một người CHƯA đăng nhập khai một định danh đã có
    // chủ cũng rơi vào đây, và câu "tài khoản này đã có mật khẩu từ trước" được
    // viết cho đúng họ. Nay nhánh không-token dừng ở `422` trước cả bước đúc
    // liên kết, nên người đứng đây là người vừa đăng nhập Google/Zalo rồi mới
    // nhập mã — và với họ, câu cũ nói sai một sự việc.
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/register`, () =>
        // Nhánh "đã có token" của `ClanInviteService`: `Optional.empty()`.
        HttpResponse.json({ appUserId: "u-1", status: "PENDING", clanInviteId: "ci-1" })
      )
    );

    const leave = vi.fn();
    const view = renderWithProviders(<RegisterScreen onLeaveForPasswordSetup={leave} />, {
      role: "member",
      locale: "vi",
    });
    await goMa({ ...view, leave, login: vi.fn() }, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await view.user.type(
      screen.getByLabelText(/Thư điện tử hoặc số điện thoại/),
      "ba.lan@ho-nguyen.vn"
    );
    await view.user.click(screen.getByRole("button", { name: /Lập tài khoản/ }));

    await waitFor(() => expect(state(view.container)).toBe("DONE"));
    const khoi = view.container.querySelector('[data-register-nolink]')!;
    expect(khoi.getAttribute("data-register-nolink")).toBe("SIGNED_IN");
    expect(khoi.textContent).toContain("đang đăng nhập sẵn");
    expect(khoi.textContent).not.toContain("đã có mật khẩu từ trước");
    // Bảo một người đang đăng nhập đi đăng nhập là một vòng tròn.
    expect(screen.queryByRole("button", { name: /^Đăng nhập$/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Đặt mật khẩu/ })).toBeNull();
  });

  it("mã dòng họ dùng được NHIỀU lần — người thứ hai vẫn vào được bằng cùng một mã", async () => {
    // Đây là cả lý do mã dòng họ tách khỏi mã cá nhân (design 07 §1.1).
    for (const thu of ["nguoi-mot@ho-nguyen.vn", "nguoi-hai@ho-nguyen.vn"]) {
      const view = render();
      await goMa(view, MOCK_CLAN_CODE);
      await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
      await lapTaiKhoan(view, thu);
      await waitFor(() => expect(state(view.container)).toBe("DONE"));
      view.unmount();
    }
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 9 · ĐỊNH DANH ĐÃ CÓ CHỦ — CHỖ VỪA BỊT MỘT LỖ HỔNG CHIẾM TÀI KHOẢN
//
// Trước bản vá của backend, `POST /register` với thư điện tử của một thành viên
// cũ trả `200` kèm một LIÊN KẾT ĐẶT MẬT KHẨU DÙNG ĐƯỢC cho tài khoản của người
// ta — mà mã dòng họ thì CẢ HỌ đang cầm. Nay nó trả `422
// IDENTITY_ALREADY_REGISTERED`, và lần từ chối ấy ĐƯỢC TÍNH vào giới hạn tần
// suất, cố ý: tín hiệu còn sót ("địa chỉ này đã là thành viên") không được phép
// rẻ tới mức quét cả danh bạ dòng họ trong một cửa sổ.
//
// Hai bất biến của giao diện ở đây, theo đúng thứ tự hậu quả:
//   1. KHÔNG một lượt gọi mạng thứ hai nào. Mỗi lần thử đốt hạn mức của chính
//      người dùng ngay tình, để nhận lại đúng câu trả lời cũ.
//   2. Có một lối dẫn sang ĐĂNG NHẬP — lối đi tiếp duy nhất đúng, vì nhánh "đã
//      có token" lấy danh tính từ Keycloak nên không đi qua phép chặn này.
// ══════════════════════════════════════════════════════════════════════════

describe("định danh đã có chủ (422)", () => {
  it("KHÔNG có lượt gọi thứ hai nào — không tự thử lại, không nút “Thử lại”", async () => {
    let luotDangKy = 0;
    let luotKiemMa = 0;
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/lookup`, () => {
        luotKiemMa += 1;
        return HttpResponse.json({
          clanName: "Dòng họ Nguyễn — Đại Lan",
          expiresAt: "2026-10-21T17:00:00Z",
        });
      }),
      http.post(`${API_BASE_URL}/api/v1/clan-invites/register`, () => {
        luotDangKy += 1;
        return HttpResponse.json(
          {
            type: "about:blank",
            title: "Định danh này đã có tài khoản",
            status: 422,
            code: "IDENTITY_ALREADY_REGISTERED",
          },
          { status: 422 }
        );
      })
    );

    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, "ba.lan@ho-nguyen.vn");

    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
    // Bất biến số 1. Nếu có ngày ai đó bật `retry` cho mutation, hoặc thêm
    // `IDENTITY_TAKEN` vào danh sách ca thử lại được, dòng này đỏ.
    expect(luotDangKy).toBe(1);
    expect(screen.queryByRole("button", { name: /Thử lại/ })).toBeNull();

    // Và ngồi yên thêm một nhịp cũng không sinh thêm lượt nào.
    await new Promise((r) => setTimeout(r, 50));
    expect(luotDangKy).toBe(1);
    expect(luotKiemMa).toBe(1);
  });

  it("có một lối dẫn sang đăng nhập, và nút ấy gọi đúng lối đăng nhập của ứng dụng", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, MOCK_CLAN_LOGIN_ID_TAKEN);

    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
    await view.user.click(screen.getByRole("button", { name: /Đăng nhập rồi nhập lại mã/ }));
    expect(view.login).toHaveBeenCalledTimes(1);
    // KHÔNG phải một cú `router.push` tới một tuyến tự chế: đăng nhập đi qua
    // Keycloak, và chỉ `useAuth().login()` biết đường quay lại đúng trang này.
    expect(routerMock.push).not.toHaveBeenCalled();
    // Gõ lại mã vẫn là một việc làm được — nhưng không còn là việc CHÍNH.
    expect(screen.getByRole("button", { name: /Gõ lại mã/ })).toBeTruthy();
  });

  it("câu chữ nói VIỆC CẦN LÀM, không nói “đã có lỗi”, và không xác nhận dứt khoát", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, MOCK_CLAN_LOGIN_ID_TAKEN);

    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
    const p = panel(view.container, "IDENTITY_TAKEN");
    // Câu ĐÃ DỊCH — đọc riêng khỏi câu nguyên văn của máy chủ, vì hai câu ấy
    // chịu hai luật khác nhau: câu của ta không được khẳng định dứt khoát, còn
    // câu của máy chủ là của máy chủ và contract dặn in nguyên văn.
    const cauCuaTa = Array.from(p.querySelectorAll("[data-problem-copy]"))
      .map((el) => el.textContent ?? "")
      .join(" ");

    // Việc cần làm, nói bằng tiếng người.
    expect(cauCuaTa).toContain("đăng nhập");
    expect(cauCuaTa).toContain("nhập lại mã");
    // Trấn an đúng chỗ: mã KHÔNG phải thứ hỏng, nên đừng đi xin mã mới.
    expect(cauCuaTa).toContain("vẫn đúng và vẫn còn lượt");
    // Nguyên nhân hay gặp nhất, và là nguyên nhân người ta không nhớ nổi.
    expect(cauCuaTa).toMatch(/Google/);
    // Lối đi thứ hai, cho người không đăng nhập nổi bằng cách nào cả.
    expect(cauCuaTa).toContain("Trưởng chi");
    // Và nói ra vì sao đừng bấm lại — hạn mức ấy là của chính họ.
    expect(cauCuaTa).toContain("đừng bấm lập tài khoản lại nhiều lần");

    // KHÔNG phải một dải "đã có lỗi xảy ra".
    expect(cauCuaTa).not.toMatch(/đã có lỗi/i);
    // Và KHÔNG một câu khẳng định dứt khoát đem đi dò được: màn này trả lời cho
    // bất kỳ ai gõ một địa chỉ bất kỳ, nên câu của CHÍNH TA không được là một
    // câu trả lời có/không về địa chỉ ấy.
    expect(cauCuaTa).toContain("có thể đã được dùng");
    expect(cauCuaTa).not.toMatch(/địa chỉ này đã|tài khoản này đã tồn tại|đã là thành viên/);
  });

  it("KHÔNG in `detail` của máy chủ ra màn hình — sản phẩm song ngữ", async () => {
    // BẢN ĐẦU CỦA CA NÀY ghim điều ngược lại: rằng màn hình in NGUYÊN VĂN câu
    // `detail`, thêm vào chứ không thay câu đã dịch. Lý lẽ khi ấy là câu của
    // máy chủ mang hai tình tiết quý mà câu dựng sẵn không có — đã từng đăng
    // nhập bằng Google, và lối đi thứ hai là đưa mã cho Trưởng chi.
    //
    // Nó bỏ sót một điều nặng hơn: **sản phẩm này song ngữ**. `detail` chỉ có
    // tiếng Việt, nên với một bà con đang xem giao diện tiếng Anh thì khối ấy
    // là một đoạn tiếng Việt KHÔNG DẤU dán dưới một đoạn tiếng Anh hoàn chỉnh.
    // Đó không phải thêm tình tiết, đó là một lỗi song ngữ.
    //
    // Và nó thừa: hai tình tiết ấy đã nằm trong bộ dịch của chính client, ở cả
    // hai ngôn ngữ — đúng cái lý do hợp đồng dùng một `ProblemCode` ĐÓNG cộng
    // một bộ dịch phía client. `detail` dành cho nhật ký và cho người hỗ trợ
    // đang đọc log.
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, MOCK_CLAN_LOGIN_ID_TAKEN);

    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
    const p = panel(view.container, "IDENTITY_TAKEN");
    expect(p.querySelector("[data-problem-server-detail]")).toBeNull();
    // Không mảnh nào của câu Java lọt lên màn hình — kể cả khi dán vào giữa một
    // đoạn khác.
    expect(p.textContent).not.toContain("Dinh danh nay da co tai khoan");
    expect(p.textContent).not.toMatch(/dang nhap roi nhap lai/i);
  });

  it("câu ĐÃ DỊCH tự nó mang đủ hai tình tiết, ở CẢ HAI ngôn ngữ", async () => {
    // Đây là vế phải của quyết định trên: gỡ câu của máy chủ chỉ đúng nếu bộ
    // dịch thật sự gánh nổi. Hai tình tiết phải có ở cả `vi` lẫn `en`, nếu
    // không thì bản tiếng Anh là bản bị nghèo đi trong im lặng — đúng kiểu hỏng
    // mà không ai thấy cho tới khi một bà con ở xa gọi về.
    for (const [locale, google, truongChi] of [
      ["vi", /Google/, /Trưởng chi/],
      ["en", /Google/, /chi head/],
    ] as const) {
      const view = render(locale);
      await goMa(view, MOCK_CLAN_CODE);
      await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
      await lapTaiKhoan(view, MOCK_CLAN_LOGIN_ID_TAKEN);

      await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
      const p = panel(view.container, "IDENTITY_TAKEN");
      const cauCuaTa = Array.from(p.querySelectorAll("[data-problem-copy]"))
        .map((el) => el.textContent ?? "")
        .join(" ");

      expect(cauCuaTa, `${locale}: thiếu tình tiết Google/Zalo`).toMatch(google);
      expect(cauCuaTa, `${locale}: thiếu lối đi thứ hai`).toMatch(truongChi);
      expect(p.textContent).not.toContain("MISSING_MESSAGE");
      view.unmount();
    }
  });

  it("KHÔNG phát liên kết đặt mật khẩu, và KHÔNG rơi vào màn “xong”", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, MOCK_CLAN_LOGIN_ID_TAKEN);

    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
    // Đây chính là cái lỗ hổng: một liên kết đặt mật khẩu cho tài khoản người
    // khác. Nó không được xuất hiện dưới bất kỳ hình thức nào.
    expect(screen.queryByRole("button", { name: /Đặt mật khẩu/ })).toBeNull();
    expect(view.leave).not.toHaveBeenCalled();
    expect(view.container.querySelector('[data-register-state="DONE"]')).toBeNull();
  });

  it("“Gõ lại mã” đưa về ô mã sạch, không giữ lại nhánh hỏng", async () => {
    const view = render();
    await goMa(view, MOCK_CLAN_CODE);
    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT"));
    await lapTaiKhoan(view, MOCK_CLAN_LOGIN_ID_TAKEN);
    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));

    await view.user.click(screen.getByRole("button", { name: /Gõ lại mã/ }));
    expect(state(view.container)).toBe("CODE");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// 10 · SONG NGỮ, VÀ LỐI ĐI CHO NGƯỜI KHÔNG CÓ MÃ
// ══════════════════════════════════════════════════════════════════════════

describe("song ngữ và cửa 3", () => {
  it("bản tiếng Anh không lọt khoá i18n, và giữ nguyên thuật ngữ dòng họ", async () => {
    const view = render("en");
    await goMa(view, MOCK_CLAN_CODE_EXPIRED);

    await waitFor(() => expect(state(view.container)).toBe("EXPIRED"));
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
    expect(view.container.textContent).toContain("Hội đồng Tộc biểu");
  });

  it("người không có mã được chỉ đường, không bị một bức tường", async () => {
    const view = render();
    // 06 §5.4: phản xạ mặc định "Bạn không có quyền truy cập" sai cả về nghiệp
    // vụ lẫn sự thật — người lạ CÓ quyền xem phần công khai.
    expect(view.container.textContent).not.toContain("không có quyền");
    expect(view.container.textContent).toContain("Ông/bà chưa có mã?");
    expect(view.container.querySelector('[data-guest-mode="notice"]')).not.toBeNull();
    expect(screen.getByRole("button", { name: /Tôi đã có tài khoản/ })).toBeTruthy();
  });

  it("ô mã trống: nói việc cần làm, không gọi máy chủ", async () => {
    const traMa = vi.fn();
    server.use(
      http.post(`${API_BASE_URL}/api/v1/clan-invites/lookup`, () => {
        traMa();
        return HttpResponse.json({ clanName: "X", expiresAt: "2026-10-21T17:00:00Z" });
      })
    );

    const view = render();
    await view.user.click(screen.getByRole("button", { name: /Kiểm mã/ }));

    expect(screen.getByRole("alert").textContent).toContain("Xin gõ mã mời");
    expect(traMa).not.toHaveBeenCalled();
  });
});

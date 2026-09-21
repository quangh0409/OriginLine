import { describe, expect, it, vi, beforeEach } from "vitest";
import { http, HttpResponse } from "msw";
import { screen, waitFor, within } from "@testing-library/react";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { renderWithProviders, FORBIDDEN_PLACEHOLDER_PATTERNS } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import {
  MOCK_INVITATION_CODE,
  MOCK_INVITATION_CODE_EXPIRED,
  MOCK_INVITATION_CODE_NEW_ACCOUNT,
  MOCK_INVITATION_CODE_REVOKED,
  MOCK_INVITATION_CODE_RATE_LIMITED,
  MOCK_INVITATION_CODE_SERVER_DOWN,
  MOCK_INVITATION_CODE_USED,
  MOCK_INVITATION_LOGIN_ID_TAKEN,
  resetInvitationMockState,
} from "@/mocks/handlers/invitation";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/moi/K7M2QD",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/moi/K7M2QD",
}));

/**
 * KHÔNG mock `@/lib/auth/auth-context` ở đây nữa.
 *
 * <p>Bản đầu của bộ kiểm này phải mock cả module, vì `renderWithProviders({ role })`
 * khi ấy chỉ ghim header `x-mock-role` cho MSW và **không dựng
 * `AuthContext.Provider` nào** — nên `useAuth()` rơi về nhánh "ngoài cây
 * provider" (khách) bất kể `role` truyền vào là gì. Màn này là màn đầu tiên
 * vừa đọc `isAuthenticated` vừa phụ thuộc `role` để lấy dữ liệu giả, nên nó
 * cũng là chỗ đầu tiên chỗ lệch ấy lộ ra.</p>
 *
 * <p>Chỗ lệch đã được vá tận gốc ở `tests/setup/render.tsx`: `role` nay quyết
 * định **cả hai** — vai giả của máy chủ mô phỏng và phiên mà giao diện nhìn
 * thấy. Giữ lại bản mock cục bộ sẽ tệ hơn là thừa: mock thay cả module nên
 * `AuthContext` biến mất, và chính `renderWithProviders` gãy.</p>
 */
import { InvitationScreen } from "@/components/auth/invitation-screen";

/**
 * Màn nhận lời mời — `/moi/<mã>`, thiết kế 06 §5.
 *
 * Bộ kiểm này canh ba thứ, theo thứ tự quan trọng:
 *  1. **ba ca mã hỏng** đều ra một màn có câu chữ riêng VÀ một lối đi tiếp —
 *     không ca nào rơi về "Đã có lỗi xảy ra";
 *  2. **mất đường truyền không bị đọc thành mã sai** — nhánh dễ làm sai nhất,
 *     và làm sai thì người dùng đi xin một mã mới cũng vô ích;
 *  3. màn hợp lệ **không lộ quá mức tối thiểu** về một người đang sống.
 */

function state(container: HTMLElement): string | null {
  return container.querySelector("[data-invitation-state]")?.getAttribute("data-invitation-state") ?? null;
}

async function renderInvitation(
  code: string,
  locale: "vi" | "en" = "vi",
  role: "guest" | "member" = "guest"
) {
  const leave = vi.fn();
  const view = renderWithProviders(
    <InvitationScreen code={code} onLeaveForPasswordSetup={leave} />,
    { role, locale }
  );
  await waitFor(() => expect(state(view.container)).not.toBe("LOADING"), { timeout: 5000 });
  return { ...view, leave };
}

/**
 * Gõ định danh (email hoặc số điện thoại) vào ô của `InvitationAccountForm` —
 * khối thay cho nút "Đúng là tôi" đơn giản khi người xem CHƯA đăng nhập — rồi
 * bấm gửi. Nhãn ô dùng lại `auth.register.identifierLabel`, cùng ô với màn
 * đăng ký bằng mã dòng họ.
 */
async function guiDinhDanhVaGui(
  user: Awaited<ReturnType<typeof renderInvitation>>["user"],
  loginId: string
): Promise<void> {
  await user.type(
    await screen.findByLabelText("Thư điện tử hoặc số điện thoại"),
    loginId
  );
  await user.click(screen.getByRole("button", { name: /Đúng là tôi/ }));
}

beforeEach(() => {
  resetInvitationMockState();
});

describe("ba ca mã mời hỏng — đều bình thường, đều có lối đi tiếp", () => {
  it("mã hết hạn: nói rõ hết hạn, vì sao, và việc cần làm", async () => {
    const { container } = await renderInvitation(MOCK_INVITATION_CODE_EXPIRED);

    expect(state(container)).toBe("EXPIRED");
    const panel = container.querySelector('[data-invitation-state="EXPIRED"]')!;
    expect(panel.textContent).toContain("đã hết hạn");
    // Vì sao — một lý do người thường hiểu được, không phải mã lỗi.
    expect(panel.textContent).toContain("bảy ngày");
    // Việc cần làm.
    expect(panel.textContent).toContain("cần một mã mới");
  });

  it("mã đã dùng rồi: mời đăng nhập nếu là mình, và cảnh báo nếu không phải", async () => {
    const { container } = await renderInvitation(MOCK_INVITATION_CODE_USED);

    expect(state(container)).toBe("ALREADY_USED");
    const panel = container.querySelector('[data-invitation-state="ALREADY_USED"]')!;
    expect(panel.textContent).toContain("đã được dùng rồi");
    expect(panel.textContent).toContain("chỉ cần đăng nhập");
    // Ca "tin nhắn tới nhầm người" là ca an toàn thật, không phải chi tiết phụ:
    // một mã đã bị tiêu mà người được mời chưa từng dùng nghĩa là có người khác
    // đã vào bằng nó.
    expect(panel.textContent).toContain("nhầm người");
  });

  it("mã sai / không tồn tại: đoán đúng nguyên nhân hay gặp, không nói 'không có quyền'", async () => {
    const { container } = await renderInvitation("KH0NGC0THAT");

    expect(state(container)).toBe("NOT_FOUND");
    const panel = container.querySelector('[data-invitation-state="NOT_FOUND"]')!;
    expect(panel.textContent).toContain("Không mở được lời mời nào với mã này");
    expect(panel.textContent).toContain("cắt ngắn");
    // 06 §5.4: người lạ không làm gì sai — tuyệt đối không có câu "không có quyền truy cập".
    expect(panel.textContent).not.toMatch(/không có quyền/i);
  });

  it("cả ba ca đều có một chỗ để đi tiếp, không ca nào là ngõ cụt", async () => {
    for (const code of [
      MOCK_INVITATION_CODE_EXPIRED,
      MOCK_INVITATION_CODE_USED,
      "KH0NGC0THAT",
    ]) {
      const view = await renderInvitation(code);
      expect(
        view.container.querySelector('[data-invitation-contact="fallback"]'),
        `mã ${code} không có khối liên hệ`
      ).not.toBeNull();
      view.unmount();
    }
  });

  it("không ca nào hiện mã mời ra màn hình — ảnh chụp màn hình không được mang theo bí mật", async () => {
    for (const code of [
      MOCK_INVITATION_CODE_EXPIRED,
      MOCK_INVITATION_CODE_USED,
      MOCK_INVITATION_CODE_REVOKED,
      "KH0NGC0THAT",
    ]) {
      const view = await renderInvitation(code);
      expect(view.container.textContent ?? "").not.toContain(code);
      view.unmount();
    }
  });

  it("không ca nào rơi về câu lỗi chung của sản phẩm", async () => {
    for (const code of [MOCK_INVITATION_CODE_EXPIRED, MOCK_INVITATION_CODE_USED, "XX"]) {
      const view = await renderInvitation(code);
      expect(view.container.textContent).not.toContain("Đã có lỗi xảy ra");
      expect(view.container.textContent).not.toContain("MISSING_MESSAGE");
      view.unmount();
    }
  });

  it("chỉ ca mã sai mới dẫn sang chế độ khách — hai ca kia việc cần làm là gọi điện", async () => {
    const sai = await renderInvitation("KH0NGC0THAT");
    expect(sai.container.querySelector('[data-guest-mode="notice"]')).not.toBeNull();
    sai.unmount();

    const hetHan = await renderInvitation(MOCK_INVITATION_CODE_EXPIRED);
    expect(hetHan.container.querySelector('[data-guest-mode="notice"]')).toBeNull();
  });
});

describe("chặn tần suất là một câu riêng, không phải mã sai", () => {
  it("429 nói rõ vì sao hệ thống tạm dừng và mã vẫn dùng được", async () => {
    // Ba ca lỗi phân biệt được cộng một mã ngắn nghĩa là dò mã là một tấn công
    // có thật; giới hạn tần suất là lớp chống đỡ thứ ba, ngang hàng với "dùng
    // một lần" và "hết hạn". Người dùng thật gặp nó khi mở đi mở lại vì sốt ruột.
    const { container } = await renderInvitation(MOCK_INVITATION_CODE_RATE_LIMITED);

    expect(state(container)).toBe("RATE_LIMITED");
    const panel = container.querySelector('[data-invitation-state="RATE_LIMITED"]')!;
    expect(panel.textContent).toContain("Xin thử lại sau ít phút");
    expect(panel.textContent).not.toContain("hết hạn");
  });
});

describe("mất đường truyền KHÔNG được đọc thành mã sai", () => {
  it("máy chủ 500 ra nhánh riêng, nói thẳng rằng mã vẫn còn giá trị", async () => {
    const { container } = await renderInvitation(MOCK_INVITATION_CODE_SERVER_DOWN);

    expect(state(container)).toBe("UNAVAILABLE");
    const panel = container.querySelector('[data-invitation-state="UNAVAILABLE"]')!;
    expect(panel.textContent).toContain("không phải do mã mời sai");
    expect(panel.textContent).not.toContain("hết hạn");
  });

  it("chỉ ca đường truyền có nút Thử lại — bấm lại ở ba ca kia là vô ích", async () => {
    const hong = await renderInvitation(MOCK_INVITATION_CODE_SERVER_DOWN);
    expect(within(hong.container).getByRole("button", { name: /Thử lại/ })).toBeInTheDocument();
    hong.unmount();

    const hetHan = await renderInvitation(MOCK_INVITATION_CODE_EXPIRED);
    expect(within(hetHan.container).queryByRole("button", { name: /Thử lại/ })).toBeNull();
  });
});

describe("lời mời còn hiệu lực", () => {
  it("nói đủ ba điều: dòng họ nào, ai mời, mình sẽ là ai trong phả", async () => {
    const { container } = await renderInvitation(MOCK_INVITATION_CODE);

    expect(state(container)).toBe("VALID");
    expect(container.textContent).toContain("Dòng họ Nguyễn — Đại Lan");
    expect(container.textContent).toContain("Lời mời từ Nguyễn Văn Cẩn");
    expect(container.textContent).toContain("Trần Thị Lan");
    expect(container.textContent).toContain("Chi Nhất");
  });

  it("KHÔNG tự thêm kính ngữ vào tên máy chủ gửi về", async () => {
    // Máy chủ trả tên TRẦN vì kính ngữ phụ thuộc quan hệ họ hàng — đó là việc
    // của bộ luật danh xưng, không của trình duyệt. Đoán sai một chữ "ông" cho
    // một người phụ nữ là đúng loại lỗi mà luật ấy sinh ra để chặn.
    const { container } = await renderInvitation(MOCK_INVITATION_CODE);
    const text = container.textContent ?? "";

    expect(text).not.toContain("ông Nguyễn Văn Cẩn");
    expect(text).not.toContain("bà Trần Thị Lan");
    // Sự tôn kính do chức danh dòng tộc gánh, và nó phải có mặt.
    expect(text).toContain("Trưởng Chi Nhất");
  });

  it("đọc trôi chảy dù Giai đoạn 1 không gửi danh xưng với người mời", async () => {
    // `relationToInviter` thuộc context `kinship`, che tên theo người gọi, và
    // người gọi ở đây là khách không token — contract cố ý bỏ trường ấy. Dòng
    // đó phải BIẾN MẤT HẲN, không thành một nhãn trống hay một dấu gạch.
    const { container } = await renderInvitation(MOCK_INVITATION_CODE);

    expect(container.textContent).not.toContain("Con dâu");
    expect(state(container)).toBe("VALID");
    // Khối "Lời mời này dành cho" vẫn còn nội dung thật bên dưới nhãn.
    expect(container.textContent).toContain("Lời mời này dành cho");
    expect(container.textContent).toContain("đời 5");
  });

  it("KHÔNG đọc personId ở màn xem trước — backend cố ý không gửi", async () => {
    // Đó là khoá tra cứu ở mọi endpoint khác, tức một khoá nối bền vững trao
    // cho người CHƯA xác thực. Backend ghim điều này bằng test phản chiếu.
    const { container } = await renderInvitation(MOCK_INVITATION_CODE);
    expect(container.innerHTML).not.toContain("p-140");
  });

  it("nút chính nói rõ đang xác nhận ĐIỀU GÌ, không phải 'Tiếp tục'", async () => {
    await renderInvitation(MOCK_INVITATION_CODE);

    expect(screen.getByRole("button", { name: /Đúng là tôi/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Tiếp tục$/ })).toBeNull();
  });

  it("nói rõ KHÔNG phải chờ duyệt — điểm yếu nhất của phiên đầu tiên", async () => {
    const { container } = await renderInvitation(MOCK_INVITATION_CODE);
    expect(container.textContent).toContain("Không phải chờ duyệt");
  });

  it("chỉ hiện mức tối thiểu để nhận ra mình về một người đang sống", async () => {
    // `role: "member"` — khối lập tài khoản của khách (mục tiêu của bộ kiểm
    // riêng bên dưới) chính đáng chứa các VÍ DỤ định dạng số điện thoại
    // ("0912 345 678", "+84912345678") trong câu gợi ý của Ô ĐỊNH DANH; đó
    // không phải dữ liệu của người được mời và không phải thứ ca này canh.
    const { container } = await renderInvitation(MOCK_INVITATION_CODE, "vi", "member");
    const text = container.textContent ?? "";

    // Ba trường được phép ở Giai đoạn 1: tên, chi, đời. Không hơn. Ba thứ dưới
    // đây là những thứ dễ bị "tiện tay thêm vào" nhất.
    expect(text).not.toMatch(/\bnăm sinh\b/i);
    expect(text).not.toMatch(/\+84|09\d{2}/);
    expect(text).not.toMatch(/@/);
  });

  it("không mượn từ ngữ của một ô trống bị che", async () => {
    const { container } = await renderInvitation(MOCK_INVITATION_CODE);
    const text = container.textContent ?? "";
    for (const pattern of FORBIDDEN_PLACEHOLDER_PATTERNS) {
      if (pattern.source.startsWith("^")) continue;
      expect(pattern.test(text), `khớp mẫu cấm ${pattern}`).toBe(false);
    }
  });

  it("nhận lời mời xong thì nói rõ đã vào phả, và KHÔNG chuyển hướng tới undefined", async () => {
    // Vắng `setPasswordUrl` nghĩa là tài khoản ĐÃ CÓ mật khẩu từ trước — máy chủ cố ý
    // không phát liên kết cho tài khoản như vậy, vì nếu phát thì ai cầm được một mã mời
    // cộng với đoán đúng email của một thành viên cũ sẽ đổi được mật khẩu của người ta.
    // Giao diện phải ở lại và nói việc đã xong, chứ không gọi
    // window.location.assign(undefined).
    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "member");

    await view.user.click(screen.getByRole("button", { name: /Đúng là tôi/ }));

    await waitFor(() => expect(state(view.container)).toBe("ACCEPTED"));
    expect(view.leave).not.toHaveBeenCalled();
    expect(view.container.textContent).toContain("đã có mặt trong phả");
  });

  it("CHƯA đăng nhập thì phải khai định danh trước, và KHÔNG dẫn vào hồ sơ hay phả đồ", async () => {
    // `POST /invitations/accept` KHÔNG còn đòi token, nhưng đòi biết LẬP TÀI
    // KHOẢN BẰNG GÌ — nên nút "Đúng là tôi" của một khách giờ mở ra một ô định
    // danh thay vì gọi suông.
    //
    // BẢN ĐẦU CỦA CA NÀY khẳng định một điều nay KHÔNG CÒN ĐÚNG: rằng một
    // `loginId` trùng tài khoản đã có mật khẩu sẽ ra `ACCEPTED` kèm màn "đăng
    // nhập như thường lệ". Lý lẽ khi ấy vẫn còn giá trị và được giữ lại ở đây:
    // hồ sơ là dữ liệu người còn sống, còn phả đồ với khách chỉ có các cụ đã
    // khuất, nên dẫn họ vào đó là hứa một thứ rồi đưa tới một thứ khác.
    //
    // Thứ đã đổi là trạng thái đích. Sau bản vá lỗ hổng chiếm tài khoản, một
    // định danh ĐÃ CÓ CHỦ bị từ chối ngay — vì phát liên kết đặt mật khẩu cho
    // nó chính là đưa chìa khoá tài khoản người khác cho người đang gõ. Nên ca
    // này nay ghim `IDENTITY_TAKEN`, và giữ nguyên lời khẳng định bảo vệ cũ:
    // dù đi nhánh nào, khách cũng KHÔNG được dẫn vào hồ sơ hay phả đồ.
    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "guest");

    expect(
      screen.getByLabelText("Thư điện tử hoặc số điện thoại")
    ).toBeInTheDocument();
    await guiDinhDanhVaGui(view.user, "ba.lan@example.com");
    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));

    expect(screen.getByRole("button", { name: /đăng nhập/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /hồ sơ của tôi/i })).toBeNull();
    expect(screen.queryByRole("link", { name: /phả đồ/i })).toBeNull();
    // Và không được nói dối rằng có gì đó hỏng.
    //
    // Biểu thức bắt trần chữ "lỗi" đã phải siết lại: câu mới nói **"lỗi không
    // nằm ở mã"** — tức đúng cái điều mà phép kiểm này muốn bảo đảm, chỉ là nó
    // nói bằng chính từ ấy. Ý định không đổi (đừng bảo người dùng rằng có thứ
    // gì đó đã hỏng); thứ đổi là chỉ bắt những cách nói ra điều đó.
    expect(view.container.textContent).not.toMatch(/đã có lỗi|thất bại|không thành công/i);
  });

  it("nếu máy chủ CÓ trả setPasswordUrl thì đi tới đúng URL ấy", async () => {
    // Hình dạng cuối, sau khi adapter Keycloak Admin lên. Ghim ở đây để ngày
    // trường ấy xuất hiện thì hành vi đã được kiểm sẵn, không phải viết lại.
    server.use(
      http.post(API_BASE_URL + "/api/v1/invitations/accept", () =>
        HttpResponse.json({
          appUserId: "u-moi-nhan",
          personId: "p-140",
          status: "ACTIVE",
          setPasswordUrl: "https://kc.example/realms/giapha/login-actions/action-token?key=abc",
        })
      )
    );

    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "member");
    await view.user.click(screen.getByRole("button", { name: /Đúng là tôi/ }));

    await waitFor(() => expect(view.leave).toHaveBeenCalledTimes(1));
    // URL tự ghép ở client sẽ sai realm hoặc thiếu token hành động — và hỏng
    // vào đúng giây đầu tiên người dùng gặp hệ thống.
    expect(view.leave.mock.calls[0]![0]).toContain("login-actions/action-token");
  });

  it("máy chủ cũ đòi token thì nói rõ CẦN ĐĂNG NHẬP, không nói mã sai", async () => {
    // Hợp đồng hiện tại cho `/accept` là `security: []` — KHÔNG đòi token — nên bộ giả
    // lập không được trả `401` ở đường mặc định. Nhưng một bản máy chủ cũ hơn thì còn
    // đòi, nên nhánh giao diện này vẫn là lưới an toàn và vẫn phải được kiểm. Ca `401`
    // dựng tường minh ngay tại đây thay vì bắt bộ giả lập nói dối về API thật.
    //
    // Điều phải giữ: người dùng không làm gì sai và mã của họ vẫn tốt — nói ngược lại
    // là đẩy họ đi gọi điện xin một mã mới cũng sẽ không mở được.
    server.use(
      http.post(API_BASE_URL + "/api/v1/invitations/accept", () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Chưa đăng nhập",
            status: 401,
            detail: "Yêu cầu cần một JWT hợp lệ",
            code: "UNAUTHENTICATED",
          },
          { status: 401, headers: { "content-type": "application/problem+json" } }
        )
      )
    );
    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "guest");

    // Vẫn phải khai định danh trước khi lượt gọi chạm tới máy chủ (cũ) trả
    // `401` — nhánh này là lưới an toàn cho một bản máy chủ CHƯA cập nhật,
    // không phải cho một biểu mẫu bị bỏ trống.
    await guiDinhDanhVaGui(view.user, "ba.lan@example.com");

    await waitFor(() => expect(state(view.container)).toBe("NEEDS_ACCOUNT"));
    const panel = view.container.querySelector('[data-invitation-state="NEEDS_ACCOUNT"]')!;
    expect(panel.textContent).toContain("Mã mời vẫn còn nguyên giá trị");
    expect(panel.textContent).not.toContain("hết hạn");
    expect(panel.textContent).not.toContain("đã được dùng rồi");
  });

  it("hai đầu mối nối bị chiếm là hai câu khác nhau, không phải một lỗi chung", async () => {
    for (const [code, mong, cau] of [
      ["ACCOUNT_ALREADY_LINKED", "ACCOUNT_ALREADY_LINKED", "đã gắn với một người khác"],
      ["PERSON_ALREADY_LINKED", "PERSON_ALREADY_LINKED", "Đã có người nhận lời mời này trước"],
    ] as const) {
      server.use(
        http.post(API_BASE_URL + "/api/v1/invitations/accept", () =>
          HttpResponse.json(
            { type: "about:blank", title: "...", status: 422, code },
            { status: 422 }
          )
        )
      );

      const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "member");
      await view.user.click(screen.getByRole("button", { name: /Đúng là tôi/ }));

      await waitFor(() => expect(state(view.container)).toBe(mong));
      expect(view.container.textContent).toContain(cau);
      view.unmount();
    }
  });
});

describe("chưa đăng nhập: lập tài khoản ngay tại màn nhận lời mời", () => {
  it("bỏ trống định danh rồi bấm gửi → lỗi TẠI Ô, KHÔNG gọi máy chủ", async () => {
    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "guest");

    await view.user.click(screen.getByRole("button", { name: /Đúng là tôi/ }));

    // Lỗi client-side dùng lại đúng câu `auth.register.identifierEmpty`.
    expect(
      await screen.findByText("Xin cho biết thư điện tử hoặc số điện thoại của ông/bà.")
    ).toBeInTheDocument();
    // Vẫn ở màn "VALID" — chưa có lượt gọi nào rời khỏi trình duyệt.
    expect(state(view.container)).toBe("VALID");
  });

  it("người CHƯA có tài khoản Keycloak nào: dừng lại, nhắc đặt mật khẩu, không tự rời SPA", async () => {
    const view = await renderInvitation(
      MOCK_INVITATION_CODE_NEW_ACCOUNT,
      "vi",
      "guest"
    );

    await guiDinhDanhVaGui(view.user, "ba.lan@example.com");

    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT_CREATED"));
    // KHÔNG tự động rời SPA — người dùng cần đọc được rằng việc đã xong.
    expect(view.leave).not.toHaveBeenCalled();
    expect(view.container.textContent).toContain("đặt mật khẩu");

    await view.user.click(screen.getByRole("button", { name: /Đặt mật khẩu/ }));
    expect(view.leave).toHaveBeenCalledTimes(1);
    expect(view.leave.mock.calls[0]![0]).toContain("dat-mat-khau");
  });

  it("gõ một SỐ ĐIỆN THOẠI thì nhắc lưu mật khẩu — liên kết đặt lại mật khẩu đi qua thư mà tài khoản này không có", async () => {
    const view = await renderInvitation(MOCK_INVITATION_CODE_NEW_ACCOUNT, "vi", "guest");

    await guiDinhDanhVaGui(view.user, "0912345678");

    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT_CREATED"));
    // Câu ĐÃ CÓ SẴN ở `auth.register.donePasswordPhoneRecovery` — không phải
    // một câu thứ hai được viết riêng cho màn này.
    expect(
      view.container.querySelector('[data-register-recovery="PHONE"]')
    ).not.toBeNull();
    expect(view.container.textContent).toContain("Trưởng chi của mình");
  });

  it("gõ một ĐỊA CHỈ THƯ thì KHÔNG hiện lời nhắc dành riêng cho số điện thoại", async () => {
    const view = await renderInvitation(MOCK_INVITATION_CODE_NEW_ACCOUNT, "vi", "guest");

    await guiDinhDanhVaGui(view.user, "ba.lan@example.com");

    await waitFor(() => expect(state(view.container)).toBe("ACCOUNT_CREATED"));
    expect(
      view.container.querySelector('[data-register-recovery="PHONE"]')
    ).toBeNull();
  });

  it("`VALIDATION_FAILED` có `detail` → hiện đúng câu ấy, TẠI Ô, không đổi cả màn", async () => {
    server.use(
      http.post(API_BASE_URL + "/api/v1/invitations/accept", () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "...",
            status: 422,
            code: "VALIDATION_FAILED",
            detail: "Dia chi thu dien tu khong hop le",
          },
          { status: 422 }
        )
      )
    );
    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "guest");

    await guiDinhDanhVaGui(view.user, "khong-ra-gi-ca");

    expect(await screen.findByText("Dia chi thu dien tu khong hop le")).toBeInTheDocument();
    // KHÔNG đổi cả màn — vẫn còn đúng ô định danh để sửa lại ngay.
    expect(state(view.container)).toBe("VALID");
    expect(
      screen.getByLabelText("Thư điện tử hoặc số điện thoại")
    ).toBeInTheDocument();
  });

  it('"Tôi đã có tài khoản rồi" đưa sang màn đăng nhập, không đòi gõ định danh', async () => {
    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "guest");

    await view.user.click(
      screen.getByRole("button", { name: /Tôi đã có tài khoản rồi/ })
    );

    // `useAuth().login` của bộ giả lập không điều hướng thật trong test — chỉ
    // cần khẳng định nút này KHÔNG gọi `accept` (không có lượt gọi ra máy chủ,
    // và màn vẫn ở trạng thái "VALID", không phải một trạng thái lỗi).
    expect(state(view.container)).toBe("VALID");
  });
});

// ══════════════════════════════════════════════════════════════════════════
// ĐỊNH DANH ĐÃ CÓ CHỦ — CÙNG MỘT MÃ, CÙNG MỘT CÁCH XỬ VỚI MÀN ĐĂNG KÝ
//
// `POST /invitations/accept` không đòi đăng nhập, nên chuỗi người gọi tự gõ
// không chứng minh được quyền sở hữu định danh ấy. Nếu nó đã có chủ thì bước
// tiếp theo — GHÉP LỜI MỜI VÀO MỘT NHÂN KHẨU TRONG PHẢ — là thao tác trên tài
// sản của người khác. Hai lối vào không cần đăng nhập dùng chung một mã lỗi,
// nên chúng phải dùng chung một cách xử; nếu không sẽ có một lối quên mất nó.
// ══════════════════════════════════════════════════════════════════════════

describe("định danh đã có chủ (422) ở bước nhận lời mời", () => {
  it("KHÔNG có lượt gọi thứ hai nào — kể cả một lượt tải lại lời mời", async () => {
    let luotNhan = 0;
    let luotTraMa = 0;
    server.use(
      http.post(`${API_BASE_URL}/api/v1/invitations/lookup`, () => {
        luotTraMa += 1;
        // Đủ để tấm thẻ dựng được; bài kiểm này đếm LƯỢT GỌI, không đọc nội dung.
        return HttpResponse.json({
          clanName: "Dòng họ Nguyễn — Đại Lan",
          inviter: { displayName: "Nguyễn Văn Cẩn", clanTitle: "Trưởng Chi Nhất" },
          invitee: {
            displayName: "Trần Thị Lan",
            branch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
            generation: 5,
          },
          expiresAt: "2026-09-26T17:00:00Z",
        });
      }),
      http.post(`${API_BASE_URL}/api/v1/invitations/accept`, () => {
        luotNhan += 1;
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

    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "guest");
    const traMaSauKhiTai = luotTraMa;
    await guiDinhDanhVaGui(view.user, "ba.lan@example.com");

    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
    expect(luotNhan).toBe(1);
    // Đây là cái bẫy cụ thể của màn này: nhánh mặc định cho mọi lỗi "không phải
    // UNAVAILABLE" là `preview.refetch()` — một lượt mạng thứ hai. Với mã này
    // nó vô nghĩa (trạng thái của MÃ có đổi đâu) và nó đốt hạn mức chống dò của
    // chính người dùng ngay tình.
    await new Promise((r) => setTimeout(r, 50));
    expect(luotTraMa).toBe(traMaSauKhiTai);
    expect(screen.queryByRole("button", { name: /Thử lại/ })).toBeNull();
  });

  it("dẫn sang đăng nhập, và KHÔNG in `detail` của máy chủ — sản phẩm song ngữ", async () => {
    // BẢN ĐẦU CỦA CA NÀY ghim điều ngược lại: rằng khối `IDENTITY_TAKEN` in
    // NGUYÊN VĂN câu `detail` của `InvitationService`, thêm vào chứ không thay
    // câu đã dịch. Lý lẽ khi ấy — câu Java mang hai tình tiết quý (đã từng
    // đăng nhập bằng Google, và lối đi thứ hai là đưa mã cho Trưởng chi) — vẫn
    // đúng, và chính vì nó đúng nên hai tình tiết ấy đã được viết vào bộ dịch.
    //
    // Thứ lý lẽ ấy bỏ sót: `detail` CHỈ CÓ TIẾNG VIỆT, mà giao diện thì song
    // ngữ. Một bà con ở nước ngoài sẽ thấy một đoạn tiếng Việt không dấu dán
    // dưới một đoạn tiếng Anh hoàn chỉnh. Hợp đồng dùng `ProblemCode` ĐÓNG cộng
    // bộ dịch phía client chính là để tránh chuyện đó; `detail` dành cho nhật
    // ký và cho người hỗ trợ đọc log.
    const view = await renderInvitation(MOCK_INVITATION_CODE, "vi", "guest");
    await guiDinhDanhVaGui(view.user, MOCK_INVITATION_LOGIN_ID_TAKEN);

    await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
    const khoi = view.container.querySelector('[data-invitation-state="IDENTITY_TAKEN"]')!;
    expect(screen.getByRole("button", { name: /Đăng nhập/ })).toBeTruthy();

    expect(khoi.querySelector("[data-problem-server-detail]")).toBeNull();
    // Không mảnh nào của câu Java lọt lên màn hình.
    expect(khoi.textContent).not.toContain("ke ca bang Google");
    expect(khoi.textContent).not.toContain("Dia chi nay co the da duoc dung");

    // Câu đã dịch gánh trọn: trấn an đúng chỗ, và không nói dối rằng có gì hỏng.
    const cauCuaTa = khoi.querySelector('[data-problem-copy="body"]')!.textContent ?? "";
    expect(cauCuaTa).toContain("vẫn còn nguyên giá trị");
    expect(cauCuaTa).not.toMatch(/đã có lỗi/i);
  });

  it("câu ĐÃ DỊCH tự nó mang đủ hai tình tiết, ở CẢ HAI ngôn ngữ", async () => {
    // Vế phải của quyết định trên: gỡ câu của máy chủ chỉ đúng nếu bộ dịch thật
    // sự gánh nổi — ở cả `vi` lẫn `en`. Thiếu một bên thì bản tiếng Anh nghèo
    // đi trong im lặng, đúng kiểu hỏng không ai thấy cho tới khi một bà con ở
    // xa gọi về.
    for (const [locale, google, truongChi] of [
      ["vi", /Google/, /Trưởng chi/],
      ["en", /Google/, /chi head/],
    ] as const) {
      const view = await renderInvitation(MOCK_INVITATION_CODE, locale, "guest");
      await view.user.type(
        await screen.findByLabelText(
          locale === "vi" ? "Thư điện tử hoặc số điện thoại" : "Email address or phone number"
        ),
        MOCK_INVITATION_LOGIN_ID_TAKEN
      );
      await view.user.click(
        screen.getByRole("button", { name: locale === "vi" ? /Đúng là tôi/ : /That is me/ })
      );

      await waitFor(() => expect(state(view.container)).toBe("IDENTITY_TAKEN"));
      const khoi = view.container.querySelector('[data-invitation-state="IDENTITY_TAKEN"]')!;
      const cauCuaTa = Array.from(khoi.querySelectorAll("[data-problem-copy]"))
        .map((el) => el.textContent ?? "")
        .join(" ");

      expect(cauCuaTa, `${locale}: thiếu tình tiết Google/Zalo`).toMatch(google);
      expect(cauCuaTa, `${locale}: thiếu lối đi thứ hai`).toMatch(truongChi);
      expect(khoi.textContent).not.toContain("MISSING_MESSAGE");
      view.unmount();
    }
  });
});

describe('"Không phải tôi" — thao tác không thu hồi được', () => {
  it("hỏi lại và nêu HỆ QUẢ chứ không hỏi suông 'bạn chắc chứ?'", async () => {
    const { user } = await renderInvitation(MOCK_INVITATION_CODE);

    await user.click(screen.getByRole("button", { name: /Không phải tôi/ }));

    const dialog = await screen.findByRole("dialog");
    expect(dialog.textContent).toContain("Trần Thị Lan");
    expect(dialog.textContent).toContain("không thu hồi được");
    expect(dialog.textContent).toContain("người đã mời nhận được thông báo");
  });

  it("huỷ xong thì mã không dùng lại được, và nói rõ phải xin mã mới", async () => {
    const view = await renderInvitation(MOCK_INVITATION_CODE);

    await view.user.click(screen.getByRole("button", { name: /Không phải tôi/ }));
    await view.user.click(await screen.findByRole("button", { name: /Đúng, huỷ mã mời/ }));

    await waitFor(() => expect(state(view.container)).toBe("DECLINED"));
    expect(view.container.textContent).toContain("mã cũ không dùng lại được");
    view.unmount();

    // Mở lại chính mã ấy: máy chủ đã huỷ thật, không chỉ giao diện đổi màn.
    const lanHai = await renderInvitation(MOCK_INVITATION_CODE);
    expect(state(lanHai.container)).toBe("REVOKED");
  });
});

describe("song ngữ", () => {
  it("ba ca hỏng đều có bản tiếng Anh đầy đủ", async () => {
    for (const [code, mong] of [
      [MOCK_INVITATION_CODE_EXPIRED, "has expired"],
      [MOCK_INVITATION_CODE_USED, "already been used"],
      ["KH0NGC0THAT", "No invitation could be opened"],
    ] as const) {
      const view = await renderInvitation(code, "en");
      expect(view.container.textContent).toContain(mong);
      expect(view.container.textContent).not.toContain("MISSING_MESSAGE");
      view.unmount();
    }
  });
});

import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-001/edit",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-001/edit",
}));

const { PersonEditScreen } = await import("@/components/person-form/person-edit-screen");

/**
 * **Không có gì được phép biến mất trong im lặng.**
 *
 * Kịch bản có thật: một Trưởng chi 55 tuổi nhập bảy mục trong nửa tiếng, vuốt
 * lùi một cái là mất sạch, không một câu hỏi. Đó đúng là người mà hệ thống cần
 * nhất — có thẩm quyền nhưng sợ làm hỏng — và mất dữ liệu một lần là họ thôi
 * đóng góp. Trước bộ chốt này, `isDirty` chỉ dùng để in dòng chữ "Có thay đổi
 * chưa lưu" rồi thôi.
 *
 * Ca quan trọng nhất ở đây là ca **huỷ điều hướng thì dữ liệu còn nguyên**:
 * một hộp thoại có hỏi mà hỏi xong vẫn mất chữ thì tệ hơn là không hỏi.
 *
 * `p-001` (Thủy tổ) đã khuất nên hồ sơ công khai, và Quản trị sửa được — không
 * vướng phân tầng riêng tư, để bài test nói đúng về một chuyện.
 */

const EDITABLE_ID = "p-001";

async function openEditor() {
  const rendered = renderWithProviders(<PersonEditScreen personId={EDITABLE_ID} />, {
    role: "admin",
  });
  await screen.findByRole("button", { name: "Lưu thay đổi" }, { timeout: 15_000 });
  return rendered;
}

/** Ô "Nghề nghiệp" — một ô văn bản đơn giản, không dính luật nghiệp vụ nào. */
function occupationInput() {
  return screen.getByLabelText("Nghề nghiệp") as HTMLInputElement;
}

const TYPED = "Thầy đồ";

/**
 * `fireEvent.change` chứ không phải `user.type`.
 *
 * react-hook-form đọc đúng sự kiện `change` của React, nên hai cách cho cùng
 * một kết quả — nhưng `user.type` gõ từng phím qua cả lớp Ant Design trong
 * jsdom và tốn hàng giây cho một câu ngắn, đủ để bài test hết giờ vì lý do
 * chẳng liên quan gì tới thứ nó đang kiểm.
 */
function typeOccupation(value: string) {
  fireEvent.change(occupationInput(), { target: { value } });
}

/** Bộ test này chờ MSW + antd nhiều lần; 45s là mức an toàn trên máy chậm. */
const SLOW = 45_000;

beforeEach(() => {
  resetRouterMock();
  window.sessionStorage.clear();
});

describe("chặn rời trang khi còn thay đổi chưa lưu", () => {
  it("bấm Huỷ khi đang dở dang thì hỏi lại, chưa điều hướng đi đâu cả", async () => {
    const { user } = await openEditor();
    typeOccupation(TYPED);

    await user.click(screen.getByRole("button", { name: "Hủy" }));

    expect(await screen.findByText("Bạn có thay đổi chưa lưu")).toBeInTheDocument();
    expect(routerMock.back).not.toHaveBeenCalled();
  }, SLOW);

  it("HUỶ ĐIỀU HƯỚNG THÌ DỮ LIỆU CÒN NGUYÊN", async () => {
    const { user } = await openEditor();
    typeOccupation(TYPED);

    await user.click(screen.getByRole("button", { name: "Hủy" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Ở lại tiếp tục nhập" }));

    await waitFor(() =>
      expect(screen.queryByText("Bạn có thay đổi chưa lưu")).not.toBeInTheDocument()
    );

    // Đây là điều duy nhất bài test này thật sự bảo vệ: form vẫn gắn trên cây
    // React, mọi ký tự còn nguyên, và không ai bị đá ra khỏi trang.
    expect(occupationInput()).toHaveValue(TYPED);
    expect(routerMock.back).not.toHaveBeenCalled();
    expect(screen.getByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  }, SLOW);

  it("chấp nhận rời đi thì mới điều hướng", async () => {
    const { user } = await openEditor();
    typeOccupation(TYPED);

    await user.click(screen.getByRole("button", { name: "Hủy" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Rời đi, bỏ phần đã nhập" }));

    await waitFor(() => expect(routerMock.back).toHaveBeenCalledTimes(1));
  }, SLOW);

  it("form còn sạch thì Huỷ đi thẳng, không hỏi han gì", async () => {
    const { user } = await openEditor();
    await user.click(screen.getByRole("button", { name: "Hủy" }));

    expect(screen.queryByText("Bạn có thay đổi chưa lưu")).not.toBeInTheDocument();
    await waitFor(() => expect(routerMock.back).toHaveBeenCalledTimes(1));
  });
});

describe("đóng tab / tải lại trang", () => {
  const fireBeforeUnload = () => {
    const event = new Event("beforeunload", { cancelable: true });
    act(() => {
      window.dispatchEvent(event);
    });
    return event;
  };

  it("chặn beforeunload khi đang dở dang", async () => {
    await openEditor();
    typeOccupation(TYPED);

    expect(fireBeforeUnload().defaultPrevented).toBe(true);
  }, SLOW);

  it("không chặn khi chưa sửa gì — hỏi thừa cũng là một kiểu làm phiền", async () => {
    await openEditor();
    expect(fireBeforeUnload().defaultPrevented).toBe(false);
  });
});

describe("vuốt lùi / nút Back của trình duyệt", () => {
  it("huỷ cú lùi, hỏi lại, và giữ nguyên mọi thứ đã gõ", async () => {
    await openEditor();
    typeOccupation(TYPED);

    const pushSpy = vi.spyOn(window.history, "pushState");
    act(() => {
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    // Đẩy lại đúng URL đang xem = huỷ cú lùi. Form không bị tháo khỏi cây React
    // nên chữ còn nguyên — đây là toàn bộ lý do làm theo cách này thay vì để
    // Next điều hướng rồi mới hỏi.
    expect(pushSpy).toHaveBeenCalled();
    expect(await screen.findByText("Bạn có thay đổi chưa lưu")).toBeInTheDocument();
    expect(occupationInput()).toHaveValue(TYPED);
    pushSpy.mockRestore();
  }, SLOW);
});

describe("nháp cục bộ", () => {
  it("ghi nháp vào sessionStorage — KHÔNG phải localStorage", async () => {
    await openEditor();
    typeOccupation(TYPED);

    // Nháp có thể chứa dữ liệu Tầng 3 của người còn sống; `localStorage` sống
    // qua cả lần đóng trình duyệt và biến máy dùng chung ở nhà thờ họ thành
    // kho dữ liệu. `sessionStorage` chết theo tab — đó là mức bền đúng.
    await waitFor(
      () => {
        const keys = Object.keys(window.sessionStorage).filter((k) =>
          k.startsWith("giapha.draft.")
        );
        expect(keys).toHaveLength(1);
      },
      { timeout: 5000 }
    );

    expect(
      Object.keys(window.localStorage).filter((k) => k.startsWith("giapha.draft."))
    ).toHaveLength(0);
  }, SLOW);

  it("khoá nháp gắn với cả nhân khẩu lẫn tài khoản đang đăng nhập", async () => {
    await openEditor();
    typeOccupation(TYPED);

    await waitFor(
      () => {
        const key = Object.keys(window.sessionStorage).find((k) =>
          k.startsWith("giapha.draft.")
        );
        // Hai người dùng chung một máy không bao giờ nhìn thấy nháp của nhau.
        expect(key).toContain(EDITABLE_ID);
        expect(key).toContain("u-admin");
      },
      { timeout: 5000 }
    );
  }, SLOW);

  it("người dùng xoá được nháp ngay trên màn hình, không phải đi tìm trong cài đặt", async () => {
    const { user } = await openEditor();
    typeOccupation(TYPED);

    const discard = await screen.findByRole("button", { name: "Bỏ nháp" }, { timeout: 5000 });
    await user.click(discard);

    await waitFor(() =>
      expect(
        Object.keys(window.sessionStorage).filter((k) => k.startsWith("giapha.draft."))
      ).toHaveLength(0)
    );
  });
});

import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { useQuery } from "@tanstack/react-query";
import { renderWithProviders, FORBIDDEN_PLACEHOLDER_PATTERNS } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import { ApiError } from "@/lib/api/http";
import type { Problem, ProblemCode } from "@/types/api";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-100",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-100",
}));

const { AccountStateNotice } = await import("@/components/auth/account-state-notice");
const { AccountStateGate } = await import("@/components/auth/account-state-gate");
const { accountProblemOf } = await import("@/components/auth/use-account-problem");

/**
 * Trạng thái 4 của thiết kế 06 §7 — **tài khoản chưa nối với ai trong phả**.
 *
 * Ca này hôm nay đang HỎNG THẬT và kiểm được ngay: tài khoản `chuaduyet` đăng
 * nhập được, rồi mọi lời gọi API cần phạm vi chi nhận một `404` mang mã
 * `ACCOUNT_NOT_PROVISIONED`, và bộ xử lý lỗi chung vẽ *"không tìm thấy trang"*.
 * Người dùng kết luận trang hỏng, tải lại, rồi vẫn thế.
 *
 * Bộ kiểm này ghim hai bất biến:
 *  1. giao diện phân nhánh theo **mã lỗi**, nên nó đúng cả trước và sau khi
 *     backend đổi mã HTTP — không có một nhịp nào hai bên lệch nhau;
 *  2. màn thay thế **không in một chữ kỹ thuật nào** ra chỗ người dùng đọc.
 */

/** `ApiError` thật, dựng từ một thân RFC 7807 đúng hình dạng máy chủ trả. */
function apiError(status: number, code: string, title: string): ApiError {
  const problem: Problem = {
    type: "about:blank",
    title,
    status,
    code: code as ProblemCode,
  };
  return new ApiError(status, problem);
}

/** Một truy vấn luôn hỏng với đúng lỗi cho trước — đóng vai "lời gọi API tiếp theo". */
function ManHinhBatKy({ error }: { error: unknown }) {
  useQuery({
    queryKey: ["mot-truy-van-bat-ky"],
    queryFn: () => Promise.reject(error),
    retry: false,
  });
  return <p>Nội dung trang bình thường</p>;
}

describe("phân nhánh theo MÃ LỖI, không theo mã HTTP", () => {
  it("404 mang ACCOUNT_NOT_PROVISIONED được nhận ra — đây là máy chủ hôm nay", () => {
    expect(accountProblemOf(apiError(404, "ACCOUNT_NOT_PROVISIONED", "..."))).toBe(
      "NOT_PROVISIONED"
    );
  });

  it("vẫn nhận ra khi backend sửa xong và đổi sang 403", () => {
    // Đây là lý do tồn tại của phép phân nhánh theo `code`: một agent backend
    // khác đang sửa đúng mã HTTP ấy, và giao diện phải đúng ở CẢ HAI phía của
    // đợt sửa, không cần hẹn giờ triển khai chung.
    expect(accountProblemOf(apiError(403, "ACCOUNT_NOT_PROVISIONED", "..."))).toBe(
      "NOT_PROVISIONED"
    );
    expect(accountProblemOf(apiError(409, "ACCOUNT_NOT_PROVISIONED", "..."))).toBe(
      "NOT_PROVISIONED"
    );
  });

  it("ACCOUNT_NOT_LINKED để nguyên cho hộp thư tự xử — nó đã có câu riêng, cụ thể hơn", () => {
    // `notification-center.tsx` nói đúng chỗ người dùng đang nhìn. Giành lấy
    // mã này là đổi một câu tốt hơn lấy một màn toàn trang.
    expect(accountProblemOf(apiError(404, "ACCOUNT_NOT_LINKED", "..."))).toBeNull();
  });

  it("ACCOUNT_NOT_ACTIVE là một màn khác — chờ duyệt không phải chưa nối", () => {
    expect(accountProblemOf(apiError(403, "ACCOUNT_NOT_ACTIVE", "..."))).toBe("NOT_ACTIVE");
  });

  it("một 404 TRẦN vẫn là 404 trần — không được nuốt thành trạng thái tài khoản", () => {
    // Bất biến quan trọng nhất của phép dò: "khách hỏi hồ sơ một người còn
    // sống" trả đúng 404 mang mã NOT_FOUND. Đọc nó thành "chưa nối tài khoản"
    // sẽ làm trắng toàn bộ ứng dụng ở một ca hoàn toàn bình thường.
    expect(accountProblemOf(apiError(404, "NOT_FOUND", "Không tìm thấy"))).toBeNull();
    expect(accountProblemOf(apiError(403, "FORBIDDEN", "..."))).toBeNull();
    expect(accountProblemOf(new Error("mất mạng"))).toBeNull();
    expect(accountProblemOf(null)).toBeNull();
  });
});

describe("cổng chắn thay nội dung trang bằng lời giải thích", () => {
  it("trang bình thường đi qua nguyên vẹn", async () => {
    renderWithProviders(
      <AccountStateGate>
        <p>Nội dung trang bình thường</p>
      </AccountStateGate>,
      { role: "member" }
    );

    expect(await screen.findByText("Nội dung trang bình thường")).toBeInTheDocument();
  });

  it("KHÁCH không bao giờ rơi vào màn này — họ là khách, không phải thành viên thiếu mắt xích", async () => {
    // Máy chủ trả ACCOUNT_NOT_PROVISIONED cho cả khách vãng lai (họ cũng chưa
    // có dòng `app_user`). Câu "Chưa tìm thấy ông/bà trong phả" nói sai hoàn
    // toàn với người chưa hề đăng nhập, và nó sẽ giành mất câu dành cho khách
    // mà các màn liên quan đã viết đúng.
    const { container } = renderWithProviders(
      <AccountStateGate>
        <ManHinhBatKy error={apiError(403, "ACCOUNT_NOT_PROVISIONED", "...")} />
      </AccountStateGate>,
      { role: "guest" }
    );

    await waitFor(() =>
      expect(screen.getByText("Nội dung trang bình thường")).toBeInTheDocument()
    );
    expect(container.querySelector("[data-account-state]")).toBeNull();
  });

  it("một lời gọi API hỏng vì chưa nối nhân khẩu thì thay cả trang", async () => {
    const { container } = renderWithProviders(
      <AccountStateGate>
        <ManHinhBatKy error={apiError(404, "ACCOUNT_NOT_PROVISIONED", "...")} />
      </AccountStateGate>,
      { role: "member" }
    );

    await waitFor(() =>
      expect(container.querySelector('[data-account-state="NOT_PROVISIONED"]')).not.toBeNull()
    );
    // Và cái "không tìm thấy trang" không còn cơ hội xuất hiện.
    expect(container.textContent).not.toContain("Nội dung trang bình thường");
    expect(container.textContent).toContain("Chưa tìm thấy ông/bà trong phả");
  });

  it("một 404 bình thường KHÔNG làm trắng trang", async () => {
    const { container } = renderWithProviders(
      <AccountStateGate>
        <ManHinhBatKy error={apiError(404, "NOT_FOUND", "Không tìm thấy nhân khẩu này.")} />
      </AccountStateGate>,
      { role: "member" }
    );

    await waitFor(() =>
      expect(screen.getByText("Nội dung trang bình thường")).toBeInTheDocument()
    );
    expect(container.querySelector("[data-account-state]")).toBeNull();
  });
});

describe("câu chữ của màn chưa-nối-phả", () => {
  it("nói việc cần làm, không nói cái gì đã hỏng", () => {
    const { container } = renderWithProviders(
      <AccountStateNotice problem="NOT_PROVISIONED" technicalCode="ACCOUNT_NOT_PROVISIONED" />
    );

    expect(container.textContent).toContain("Việc cần làm");
    expect(container.textContent).toContain("trưởng chi");
    // Không đổ lỗi: người dùng không làm gì sai và không sửa được nó.
    expect(container.textContent).not.toMatch(/bạn đã|ông\/bà đã nhập sai/i);
  });

  it("không in một chữ kỹ thuật nào ở chỗ người dùng đọc", () => {
    const { container } = renderWithProviders(
      <AccountStateNotice problem="NOT_PROVISIONED" technicalCode="ACCOUNT_NOT_PROVISIONED" />
    );

    // Mã lỗi có mặt — nhưng CHỈ bên trong khối gập, mặc định đóng.
    const details = container.querySelector("details[data-account-state-technical]")!;
    expect(details).not.toBeNull();
    expect(details.hasAttribute("open")).toBe(false);
    expect(details.textContent).toContain("ACCOUNT_NOT_PROVISIONED");

    const ngoaiKhoiGap = (container.textContent ?? "").replace(details.textContent ?? "", "");
    expect(ngoaiKhoiGap).not.toContain("ACCOUNT_NOT_PROVISIONED");
    expect(ngoaiKhoiGap).not.toMatch(/provision/i);
    expect(ngoaiKhoiGap).not.toMatch(/\b404\b/);
  });

  it("có ít nhất một lối đi tiếp — không phải một ngõ cụt có màu", () => {
    const { container } = renderWithProviders(
      <AccountStateNotice problem="NOT_PROVISIONED" />
    );

    expect(container.querySelector('[data-guest-mode="notice"]')).not.toBeNull();
    expect(screen.getByRole("link", { name: /phả đồ công khai/i })).toBeInTheDocument();
  });

  it("không mượn từ ngữ của một ô trống bị che", () => {
    const { container } = renderWithProviders(
      <AccountStateNotice problem="NOT_PROVISIONED" technicalCode="ACCOUNT_NOT_PROVISIONED" />
    );
    const text = container.textContent ?? "";
    for (const pattern of FORBIDDEN_PLACEHOLDER_PATTERNS) {
      if (pattern.source.startsWith("^")) continue;
      expect(pattern.test(text), `khớp mẫu cấm ${pattern}`).toBe(false);
    }
  });

  it("bản tiếng Anh đầy đủ, giữ nguyên thuật ngữ dòng họ", () => {
    const { container } = renderWithProviders(
      <AccountStateNotice problem="NOT_PROVISIONED" />,
      { locale: "en" }
    );

    expect(container.textContent).not.toContain("MISSING_MESSAGE");
    // 00 §2.6: thuật ngữ dòng họ giữ nguyên tiếng Việt kèm giải thích ngắn,
    // không dịch thành một từ tiếng Anh xoá mất phân biệt.
    expect(container.textContent).toContain("trưởng chi");
    expect(container.textContent).toContain("Hội đồng Tộc biểu");
  });
});

describe("màn chờ duyệt là một màn KHÁC", () => {
  it("nói đang chờ ai và vẫn làm được gì", () => {
    const { container } = renderWithProviders(<AccountStateNotice problem="NOT_ACTIVE" />);

    expect(container.querySelector('[data-account-state="NOT_ACTIVE"]')).not.toBeNull();
    expect(container.textContent).toContain("đang chờ duyệt");
    expect(container.textContent).toContain("Hội đồng Tộc biểu");
    expect(container.querySelector('[data-guest-mode="notice"]')).not.toBeNull();
  });
});

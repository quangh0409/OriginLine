import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { claimRoutes } from "@/components/claim";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/correction-requests",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/correction-requests",
}));

const { CorrectionScreen } = await import("@/components/correction/correction-screen");

/**
 * NT-MOI-12 — tài khoản đã đăng ký nhưng **chưa gắn vào phả** mở `/correction-requests`.
 *
 * Bấm thật ngày 24/09: màn hình chỉ nói "Bạn chưa gửi đề nghị đính chính nào", trong khi máy chủ
 * từ chối mọi đề nghị của nhóm này (ACCOUNT_NOT_LINKED). Câu ấy đúng về dữ liệu mà sai về ý.
 * `/me` được ghi đè đúng hình dạng máy chủ thật trả cho một tài khoản như vậy.
 */
function meAs(personId: string | null, role = "MEMBER") {
  server.use(
    http.get(`${API_BASE_URL}/api/v1/me`, () =>
      HttpResponse.json({
        appUserId: "u-moi",
        personId,
        role,
        clanWide: false,
        managedBranches: role === "BRANCH_HEAD" ? ["root.chi_nhat"] : [],
        homeBranch: null,
        linkedToTree: personId !== null,
      })
    )
  );
}

describe("tài khoản chưa gắn phả", () => {
  it("được nói thẳng là chưa gửi được đề nghị, kèm lối xem đơn nhận mình — không phải một danh sách rỗng", async () => {
    meAs(null);
    renderWithProviders(<CorrectionScreen />, { role: "member" });
    expect(
      await screen.findByText(
        "Tài khoản của bạn đang chờ Hội đồng Tộc biểu duyệt, chưa gửi được đề nghị."
      )
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem trạng thái đơn nhận mình trong phả" })).toHaveAttribute(
      "href",
      claimRoutes.pending
    );
    expect(screen.queryByText("Bạn chưa gửi đề nghị đính chính nào.")).not.toBeInTheDocument();
  });
});

describe("thành viên đã gắn phả", () => {
  it("không thấy lời nhắc ấy — nó chỉ dành cho người chưa vào phả", async () => {
    meAs("p-010");
    renderWithProviders(<CorrectionScreen />, { role: "member" });
    expect(await screen.findByRole("tab", { name: /Đề nghị của tôi/ })).toBeInTheDocument();
    expect(screen.queryByTestId("correction-not-linked")).not.toBeInTheDocument();
  });
});

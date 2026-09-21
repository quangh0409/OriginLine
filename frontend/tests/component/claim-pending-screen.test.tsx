import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import {
  pendingClaimFixture,
  rejectedClaimFixture,
  resetClaimMockState,
  seedClaimMockState,
} from "@/mocks/handlers/claim";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/nhan-dien/cho-duyet",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/nhan-dien/cho-duyet",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/nhan-dien/cho-duyet",
}));

const { ClaimPendingScreen } = await import("@/components/claim/claim-pending-screen");

/**
 * MÀN "ĐANG CHỜ DUYỆT".
 *
 * Nó chữa một thứ rất cụ thể: **sự im lặng**. Không có màn này thì người dùng
 * gửi đơn xong không biết chuyện gì đang xảy ra và sẽ gửi lại lần hai, lần ba.
 * Nên hai câu nó phải trả lời — *đang chờ ai* và *trong lúc chờ xem được gì* —
 * là hai nhóm ca kiểm nặng nhất ở đây.
 *
 * Nhóm thứ hai còn ghim một quyết định đã chốt mà rất dễ kể nhầm theo hướng
 * rộng rãi hơn thực tế: **người chưa được duyệt KHÔNG viết bài được**. Một danh
 * sách "làm được gì" quên mục ấy sẽ dẫn thẳng tới một nút Viết bài trả về 403.
 */

function renderPending(locale: "vi" | "en" = "vi") {
  resetRouterMock();
  return renderWithProviders(<ClaimPendingScreen />, { role: "member", locale });
}

async function choTaiXong(container: HTMLElement): Promise<void> {
  await waitFor(() =>
    expect(container.querySelector('[data-claim-state="LOADING"]')).toBeNull()
  );
}

beforeEach(() => {
  resetClaimMockState();
});

describe("đang chờ ai", () => {
  it("nêu đúng chi đang giữ đơn và chức danh dòng tộc của người giữ", async () => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });
    const { container } = renderPending();
    await choTaiXong(container);

    await screen.findByText("Đang chờ ai");
    const chu = container.textContent ?? "";
    expect(chu).toContain("Chi Nhất");
    // Chức danh DÒNG TỘC, không phải vai kỹ thuật `BRANCH_HEAD`.
    expect(chu).toContain("Trưởng Chi Nhất");
    expect(chu).not.toMatch(/BRANCH_HEAD/);
  });

  it("lùi về câu dự phòng khi máy chủ chưa gửi kèm tên chi — và câu ấy vẫn đúng", async () => {
    seedClaimMockState({
      claims: [pendingClaimFixture({ targetBranch: null, targetBranchId: null })],
    });
    const { container } = renderPending();
    await choTaiXong(container);

    await screen.findByText("Đang chờ ai");
    // Không bịa một cái tên, và cũng không bỏ trống: thiết kế 06 §7 đã kết luận
    // rằng cả hai đều tệ hơn một câu chung mà đúng.
    expect(container.textContent).toContain("Trưởng chi của chi liên quan");
  });

  it("KHÔNG bịa tên riêng hay số điện thoại của Trưởng chi", async () => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });
    const { container } = renderPending();
    await choTaiXong(container);
    await screen.findByText("Đang chờ ai");

    // Dữ kiện ấy không có trong hợp đồng, và bịa ra thì sai với một người thật.
    expect(container.textContent).not.toMatch(/\d{4}\s?\d{3}\s?\d{3}/);
  });
});

describe("trong lúc chờ xem được gì — phải kể ĐÚNG, kể cả phần không được phép", () => {
  beforeEach(() => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });
  });

  it("nói phả đồ chỉ ở mức tên, đời, quan hệ", async () => {
    const { container } = renderPending();
    await choTaiXong(container);

    await screen.findByText("Trong lúc chờ, ông/bà xem được gì");
    const chu = container.textContent ?? "";
    expect(chu).toContain("tên, đời và quan hệ");
    // Không hứa rộng hơn mức đã chốt.
    expect(chu).not.toMatch(/xem được hồ sơ đầy đủ/);
  });

  it("nói rõ CHƯA viết bài được — quyết định đã chốt, và dễ kể nhầm nhất", async () => {
    const { container } = renderPending();
    await choTaiXong(container);

    await screen.findByText("Trong lúc chờ, ông/bà xem được gì");
    expect(container.textContent).toContain("Chưa viết bài được");
    // Và tuyệt đối không mời họ bấm vào một lối viết bài.
    expect(screen.queryByRole("link", { name: /viết bài/i })).not.toBeInTheDocument();
  });

  it("nói rõ chưa xem được dữ liệu riêng của người đang sống", async () => {
    const { container } = renderPending();
    await choTaiXong(container);

    await screen.findByText("Trong lúc chờ, ông/bà xem được gì");
    expect(container.textContent).toContain("Chưa xem được số điện thoại");
  });
});

describe("đơn 'tôi chưa có trong phả' — nhắc lại rằng hồ sơ CHƯA được lập", () => {
  it("nói ra thành lời, vì người ta quay lại màn này sau nhiều ngày", async () => {
    seedClaimMockState({
      claims: [
        pendingClaimFixture({
          id: "claim-moi-1",
          kind: "NEW_PERSON",
          personId: null,
          declaredName: "Trần Thị Lan",
          relativePersonId: "p-010",
          relativeKind: "SPOUSE",
        }),
      ],
    });
    const { container } = renderPending();
    await choTaiXong(container);

    expect(await screen.findByText(/Trần Thị Lan/)).toBeInTheDocument();
    expect(container.textContent).toContain("chưa được lập");
  });
});

describe("rút đơn", () => {
  it("có lối rút, và nói rõ rút không mất lượt nào", async () => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });
    const { container } = renderPending();
    await choTaiXong(container);

    const nut = await screen.findByRole("button", { name: "Rút đơn này" });
    expect(nut).toBeInTheDocument();
    // Chính câu từ chối của máy chủ bảo người dùng làm việc này; không có nút
    // rút thì lời khuyên ấy dẫn vào ngõ cụt.
    expect(container.textContent).toContain("Rút đơn không mất lượt nào");
  });

  it("rút xong thì đơn chuyển sang trạng thái đã rút", async () => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });
    const { container, user } = renderPending();
    await choTaiXong(container);

    await user.click(await screen.findByRole("button", { name: "Rút đơn này" }));

    await waitFor(() =>
      expect(
        container.querySelector('[data-claim-status="CANCELLED"]')
      ).not.toBeNull()
    );
    expect(screen.getByText("Đã rút")).toBeInTheDocument();
  });
});

describe("đơn bị từ chối", () => {
  it("hiện lý do của Trưởng chi và số lần còn lại, ngay cạnh nút gửi lại", async () => {
    seedClaimMockState({ claims: [rejectedClaimFixture()] });
    const { container } = renderPending();
    await choTaiXong(container);

    expect(await screen.findByText("Trưởng chi trả lời")).toBeInTheDocument();
    const chu = container.textContent ?? "";
    expect(chu).toContain("ông Cẩn chỉ có hai người con");
    expect(chu).toContain("còn 2 lần");
    expect(screen.getByRole("link", { name: "Gửi lại đơn khác" })).toBeInTheDocument();
  });

  it("hết lượt thì bỏ nút gửi lại và chỉ sang một con người", async () => {
    seedClaimMockState({
      claims: [
        rejectedClaimFixture(),
        rejectedClaimFixture({ id: "claim-tu-choi-2" }),
        rejectedClaimFixture({ id: "claim-tu-choi-3" }),
      ],
    });
    const { container } = renderPending();
    await choTaiXong(container);

    await screen.findAllByText("Trưởng chi trả lời");
    expect(screen.queryByRole("link", { name: "Gửi lại đơn khác" })).not.toBeInTheDocument();
    expect(container.textContent).toContain("gọi cho Trưởng chi của mình");
  });
});

describe("chưa gửi đơn nào", () => {
  it("không phải màn trắng, và không phải lỗi — là một lối đi tiếp", async () => {
    const { container } = renderPending();
    await choTaiXong(container);

    expect(await screen.findByText("Ông/bà chưa gửi đơn nào")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Bắt đầu nhận mình trong phả" })
    ).toHaveAttribute("href", "/nhan-dien");
    expect(container.textContent).not.toMatch(/MISSING_MESSAGE/);
  });
});

describe("song ngữ", () => {
  it("bản tiếng Anh không để lọt khoá dịch thiếu", async () => {
    seedClaimMockState({ claims: [pendingClaimFixture()] });
    const { container } = renderPending("en");
    await choTaiXong(container);

    expect(await screen.findByText("Who it is waiting for")).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/MISSING_MESSAGE/);
  });
});

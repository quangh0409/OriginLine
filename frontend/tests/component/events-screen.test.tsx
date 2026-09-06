import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import type { EventDto } from "@/types/api";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/events",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/events",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/events",
}));

const { EventCard } = await import("@/components/events/event-card");
const { EventDualDate } = await import("@/components/events/event-dual-date");
const { EventsScreen } = await import("@/components/events/events-screen");

/**
 * F7 — sự kiện & nhắc giỗ.
 *
 * Ba điều màn hình này bắt buộc phải đúng, vì sai là cả họ đi giỗ nhầm ngày:
 *
 *  1. **Ngày âm đứng trước.** `death_lunar` là nguồn sự thật của một đám giỗ
 *     (CLAUDE.md); ngày dương chỉ là tiện ích cho người sống theo lịch làm
 *     việc Gregory. Thứ tự hiển thị nói lên cái nào là thật.
 *  2. **Tháng nhuận luôn phải hiện.** Tháng 9 nhuận không phải tháng 9. Bỏ
 *     quên chữ "nhuận" là dời đám giỗ đi trọn một tháng mà không có lỗi nào
 *     báo lên.
 *  3. **Mốc nhắc 7 / 3 / 1 ngày (FR-2.2)** hiện đúng như máy chủ sẽ gửi.
 *
 * Ngày dương lấy nguyên từ `nextOccurrenceSolar` do máy chủ tính (Hồ Ngọc
 * Đức, GMT+7). Không có một phép quy đổi nào ở phía client.
 */

function giỗ(overrides: Partial<EventDto> = {}): EventDto {
  return {
    id: "ev-test",
    eventType: "GIO_TO",
    title: "Giỗ Thủy tổ Nguyễn Văn Thủy Tổ",
    lunarDate: { year: 2026, month: 9, day: 22, leap: false },
    nextOccurrenceSolar: "2026-11-01",
    nextOccurrenceLunarYear: 2026,
    daysUntil: 62,
    isClanLevel: true,
    reminderOffsets: [7, 3, 1],
    ...overrides,
  };
}

beforeEach(() => {
  resetRouterMock();
  searchParams = new URLSearchParams();
});

describe("ngày âm và ngày dương trên một thẻ giỗ", () => {
  it("hiện ngày âm bằng chữ đầy đủ 'Ngày 22 tháng 9 âm lịch'", () => {
    renderWithProviders(<EventDualDate event={giỗ()} />, { role: "member" });

    expect(screen.getByText(/Ngày 22 tháng 9 âm lịch/)).toBeInTheDocument();
  });

  it("hiện kèm ngày dương do máy chủ quy đổi, định dạng ngày/tháng/năm", () => {
    renderWithProviders(<EventDualDate event={giỗ()} />, { role: "member" });

    expect(screen.getByText("Dương lịch: 01/11/2026")).toBeInTheDocument();
  });

  it("đặt ngày ÂM trước ngày dương — âm lịch mới là nguồn sự thật của giỗ", () => {
    const { container } = renderWithProviders(<EventDualDate event={giỗ()} />, {
      role: "member",
    });

    const text = container.textContent ?? "";
    expect(text.indexOf("âm lịch")).toBeGreaterThanOrEqual(0);
    expect(text.indexOf("âm lịch")).toBeLessThan(text.indexOf("Dương lịch"));
  });

  it("ĐÁNH DẤU THÁNG NHUẬN — tháng 9 nhuận không phải tháng 9", () => {
    renderWithProviders(
      <EventDualDate event={giỗ({ lunarDate: { year: 2026, month: 9, day: 22, leap: true } })} />,
      { role: "member" }
    );

    expect(screen.getByText("(tháng nhuận)")).toBeInTheDocument();
  });

  it("không gắn chữ 'nhuận' cho tháng thường", () => {
    renderWithProviders(<EventDualDate event={giỗ()} />, { role: "member" });

    expect(screen.queryByText("(tháng nhuận)")).not.toBeInTheDocument();
  });

  it("hiện can chi khi máy chủ có gửi", () => {
    renderWithProviders(
      <EventDualDate
        event={giỗ({
          lunarDate: { year: 2026, month: 9, day: 22, leap: false, canChi: "Bính Ngọ" },
        })}
      />,
      { role: "member" }
    );

    expect(screen.getByText("Bính Ngọ")).toBeInTheDocument();
  });

  it("không bịa ngày dương khi máy chủ chưa quy đổi được", () => {
    const { container } = renderWithProviders(
      <EventDualDate event={giỗ({ nextOccurrenceSolar: null })} />,
      { role: "member" }
    );

    expect(container.textContent).toContain("Ngày 22 tháng 9 âm lịch");
    expect(container.textContent).not.toContain("Dương lịch");
  });
});

describe("mốc nhắc 7 / 3 / 1 ngày (FR-2.2)", () => {
  it("ghi rõ sẽ nhắc trước 7, 3 và 1 ngày", () => {
    renderWithProviders(<EventCard event={giỗ()} />, { role: "member" });

    expect(screen.getByText("Nhắc trước 7, 3, 1 ngày")).toBeInTheDocument();
  });

  it("đếm ngược đúng số ngày còn lại", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: 3 })} />, { role: "member" });

    expect(screen.getByText("Còn 3 ngày")).toBeInTheDocument();
  });

  it("gọi đúng là 'Hôm nay' khi đến ngày", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: 0 })} />, { role: "member" });

    expect(screen.getByText("Hôm nay")).toBeInTheDocument();
  });

  it("nói rõ đã qua bao nhiêu ngày thay vì đếm ngược số âm", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: -2 })} />, { role: "member" });

    expect(screen.getByText("Đã qua 2 ngày")).toBeInTheDocument();
  });

  it("không hiện thẻ đếm ngày khi máy chủ không gửi daysUntil", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: null })} />, { role: "member" });

    expect(screen.queryByText(/Còn \d+ ngày$/)).not.toBeInTheDocument();
    expect(screen.queryByText("Hôm nay")).not.toBeInTheDocument();
  });
});

describe("thẻ giỗ — phạm vi dòng họ / chi", () => {
  it("gắn nhãn 'Cả dòng họ' cho giỗ cấp dòng họ", () => {
    renderWithProviders(<EventCard event={giỗ()} />, { role: "member" });

    expect(screen.getAllByText("Cả dòng họ").length).toBeGreaterThan(0);
    expect(screen.getByText("Giỗ Tổ")).toBeInTheDocument();
  });

  it("gắn tên chi cho giỗ cấp chi", () => {
    renderWithProviders(
      <EventCard
        event={giỗ({
          eventType: "GIO_CHI",
          isClanLevel: false,
          targetBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
        })}
      />,
      { role: "member" }
    );

    expect(screen.getByText("Chi Nhất")).toBeInTheDocument();
    expect(screen.getByText("Giỗ chi")).toBeInTheDocument();
  });

  it("dùng nguyên tiêu đề máy chủ soạn, không tự ghép 'Giỗ ' + tên người", () => {
    // Tự ghép sẽ in ra tên của một người mà người xem có thể không được phép
    // thấy — máy chủ soạn sẵn tiêu đề chính là để tránh việc đó.
    renderWithProviders(<EventCard event={giỗ({ title: "Giỗ cụ Tổ đời thứ nhất" })} />, {
      role: "member",
    });

    expect(
      screen.getByRole("heading", { name: "Giỗ cụ Tổ đời thứ nhất" })
    ).toBeInTheDocument();
  });

  it("thiếu tiêu đề thì lùi về tên loại lễ, không ghép tên người", () => {
    renderWithProviders(<EventCard event={giỗ({ title: null })} />, { role: "member" });

    expect(screen.getByRole("heading", { name: "Giỗ Tổ" })).toBeInTheDocument();
  });
});

describe("màn hình sự kiện", () => {
  it("hiện danh sách sắp tới và lịch mười hai tháng từ cùng một lần tải", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    expect(
      await screen.findByRole("heading", { name: "Sắp tới" }, { timeout: 15_000 })
    ).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Lịch mười hai tháng tới" })).toBeInTheDocument();
  });

  it("lịch năm có đủ 12 ô tháng", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    const calendar = await screen.findByLabelText("Lịch mười hai tháng tới", undefined, {
      timeout: 15_000,
    });
    const monthLabels = within(calendar)
      .getAllByText(/^Tháng \d+$/)
      .map((el) => el.textContent);
    expect(monthLabels).toHaveLength(12);
  });

  it("mỗi ngày giỗ trong danh sách đều mang cả ngày âm lẫn ngày dương", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    const list = await screen.findByRole("heading", { name: "Sắp tới" }, { timeout: 15_000 });
    const section = list.closest("section")!;
    const lunar = within(section).getAllByText(/Ngày \d+ tháng \d+ âm lịch/);
    expect(lunar.length).toBeGreaterThan(0);
    expect(within(section).getAllByText(/^Dương lịch: /).length).toBeGreaterThan(0);
  });

  it("khách vãng lai không thấy sự kiện của người còn sống (mừng thọ)", async () => {
    renderWithProviders(<EventsScreen />, { role: "guest" });

    await screen.findByRole("heading", { name: "Sắp tới" }, { timeout: 15_000 });
    await waitFor(() => {
      expect(screen.queryByText(/Mừng thọ/)).not.toBeInTheDocument();
    });
  });
});

import { describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import type { EventDto } from "@/types/api";

/**
 * F7 Đợt 2 — lịch tháng, kiểu xem MẶC ĐỊNH mới của màn sự kiện.
 *
 * `todayInVietnam` bị GIẢ LẬP qua `vi.mock` từng phần, KHÔNG qua
 * `vi.useFakeTimers()`. Đã thử `vi.useFakeTimers()` + `vi.setSystemTime()`
 * trước — mọi truy vấn bất đồng bộ của Testing Library (`findBy*`, `waitFor`)
 * đợi mãi tới đúng trần 20000ms của Vitest rồi mới báo lỗi, vì bộ đếm giờ giả
 * của Vitest cũng giả luôn cơ chế mà `waitFor` dùng để biết DOM vừa đổi. Giả
 * lập đúng MỘT hàm (`todayInVietnam`) và giữ nguyên mọi thứ khác của module
 * (`buildCalendarMonth`, `addMonths`, `countUnscheduled` vẫn chạy thật) tránh
 * hẳn cái bẫy đó — đồng hồ hệ thống không hề bị đụng tới.
 */
vi.mock("@/lib/format/event", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/format/event")>();
  return {
    ...actual,
    // 15/09/2026 — cùng ngày với `nextOccurrenceSolar` của `giỗ()` bên dưới.
    todayInVietnam: () => ({ year: 2026, month: 9, day: 15 }),
  };
});

const { EventMonthCalendar } = await import("@/components/events/event-month-calendar");

function giỗ(overrides: Partial<EventDto> = {}): EventDto {
  return {
    id: "ev-month-test",
    eventType: "GIO_THUONG",
    title: "Giỗ cụ Nguyễn Văn Kiểm Thử",
    lunarDate: { year: 2026, month: 8, day: 10, leap: false },
    nextOccurrenceSolar: "2026-09-15",
    nextOccurrenceLunarYear: 2026,
    daysUntil: 0,
    isClanLevel: false,
    reminderOffsets: [7, 3, 1],
    ...overrides,
  };
}

describe("EventMonthCalendar — mặc định chọn hôm nay", () => {
  it("tự chọn HÔM NAY và hiện đủ song lịch âm–dương của việc rơi vào ngày ấy", async () => {
    renderWithProviders(<EventMonthCalendar events={[giỗ()]} />, { role: "member" });

    expect(await screen.findByText("Giỗ cụ Nguyễn Văn Kiểm Thử")).toBeInTheDocument();
    // Âm lịch trước — nguồn sự thật của giỗ.
    expect(screen.getByText(/Ngày 10 tháng 8 âm lịch/)).toBeInTheDocument();
    // Dương lịch sau, lấy NGUYÊN VĂN từ nextOccurrenceSolar do máy chủ tính —
    // không có phép quy đổi nào chạy ở đây.
    expect(screen.getByText("Dương lịch: 15/09/2026")).toBeInTheDocument();
  });

  it("ngày không có việc họ thì nói rõ, không để trống mập mờ", async () => {
    renderWithProviders(<EventMonthCalendar events={[]} />, { role: "member" });

    expect(await screen.findByText("Không có giỗ ngày này.")).toBeInTheDocument();
  });

  it("chấm mật độ chỉ xuất hiện ở đúng ngày có việc họ", async () => {
    renderWithProviders(<EventMonthCalendar events={[giỗ()]} />, { role: "member" });

    const withEvent = await screen.findByRole("button", { name: "Ngày 15: 1 việc họ" });
    expect(withEvent).toBeInTheDocument();
    const empty = screen.getByRole("button", { name: "Ngày 16: không có việc họ" });
    expect(empty).toBeInTheDocument();
  });
});

describe("EventMonthCalendar — điều hướng trong đúng cửa sổ 12 tháng đã tải", () => {
  it("khoá 'Tháng trước' ngay ở tháng hiện tại — không lùi về quá khứ", async () => {
    renderWithProviders(<EventMonthCalendar events={[]} />, { role: "member" });

    await screen.findByText("Tháng 9 2026");
    expect(screen.getByLabelText("Tháng trước")).toBeDisabled();
    expect(screen.getByLabelText("Tháng sau")).not.toBeDisabled();
  });

  it("khoá 'Tháng sau' đúng ở tháng thứ 12 của cửa sổ, không đi xa hơn", async () => {
    renderWithProviders(<EventMonthCalendar events={[]} />, { role: "member" });

    await screen.findByText("Tháng 9 2026");
    const next = screen.getByLabelText("Tháng sau");
    for (let i = 0; i < 11; i += 1) {
      fireEvent.click(next);
    }

    // 9/2026 + 11 tháng = 8/2027.
    await waitFor(() => {
      expect(screen.getByText("Tháng 8 2027")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("Tháng sau")).toBeDisabled();
  });
});

describe("EventMonthCalendar — sàn vùng chạm và sàn chữ (định hướng 00 §2.2, không ngoại lệ)", () => {
  it("ô ngày đạt sàn 44px (min-h-11) và số ngày đạt sàn 16px (text-than)", async () => {
    renderWithProviders(<EventMonthCalendar events={[giỗ()]} />, { role: "member" });

    const dayButton = await screen.findByRole("button", { name: "Ngày 15: 1 việc họ" });
    expect(dayButton.className).toMatch(/\bmin-h-11\b/);
    expect(dayButton.className).toMatch(/\btext-than\b/);
  });

  it("nút chuyển tháng cũng đạt cả hai chiều của sàn 44×44px", async () => {
    renderWithProviders(<EventMonthCalendar events={[]} />, { role: "member" });

    const next = await screen.findByLabelText("Tháng sau");
    expect(next.className).toMatch(/\bmin-h-11\b/);
    expect(next.className).toMatch(/\bmin-w-11\b/);
  });
});

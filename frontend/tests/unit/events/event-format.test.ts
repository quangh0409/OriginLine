import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildCalendarYear,
  eventUrgency,
  parseSolar,
  todayInVietnam,
} from "@/lib/format/event";
import type { EventDto } from "@/types/api";

/**
 * F7 — các hàm định dạng cho lịch giỗ.
 *
 * Nguyên tắc bất di bất dịch: module này ĐỌC ngày chứ không QUY ĐỔI ngày.
 * Việc quy đổi âm ↔ dương là của backend (thuật toán Hồ Ngọc Đức, GMT+7, có
 * tháng nhuận và tiết khí). Một phép quy đổi làm ở trình duyệt sẽ sai nguyên
 * một tháng quanh tháng nhuận mà không ném ra lỗi nào — đúng loại lỗi khiến
 * cả họ đi giỗ nhầm ngày.
 *
 * Mốc nhắc 7 / 3 / 1 ngày (FR-2.2) phải khớp giữa thứ màn hình tô đậm và thứ
 * máy chủ thật sự gửi đi.
 */

function giỗ(overrides: Partial<EventDto> = {}): EventDto {
  return {
    id: "ev-1",
    eventType: "GIO_TO",
    title: "Giỗ Nguyễn Văn Thủy Tổ",
    lunarDate: { year: 2026, month: 9, day: 22, leap: false },
    nextOccurrenceSolar: "2026-11-01",
    nextOccurrenceLunarYear: 2026,
    daysUntil: 30,
    isClanLevel: true,
    reminderOffsets: [7, 3, 1],
    ...overrides,
  };
}

describe("parseSolar", () => {
  it("tách được ISO YYYY-MM-DD", () => {
    expect(parseSolar("2026-11-01")).toEqual({ year: 2026, month: 11, day: 1 });
  });

  it("trả undefined cho chuỗi thiếu ngày, thay vì đoán mò ngày mùng 1", () => {
    expect(parseSolar("2026-11")).toBeUndefined();
    expect(parseSolar("01/11/2026")).toBeUndefined();
  });

  it("trả undefined cho giá trị vắng mặt (trường bị lọc theo tầng riêng tư)", () => {
    expect(parseSolar(null)).toBeUndefined();
    expect(parseSolar(undefined)).toBeUndefined();
    expect(parseSolar("   ")).toBeUndefined();
  });
});

describe("eventUrgency — bám đúng mốc nhắc 7 / 3 / 1 ngày (FR-2.2)", () => {
  it("hôm nay là TODAY", () => {
    expect(eventUrgency(0)).toBe("TODAY");
  });

  it("còn 1 và 3 ngày đều là IMMINENT — hai mốc nhắc gấp", () => {
    expect(eventUrgency(1)).toBe("IMMINENT");
    expect(eventUrgency(3)).toBe("IMMINENT");
  });

  it("còn 4 đến 7 ngày là SOON — mốc nhắc sớm nhất", () => {
    expect(eventUrgency(4)).toBe("SOON");
    expect(eventUrgency(7)).toBe("SOON");
  });

  it("quá 7 ngày thì chưa có lời nhắc nào, nên chỉ là LATER", () => {
    expect(eventUrgency(8)).toBe("LATER");
    expect(eventUrgency(365)).toBe("LATER");
  });

  it("ngày đã qua là PAST", () => {
    expect(eventUrgency(-1)).toBe("PAST");
  });

  it("thiếu daysUntil thì lùi về LATER, không tự suy ra ngày", () => {
    expect(eventUrgency(null)).toBe("LATER");
    expect(eventUrgency(undefined)).toBe("LATER");
  });

  it("ranh giới giữa các mốc rơi đúng chỗ chuyển nhóm", () => {
    // 3 -> 4 và 7 -> 8 là hai chỗ đổi nhóm duy nhất.
    expect([3, 4].map(eventUrgency)).toEqual(["IMMINENT", "SOON"]);
    expect([7, 8].map(eventUrgency)).toEqual(["SOON", "LATER"]);
  });
});

describe("buildCalendarYear — lịch mười hai tháng", () => {
  it("luôn trả đủ 12 tháng liên tiếp, kể cả tháng không có giỗ nào", () => {
    const { months } = buildCalendarYear([], 2026, 1);

    expect(months).toHaveLength(12);
    expect(months.map((m) => m.month)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
    expect(months.every((m) => m.year === 2026)).toBe(true);
  });

  it("vắt qua năm dương khi cửa sổ bắt đầu giữa năm (khoảng Tết)", () => {
    const { months } = buildCalendarYear([], 2026, 11);

    expect(months[0]).toMatchObject({ month: 11, year: 2026 });
    expect(months[2]).toMatchObject({ month: 1, year: 2027 });
    expect(months[11]).toMatchObject({ month: 10, year: 2027 });
  });

  it("xếp sự kiện vào đúng ô tháng của ngày dương kế tiếp", () => {
    const event = giỗ({ nextOccurrenceSolar: "2026-03-14" });
    const { months } = buildCalendarYear([event], 2026, 1);

    const march = months.find((m) => m.month === 3 && m.year === 2026)!;
    expect(march.events.map((e) => e.id)).toEqual(["ev-1"]);
  });

  it("sắp xếp trong tháng theo ngày tăng dần", () => {
    const events = [
      giỗ({ id: "muộn", nextOccurrenceSolar: "2026-03-28" }),
      giỗ({ id: "sớm", nextOccurrenceSolar: "2026-03-02" }),
      giỗ({ id: "giữa", nextOccurrenceSolar: "2026-03-15" }),
    ];
    const { months } = buildCalendarYear(events, 2026, 1);

    const march = months.find((m) => m.month === 3)!;
    expect(march.events.map((e) => e.id)).toEqual(["sớm", "giữa", "muộn"]);
  });

  it("KHÔNG bỏ rơi sự kiện thiếu ngày dương — trả riêng ở 'unscheduled'", () => {
    const events = [
      giỗ({ id: "có-ngày", nextOccurrenceSolar: "2026-05-05" }),
      giỗ({ id: "chưa-quy-đổi", nextOccurrenceSolar: null }),
    ];
    const { months, unscheduled } = buildCalendarYear(events, 2026, 1);

    expect(unscheduled.map((e) => e.id)).toEqual(["chưa-quy-đổi"]);
    expect(months.flatMap((m) => m.events).map((e) => e.id)).toEqual(["có-ngày"]);
  });

  it("sự kiện rơi ngoài cửa sổ 12 tháng cũng vào 'unscheduled', không bị mất", () => {
    const events = [giỗ({ id: "xa", nextOccurrenceSolar: "2030-01-01" })];
    const { months, unscheduled } = buildCalendarYear(events, 2026, 1);

    expect(months.flatMap((m) => m.events)).toHaveLength(0);
    expect(unscheduled.map((e) => e.id)).toEqual(["xa"]);
  });

  it("không quy đổi gì cả — dữ liệu âm lịch của sự kiện được giữ nguyên", () => {
    const event = giỗ({ lunarDate: { year: 2026, month: 9, day: 22, leap: true } });
    const { months } = buildCalendarYear([event], 2026, 1);

    const placed = months.flatMap((m) => m.events)[0]!;
    expect(placed.lunarDate).toEqual({ year: 2026, month: 9, day: 22, leap: true });
    // Ô tháng đến từ ngày DƯƠNG do máy chủ tính (11), không phải tháng âm (9).
    expect(months.find((m) => m.events.includes(placed))!.month).toBe(11);
  });
});

describe("todayInVietnam", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("trả ngày ở Việt Nam, không phải ngày của máy người dùng", () => {
    // 22:00 ngày 20/07 giờ California = 12:00 ngày 21/07 giờ Hà Nội.
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-07-21T05:00:00Z"));

    expect(todayInVietnam()).toEqual({ year: 2026, month: 7, day: 21 });
  });

  it("qua nửa đêm ở Hà Nội thì đã sang ngày mới, dù nơi khác còn hôm trước", () => {
    vi.useFakeTimers();
    // 17:30 UTC = 00:30 hôm sau ở GMT+7.
    vi.setSystemTime(new Date("2026-07-20T17:30:00Z"));

    expect(todayInVietnam()).toEqual({ year: 2026, month: 7, day: 21 });
  });
});

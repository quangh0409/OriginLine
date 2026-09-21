import type { EventDto } from "@/types/api";
import { isPresent } from "@/lib/privacy/present";

/**
 * Grouping and label helpers for the giỗ calendar. Like
 * src/lib/format/date-dual.ts, this module PARSES but never CONVERTS: the
 * only date arithmetic allowed here is reading `YYYY-MM-DD` apart.
 *
 * `nextOccurrenceSolar` is computed by the backend from `lunarDate` with the
 * Hồ Ngọc Đức algorithm at GMT+7, leap months included. Recomputing it in the
 * browser — even "just to fill a gap in the calendar" — is wrong by a whole
 * month around a tháng nhuận and throws no error while being wrong
 * (contracts/README §7.8). If the server didn't send a solar date, the event
 * has no place on the solar calendar, full stop.
 */

export interface SolarParts {
  year: number;
  month: number; // 1-12
  day: number; // 1-31
}

export function parseSolar(iso: string | null | undefined): SolarParts | undefined {
  if (!isPresent(iso)) return undefined;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim());
  if (!match) return undefined;
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

export interface CalendarMonth {
  /** 1-12. */
  month: number;
  /** Calendar year this month belongs to — the window can straddle Tết. */
  year: number;
  events: EventDto[];
}

/**
 * Twelve consecutive months starting at `startYear`/`startMonth`, each with
 * the events whose next occurrence falls inside it.
 *
 * Months are emitted even when empty: a year view with holes in it is the
 * point — it shows the family when the quiet stretches are. Events with no
 * `nextOccurrenceSolar` are returned separately rather than dropped, because
 * silently losing a giỗ is the one failure mode this screen must not have.
 */
export function buildCalendarYear(
  events: EventDto[],
  startYear: number,
  startMonth = 1
): { months: CalendarMonth[]; unscheduled: EventDto[] } {
  const months: CalendarMonth[] = [];
  const index = new Map<string, CalendarMonth>();

  for (let offset = 0; offset < 12; offset += 1) {
    const absolute = startMonth - 1 + offset;
    const cell: CalendarMonth = {
      month: (absolute % 12) + 1,
      year: startYear + Math.floor(absolute / 12),
      events: [],
    };
    months.push(cell);
    index.set(`${cell.year}-${cell.month}`, cell);
  }

  const unscheduled: EventDto[] = [];
  for (const event of events) {
    const solar = parseSolar(event.nextOccurrenceSolar);
    if (!solar) {
      unscheduled.push(event);
      continue;
    }
    const cell = index.get(`${solar.year}-${solar.month}`);
    if (cell) cell.events.push(event);
    else unscheduled.push(event);
  }

  for (const cell of months) {
    cell.events.sort(
      (a, b) => (parseSolar(a.nextOccurrenceSolar)?.day ?? 0) - (parseSolar(b.nextOccurrenceSolar)?.day ?? 0)
    );
  }

  return { months, unscheduled };
}

/** Zero-padded `YYYY-MM-DD` from parts already known to be a valid Gregorian date. */
function isoOfParts(year: number, month: number, day: number): string {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/**
 * `startYear`/`startMonth` shifted by `deltaMonths` (may be negative).
 *
 * Pure Gregorian calendar arithmetic — the same kind `buildCalendarYear`
 * already does to walk from a start month to the next twelve. Nothing here
 * touches the lunar calendar; see the module javadoc.
 */
export function addMonths(
  year: number,
  month: number,
  deltaMonths: number
): { year: number; month: number } {
  const total = year * 12 + (month - 1) + deltaMonths;
  return { year: Math.floor(total / 12), month: (((total % 12) + 12) % 12) + 1 };
}

export interface CalendarDay {
  /** Solar day-of-month, 1-31. */
  day: number;
  /** ISO date (`YYYY-MM-DD`) of this cell — stable key and lookup. */
  iso: string;
  /** Falls inside the requested month, as opposed to a leading/trailing filler day. */
  inMonth: boolean;
  events: EventDto[];
}

export interface CalendarMonthGrid {
  year: number;
  /** 1-12. */
  month: number;
  /** Sunday-first weeks of exactly 7 days each, always full weeks. */
  weeks: CalendarDay[][];
}

/**
 * One month as a day grid — lịch tháng (plan §4 F7, Đợt 2).
 *
 * Placement uses only `nextOccurrenceSolar`, exactly like `buildCalendarYear`
 * — this function does not know what a lunar month is. An event with no
 * computable solar date simply does not appear in any day cell; count it
 * with {@link countUnscheduled} and show that count next to the grid so a
 * giỗ awaiting conversion never disappears without a trace.
 *
 * Leading/trailing days from the adjacent month are included so every week
 * row has exactly 7 cells (`inMonth: false` for those), which is what lets
 * the caller render a rectangular grid without special-casing the first and
 * last rows.
 */
export function buildCalendarMonth(events: EventDto[], year: number, month: number): CalendarMonthGrid {
  const byIso = new Map<string, EventDto[]>();
  for (const event of events) {
    const solar = parseSolar(event.nextOccurrenceSolar);
    if (!solar) continue;
    const iso = isoOfParts(solar.year, solar.month, solar.day);
    const bucket = byIso.get(iso);
    if (bucket) bucket.push(event);
    else byIso.set(iso, [event]);
  }

  const firstOfMonthUtc = Date.UTC(year, month - 1, 1);
  const firstWeekday = new Date(firstOfMonthUtc).getUTCDay(); // 0 = CN (Sunday)
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const totalCells = Math.ceil((firstWeekday + daysInMonth) / 7) * 7;
  const DAY_MS = 24 * 60 * 60 * 1000;

  const cells: CalendarDay[] = [];
  for (let i = 0; i < totalCells; i += 1) {
    const date = new Date(firstOfMonthUtc + (i - firstWeekday) * DAY_MS);
    const cellYear = date.getUTCFullYear();
    const cellMonth = date.getUTCMonth() + 1;
    const cellDay = date.getUTCDate();
    const iso = isoOfParts(cellYear, cellMonth, cellDay);
    cells.push({
      day: cellDay,
      iso,
      inMonth: cellYear === year && cellMonth === month,
      events: byIso.get(iso) ?? [],
    });
  }

  const weeks: CalendarDay[][] = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));

  return { year, month, weeks };
}

/**
 * Sự kiện KHÔNG có ngày dương kế tiếp (chưa quy đổi được — `LUNAR_CONVERSION_FAILED`
 * hoặc tương tự). `buildCalendarMonth` không đặt được các sự kiện này vào ô
 * nào, nên đếm riêng để màn hình còn một chỗ nói "còn N việc chưa có ngày",
 * đúng nguyên tắc "không bao giờ để một giỗ biến mất trong im lặng" của
 * `buildCalendarYear`'s `unscheduled`.
 */
export function countUnscheduled(events: EventDto[]): number {
  return events.filter((event) => !parseSolar(event.nextOccurrenceSolar)).length;
}

/**
 * Urgency bucket for a `daysUntil`, used only to pick a visual emphasis.
 * Mirrors the reminder offsets in FR-2.2 (7 / 3 / 1 days) so what the screen
 * highlights matches what the server will actually send a reminder about.
 */
export type EventUrgency = "PAST" | "TODAY" | "IMMINENT" | "SOON" | "LATER";

export function eventUrgency(daysUntil: number | null | undefined): EventUrgency {
  if (!isPresent(daysUntil)) return "LATER";
  if (daysUntil < 0) return "PAST";
  if (daysUntil === 0) return "TODAY";
  if (daysUntil <= 3) return "IMMINENT";
  if (daysUntil <= 7) return "SOON";
  return "LATER";
}

/**
 * Today's SOLAR date in Vietnam (GMT+7), regardless of where the phone is.
 *
 * A member in California opening the app at 22:00 local is already on
 * tomorrow in Hà Nội, and a giỗ that "starts today" back home must read that
 * way — the whole point of the product is that distance doesn't cost you the
 * date. This is a timezone shift only, never a calendar conversion: it says
 * which Gregorian day it is in Vietnam, and nothing about the lunar date.
 *
 * MUST be called after mount (never during SSR render) — the server and the
 * browser can straddle midnight and produce different markup.
 */
export function todayInVietnam(): SolarParts {
  const iso = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  return parseSolar(iso) ?? { year: new Date().getUTCFullYear(), month: 1, day: 1 };
}

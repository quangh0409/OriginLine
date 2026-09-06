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

import type { DateDual, LunarDate } from "@/types/api";
import { isPresent } from "@/lib/privacy/present";

/**
 * Formatting ONLY. This module never converts between lunar and solar — that
 * is a backend concern (Hồ Ngọc Đức algorithm at GMT+7, leap months, tiết khí;
 * contracts/README §7.8 calls a client-side conversion "the quietest bug in
 * the system", because it is wrong by a whole month with no error raised).
 * Whatever the API sent for `solar` and `lunar` is what we render.
 *
 * `precision` narrows what we are ALLOWED to show: a living person at Tier 2
 * gets `birth` truncated to the year, and the wire value may still carry a
 * `-01-01` filler that must never reach the screen as a real day.
 */

/** Parsed pieces of an ISO `YYYY-MM-DD`; null when the string isn't usable. */
function splitSolar(solar: string): { y: string; m: string; d: string } | null {
  const match = /^(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?/.exec(solar.trim());
  if (!match?.[1]) return null;
  return { y: match[1], m: match[2] ?? "", d: match[3] ?? "" };
}

/**
 * Solar date honouring `precision`:
 *   DAY -> `20/07/1990` · MONTH -> `07/1990` · YEAR -> `1990` · UNKNOWN -> absent.
 * Day-first matches Vietnamese convention in both locales of this product.
 */
export function formatSolar(date: DateDual | null | undefined): string | undefined {
  if (!isPresent(date) || !isPresent(date.solar)) return undefined;
  const parts = splitSolar(date.solar);
  if (!parts) return undefined;

  switch (date.precision) {
    case "DAY":
      return parts.d && parts.m ? `${parts.d}/${parts.m}/${parts.y}` : parts.y;
    case "MONTH":
      return parts.m ? `${parts.m}/${parts.y}` : parts.y;
    case "YEAR":
      return parts.y;
    default:
      return undefined;
  }
}

/** Just the solar year, for compact contexts (cards, pickers). */
export function solarYear(date: DateDual | null | undefined): string | undefined {
  if (!isPresent(date) || !isPresent(date.solar)) return undefined;
  return splitSolar(date.solar)?.y;
}

export interface LunarParts {
  day: number;
  month: number;
  year: number;
  leap: boolean;
  canChi?: string;
  yearLabel?: string;
  /** How much of the lunar date the precision permits showing. */
  granularity: "DAY" | "MONTH" | "YEAR";
}

/**
 * Lunar pieces for the UI to compose with an ICU message (so "ngày 22 tháng 9
 * âm lịch" and "the 22nd of the 9th lunar month" can differ structurally).
 * `leap` is surfaced explicitly and must always be rendered when true —
 * silently dropping it is the classic giỗ-off-by-a-month bug.
 */
export function lunarParts(date: DateDual | null | undefined): LunarParts | undefined {
  if (!isPresent(date) || !isPresent(date.lunar)) return undefined;
  const lunar: LunarDate = date.lunar;
  if (date.precision === "UNKNOWN") return undefined;

  return {
    day: lunar.day,
    month: lunar.month,
    year: lunar.year,
    leap: lunar.leap,
    canChi: isPresent(lunar.canChi) ? lunar.canChi : undefined,
    yearLabel: isPresent(lunar.yearLabel) ? lunar.yearLabel : undefined,
    granularity:
      date.precision === "DAY" ? "DAY" : date.precision === "MONTH" ? "MONTH" : "YEAR",
  };
}

/** True when a date carries anything worth rendering at all. */
export function hasDisplayableDate(date: DateDual | null | undefined): boolean {
  return formatSolar(date) !== undefined || lunarParts(date) !== undefined;
}

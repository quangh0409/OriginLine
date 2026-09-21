"use client";

import { useTranslations } from "next-intl";
import { parseSolar } from "@/lib/format/event";
import { isPresent } from "@/lib/privacy/present";
import type { EventDto } from "@/types/api";

/**
 * A giỗ shown in BOTH calendars, lunar first.
 *
 * Order is not cosmetic: `death_lunar` is the source of truth for a giỗ
 * (CLAUDE.md domain rules), and the solar date is a derived convenience for
 * people living on a Gregorian work calendar. Putting the lunar date first
 * keeps the screen honest about which one the family actually keeps.
 *
 * `leap` is always rendered when true. Dropping it silently moves a giỗ by a
 * whole month — tháng 9 nhuận is not tháng 9.
 */
export function EventDualDate({ event }: { event: EventDto }) {
  const t = useTranslations("events");
  const solar = parseSolar(event.nextOccurrenceSolar);
  const lunar = event.lunarDate;

  return (
    // `max-w-full` is load-bearing: an `inline-flex` box sizes to max-content, so one long
    // value pushes the box itself past the `<dd>` around it. The `min-w-0` on that `<dd>`
    // cannot help — the overflow is in the CHILD box. Cap the box that overflows, and the
    // `overflow-wrap: break-word` floor then wraps the text as usual.
    <span className="inline-flex max-w-full flex-col gap-0.5">
      <span className="text-than font-medium text-text-main">
        {t("lunarDayMonth", { day: lunar.day, month: lunar.month })}
        {lunar.leap && <span className="ml-1 font-semibold text-accent">{t("lunarLeap")}</span>}
        {isPresent(event.nextOccurrenceLunarYear) && (
          <span className="ml-1 font-normal text-text-muted">
            {t("lunarYear", { year: event.nextOccurrenceLunarYear })}
          </span>
        )}
      </span>

      {solar && (
        <span className="text-than text-text-muted">
          {t("solarDate", {
            date: `${String(solar.day).padStart(2, "0")}/${String(solar.month).padStart(2, "0")}/${solar.year}`,
          })}
        </span>
      )}

      {isPresent(lunar.canChi) && (
        <span className="text-than text-text-muted">{lunar.canChi}</span>
      )}
    </span>
  );
}

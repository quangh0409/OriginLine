"use client";

import { useTranslations } from "next-intl";
import { formatSolar, lunarParts } from "@/lib/format/date-dual";
import type { DateDual } from "@/types/api";

export interface DualDateProps {
  date: DateDual | null | undefined;
}

/**
 * One date shown in BOTH calendars, solar above, lunar below.
 *
 * Neither value is derived from the other: the backend sends both (Hồ Ngọc
 * Đức, GMT+7). Converting on the client would be wrong by a whole month
 * around a leap month and would fail silently — the quietest bug in this
 * system (contracts/README §7.8).
 *
 * `precision` decides how much is allowed on screen: a living person's birth
 * arrives truncated to the year, and printing a fabricated day would be both
 * false and a privacy regression. The leap-month marker is never dropped when
 * present — a giỗ on tháng 9 nhuận is not the same day as tháng 9.
 */
export function DualDate({ date }: DualDateProps) {
  const t = useTranslations("person");
  const solar = formatSolar(date);
  const lunar = lunarParts(date);

  if (!solar && !lunar) return null;

  return (
    <span className="inline-flex flex-col gap-0.5">
      {solar && (
        <span>
          {solar}
          <span className="ml-1.5 text-than text-text-muted">{t("solarSuffix")}</span>
        </span>
      )}
      {lunar && (
        <span className="text-than text-text-muted">
          {lunar.granularity === "DAY"
            ? t("lunarDayMonth", { day: lunar.day, month: lunar.month })
            : lunar.granularity === "MONTH"
              ? t("lunarMonth", { month: lunar.month })
              : t("lunarYear", { year: lunar.year })}
          {lunar.leap && (
            <span className="ml-1 font-medium text-accent">{t("lunarLeap")}</span>
          )}
          {lunar.granularity !== "YEAR" && <> · {t("lunarYear", { year: lunar.year })}</>}
          {lunar.canChi && <> · {lunar.canChi}</>}
        </span>
      )}
    </span>
  );
}

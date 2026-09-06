"use client";

import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "antd";
import { useTranslations } from "next-intl";
import {
  buildCalendarYear,
  parseSolar,
  todayInVietnam,
  type SolarParts,
} from "@/lib/format/event";
import { isPresent } from "@/lib/privacy/present";
import { colorTokens } from "@/styles/tokens";
import type { EventDto } from "@/types/api";

export interface EventYearCalendarProps {
  events: EventDto[];
  /** Scrolls the matching event into view in the list below. */
  onSelectEvent?: (eventId: string) => void;
}

const MAX_ROWS_PER_MONTH = 3;

/**
 * Lịch năm — the next twelve months at a glance (plan §4 F7).
 *
 * Twelve month cards rather than a 365-cell grid: on a phone a day grid is
 * unreadable, and for a clan calendar the useful question is "which months do
 * we have giỗ in", not "what does the third week of May look like".
 *
 * The window starts from today **in Vietnam**, computed after mount — see
 * `todayInVietnam`. Placement inside the window uses only the backend's
 * `nextOccurrenceSolar`; nothing here derives a solar date from a lunar one.
 */
export function EventYearCalendar({ events, onSelectEvent }: EventYearCalendarProps) {
  const t = useTranslations("events");
  const [today, setToday] = useState<SolarParts | null>(null);

  useEffect(() => {
    setToday(todayInVietnam());
  }, []);

  const calendar = useMemo(
    () => (today ? buildCalendarYear(events, today.year, today.month) : null),
    [events, today]
  );

  if (!calendar) {
    return <Skeleton active paragraph={{ rows: 3 }} />;
  }

  return (
    <section aria-label={t("calendarTitle")}>
      <h2 className="mb-2 font-serif text-[17px] font-semibold text-text-main">
        {t("calendarTitle")}
      </h2>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
        {calendar.months.map((cell) => {
          const isCurrent = cell.year === today?.year && cell.month === today?.month;
          return (
            <div
              key={`${cell.year}-${cell.month}`}
              className="rounded-lg border bg-bg-card p-2.5"
              style={{
                borderColor: isCurrent ? colorTokens.primary : colorTokens.border,
              }}
            >
              <div className="flex items-baseline justify-between gap-1">
                <span
                  className="font-serif text-[13.5px] font-semibold"
                  style={{ color: isCurrent ? colorTokens.primary : colorTokens.textMain }}
                >
                  {t("monthLabel", { month: cell.month })}
                </span>
                <span className="text-[11px] text-text-muted">{cell.year}</span>
              </div>

              {cell.events.length === 0 ? (
                <p className="mb-0 mt-1.5 text-[12px] text-text-muted">{t("monthEmpty")}</p>
              ) : (
                <ul className="m-0 mt-1.5 flex list-none flex-col gap-1 p-0">
                  {cell.events.slice(0, MAX_ROWS_PER_MONTH).map((event) => {
                    const day = parseSolar(event.nextOccurrenceSolar)?.day;
                    return (
                      <li key={event.id}>
                        <button
                          type="button"
                          onClick={() => onSelectEvent?.(event.id)}
                          className="flex w-full items-baseline gap-1.5 rounded border-0 bg-transparent p-0 text-left text-[12px] text-text-muted hover:text-primary"
                        >
                          {isPresent(day) && (
                            <span
                              className="shrink-0 rounded px-1 text-[11px] font-semibold"
                              style={{
                                background: colorTokens.primaryLight,
                                color: colorTokens.primary,
                              }}
                            >
                              {day}
                            </span>
                          )}
                          <span className="truncate">
                            {isPresent(event.title)
                              ? event.title
                              : t(`eventType.${event.eventType}`)}
                          </span>
                        </button>
                      </li>
                    );
                  })}
                  {cell.events.length > MAX_ROWS_PER_MONTH && (
                    <li className="text-[11.5px] text-text-muted">
                      {t("moreInMonth", { n: cell.events.length - MAX_ROWS_PER_MONTH })}
                    </li>
                  )}
                </ul>
              )}
            </div>
          );
        })}
      </div>

      {calendar.unscheduled.length > 0 && (
        // A giỗ whose next solar occurrence the backend could not compute
        // (LUNAR_CONVERSION_FAILED, or a lunar date the converter rejects)
        // must still be visible. Dropping it would hide a real obligation.
        <p className="mb-0 mt-2 text-[12.5px] text-text-muted">
          {t("unscheduledCount", { n: calendar.unscheduled.length })}
        </p>
      )}
    </section>
  );
}

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
import { colorVars } from "@/styles/tokens";
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
 *
 * <h2>Vì sao màn này phải ĐỔI BỐ CỤC chứ không chỉ chỉnh CSS</h2>
 * Đo được trên trình duyệt: 413/469 đoạn chữ ở đây dưới sàn 16px, và phần lớn
 * điều khiển dưới sàn chạm của cả sản phẩm nằm trên màn này. Đây là màn đông
 * điều khiển nhất trên MỘT khung nhìn — mười hai ô tháng, mỗi ô một danh sách —
 * nên nó là màn duy nhất mà "cho vùng chạm to lên" đụng thẳng vào bố cục: 76
 * điều khiển × 44px không nằm vừa lưới bốn cột hiện tại.
 *
 * Lời giải KHÔNG phải giãn lưới — làm thế là vỡ cái nhìn "cả năm trong một màn",
 * thứ Hội đồng dùng để soạn lịch việc họ. Lời giải là đổi cách xếp: mỗi ô tháng
 * hiện tối đa ba lễ ở dạng hàng cao 44px, phần còn lại gom vào một hàng
 * "còn N lễ nữa" — bản thân nó cũng là một điều khiển 44px — mở ra tại chỗ.
 *
 * Cái giá và cách trả giá: gom bớt lễ làm mất cảm giác "tháng nào dày". Bù lại
 * bằng một dải CHẤM MẬT ĐỘ — trang trí thuần, `aria-hidden`, không phải điều
 * khiển nên không tính vào phép đo vùng chạm — để mắt vẫn thấy tháng nào nặng
 * việc mà không phải mở ra đếm.
 *
 * Lưới cũng bớt một cột ở mỗi mốc (1 · 2 · 3 · 4 thay cho 2 · 3 · 4): ở 393px,
 * hai cột chia nhau 393px cho ra ô tháng rộng 186px, và một hàng gồm ô ngày +
 * tên người ở cỡ chữ 16px không nằm vừa 186px mà không cắt mất tên. Tên người là
 * DỮ LIỆU (định hướng 00 §3), không phải nhãn giao diện để cắt bằng dấu ba chấm.
 */
export function EventYearCalendar({ events, onSelectEvent }: EventYearCalendarProps) {
  const t = useTranslations("events");
  const [today, setToday] = useState<SolarParts | null>(null);
  /** Những tháng người dùng đã bấm mở. Khoá là "năm-tháng". */
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set());

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

  const toggleMonth = (key: string) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  return (
    <section aria-label={t("calendarTitle")}>
      <h2 className="mb-2 font-serif text-de font-semibold text-text-main">
        {t("calendarTitle")}
      </h2>

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {calendar.months.map((cell) => {
          const key = `${cell.year}-${cell.month}`;
          const isCurrent = cell.year === today?.year && cell.month === today?.month;
          const isOpen = expanded.has(key);
          const hidden = Math.max(0, cell.events.length - MAX_ROWS_PER_MONTH);
          const shown = isOpen ? cell.events : cell.events.slice(0, MAX_ROWS_PER_MONTH);

          return (
            <div
              key={key}
              className="rounded-lg border bg-bg-card p-2.5"
              style={{ borderColor: isCurrent ? colorVars.primary : colorVars.border }}
            >
              <div className="flex items-baseline justify-between gap-1">
                <span
                  className="font-serif text-than font-semibold"
                  style={{ color: isCurrent ? colorVars.primary : colorVars.textMain }}
                >
                  {t("monthLabel", { month: cell.month })}
                </span>
                <span className="text-than text-text-muted">{cell.year}</span>
              </div>

              {cell.events.length === 0 ? (
                <p className="mb-0 mt-1.5 text-than text-text-muted">{t("monthEmpty")}</p>
              ) : (
                <ul className="m-0 mt-1 flex list-none flex-col p-0">
                  {shown.map((event) => {
                    const day = parseSolar(event.nextOccurrenceSolar)?.day;
                    return (
                      <li key={event.id}>
                        {/*
                          Hai dòng, không một: dòng đầu là ô ngày DƯƠNG + tên, dòng
                          sau là ngày ÂM — bắt buộc có mặt ở MỌI kiểu xem (Đợt 2).
                          Trước đây hàng này chỉ có ngày dương, và đó là chỗ duy nhất
                          của cả màn hình vi phạm "song lịch âm–dương ở mọi nơi": một
                          Trưởng chi lướt lịch năm sẽ chỉ thấy ngày Gregory, đúng thứ
                          `EventDualDate` ở nơi khác đang cố tránh. Chiều cao hàng vì
                          thế cao hơn sàn 44px một chút — sàn là mức TỐI THIỂU, không
                          phải mức cố định, nên việc "cao hơn sàn" không vi phạm gì.
                        */}
                        <button
                          type="button"
                          onClick={() => onSelectEvent?.(event.id)}
                          className="flex min-h-11 w-full flex-col gap-0.5 rounded border-0 bg-transparent px-1 py-1 text-left text-than text-text-muted hover:bg-primary-light hover:text-primary"
                        >
                          <span className="flex items-center gap-2">
                            {isPresent(day) && (
                              // Ô ngày cũng phải đạt sàn chữ: nó là DỮ LIỆU (ngày mấy),
                              // không phải một nhãn trang trí.
                              <span
                                className="min-w-7 shrink-0 rounded px-1 text-center text-than font-semibold"
                                style={{
                                  background: colorVars.primaryLight,
                                  color: colorVars.primary,
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
                          </span>
                          <span className="pl-1 text-than text-text-muted">
                            {t("lunarDayMonth", {
                              day: event.lunarDate.day,
                              month: event.lunarDate.month,
                            })}
                            {event.lunarDate.leap && (
                              <span className="ml-1 font-semibold text-accent">
                                {t("lunarLeap")}
                              </span>
                            )}
                          </span>
                        </button>
                      </li>
                    );
                  })}

                  {hidden > 0 && (
                    <li>
                      <button
                        type="button"
                        onClick={() => toggleMonth(key)}
                        aria-expanded={isOpen}
                        data-testid="calendar-month-more"
                        className="flex min-h-11 w-full items-center gap-2 rounded border-0 bg-transparent px-1 text-left text-than text-text-muted hover:bg-primary-light hover:text-primary"
                      >
                        {!isOpen && (
                          <span aria-hidden className="flex shrink-0 items-center gap-0.5">
                            {Array.from({ length: Math.min(hidden, 6) }).map((_, index) => (
                              <span
                                key={index}
                                className="block h-1.5 w-1.5 rounded-full"
                                style={{ background: colorVars.borderDark }}
                              />
                            ))}
                          </span>
                        )}
                        <span className="truncate">
                          {isOpen ? t("showLessInMonth") : t("moreInMonth", { n: hidden })}
                        </span>
                      </button>
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
        <p className="mb-0 mt-2 text-than text-text-muted">
          {t("unscheduledCount", { n: calendar.unscheduled.length })}
        </p>
      )}
    </section>
  );
}

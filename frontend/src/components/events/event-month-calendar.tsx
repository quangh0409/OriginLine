"use client";

import { useEffect, useState } from "react";
import { LeftOutlined, RightOutlined } from "@ant-design/icons";
import { Skeleton } from "antd";
import { useTranslations } from "next-intl";
import {
  addMonths,
  buildCalendarMonth,
  countUnscheduled,
  todayInVietnam,
  type SolarParts,
} from "@/lib/format/event";
import { colorVars } from "@/styles/tokens";
import { EventCard } from "./event-card";
import type { EventDto } from "@/types/api";

export interface EventMonthCalendarProps {
  events: EventDto[];
  showManageLink?: boolean;
}

/** Cùng cửa sổ 12 tháng mà `EventsScreen` đã xin dữ liệu (`UPCOMING_DAYS = 366`). */
const WINDOW_MONTHS = 12;

const WEEKDAY_KEYS = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"] as const;

function isoOf(parts: SolarParts): string {
  return `${String(parts.year).padStart(4, "0")}-${String(parts.month).padStart(2, "0")}-${String(parts.day).padStart(2, "0")}`;
}

/**
 * Lịch tháng — kiểu xem MẶC ĐỊNH của F7 từ Đợt 2 (thay lịch năm/danh sách).
 *
 * <h2>Vì sao vừa được ở 393px, khác lịch năm</h2>
 * `EventYearCalendar` phải đổi hẳn bố cục (xem javadoc ở đó) vì nó nhồi
 * MƯỜI HAI danh sách vào một khung nhìn. Lịch tháng chỉ vẽ MỘT tháng, và ô
 * ngày ở đây không chứa văn bản dữ liệu nào ngoài số ngày dương — số lượng
 * việc họ trong ngày chỉ là một CHẤM trang trí (`aria-hidden`), còn danh sách
 * đầy đủ (tên, dâu/rể, cả hai lịch) nằm ở khối chương trình bên dưới lưới,
 * ngoài lưới 7 cột. Nhờ vậy phép tính hình học đơn giản và đo được:
 *
 *   - `KhungTrang` trừ 16px lề mỗi bên ở MỌI bề rộng → còn 393 − 32 = 361px;
 *   - khung lịch ở đây dùng `border` (1px) + `p-1` (4px) mỗi bên → 361 − 2×5 = 351px;
 *   - bảy cột, khe hở `gap-1` (4px) × 6 khe = 24px → mỗi ô rộng (351 − 24) / 7
 *     ≈ 46,7px — TRÊN sàn 44px của định hướng 00, còn dư khoảng 2,7px/ô.
 *
 * Chiều cao mỗi ô đặt cứng `min-h-11` (= 44px, đúng sàn, không thấp hơn), và
 * số ngày dùng `text-than` (16px, sàn thân bài — không có ngoại lệ "chật quá"
 * ngoài canvas phả đồ). Vì cả hai chiều đều đạt sàn bằng phép tính thật ở
 * trên — không phải cảm nhận — lịch tháng ĐƯỢC PHÉP là mặc định trên điện
 * thoại, khác với lịch năm.
 *
 * <h2>Song lịch âm–dương</h2>
 * Ô ngày trong lưới chỉ mang ngày DƯƠNG (số duy nhất mà lưới 7×6 có chỗ cho) —
 * không có ngày âm nào bị "quy đổi" ở đây, vì lưới không hiển thị ngày âm của
 * CÁC NGÀY KHÔNG CÓ SỰ KIỆN (không có gì để hiển thị: máy chủ không gửi ngày
 * âm cho một ngày bất kỳ, chỉ gửi cho sự kiện). Ngày nào CÓ việc họ thì việc
 * ấy hiện đầy đủ — cả hai lịch — trong khối chương trình bên dưới, qua
 * `<EventCard>` → `<EventDualDate>`, y hệt lịch năm và danh sách.
 */
export function EventMonthCalendar({ events, showManageLink = false }: EventMonthCalendarProps) {
  const t = useTranslations("events");
  const [today, setToday] = useState<SolarParts | null>(null);
  const [viewed, setViewed] = useState<{ year: number; month: number } | null>(null);
  const [selectedIso, setSelectedIso] = useState<string | null>(null);

  useEffect(() => {
    setToday(todayInVietnam());
  }, []);

  useEffect(() => {
    if (today && !viewed) setViewed({ year: today.year, month: today.month });
  }, [today, viewed]);

  // Về đúng "hôm nay" khi tháng đang xem chính là tháng hiện tại; rời khỏi đó
  // thì bỏ lựa chọn — chọn hộ một ngày nào đó trong một tháng khác chỉ tổ sai.
  useEffect(() => {
    if (!today || !viewed) return;
    setSelectedIso(
      viewed.year === today.year && viewed.month === today.month ? isoOf(today) : null
    );
  }, [today, viewed]);

  if (!today || !viewed) {
    return <Skeleton active paragraph={{ rows: 4 }} />;
  }

  const bounds = {
    min: { year: today.year, month: today.month },
    max: addMonths(today.year, today.month, WINDOW_MONTHS - 1),
  };
  const grid = buildCalendarMonth(events, viewed.year, viewed.month);
  const unscheduled = countUnscheduled(events);

  const atMin = viewed.year === bounds.min.year && viewed.month === bounds.min.month;
  const atMax = viewed.year === bounds.max.year && viewed.month === bounds.max.month;

  const go = (deltaMonths: number) => {
    setViewed((current) => {
      if (!current) return current;
      const next = addMonths(current.year, current.month, deltaMonths);
      // Kẹp trong đúng cửa sổ 12 tháng đã tải — đi xa hơn chỉ ra một tháng
      // rỗng vì chưa có dữ liệu, không phải vì dòng họ không có việc gì.
      const nextKey = next.year * 12 + next.month;
      const minKey = bounds.min.year * 12 + bounds.min.month;
      const maxKey = bounds.max.year * 12 + bounds.max.month;
      if (nextKey < minKey || nextKey > maxKey) return current;
      return next;
    });
  };

  const selectedDay = grid.weeks.flat().find((day) => day.iso === selectedIso) ?? null;
  const selectedEvents = selectedDay?.events ?? [];

  return (
    <section aria-label={t("monthView.title")}>
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => go(-1)}
          disabled={atMin}
          aria-label={t("monthView.prev")}
          className="flex min-h-11 min-w-11 items-center justify-center rounded border-0 bg-transparent text-text-main hover:bg-primary-light hover:text-primary disabled:opacity-40 disabled:hover:bg-transparent"
        >
          <LeftOutlined aria-hidden />
        </button>

        <h2 className="m-0 font-serif text-de font-semibold text-text-main" aria-live="polite">
          {t("monthLabel", { month: grid.month })} {grid.year}
        </h2>

        <button
          type="button"
          onClick={() => go(1)}
          disabled={atMax}
          aria-label={t("monthView.next")}
          className="flex min-h-11 min-w-11 items-center justify-center rounded border-0 bg-transparent text-text-main hover:bg-primary-light hover:text-primary disabled:opacity-40 disabled:hover:bg-transparent"
        >
          <RightOutlined aria-hidden />
        </button>
      </div>

      <div className="mt-3 rounded-lg border border-border p-1" role="group" aria-label={t("monthView.gridLabel")}>
        <div className="grid grid-cols-7 gap-1">
          {WEEKDAY_KEYS.map((key) => (
            <div
              key={key}
              aria-hidden
              className="flex min-h-6 items-center justify-center text-than font-medium text-text-muted"
            >
              {t(`monthView.weekday.${key}`)}
            </div>
          ))}
        </div>

        {grid.weeks.map((week, weekIndex) => (
          <div key={weekIndex} className="mt-1 grid grid-cols-7 gap-1">
            {week.map((day) => {
              const hasEvents = day.events.length > 0;
              const isSelected = day.iso === selectedIso;
              const isToday = today !== null && day.iso === isoOf(today);

              return (
                <button
                  key={day.iso}
                  type="button"
                  disabled={!day.inMonth}
                  onClick={() => setSelectedIso(day.iso)}
                  aria-pressed={isSelected}
                  aria-label={
                    hasEvents
                      ? t("monthView.dayAriaWithEvents", { day: day.day, count: day.events.length })
                      : t("monthView.dayAriaEmpty", { day: day.day })
                  }
                  className="flex min-h-11 flex-col items-center justify-center gap-0.5 rounded border-0 text-than font-medium disabled:pointer-events-none"
                  style={{
                    background: isSelected ? colorVars.primaryLight : "transparent",
                    color: !day.inMonth
                      ? colorVars.borderDark
                      : isToday
                        ? colorVars.primary
                        : colorVars.textMain,
                  }}
                >
                  <span>{day.day}</span>
                  {hasEvents && (
                    <span
                      aria-hidden
                      className="block h-1.5 w-1.5 rounded-full"
                      style={{ background: colorVars.primary }}
                    />
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </div>

      <div className="mt-3" aria-live="polite">
        {selectedDay ? (
          selectedEvents.length > 0 ? (
            <ul className="m-0 flex list-none flex-col gap-2 p-0">
              {selectedEvents.map((event) => (
                <li key={event.id} className="list-none">
                  <EventCard event={event} showManageLink={showManageLink} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="m-0 text-than text-text-muted">{t("monthView.dayEmpty")}</p>
          )
        ) : (
          <p className="m-0 text-than text-text-muted">{t("monthView.selectPrompt")}</p>
        )}
      </div>

      {unscheduled > 0 && (
        <p className="mb-0 mt-2 text-than text-text-muted">{t("unscheduledCount", { n: unscheduled })}</p>
      )}
    </section>
  );
}

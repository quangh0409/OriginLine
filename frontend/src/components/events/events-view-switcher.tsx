"use client";

import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";

export type EventsViewMode = "month" | "year" | "list";

const MODES: readonly EventsViewMode[] = ["month", "year", "list"];

export interface EventsViewSwitcherProps {
  value: EventsViewMode;
  onChange: (mode: EventsViewMode) => void;
}

/**
 * Ba kiểu xem của F7 (Đợt 2): lịch tháng / lịch năm / danh sách.
 *
 * Không dùng `<Segmented>` của Ant Design: bản thân nó không cam kết vùng
 * chạm 44px (`tree-toolbar.tsx` đã phải ghi đè `!min-h-11` từng mục một, và
 * ở BỐN lựa chọn nó còn tràn ngang trên 393px). Ba nút thường ở đây tự khai
 * kích thước bằng đúng lớp Tailwind của cả sản phẩm — `min-h-11 min-w-11`,
 * `text-than` — không lệ thuộc bộ đếm kích thước nội bộ của thư viện.
 */
export function EventsViewSwitcher({ value, onChange }: EventsViewSwitcherProps) {
  const t = useTranslations("events");

  return (
    <div role="radiogroup" aria-label={t("viewMode.label")} className="flex flex-wrap gap-1.5">
      {MODES.map((mode) => {
        const active = mode === value;
        return (
          <button
            key={mode}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(mode)}
            className="min-h-11 min-w-11 rounded-full border px-3 text-than font-medium transition-colors"
            style={
              active
                ? { background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }
                : { background: "transparent", color: colorVars.textMain, borderColor: colorVars.border }
            }
          >
            {t(`viewMode.${mode}`)}
          </button>
        );
      })}
    </div>
  );
}

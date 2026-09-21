"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { eventUrgency } from "@/lib/format/event";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import { EventDualDate } from "./event-dual-date";
import { EventTypeTag } from "./event-type-tag";
import type { EventDto } from "@/types/api";

const URGENCY_STYLE: Record<string, { background: string; color: string }> = {
  TODAY: { background: colorVars.primary, color: colorVars.bgCard },
  IMMINENT: { background: colorVars.primaryLight, color: colorVars.primary },
  SOON: { background: colorVars.warningBg, color: colorVars.accentText },
  LATER: { background: colorVars.bgDeceased, color: colorVars.textMuted },
  PAST: { background: colorVars.bgDeceased, color: colorVars.textMuted },
};

/**
 * One upcoming giỗ / lễ.
 *
 * `title` is rendered verbatim — the backend composes it per
 * `Accept-Language` precisely so the client never concatenates
 * "Giỗ " + a person's name, which would print the name of someone the caller
 * is not entitled to see. When `title` is absent we fall back to the event
 * type label alone, never to a name we assembled ourselves.
 *
 * `showManageLink` is an AFFORDANCE ONLY — the caller (`EventsScreen`)
 * derives it from `useMe().role`, exactly like `CorrectionQueueLink`. It does
 * not mean this particular event is inside the caller's branch scope:
 * `EventDto` carries no such flag yet (the contract has no `meta.canEdit` for
 * events), so a Trưởng chi may still see the link on an event outside their
 * chi and get a `403` from the server on `PATCH` — the real gate, never
 * bypassed by this link being visible (Việc 2: "phạm vi do máy chủ cắt").
 *
 * `adjustmentNote` — khi máy chủ đã LÙI một ngày âm không tồn tại (tháng
 * nhuận biến mất, ngày 30 ở tháng thiếu) về một ngày gần nhất — PHẢI hiện,
 * không được coi là chi tiết phụ: người xem cần biết ngày trên màn hình khác
 * ngày họ đã gõ, nếu không sẽ tưởng hệ thống ghi sai. Đặt ngay cạnh
 * `EventDualDate`, không gộp chung với `note` chung chung ở cuối thẻ.
 */
export function EventCard({
  event,
  showManageLink = false,
}: {
  event: EventDto;
  showManageLink?: boolean;
}) {
  const t = useTranslations("events");
  const urgency = eventUrgency(event.daysUntil);
  const urgencyStyle = URGENCY_STYLE[urgency] ?? URGENCY_STYLE.LATER;

  const heading = isPresent(event.title) ? event.title : t(`eventType.${event.eventType}`);

  return (
    <article className="rounded-lg border border-border bg-bg-card px-3 py-3 sm:px-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <h3 className="m-0 font-serif text-[16px] font-semibold leading-snug text-text-main">
            {heading}
          </h3>
          <div className="mt-1.5">
            <EventDualDate event={event} />
          </div>
          {isPresent(event.adjustmentNote) && (
            <p
              className="mb-0 mt-1 text-than font-medium"
              style={{ color: colorVars.accentText }}
            >
              {event.adjustmentNote}
            </p>
          )}
          {isPresent(event.location) && (
            <p className="mb-0 mt-1 text-than text-text-muted">{event.location}</p>
          )}
        </div>

        {isPresent(event.daysUntil) && (
          <Tag bordered={false} className="!m-0 !text-than !font-medium" style={urgencyStyle}>
            {urgency === "TODAY"
              ? t("today")
              : urgency === "PAST"
                ? t("daysAgo", { n: Math.abs(event.daysUntil) })
                : t("daysUntil", { n: event.daysUntil })}
          </Tag>
        )}
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <EventTypeTag eventType={event.eventType} />

        {event.isClanLevel ? (
          <Tag bordered={false} className="!m-0 !text-than">
            {t("clanLevel")}
          </Tag>
        ) : (
          isPresent(event.targetBranch?.name) && (
            <Tag bordered={false} className="!m-0 !text-than">
              {event.targetBranch?.name}
            </Tag>
          )
        )}

        {isPresent(event.person) && (
          // `min-h-11` + `inline-flex`: liên kết này nằm trong một hàng flex nên nó
          // KHÔNG được hưởng ngoại lệ "liên kết giữa dòng chữ" của WCAG 2.5.8 —
          // nó đứng riêng, là một điều khiển thật, và đo được 20px.
          <Link
            href={`/persons/${event.person.id}`}
            className="inline-flex min-h-11 items-center rounded px-1 text-than text-primary no-underline hover:bg-primary-light hover:underline"
          >
            {t("viewPerson")}
          </Link>
        )}

        {showManageLink && (
          <Link
            href={`/events/${event.id}/edit`}
            className="inline-flex min-h-11 items-center rounded px-1 text-than text-primary no-underline hover:bg-primary-light hover:underline"
          >
            {t("editEvent")}
          </Link>
        )}
      </div>

      {isPresent(event.reminderOffsets) && (
        <p className="mb-0 mt-2 text-than text-text-muted">
          {t("reminderOffsets", { days: event.reminderOffsets.join(", ") })}
        </p>
      )}

      {isPresent(event.note) && (
        <p className="mb-0 mt-1.5 text-than leading-relaxed text-text-muted">{event.note}</p>
      )}
    </article>
  );
}

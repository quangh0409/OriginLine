"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { eventUrgency } from "@/lib/format/event";
import { isPresent } from "@/lib/privacy/present";
import { colorTokens } from "@/styles/tokens";
import { EventDualDate } from "./event-dual-date";
import { EventTypeTag } from "./event-type-tag";
import type { EventDto } from "@/types/api";

const URGENCY_STYLE: Record<string, { background: string; color: string }> = {
  TODAY: { background: colorTokens.primary, color: "#ffffff" },
  IMMINENT: { background: colorTokens.primaryLight, color: colorTokens.primary },
  SOON: { background: colorTokens.warningBg, color: colorTokens.accent },
  LATER: { background: colorTokens.bgDeceased, color: colorTokens.textMuted },
  PAST: { background: colorTokens.bgDeceased, color: colorTokens.textMuted },
};

/**
 * One upcoming giỗ / lễ.
 *
 * `title` is rendered verbatim — the backend composes it per
 * `Accept-Language` precisely so the client never concatenates
 * "Giỗ " + a person's name, which would print the name of someone the caller
 * is not entitled to see. When `title` is absent we fall back to the event
 * type label alone, never to a name we assembled ourselves.
 */
export function EventCard({ event }: { event: EventDto }) {
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
        </div>

        {isPresent(event.daysUntil) && (
          <Tag bordered={false} className="!m-0 !text-[12px] !font-medium" style={urgencyStyle}>
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
          <Tag bordered={false} className="!m-0 !text-[11.5px]">
            {t("clanLevel")}
          </Tag>
        ) : (
          isPresent(event.targetBranch?.name) && (
            <Tag bordered={false} className="!m-0 !text-[11.5px]">
              {event.targetBranch?.name}
            </Tag>
          )
        )}

        {isPresent(event.person) && (
          <Link
            href={`/persons/${event.person.id}`}
            className="text-[12.5px] text-primary no-underline hover:underline"
          >
            {t("viewPerson")}
          </Link>
        )}
      </div>

      {isPresent(event.reminderOffsets) && (
        <p className="mb-0 mt-2 text-[12px] text-text-muted">
          {t("reminderOffsets", { days: event.reminderOffsets.join(", ") })}
        </p>
      )}

      {isPresent(event.note) && (
        <p className="mb-0 mt-1.5 text-[13px] leading-relaxed text-text-muted">{event.note}</p>
      )}
    </article>
  );
}

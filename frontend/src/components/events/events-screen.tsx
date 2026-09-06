"use client";

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Alert, Empty, Select, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useBranches } from "@/hooks/use-branches";
import { useEvents } from "@/hooks/use-events";
import { colorTokens } from "@/styles/tokens";
import { PushEngagementSignal } from "@/components/notifications/push-engagement-signal";
import { EventCard } from "./event-card";
import { EventYearCalendar } from "./event-year-calendar";

/**
 * A full solar year ahead — enough for the calendar below to be complete, and
 * within the contract's `upcomingDays` ceiling of 400.
 */
const UPCOMING_DAYS = 366;
const PAGE_SIZE = 100;

/**
 * F7 — sự kiện & nhắc giỗ.
 *
 * Two views of the same fetch: the chronological "sắp tới" list, and the
 * twelve-month lịch năm. One request feeds both, because the year view is a
 * regrouping of the same events, not a second query.
 *
 * Both calendars are always shown per event (lunar first — it is the stored
 * truth for a giỗ; solar second, from `nextOccurrenceSolar`, which only the
 * backend may compute).
 *
 * Reaching this screen counts as an engagement signal for the Web Push
 * permission ask — "sau khi người dùng đã xem … một ngày giỗ" (plan §4 F7).
 * The signal is only recorded once real events are on screen, so an empty or
 * failed load never triggers a permission card.
 */
export function EventsScreen() {
  const t = useTranslations("events");
  const searchParams = useSearchParams();
  const [branchId, setBranchId] = useState<string | undefined>();
  const [selectedId, setSelectedId] = useState<string | undefined>(
    () => searchParams.get("event") ?? undefined
  );

  const { data: branches } = useBranches();
  const { data, isLoading, isError } = useEvents({
    upcomingDays: UPCOMING_DAYS,
    branchId,
    size: PAGE_SIZE,
  });

  const events = data?.items ?? [];
  const listRef = useRef<HTMLUListElement>(null);

  // Deep link from a reminder: bring the event into view once it has rendered.
  useEffect(() => {
    if (!selectedId || events.length === 0) return;
    const node = listRef.current?.querySelector(`[data-event-id="${selectedId}"]`);
    node?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [selectedId, events.length]);

  return (
    <div className="space-y-5">
      {events.length > 0 && <PushEngagementSignal reason="EVENT" />}

      {(branches?.length ?? 0) > 1 && (
        <label className="block max-w-sm" htmlFor="events-branch-filter">
          <span className="mb-1 block text-[13px] text-text-muted">{t("branchFilter")}</span>
          <Select<string>
            id="events-branch-filter"
            className="w-full"
            size="large"
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t("anyBranch")}
            value={branchId}
            onChange={(next) => setBranchId(next ?? undefined)}
            options={(branches ?? []).map((branch) => ({ value: branch.id, label: branch.name }))}
          />
          <span className="mt-1 block text-[12px] text-text-muted">{t("branchFilterHint")}</span>
        </label>
      )}

      {isError && <Alert type="error" showIcon message={t("loadError")} />}
      {isLoading && <Skeleton active paragraph={{ rows: 5 }} />}

      {!isLoading && !isError && events.length === 0 && (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<span className="text-text-muted">{t("empty")}</span>}
        />
      )}

      {events.length > 0 && (
        <>
          <EventYearCalendar events={events} onSelectEvent={setSelectedId} />

          <section aria-label={t("upcomingTitle")}>
            <h2 className="mb-2 font-serif text-[17px] font-semibold text-text-main">
              {t("upcomingTitle")}
            </h2>
            <ul ref={listRef} className="m-0 flex list-none flex-col gap-2 p-0">
              {events.map((event) => (
                <li
                  key={event.id}
                  data-event-id={event.id}
                  className="list-none rounded-lg"
                  style={
                    selectedId === event.id
                      ? { outline: `2px solid ${colorTokens.accent}`, outlineOffset: "2px" }
                      : undefined
                  }
                >
                  <EventCard event={event} />
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}

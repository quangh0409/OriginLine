"use client";

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Alert, Button, Empty, Select, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useBranches } from "@/hooks/use-branches";
import { useEvents } from "@/hooks/use-events";
import { useMe } from "@/hooks/use-me";
import { colorVars } from "@/styles/tokens";
import { PushEngagementSignal } from "@/components/notifications/push-engagement-signal";
import { EventCard } from "./event-card";
import { EventMonthCalendar } from "./event-month-calendar";
import { EventYearCalendar } from "./event-year-calendar";
import { EventsViewSwitcher, type EventsViewMode } from "./events-view-switcher";

/**
 * A full solar year ahead — enough for the calendar below to be complete, and
 * within the contract's `upcomingDays` ceiling of 400.
 */
const UPCOMING_DAYS = 366;
const PAGE_SIZE = 100;

/**
 * F7 — sự kiện & nhắc giỗ.
 *
 * <h2>Ba kiểu xem, một lần tải (Đợt 2)</h2>
 * Lịch tháng (MẶC ĐỊNH), lịch năm và danh sách đều đọc từ CÙNG một `useEvents`
 * — chúng là ba cách xếp lại cùng một mảng, không phải ba truy vấn. Trước bản
 * này, lịch năm và danh sách luôn hiện CÙNG LÚC; nay chúng loại trừ nhau qua
 * `<EventsViewSwitcher>`, vì mục tiêu của đợt này là "lịch làm kiểu xem mặc
 * định" — hiện cả ba cùng lúc mãi mãi thì "mặc định" không còn nghĩa gì.
 *
 * <h2>Vì sao mặc định là LỊCH THÁNG chứ không phải danh sách</h2>
 * Xem javadoc `EventMonthCalendar` cho phép tính hình học thật (46,7px/ô ở
 * 393px, trên sàn 44px) — đây KHÔNG phải lựa chọn thẩm mỹ, nó là kiểu xem duy
 * nhất trong ba kiểu còn giữ được cả sàn chạm lẫn sàn chữ MÀ VẪN cho thấy "cả
 * tháng trong một khung nhìn", thứ lịch năm phải hy sinh (xem javadoc
 * `EventYearCalendar`) để đạt cùng hai sàn ấy.
 *
 * <h2>Đường vào có sẵn (`?event=`) buộc về "danh sách"</h2>
 * Đây là lối duy nhất đã có cơ chế cuộn-tới-đúng-thẻ (`data-event-id` +
 * `scrollIntoView`, hiệu ứng bên dưới). Dựng lại cùng cơ chế ấy cho lịch
 * tháng (nhảy đúng tháng, mở đúng ngày) hay lịch năm là việc thật nhưng rộng
 * hơn phạm vi Đợt 2 — xem báo cáo bàn giao. Bấm một ô trong lịch năm cũng
 * chuyển về danh sách rồi cuộn tới, vì hàng gọn của lịch năm không có chỗ cho
 * `<EventCard>` đầy đủ.
 *
 * Cả ba kiểu xem đều hiện song lịch âm–dương cho MỌI sự kiện chúng vẽ ra
 * (lịch tháng và danh sách qua `<EventCard>` → `<EventDualDate>`; lịch năm
 * qua chính nó) — không có kiểu xem nào chỉ hiện ngày dương.
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
  const [viewMode, setViewMode] = useState<EventsViewMode>(() =>
    searchParams.get("event") ? "list" : "month"
  );

  const { data: branches } = useBranches();
  const { data: me } = useMe();
  const { data, isLoading, isError } = useEvents({
    upcomingDays: UPCOMING_DAYS,
    branchId,
    size: PAGE_SIZE,
  });

  const events = data?.items ?? [];
  const listRef = useRef<HTMLUListElement>(null);

  /**
   * Chỉ Trưởng cành/chi/họ tạo/sửa được — cổng THẬT nằm ở `403` của chính
   * `POST`/`PATCH /events`. Đây là gợi ý vẽ giao diện, cùng khuôn với
   * `CorrectionQueueLink`: ẩn hẳn cho người chắc chắn không có quyền, không
   * hiện một nút xám kèm chú thích "bạn không có quyền" — điều đó chỉ tổ mời
   * bấm rồi ăn lỗi.
   */
  const canManage = me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";

  // Deep link from a reminder: bring the event into view once it has
  // rendered, and only while the list is the thing on screen.
  useEffect(() => {
    if (!selectedId || events.length === 0 || viewMode !== "list") return;
    const node = listRef.current?.querySelector(`[data-event-id="${selectedId}"]`);
    node?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [selectedId, events.length, viewMode]);

  return (
    <div className="space-y-5">
      {events.length > 0 && <PushEngagementSignal reason="EVENT" />}

      {canManage && (
        <div>
          <Link
            href="/events/new"
            className="inline-flex min-h-11 items-center rounded-lg px-4 text-than font-medium no-underline"
            style={{ background: colorVars.primary, color: colorVars.bgCard }}
          >
            {t("createEvent")}
          </Link>
        </div>
      )}

      {(branches?.length ?? 0) > 1 && (
        <label className="block max-w-sm" htmlFor="events-branch-filter">
          <span className="mb-1 block text-than text-text-muted">{t("branchFilter")}</span>
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
          <span className="mt-1 block text-than text-text-muted">{t("branchFilterHint")}</span>
        </label>
      )}

      {isError && <Alert type="error" showIcon message={t("loadError")} />}
      {isLoading && <Skeleton active paragraph={{ rows: 5 }} />}

      {!isLoading && !isError && events.length === 0 && (
        // Rỗng-vì-lọc và rỗng-vì-không-có là hai chuyện khác nhau, và người dùng
        // đọc nhầm chuyện thứ nhất thành chuyện thứ hai sẽ kết luận rằng hệ thống
        // đã mất dữ liệu. Từ V10 khoảng cách ấy rộng thêm: `MUNG_THO` và `KHAC`
        // nay hẹp nghĩa hơn trước, nên một bộ lọc theo hai mã ấy trả **ít dòng
        // hơn** — đúng thiết kế, nhưng chỉ đọc ra là "đúng thiết kế" khi màn hình
        // nói rõ rằng đang có bộ lọc.
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <span className="text-text-muted">{branchId ? t("emptyFiltered") : t("empty")}</span>
          }
        >
          {branchId && (
            <Button onClick={() => setBranchId(undefined)}>{t("clearFilters")}</Button>
          )}
        </Empty>
      )}

      {events.length > 0 && (
        <>
          <EventsViewSwitcher value={viewMode} onChange={setViewMode} />

          {viewMode === "month" && (
            <EventMonthCalendar events={events} showManageLink={canManage} />
          )}

          {viewMode === "year" && (
            <EventYearCalendar
              events={events}
              onSelectEvent={(id) => {
                // Hàng gọn của lịch năm không có chỗ cho một <EventCard> đầy đủ —
                // chuyển về danh sách rồi cuộn tới đúng thẻ.
                setSelectedId(id);
                setViewMode("list");
              }}
            />
          )}

          {viewMode === "list" && (
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
                        ? {
                            // Vòng tiêu điểm hai lớp: lõi tương phản cao + quầng nền, để thấy được
                            // trên MỌI nền. Dùng `accent` ở đây chỉ đạt 2,95:1 — dưới ngưỡng 3:1.
                            outline: `2px solid ${colorVars.focusRing}`,
                            outlineOffset: "2px",
                            boxShadow: `0 0 0 4px ${colorVars.focusHalo}`,
                          }
                        : undefined
                    }
                  >
                    <EventCard event={event} showManageLink={canManage} />
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  );
}

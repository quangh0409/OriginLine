"use client";

import { Alert, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useEvent } from "@/hooks/use-events";
import { useMe } from "@/hooks/use-me";
import { ApiError } from "@/lib/api/http";
import { EventForm } from "./event-form";

export interface EventEditScreenProps {
  eventId: string;
}

/**
 * Tải sự kiện + `ETag`, gác vai hiển thị, rồi giao cho `<EventForm mode="edit">`.
 *
 * Cùng khuôn với `PersonEditScreen`. Khác đúng một điểm: `EventDto` (đọc)
 * không mang `meta.canEdit` — hợp đồng chưa lên tới đó (xem javadoc
 * `eventsApi` và báo cáo bàn giao) — nên phép gác ở đây dùng `useMe().role`
 * thay vì một cờ máy chủ trả sẵn. Đó vẫn chỉ là gợi ý vẽ giao diện; cổng thật
 * nằm ở `403` của `PATCH /events/{id}`.
 */
export function EventEditScreen({ eventId }: EventEditScreenProps) {
  const t = useTranslations("eventForm");
  const { data: me, isPending: mePending } = useMe();
  const { data, isPending, error } = useEvent(eventId);

  if (isPending || mePending) return <Skeleton active paragraph={{ rows: 8 }} />;

  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return notFound ? (
      <Empty description={t("errors.notFound")} />
    ) : (
      <Alert type="error" showIcon message={t("errors.loadFailed")} />
    );
  }

  const canManage = me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";
  if (!canManage) {
    return <Alert type="info" showIcon message={t("errors.notAllowedToEdit")} />;
  }

  const { event, etag } = data;

  if (!etag) {
    return <Alert type="warning" showIcon message={t("errors.missingEtag")} />;
  }

  return <EventForm mode="edit" event={event} etag={etag} />;
}

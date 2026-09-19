"use client";

import { useState } from "react";
import { Alert, Empty, Pagination, Segmented, Select, Skeleton, Tag } from "antd";
import { useTranslations } from "next-intl";
import { useMarkNotificationRead, useNotifications } from "@/hooks/use-notifications";
import { ApiError } from "@/lib/api/http";
import { colorVars } from "@/styles/tokens";
import { NotificationItem } from "./notification-item";
import type { NotificationCategory } from "@/types/api";

type StatusFilter = "ALL" | "UNREAD" | "READ";

const CATEGORIES: NotificationCategory[] = [
  "GIO_REMINDER",
  "EVENT",
  "CHANGE_REQUEST",
  "SYSTEM",
];

const PAGE_SIZE = 20;

/**
 * F7 — trung tâm thông báo.
 *
 * The full inbox behind the header bell, and the MVP's guaranteed delivery
 * surface for giỗ reminders: whatever the backend queued is here whether or
 * not the user ever granted Web Push. Nothing on this screen is gated on
 * push, and the filters below are server-side (`status`, `category`) so the
 * inbox a member sees is the one the backend actually holds.
 *
 * `unreadCount` in the header comes from the response, never from counting
 * rows: with a filter or a second page applied, the two would disagree.
 */
export function NotificationCenter() {
  const t = useTranslations("notifications");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [category, setCategory] = useState<NotificationCategory | undefined>();
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, error, isFetching } = useNotifications({
    status,
    category,
    page,
    size: PAGE_SIZE,
  });
  const markRead = useMarkNotificationRead();

  const items = data?.items ?? [];
  const total = data?.page.totalElements ?? 0;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Segmented<StatusFilter>
          value={status}
          onChange={(next) => {
            setStatus(next);
            setPage(0);
          }}
          options={(["ALL", "UNREAD", "READ"] as const).map((value) => ({
            value,
            label: t(`filter.${value}`),
          }))}
        />

        <Select<NotificationCategory>
          allowClear
          className="min-w-[10rem]"
          placeholder={t("filter.anyCategory")}
          value={category}
          onChange={(next) => {
            setCategory(next ?? undefined);
            setPage(0);
          }}
          options={CATEGORIES.map((value) => ({ value, label: t(`category.${value}`) }))}
        />

        {(data?.unreadCount ?? 0) > 0 && (
          <Tag
            bordered={false}
            className="!m-0"
            style={{ background: colorVars.primaryLight, color: colorVars.primary }}
          >
            {t("unread", { count: data?.unreadCount ?? 0 })}
          </Tag>
        )}
      </div>

      {/* Tài khoản đã đăng nhập nhưng chưa ghép với nhân khẩu nào thì hộp thư đương nhiên
          trống. Đó không phải sự cố, và cũng không được để thành một màn hình trắng: người dùng
          cần biết phải hỏi ai để được ghép, nếu không họ sẽ tưởng hệ thống nuốt mất nhắc giỗ. */}
      {isError &&
        (error instanceof ApiError && error.code === "ACCOUNT_NOT_LINKED" ? (
          <Alert
            type="info"
            showIcon
            message={t("accountNotLinked")}
            description={t("accountNotLinkedHint")}
          />
        ) : (
          <Alert type="error" showIcon message={t("loadError")} />
        ))}

      {isLoading && <Skeleton active paragraph={{ rows: 5 }} />}

      {!isLoading && !isError && items.length === 0 && (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<span className="text-text-muted">{t("empty")}</span>}
        />
      )}

      {items.length > 0 && (
        <ul className="m-0 flex list-none flex-col gap-2 p-0" aria-busy={isFetching}>
          {items.map((notification) => (
            <NotificationItem
              key={notification.id}
              notification={notification}
              onMarkRead={(id) => markRead.mutate(id)}
              markingId={markRead.isPending ? (markRead.variables as string) : undefined}
            />
          ))}
        </ul>
      )}

      {total > PAGE_SIZE && (
        <div className="flex justify-center pt-1">
          <Pagination
            simple
            current={page + 1}
            pageSize={PAGE_SIZE}
            total={total}
            onChange={(next) => setPage(next - 1)}
          />
        </div>
      )}
    </div>
  );
}

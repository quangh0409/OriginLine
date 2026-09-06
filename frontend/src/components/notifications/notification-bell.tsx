"use client";

import { useState } from "react";
import { Badge, Popover, Typography, Empty, Spin } from "antd";
import { BellOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import {
  NOTIFICATION_PREVIEW_SIZE,
  useMarkNotificationRead,
  useNotifications,
} from "@/hooks/use-notifications";
import { NotificationItem } from "./notification-item";

/**
 * The header bell + unread badge. This is the primary MVP giỗ-reminder
 * surface (plan §4 F7) — it must stay visible in the main header, not tucked
 * into a secondary menu. It shows the newest few; the full inbox, with
 * filters and paging, lives at /notifications.
 *
 * The badge count comes from the response's `unreadCount`, which is the whole
 * inbox — deliberately NOT `items.length`, since this query only asks for a
 * preview page.
 */
export function NotificationBell() {
  const t = useTranslations("notifications");
  const [open, setOpen] = useState(false);
  const { data, isLoading, isError } = useNotifications({ size: NOTIFICATION_PREVIEW_SIZE });
  const markRead = useMarkNotificationRead();

  const items = data?.items ?? [];
  const unreadCount = data?.unreadCount ?? 0;

  return (
    <Popover
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
      title={<span className="font-serif">{t("title")}</span>}
      content={
        <div className="w-72 max-w-[80vw]">
          {isLoading && (
            <div className="flex justify-center py-6">
              <Spin size="small" />
            </div>
          )}
          {isError && <Typography.Text type="danger">{t("loadError")}</Typography.Text>}
          {!isLoading && !isError && items.length === 0 && (
            <Empty description={t("empty")} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
          {!isLoading && items.length > 0 && (
            <ul className="m-0 flex max-h-80 list-none flex-col gap-1.5 overflow-y-auto p-0">
              {items.map((notification) => (
                <NotificationItem
                  key={notification.id}
                  notification={notification}
                  compact
                  onMarkRead={(id) => markRead.mutate(id)}
                  markingId={markRead.isPending ? (markRead.variables as string) : undefined}
                />
              ))}
            </ul>
          )}

          <div className="mt-2 border-t border-border pt-2 text-center">
            <Link
              href="/notifications"
              className="text-[13px] text-primary no-underline hover:underline"
              onClick={() => setOpen(false)}
            >
              {t("viewAll")}
            </Link>
          </div>
        </div>
      }
    >
      <Badge count={unreadCount} size="small" offset={[-2, 2]}>
        <BellOutlined
          className="cursor-pointer text-lg text-text-main hover:text-primary"
          aria-label={t("title")}
        />
      </Badge>
    </Popover>
  );
}

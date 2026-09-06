"use client";

import type { ReactNode } from "react";
import { Button, Tag } from "antd";
import {
  BellOutlined,
  CalendarOutlined,
  EditOutlined,
  InfoCircleOutlined,
} from "@ant-design/icons";
import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { resolveNotificationHref } from "@/lib/format/notification-link";
import { isPresent } from "@/lib/privacy/present";
import { colorTokens } from "@/styles/tokens";
import type { NotificationCategory, NotificationDto } from "@/types/api";

const CATEGORY_ICON: Record<NotificationCategory, ReactNode> = {
  GIO_REMINDER: <BellOutlined aria-hidden />,
  EVENT: <CalendarOutlined aria-hidden />,
  CHANGE_REQUEST: <EditOutlined aria-hidden />,
  SYSTEM: <InfoCircleOutlined aria-hidden />,
};

export interface NotificationItemProps {
  notification: NotificationDto;
  onMarkRead: (id: string) => void;
  markingId?: string;
  /** Compact layout for the header bell popover. */
  compact?: boolean;
}

/**
 * One inbox row.
 *
 * `body` is rendered as sent. The backend guarantees it carries no Tier-3
 * data precisely because a push notification can appear on a locked screen,
 * so the client must not enrich it with anything fetched separately.
 *
 * Timestamps are formatted in Asia/Ho_Chi_Minh rather than the device zone,
 * and absolutely rather than as "3 giờ trước": the clan's day is the one in
 * Vietnam, and a fixed timezone also keeps server and client markup
 * identical, which a relative time computed at render would not.
 */
export function NotificationItem({
  notification,
  onMarkRead,
  markingId,
  compact = false,
}: NotificationItemProps) {
  const t = useTranslations("notifications");
  const format = useFormatter();
  const href = resolveNotificationHref(notification);
  const unread = !notification.isRead;

  const content = (
    <div className="flex items-start gap-2">
        <span
          className="mt-0.5 shrink-0 text-[15px]"
          style={{ color: unread ? colorTokens.primary : colorTokens.textMuted }}
        >
          {CATEGORY_ICON[notification.category]}
        </span>
        <div className="min-w-0 flex-1">
          <p
            className={`m-0 text-[14px] leading-snug ${unread ? "font-semibold text-text-main" : "text-text-muted"}`}
          >
            {notification.title}
          </p>
          {isPresent(notification.body) && (
            <p className="mb-0 mt-0.5 text-[13px] leading-relaxed text-text-muted">
              {notification.body}
            </p>
          )}
          <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="text-[11.5px] text-text-muted">
              {format.dateTime(new Date(notification.createdAt), {
                dateStyle: "medium",
                timeStyle: "short",
                timeZone: "Asia/Ho_Chi_Minh",
              })}
            </span>
            {!compact && (
              <Tag bordered={false} className="!m-0 !text-[11px]">
                {t(`category.${notification.category}`)}
              </Tag>
            )}
            {isPresent(notification.reminderOffsetDays) && (
              <Tag
                bordered={false}
                className="!m-0 !text-[11px]"
                style={{ background: colorTokens.warningBg, color: colorTokens.accent }}
              >
                {t("offsetTag", { n: notification.reminderOffsetDays })}
              </Tag>
            )}
          </div>
        </div>
        {unread && (
          <span
            aria-label={t("unreadDot")}
            className="mt-1.5 h-2 w-2 shrink-0 rounded-full"
            style={{ background: colorTokens.primary }}
          />
        )}
    </div>
  );

  return (
    <li
      className="list-none rounded-lg border px-3 py-2.5"
      style={{
        borderColor: unread ? colorTokens.borderDark : colorTokens.border,
        background: unread ? colorTokens.bgCard : "transparent",
      }}
    >
      {href ? (
        <Link href={href} className="block no-underline" onClick={() => unread && onMarkRead(notification.id)}>
          {content}
        </Link>
      ) : (
        content
      )}

      {unread && (
        <div className="mt-1.5 flex justify-end">
          <Button
            type="link"
            size="small"
            className="!h-auto !px-0 !text-[12.5px]"
            loading={markingId === notification.id}
            onClick={() => onMarkRead(notification.id)}
          >
            {t("markRead")}
          </Button>
        </div>
      )}
    </li>
  );
}

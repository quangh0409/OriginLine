"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { colorTokens } from "@/styles/tokens";
import type { EventType } from "@/types/api";

/**
 * The kind of lễ, as a warm-toned tag. Clan-wide rites (giỗ Tổ, giỗ họ, chạp
 * mả) get the deep-red emphasis; branch-level and personal ones stay quieter,
 * so a member scanning the list sees at a glance which days the whole dòng họ
 * gathers for.
 */
const CLAN_SCALE_TYPES = new Set<EventType>(["GIO_TO", "GIO_HO", "CHAP_MA"]);

export function EventTypeTag({ eventType }: { eventType: EventType }) {
  const t = useTranslations("events");
  const prominent = CLAN_SCALE_TYPES.has(eventType);

  return (
    <Tag
      bordered={false}
      className="!m-0 !text-[11.5px]"
      style={
        prominent
          ? { background: colorTokens.primaryLight, color: colorTokens.primary }
          : { background: colorTokens.bgDeceased, color: colorTokens.textMuted }
      }
    >
      {t(`eventType.${eventType}`)}
    </Tag>
  );
}

"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import type { EventType } from "@/types/api";

/**
 * The kind of lễ, as a warm-toned tag. Clan-wide rites (giỗ Tổ, giỗ họ, chạp
 * mả) get the deep-red emphasis; branch-level and personal ones stay quieter,
 * so a member scanning the list sees at a glance which days the whole dòng họ
 * gathers for.
 */
/**
 * Từ V10, ba việc họ vốn bị gộp vào `KHAC` đã có mã riêng — và hai trong ba
 * (`HOP_HO`, `KHANH_THANH`) là dịp cả dòng họ tụ về, ngang hàng giỗ Tổ về mặt
 * "hôm ấy ai cũng phải có mặt". Chúng thuộc nhóm nhấn mạnh; `CUOI_HOI` thì
 * không, vì đó là việc của một nhà.
 */
const CLAN_SCALE_TYPES = new Set<EventType>([
  "GIO_TO",
  "GIO_HO",
  "CHAP_MA",
  "HOP_HO",
  "KHANH_THANH",
]);

export function EventTypeTag({ eventType }: { eventType: EventType }) {
  const t = useTranslations("events");
  const prominent = CLAN_SCALE_TYPES.has(eventType);

  return (
    <Tag
      bordered={false}
      className="!m-0 !text-than"
      style={
        prominent
          ? { background: colorVars.primaryLight, color: colorVars.primary }
          : { background: colorVars.bgDeceased, color: colorVars.textMuted }
      }
    >
      {t(`eventType.${eventType}`)}
    </Tag>
  );
}

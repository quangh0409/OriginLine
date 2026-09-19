"use client";

import { Tag } from "antd";
import { useLocale } from "next-intl";
import { BADGE_META, displayBadges } from "@/lib/tree/badges";
import type { PersonBadge } from "@/types/api";

export interface PersonBadgeListProps {
  badges: PersonBadge[] | undefined;
  size?: "sm" | "md";
}

/**
 * Renders the backend-computed badges (dâu · rể · con nuôi · đích tôn · thừa
 * tự · kế tự · tuyệt tự · trưởng chi). Reuses the canvas's BADGE_META so a
 * person carries the same colour and wording on the tree node and on the
 * profile.
 *
 * These are never inferred here: dâu/rể in particular are derived from SPOUSE
 * edges plus bloodline on the server (contracts/README §7.6). If `badges` is
 * absent, nothing renders — no "chưa xác định" placeholder.
 */
export function PersonBadgeList({ badges, size = "md" }: PersonBadgeListProps) {
  const locale = useLocale();
  const visible = displayBadges(badges);
  if (visible.length === 0) return null;

  return (
    <ul className="m-0 flex list-none flex-wrap gap-1.5 p-0">
      {visible.map((badge) => (
        <li key={badge}>
          <Tag
            color={BADGE_META[badge].color}
            style={{ color: BADGE_META[badge].ink }}
            bordered={false}
            className={size === "sm" ? "!m-0 !px-1.5" : "!m-0"}
          >
            {locale === "vi" ? BADGE_META[badge].vi : BADGE_META[badge].en}
          </Tag>
        </li>
      ))}
    </ul>
  );
}

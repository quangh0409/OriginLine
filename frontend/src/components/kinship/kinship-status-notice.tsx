"use client";

import { Alert } from "antd";
import { useTranslations } from "next-intl";
import type { KinshipStatus } from "@/types/api";

export interface KinshipStatusNoticeProps {
  status: Exclude<KinshipStatus, "RESOLVED">;
}

/**
 * `status !== "RESOLVED"` is an ANSWER, not a failure (contracts/README §7.7).
 * Each one gets its own wording and its own tone:
 *
 *  - `SELF`                — the two pickers hold the same person.
 *  - `NO_COMMON_ANCESTOR`  — no tổ chung inside the data we hold. Informational:
 *                            a rể who married in genuinely has no blood tie,
 *                            and a not-yet-linked record looks identical.
 *  - `NO_MATCHING_RULE`    — the LCA was found, but the clan's rule set has no
 *                            entry for this combination. This is an invitation
 *                            to the Hội đồng Tộc biểu to add a rule, so it must
 *                            never read as "something broke": showing a red
 *                            error here would send people hunting a bug that
 *                            does not exist.
 */
export function KinshipStatusNotice({ status }: KinshipStatusNoticeProps) {
  const t = useTranslations("kinship");

  const type = status === "NO_MATCHING_RULE" ? "warning" : "info";

  return (
    <Alert
      type={type}
      showIcon
      message={t(`status.${status}.title`)}
      description={t(`status.${status}.body`)}
    />
  );
}

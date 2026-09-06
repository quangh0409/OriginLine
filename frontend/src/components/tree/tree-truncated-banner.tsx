"use client";

import { Alert } from "antd";
import { useTranslations } from "next-intl";

export interface TreeTruncatedBannerProps {
  truncatedCount: number;
}

/**
 * Fires ONLY for `TreeProjection.meta.truncated` (a maxNodes/volume cutoff —
 * contracts/README §7.3), never for privacy filtering. The BFS engine keeps
 * these two concepts strictly separate (src/mocks/tree-graph/query-tree.ts)
 * specifically so this banner can exist without ever leaking "a living
 * person is hidden here" to a guest — that distinction is the whole point
 * of requirement #6 in the F2 brief.
 */
export function TreeTruncatedBanner({ truncatedCount }: TreeTruncatedBannerProps) {
  const t = useTranslations("tree");

  return (
    <Alert
      type="warning"
      showIcon
      banner
      closable
      className="!rounded-none"
      message={t("truncatedBanner", { count: truncatedCount })}
    />
  );
}

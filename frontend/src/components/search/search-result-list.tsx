"use client";

import { Alert, Empty, Pagination, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { SearchResultCard } from "./search-result-card";
import type { PersonSummaryPage } from "@/types/api";

export interface SearchResultListProps {
  data: PersonSummaryPage | undefined;
  isLoading: boolean;
  isFetching: boolean;
  isError: boolean;
  /** No query typed yet — a prompt, not an empty result. */
  idle: boolean;
  page: number;
  onPageChange: (page: number) => void;
}

/**
 * Results for F6.
 *
 * Three distinct states that must never be collapsed into one:
 *  - idle    → nothing typed yet ("gõ tên để tìm")
 *  - empty   → searched, nothing matched
 *  - error   → the request failed
 *
 * Note what is deliberately absent: any hint that results were withheld. A
 * guest searching a living relative's name gets a plain "không tìm thấy",
 * identical to searching a name nobody in the clan has ever borne — that
 * indistinguishability IS the privacy requirement (BA v2 §10), not a missing
 * feature. `page.totalElements` is likewise already filtered server-side, so
 * showing it leaks nothing.
 */
export function SearchResultList({
  data,
  isLoading,
  isFetching,
  isError,
  idle,
  page,
  onPageChange,
}: SearchResultListProps) {
  const t = useTranslations("search");

  if (idle) {
    return (
      <div className="rounded-lg border border-dashed border-border bg-bg-card px-4 py-10 text-center text-[14px] text-text-muted">
        {t("idlePrompt")}
      </div>
    );
  }

  if (isError) {
    return <Alert type="error" showIcon message={t("loadError")} />;
  }

  if (isLoading) {
    return <Skeleton active paragraph={{ rows: 4 }} />;
  }

  const items = data?.items ?? [];
  const total = data?.page.totalElements ?? 0;

  if (items.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={<span className="text-text-muted">{t("noResults")}</span>}
      />
    );
  }

  return (
    <div className="space-y-3" aria-busy={isFetching}>
      <p
        className="m-0 text-[13px] text-text-muted"
        role="status"
        aria-live="polite"
      >
        {t("resultCount", { count: total })}
      </p>

      <ul className="m-0 flex list-none flex-col gap-2 p-0">
        {items.map((person) => (
          <SearchResultCard key={person.id} person={person} />
        ))}
      </ul>

      {total > (data?.page.size ?? 20) && (
        <div className="flex justify-center pt-1">
          <Pagination
            simple
            // API pages are zero-based; antd's are one-based.
            current={page + 1}
            pageSize={data?.page.size ?? 20}
            total={total}
            onChange={(next) => onPageChange(next - 1)}
          />
        </div>
      )}
    </div>
  );
}

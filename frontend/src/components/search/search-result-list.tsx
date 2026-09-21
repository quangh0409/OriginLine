"use client";

import { Alert, Button, Empty, Pagination, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { SearchResultCard } from "./search-result-card";
import type { PersonSearchResultPage } from "@/lib/api/search";

export interface SearchResultListProps {
  data: PersonSearchResultPage | undefined;
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
 *
 * <h2>`totalElements` có thể là `null` — bề mặt công khai không trả tổng</h2>
 * Một Khách hỏi `/public/persons/search` không nhận được `totalElements`
 * (xem javadoc `PersonSearchResultPage`), vì bản thân con số ấy là một phép
 * đếm dân số dòng họ. Chỗ này KHÔNG được coi `null` là `0` — hai câu trả lời
 * là hai câu chuyện khác nhau — và cũng KHÔNG được tự bịa ra một con số. Dòng
 * trạng thái đổi sang một câu không mang số (`resultCountUnknown`), và việc
 * lật trang đổi từ "trang N/M" (đếm được) sang "trang trước/sau" (chỉ dựa
 * `hasNext`, giống con trỏ) — cùng khuôn với `TreeJumpSearch`'s "không đếm,
 * không hứa".
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
      <div className="rounded-lg border border-dashed border-border bg-bg-card px-4 py-10 text-center text-than text-text-muted">
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
  const total = data?.page.totalElements ?? null;
  const size = data?.page.size ?? 20;
  const hasNext = data?.page.hasNext ?? false;

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
        className="m-0 text-than text-text-muted"
        role="status"
        aria-live="polite"
      >
        {total !== null ? t("resultCount", { count: total }) : t("resultCountUnknown")}
      </p>

      <ul className="m-0 flex list-none flex-col gap-2 p-0">
        {items.map((person) => (
          <SearchResultCard key={person.id} person={person} />
        ))}
      </ul>

      {total !== null && total > size && (
        <div className="flex justify-center pt-1">
          <Pagination
            simple
            // API pages are zero-based; antd's are one-based.
            current={page + 1}
            pageSize={size}
            total={total}
            onChange={(next) => onPageChange(next - 1)}
          />
        </div>
      )}

      {total === null && (page > 0 || hasNext) && (
        <div className="flex justify-center gap-2 pt-1">
          <Button disabled={page === 0} onClick={() => onPageChange(page - 1)}>
            {t("pager.prev")}
          </Button>
          <Button disabled={!hasNext} onClick={() => onPageChange(page + 1)}>
            {t("pager.next")}
          </Button>
        </div>
      )}
    </div>
  );
}

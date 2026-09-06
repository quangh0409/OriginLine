"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { usePathname, useRouter } from "@/i18n/navigation";
import { usePersonSearch } from "@/hooks/use-person-search";
import { SearchBox } from "./search-box";
import { SearchFilters, type SearchFilterValues } from "./search-filters";
import { SearchResultList } from "./search-result-list";

/** One timer for query + filters together, so typing and filtering don't race. */
const DEBOUNCE_MS = 250;
const PAGE_SIZE = 20;

/**
 * F6 — tìm kiếm nhân khẩu.
 *
 * State lives in the URL (`?q=&generation=&branchId=&nativePlace=`) so a
 * search is shareable in a family group chat, which is how these things
 * actually get used. `page` stays local: page 3 of a search is not something
 * anyone links to, and keeping it out of the URL means a filter change can
 * reset it without a history entry.
 *
 * The debounce is what makes this "gợi ý tức thời" rather than a form: the
 * result list under the box updates as you type, with no submit button. React
 * Query keeps the previous page's data on screen while the next one loads
 * (`placeholderData`), so the list doesn't blink to a skeleton on every
 * keystroke — see use-person-search.ts.
 */
export function PersonSearchScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [rawQuery, setRawQuery] = useState(() => searchParams.get("q") ?? "");
  const [filters, setFilters] = useState<SearchFilterValues>(() => {
    const generation = searchParams.get("generation");
    return {
      generation: generation ? Number(generation) : undefined,
      branchId: searchParams.get("branchId") ?? undefined,
      nativePlace: searchParams.get("nativePlace") ?? undefined,
    };
  });
  const [page, setPage] = useState(0);

  // Debounced mirror of (query + filters) — the only thing the API sees.
  const [applied, setApplied] = useState(() => ({ q: rawQuery, ...filters }));

  useEffect(() => {
    const timer = setTimeout(() => {
      setApplied({ q: rawQuery, ...filters });
      setPage(0);
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [rawQuery, filters]);

  // Keep the address bar in sync with what was actually applied, not with
  // every intermediate keystroke.
  useEffect(() => {
    const params = new URLSearchParams();
    if (applied.q.trim()) params.set("q", applied.q.trim());
    if (applied.generation !== undefined) params.set("generation", String(applied.generation));
    if (applied.branchId) params.set("branchId", applied.branchId);
    if (applied.nativePlace) params.set("nativePlace", applied.nativePlace);
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }, [applied, pathname, router]);

  const params = useMemo(
    () => ({
      q: applied.q.trim(),
      generation: applied.generation,
      branchId: applied.branchId,
      nativePlace: applied.nativePlace?.trim() || undefined,
      page,
      size: PAGE_SIZE,
    }),
    [applied, page]
  );

  const { data, isLoading, isFetching, isError } = usePersonSearch(params);

  // `q` is required by the contract (min length 1), so filters alone cannot
  // run a search. Say so with a prompt rather than firing a doomed request.
  const idle = params.q.length === 0;

  return (
    <div className="space-y-4">
      <div className="space-y-3 rounded-lg border border-border bg-bg-card p-3 sm:p-4">
        <SearchBox value={rawQuery} onChange={setRawQuery} busy={isFetching} />
        <SearchFilters value={filters} onChange={setFilters} />
      </div>

      <SearchResultList
        data={data}
        isLoading={isLoading && !idle}
        isFetching={isFetching}
        isError={isError}
        idle={idle}
        page={page}
        onPageChange={setPage}
      />
    </div>
  );
}

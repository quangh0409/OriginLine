"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/i18n/navigation";
import { useTreeAudience } from "@/hooks/use-tree-audience";
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
 *
 * <h2>Khách tìm kiếm được (Đợt 2 — lỗi thật đã sửa)</h2>
 * `audience` (`useTreeAudience`, cùng hàm cây phả đồ đã dùng) quyết định lối
 * gọi: Khách đi `/public/persons/search` (chỉ người đã khuất, không cần
 * phiên), thành viên đi `/persons/search` như cũ — xem `searchApi.persons`.
 * Hai ô lọc `branchId`/`nativePlace` không có tác dụng trên bề mặt công khai
 * nên bị ẨN hẳn khi Khách, thay vì hiện ra rồi bị máy chủ lặng lẽ bỏ qua.
 */
export function PersonSearchScreen() {
  const t = useTranslations("search");
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const audience = useTreeAudience();

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

  // Bề mặt công khai không có `branchId`/`nativePlace` — dọn ngay khi audience
  // chuyển sang Khách (đăng xuất giữa phiên, hoặc bộ chuyển vai dev), để
  // không giữ một bộ lọc trông như đang hoạt động mà thực ra chưa từng được
  // gửi đi.
  useEffect(() => {
    if (audience !== "public") return;
    setFilters((current) =>
      current.branchId || current.nativePlace
        ? { generation: current.generation }
        : current
    );
  }, [audience]);

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

  const { data, isLoading, isFetching, isError } = usePersonSearch(audience, params);

  // `q` is required by the contract (min length 1), so filters alone cannot
  // run a search. Say so with a prompt rather than firing a doomed request.
  const idle = params.q.length === 0;

  return (
    <div className="space-y-4">
      {audience === "public" && (
        // `bg-warning-bg` + `border-border-dark`, giống `directory-guest-notice`: đó là bề mặt
        // đã được đặt cho câu "khách thấy ít hơn". Bản đầu viết `bg-bg-deceased` — một lớp
        // KHÔNG SINH RA CSS NÀO (token tên là `deceased`, không nằm trong nhóm `bg`), nên nền
        // trong suốt và khung chú thích gần như biến mất. Và kể cả khi viết đúng thành
        // `bg-deceased` thì vẫn không nên dùng: sắc giấy cũ được GIỮ RIÊNG cho người đã khuất
        // và phải đọc như sự tôn kính; đem nó tô một dải chú thích là làm loãng đúng tín hiệu
        // mà phả đồ dựa vào để phân biệt người sống với người đã khuất.
        <p className="m-0 rounded-lg border border-border-dark bg-warning-bg px-3 py-2.5 text-than text-text-muted">
          {t("publicNotice")}
        </p>
      )}

      <div className="space-y-3 rounded-lg border border-border bg-bg-card p-3 sm:p-4">
        <SearchBox value={rawQuery} onChange={setRawQuery} busy={isFetching} />
        <SearchFilters value={filters} onChange={setFilters} restricted={audience === "public"} />
      </div>

      <SearchResultList
        data={data}
        isLoading={(isLoading && !idle) || audience === null}
        isFetching={isFetching}
        isError={isError}
        idle={idle}
        page={page}
        onPageChange={setPage}
      />
    </div>
  );
}

"use client";

import { useEffect, useMemo, useState } from "react";
import { Alert } from "antd";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/i18n/navigation";
import { useDirectory } from "@/hooks/use-directory";
import { useMe } from "@/hooks/use-me";
import { ApiError } from "@/lib/api/http";
import { DirectoryCoverageNote } from "./directory-coverage-note";
import { DirectoryFilters, type DirectoryFilterValues } from "./directory-filters";
import { DirectoryGuestNotice } from "./directory-guest-notice";
import { DirectoryList } from "./directory-list";
import { DirectoryUnavailableNotice } from "./directory-unavailable-notice";

const DEBOUNCE_MS = 250;
const PAGE_SIZE = 20;

/**
 * Danh bạ dòng họ — "ai trong họ đang ở đâu, làm nghề gì".
 *
 * <h2>Vì sao khu vực này tồn tại bên cạnh phả đồ</h2>
 * Phả đồ là **cấu trúc huyết thống**; nó trả lời "ai sinh ra ai". Danh bạ trả
 * lời một câu khác hẳn mà cái cây không trả lời được: "ai đang ở tỉnh tôi sắp
 * chuyển tới", "trong họ có ai làm nghề này không". Tài liệu kiến trúc thông
 * tin chấp nhận sự trùng lặp ở đây một cách có ý thức, vì hai khu vực trả lời
 * hai câu hỏi khác nhau.
 *
 * <h2>Bộ lọc nằm trong URL</h2>
 * Cùng lý do với màn tìm kiếm: "bà con mình ở Nam Định đây này" là một liên kết
 * người ta dán vào nhóm chat. Khác một điểm quan trọng: URL này **không mang
 * tên ai** — chỉ tỉnh, nghề, chi — nên nó chia sẻ được mà không tiết lộ một
 * người cụ thể nào. Chỉ `q` (tìm theo tên) là có thể mang tên người, và vì thế
 * nó là tham số duy nhất người dùng phải tự gõ vào.
 *
 * <h2>Khách: một câu, không phải một danh sách rỗng</h2>
 * Máy chủ trả `401`; màn hình dịch đúng một câu ấy. Không đếm, không đoán,
 * không dựng sẵn một danh sách rỗng trông như dòng họ không còn ai.
 *
 * <h2>Ba trạng thái hỏng, ba câu khác nhau — không gộp</h2>
 * Một dải đỏ "Chưa tải được danh bạ. Vui lòng thử lại." cho cả ba là cách chắc
 * chắn nhất để người dùng làm sai việc tiếp theo:
 * <ul>
 *   <li><b>`401`</b> — chưa đăng nhập. Luật chạy đúng (Nghị định 13/2023), việc
 *       cần làm là đăng nhập. → `<DirectoryGuestNotice>`</li>
 *   <li><b>`404` / `501`</b> — máy chủ **chưa có** danh bạ. Thử lại không bao
 *       giờ đổi kết quả; việc cần làm là đi hỏi người bật được nó.
 *       → `<DirectoryUnavailableNotice>`</li>
 *   <li><b>Còn lại</b> (`5xx`, mạng đứt) — mới thật sự là "tải lỗi", và chỉ ở
 *       đây "Vui lòng thử lại" mới là lời khuyên đúng.</li>
 * </ul>
 * Phân nhánh theo **mã trạng thái**, không theo `detail`: `detail` là chữ cho
 * người đọc và đổi theo `Accept-Language` (contracts/README §3).
 */
export function DirectoryScreen() {
  const t = useTranslations("directory");
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [filters, setFilters] = useState<DirectoryFilterValues>(() => ({
    q: searchParams.get("q") ?? undefined,
    province: searchParams.get("province") ?? undefined,
    occupation: searchParams.get("occupation") ?? undefined,
    branchId: searchParams.get("branchId") ?? undefined,
  }));
  const [page, setPage] = useState(0);
  const [applied, setApplied] = useState<DirectoryFilterValues>(filters);

  useEffect(() => {
    const timer = setTimeout(() => {
      setApplied(filters);
      setPage(0);
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [filters]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (applied.q?.trim()) params.set("q", applied.q.trim());
    if (applied.province) params.set("province", applied.province);
    if (applied.occupation) params.set("occupation", applied.occupation);
    if (applied.branchId) params.set("branchId", applied.branchId);
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }, [applied, pathname, router]);

  const query = useMemo(
    () => ({
      q: applied.q?.trim() || undefined,
      province: applied.province,
      occupation: applied.occupation,
      branchId: applied.branchId,
      page,
      size: PAGE_SIZE,
    }),
    [applied, page]
  );

  const { data, isLoading, isFetching, error } = useDirectory(query);
  const { data: me } = useMe();

  const unauthenticated =
    error instanceof ApiError && (error.status === 401 || error.code === "UNAUTHENTICATED");

  /**
   * `404` = không có endpoint nào ở đường dẫn ấy. Nó KHÁC hẳn `404` của
   * `GET /persons/{id}` (ở đó `404` là phép không-tiết-lộ về một người cụ thể):
   * câu hỏi ở đây không nhắc tới ai cả, nên không có gì để giấu. `501` gộp
   * chung vì cùng một nghĩa với người dùng — bản cài đặt này chưa phục vụ.
   */
  const unavailable =
    error instanceof ApiError && (error.status === 404 || error.status === 501);

  const filtered = Boolean(
    applied.province || applied.occupation || applied.branchId || applied.q?.trim()
  );

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold text-primary">{t("title")}</h1>
        <p className="m-0 mt-1 max-w-prose text-than leading-relaxed text-text-muted">
          {t("subtitle")}
        </p>
      </header>

      {unauthenticated ? (
        <DirectoryGuestNotice />
      ) : unavailable ? (
        <DirectoryUnavailableNotice />
      ) : error ? (
        <Alert type="error" showIcon message={t("loadError")} />
      ) : (
        <>
          {data && (
            <DirectoryCoverageNote
              coverage={data.coverage}
              selfPersonId={me?.personId ?? null}
            />
          )}

          <div className="rounded-lg border border-border bg-bg-card p-3 sm:p-4">
            <DirectoryFilters
              value={filters}
              onChange={setFilters}
              facets={data?.facets}
              loading={isFetching}
            />
          </div>

          <DirectoryList
            data={data}
            isLoading={isLoading}
            isFetching={isFetching}
            filtered={filtered}
            page={page}
            pageSize={PAGE_SIZE}
            onPageChange={setPage}
            onClearFilters={() => setFilters({})}
            selfPersonId={me?.personId ?? null}
          />
        </>
      )}
    </div>
  );
}

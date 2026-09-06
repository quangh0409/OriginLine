import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { TreeCanvas } from "@/components/tree/tree-canvas";

/**
 * Thủy tổ — matches the id both src/mocks/data.ts (PersonDto fixtures) and
 * src/mocks/tree-graph/build-graph.ts (generated graph root) agree on. A
 * real deployment would resolve "the clan's root person" server-side
 * (branch/clan config), not hard-code an id — this default only exists so
 * the page has something to render without a person-search flow yet (F6,
 * a later sprint).
 */
const MOCK_ROOT_ID = "p-001";

/**
 * Gốc mặc định của phả đồ.
 *
 * Với backend thật, `p-001` không tồn tại — nhân khẩu thật mang UUID. Cho tới
 * khi có endpoint "thủy tổ của dòng họ" (chưa nằm trong hợp đồng Giai đoạn 1),
 * gốc được nạp từ `NEXT_PUBLIC_DEFAULT_ROOT_ID`; bỏ trống thì rơi về id của
 * bộ dữ liệu giả lập để `npm run dev:mock` chạy nguyên như cũ.
 *
 * `?rootId=` trên URL luôn thắng — đó là lối vào từ ô tìm kiếm.
 */
const DEFAULT_ROOT_ID = process.env.NEXT_PUBLIC_DEFAULT_ROOT_ID ?? MOCK_ROOT_ID;

export default async function TreePage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ rootId?: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const { rootId } = await searchParams;

  return (
    <AppShell>
      <TreeCanvas rootId={rootId && rootId.trim().length > 0 ? rootId : DEFAULT_ROOT_ID} />
    </AppShell>
  );
}

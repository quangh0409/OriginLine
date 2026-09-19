import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { TreeCanvas } from "@/components/tree/tree-canvas";

/**
 * Trang phả đồ.
 *
 * `?rootId=` là thứ DUY NHẤT trang này biết — và là thứ duy nhất **cần** biết.
 * Thiếu nó không còn là một câu hỏi: `<TreeCanvas>` gọi `/tree` không kèm
 * `rootId` và máy chủ chọn gốc theo **vai + phạm vi chi** của người gọi, nên
 * `/tree` trần mở ra cây ngay, cho cả khách lẫn thành viên. Lý do đầy đủ ở
 * `src/lib/tree/root-id.ts`.
 *
 * Trước đây trang này tự chọn gốc và rơi về id của bộ dữ liệu giả lập khi không
 * có cấu hình. Với backend thật, id ấy không phải UUID nên
 * `GET /api/v1/tree?rootId=p-001` trả **400 cho mọi vai** — kể cả quản trị — và
 * màn hình dịch `400` ấy thành "Không tải được cây phả đồ".
 */
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
      <TreeCanvas rootId={rootId} />
    </AppShell>
  );
}

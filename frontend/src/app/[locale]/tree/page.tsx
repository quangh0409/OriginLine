import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { TreeCanvas } from "@/components/tree/tree-canvas";

/**
 * Trang phả đồ.
 *
 * `?rootId=` và `?focus=` là tất cả những gì trang này biết.
 *
 * `?focus=` nói **mở đường tới ai** — người vừa được chọn ở ô tìm trên canvas, hoặc chính người
 * đăng nhập. Nó nằm trên URL vì cùng lý do với `?rootId=` và `?view=`: một liên kết *"đây, chỗ của
 * ông trên phả đồ"* gửi qua Zalo là đường lan truyền chính của sản phẩm này. Thiếu nó thì canvas
 * tự nhắm vào hồ sơ của chính người đăng nhập (`/me` → `personId`), và thiếu cả thứ ấy thì phả đồ
 * mở ra như cũ — không có trạng thái nào hỏng.
 *
 * Thiếu `?rootId=` không còn là một câu hỏi: `<TreeCanvas>` gọi `/tree` không kèm
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
  searchParams: Promise<{ rootId?: string; focus?: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const { rootId, focus } = await searchParams;

  return (
    <AppShell>
      <TreeCanvas rootId={rootId} focus={focus} />
    </AppShell>
  );
}

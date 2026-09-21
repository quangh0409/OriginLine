import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { MediaReportQueueScreen } from "@/components/posts/media/media-report-queue-screen";

/**
 * **Hàng chờ duyệt báo cáo ảnh/video** — điều kiện an toàn của quyết định
 * "ảnh đi theo quyền của bài" (xem javadoc `MediaReportQueueScreen`). Dựng
 * ngay trong `/quan-ly` đã có, cùng cách `/quan-ly/bai-viet` đã làm.
 */
export default async function QuanLyBaoCaoAnhPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("posts.mediaReportQueue");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("title")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("subtitle")}</p>
        <MediaReportQueueScreen />
      </KhungTrang>
    </AppShell>
  );
}

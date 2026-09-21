import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { PostReviewQueueScreen } from "@/components/posts";

/**
 * **Hàng chờ duyệt bài viết** — chốt chặn của design 07 §2, dựng ngay trong
 * `/quan-ly` đã có (nav entry đã gắn theo vai) thay vì một tuyến quản trị
 * riêng — cùng cách `/quan-ly/don-gia-nhap` đã làm.
 */
export default async function QuanLyBaiVietPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("posts");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("queue.title")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("queue.subtitle")}</p>
        <PostReviewQueueScreen />
      </KhungTrang>
    </AppShell>
  );
}

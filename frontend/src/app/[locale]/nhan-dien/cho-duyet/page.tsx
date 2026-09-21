import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ClaimPendingScreen } from "@/components/claim/claim-pending-screen";

/**
 * Màn **"đang chờ duyệt"** — `/nhan-dien/cho-duyet`.
 *
 * Đây là nơi người dùng hạ cánh ngay sau khi gửi đơn ({@code router.replace}),
 * và cũng là nơi họ quay lại nhiều ngày sau để xem đã có trả lời chưa. Hai lần
 * ấy cần hai thứ khác nhau — lần đầu cần biết <em>đã gửi xong</em>, lần sau cần
 * biết <em>đang chờ ai</em> — nên màn này nói cả hai, không chọn một.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "claim" });
  return {
    title: t("pending.title"),
    description: t("pending.meanwhileTitle"),
    robots: { index: false, follow: false },
  };
}

export default async function ChoDuyetPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang beRong="hep">
        <ClaimPendingScreen />
      </KhungTrang>
    </AppShell>
  );
}

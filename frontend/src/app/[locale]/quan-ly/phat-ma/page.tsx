import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { InviteScreen } from "@/components/membership";

/**
 * **Màn phát mã và phát lời mời** — một màn, hai việc, hai vai (design 07 §1.3).
 *
 * Hội đồng phát và thu hồi <em>mã mời dòng họ</em> cùng bộ đếm lượt dùng;
 * Trưởng chi phát <em>lời mời cá nhân</em> cho một cụ cụ thể. Nửa thứ hai đã
 * chạy thật ở máy chủ từ trước nhưng chưa có màn hình nào gọi tới — cho tới
 * trang này, phát một lời mời phải làm bằng dòng lệnh.
 */
export default async function PhatMaPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("membership");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("invite.title")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("invite.subtitle")}</p>
        <InviteScreen />
      </KhungTrang>
    </AppShell>
  );
}

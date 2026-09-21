import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { HonoursListScreen } from "@/components/honours";

export default async function VinhDanhPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("honours");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("pageTitle")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("pageSubtitle")}</p>
        <HonoursListScreen />
      </KhungTrang>
    </AppShell>
  );
}

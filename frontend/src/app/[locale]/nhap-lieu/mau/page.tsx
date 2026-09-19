import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ImportTemplateScreen } from "@/components/import/import-template-screen";

/** Màn 2 — mẫu Excel riêng cho từng chi, kèm hướng dẫn ngắn. */
export default async function DataImportTemplatePage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <ImportTemplateScreen />
      </KhungTrang>
    </AppShell>
  );
}

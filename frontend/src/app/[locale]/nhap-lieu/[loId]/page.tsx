import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ImportReconcileScreen } from "@/components/import/import-reconcile-screen";

/** Màn 3b — đối soát một lô trong khu vực chờ. Màn quyết định thành bại. */
export default async function DataImportBatchPage({
  params,
}: {
  params: Promise<{ locale: string; loId: string }>;
}) {
  const { locale, loId } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <ImportReconcileScreen batchId={loId} />
      </KhungTrang>
    </AppShell>
  );
}

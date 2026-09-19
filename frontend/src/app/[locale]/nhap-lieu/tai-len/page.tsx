import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ImportUploadScreen } from "@/components/import/import-upload-screen";

/** Màn 3a — tải tệp lên. Đọc và đối soát, chưa ghi gì vào phả. */
export default async function DataImportUploadPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <ImportUploadScreen />
      </KhungTrang>
    </AppShell>
  );
}

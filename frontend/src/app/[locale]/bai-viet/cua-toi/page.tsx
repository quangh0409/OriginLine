import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { MyPostsScreen } from "@/components/posts";

export default async function BaiVietCuaToiPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <MyPostsScreen />
      </KhungTrang>
    </AppShell>
  );
}

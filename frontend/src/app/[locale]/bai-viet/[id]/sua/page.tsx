import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { PostComposeScreen } from "@/components/posts";

export default async function SuaBaiVietPage({
  params,
}: {
  params: Promise<{ locale: string; id: string }>;
}) {
  const { locale, id } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <PostComposeScreen postId={id} />
      </KhungTrang>
    </AppShell>
  );
}

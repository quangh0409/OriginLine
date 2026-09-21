import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { PostDetailScreen } from "@/components/posts";

export default async function BaiVietPage({
  params,
}: {
  params: Promise<{ locale: string; id: string }>;
}) {
  const { locale, id } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <PostDetailScreen postId={id} />
      </KhungTrang>
    </AppShell>
  );
}

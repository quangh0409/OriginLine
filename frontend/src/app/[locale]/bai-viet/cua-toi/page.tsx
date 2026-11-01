import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { MyPostsScreen } from "@/components/posts";

export default async function BaiVietCuaToiPage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ daGui?: string | string[] }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  // Đọc ở máy chủ rồi truyền xuống, KHÔNG dùng `useSearchParams()` trong component: hook ấy đòi
  // một `<Suspense>` bao ngoài, và thiếu nó là `next build` gãy ở một trang vô can (CLAUDE.md).
  const { daGui } = await searchParams;
  const justSubmittedId = typeof daGui === "string" && daGui ? daGui : undefined;

  return (
    <AppShell>
      <KhungTrang>
        <MyPostsScreen justSubmittedId={justSubmittedId} />
      </KhungTrang>
    </AppShell>
  );
}

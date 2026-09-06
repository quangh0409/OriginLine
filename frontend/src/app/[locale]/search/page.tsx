import { Suspense } from "react";
import { setRequestLocale, getTranslations } from "next-intl/server";
import { Skeleton } from "antd";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { PersonSearchScreen } from "@/components/search/person-search-screen";

/**
 * F6 — tìm kiếm nhân khẩu.
 *
 * `PersonSearchScreen` reads `?q=&generation=&branchId=&nativePlace=` through
 * `useSearchParams`, which the App Router requires to sit inside a Suspense
 * boundary so the surrounding shell can still be prerendered.
 */
export default async function SearchPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("search");

  return (
    <AppShell>
      <div className="mx-auto w-full max-w-3xl px-3 py-4 sm:px-4 sm:py-6">
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("title")}</h1>
        <p className="mb-4 mt-0 text-[14px] text-text-muted">{t("subtitle")}</p>
        <Suspense fallback={<Skeleton active paragraph={{ rows: 4 }} />}>
          <PersonSearchScreen />
        </Suspense>
      </div>
    </AppShell>
  );
}

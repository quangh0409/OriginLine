import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { HomeHero } from "@/components/home/home-hero";

export default async function HomePage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <HomeHero />
    </AppShell>
  );
}

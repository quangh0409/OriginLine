import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { PersonForm } from "@/components/person-form/person-form";

/**
 * F4 — thêm nhân khẩu.
 *
 * No client-side role gate here on purpose: the authoritative check is the
 * backend's (a MEMBER gets 403 from `POST /persons` in Phase 1). Hiding the
 * page behind a guessed role would drift from the server's answer the moment
 * branch scoping lands.
 */
export default async function NewPersonPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("personForm");

  return (
    <AppShell>
      <div className="mx-auto w-full max-w-3xl px-3 py-4 sm:px-4 sm:py-6">
        <h1 className="mb-4 font-serif text-2xl font-bold text-text-main">{t("createTitle")}</h1>
        <PersonForm mode="create" />
      </div>
    </AppShell>
  );
}

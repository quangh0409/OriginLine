import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { PersonEditScreen } from "@/components/person-form/person-edit-screen";

/** F4 — sửa nhân khẩu. Loading, RBAC and ETag handling live in the client screen. */
export default async function EditPersonPage({
  params,
}: {
  params: Promise<{ locale: string; id: string }>;
}) {
  const { locale, id } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <div className="mx-auto w-full max-w-3xl px-3 py-4 sm:px-4 sm:py-6">
        <PersonEditScreen personId={id} />
      </div>
    </AppShell>
  );
}

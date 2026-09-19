import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { PersonProfile } from "@/components/person/person-profile";
import { PushEngagementSignal } from "@/components/notifications/push-engagement-signal";

/**
 * F3 — standalone hồ sơ nhân khẩu page. The same profile also opens as a
 * drawer from the phả đồ canvas; this route exists so a profile can be linked,
 * shared and bookmarked.
 *
 * Reading a profile is one of the two engagement signals that unlock the Web
 * Push permission card (plan §4 F7 — ask afterwards, never on arrival). The
 * signal is recorded here, at the route, so <PersonProfile> itself stays free
 * of notification concerns.
 */
export default async function PersonPage({
  params,
}: {
  params: Promise<{ locale: string; id: string }>;
}) {
  const { locale, id } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <PushEngagementSignal reason="PERSON_PROFILE" />
        <PersonProfile personId={id} />
      </KhungTrang>
    </AppShell>
  );
}

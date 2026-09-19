"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { PushSettingsCard } from "@/components/notifications/push-settings-card";
import { colorVars } from "@/styles/tokens";

/**
 * Notification settings.
 *
 * Structured to make the channel hierarchy obvious: the in-app inbox is
 * listed first and has no switch, because it cannot be turned off and is
 * always complete. Web Push comes second, framed as an addition. A member who
 * never touches this page still receives every giỗ reminder — the switch only
 * decides whether their phone also buzzes when the app is closed.
 */
export function NotificationSettingsScreen() {
  const t = useTranslations("push");
  const tNotif = useTranslations("notifications");

  return (
    <div className="space-y-4">
      <section
        className="rounded-lg border p-4"
        style={{ borderColor: colorVars.border, background: colorVars.bgCard }}
      >
        <h2 className="m-0 font-serif text-[16px] font-semibold text-text-main">
          {t("settings.inAppTitle")}
        </h2>
        <p className="mb-0 mt-1 text-than leading-relaxed text-text-muted">
          {t("settings.inAppBody")}
        </p>
        <p className="mb-0 mt-2 text-than">
          <Link href="/notifications" className="text-primary no-underline hover:underline">
            {tNotif("title")}
          </Link>
        </p>
      </section>

      <PushSettingsCard />
    </div>
  );
}

"use client";

import { useTranslations } from "next-intl";
import { colorTokens } from "@/styles/tokens";

/**
 * "Thêm vào màn hình chính" — the step iPhone users must take before Web
 * Push can exist for them at all (iOS 16.4+ grants push only to an installed
 * PWA). Without this guide the majority of diaspora members on iPhones would
 * simply never receive a reminder and would have no way to find out why
 * (contracts/openapi.yaml `/push/subscriptions` platform notes).
 *
 * Written as plain numbered steps rather than screenshots: the Share sheet
 * moves between iOS versions, and stale screenshots mislead worse than words.
 */
export function IosInstallGuide({ compact = false }: { compact?: boolean }) {
  const t = useTranslations("push.ios");
  const steps = ["share", "addToHome", "confirm", "reopen"] as const;

  return (
    <div
      className="rounded-lg border p-3"
      style={{ borderColor: colorTokens.borderDark, background: colorTokens.warningBg }}
    >
      <p className="m-0 text-[13.5px] font-medium text-text-main">{t("title")}</p>
      {!compact && (
        <p className="mb-0 mt-1 text-[12.5px] leading-relaxed text-text-muted">{t("why")}</p>
      )}
      <ol className="mb-0 mt-2 list-decimal space-y-1 pl-5 text-[12.5px] leading-relaxed text-text-muted">
        {steps.map((step) => (
          <li key={step}>{t(`steps.${step}`)}</li>
        ))}
      </ol>
    </div>
  );
}

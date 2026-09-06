"use client";

import { Segmented } from "antd";
import { useLocale, useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/i18n/navigation";
import type { AppLocale } from "@/i18n/routing";

export function LanguageSwitcher() {
  const locale = useLocale();
  const t = useTranslations("language");
  const pathname = usePathname();
  const router = useRouter();

  return (
    <Segmented
      size="small"
      value={locale}
      aria-label={t("vi") + " / " + t("en")}
      options={[
        { label: "VI", value: "vi", title: t("vi") },
        { label: "EN", value: "en", title: t("en") },
      ]}
      onChange={(value) => {
        router.replace(pathname, { locale: value as AppLocale });
      }}
    />
  );
}

import { defineRouting } from "next-intl/routing";

/**
 * Bilingual vi/en, default vi (per plan §4 F0 + BA v2 UI requirement).
 * localePrefix "as-needed" means the default locale (vi) is served at "/"
 * with no prefix, and English lives under "/en" — matches "mặc định vi".
 */
export const routing = defineRouting({
  locales: ["vi", "en"],
  defaultLocale: "vi",
  localePrefix: "as-needed",
});

export type AppLocale = (typeof routing.locales)[number];

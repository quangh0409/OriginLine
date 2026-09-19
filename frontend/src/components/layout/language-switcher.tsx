"use client";

import { Segmented } from "antd";
import { useLocale, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { usePathname, useRouter } from "@/i18n/navigation";
import type { AppLocale } from "@/i18n/routing";

export function LanguageSwitcher() {
  const locale = useLocale();
  const t = useTranslations("language");
  const pathname = usePathname();
  const searchParams = useSearchParams();
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
        // Phải mang theo chuỗi truy vấn. `usePathname` của next-intl chỉ trả phần đường dẫn, nên
        // `router.replace(pathname, ...)` làm RƠI hết tham số. Kịch bản hỏng đúng là kịch bản
        // sản phẩm sinh ra để phục vụ: người trong họ gửi anh em kiều bào một liên kết tra danh
        // xưng `/kinship?from=…&to=…`, người nhận bấm sang EN cho dễ đọc, và mất sạch hai người
        // vừa được chọn. Tương tự với `/search?q=…` và `/tree?rootId=…`.
        const truyVan = searchParams.toString();
        const dich = truyVan ? `${pathname}?${truyVan}` : pathname;
        router.replace(dich, { locale: value as AppLocale });
      }}
    />
  );
}

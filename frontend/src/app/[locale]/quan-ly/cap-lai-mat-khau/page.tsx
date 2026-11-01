import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { PasswordResetScreen } from "@/components/membership";

/**
 * **Cấp lại liên kết đặt mật khẩu** — ô thứ sáu của `/quan-ly`.
 *
 * Backend (`POST /api/v1/password-resets`) đã xong, có test tích hợp xanh và
 * một migration (`V21`) từ trước; đây là trang đầu tiên gọi tới nó. Trước
 * trang này, việc duy nhất Trưởng chi làm được khi một cụ quên mật khẩu là
 * gọi điện — không có nút nào để bấm.
 */
export default async function CapLaiMatKhauPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("membership");

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">
          {t("passwordReset.title")}
        </h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("passwordReset.lead")}</p>
        <PasswordResetScreen />
      </KhungTrang>
    </AppShell>
  );
}

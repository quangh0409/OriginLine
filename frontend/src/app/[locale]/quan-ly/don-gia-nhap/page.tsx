import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { MembershipQueueScreen } from "@/components/membership";

/**
 * **Hàng chờ duyệt đơn gia nhập** — chốt chặn thứ năm của design 07 §1.3.
 *
 * Phân quyền nằm trọn trong {@code <MembershipQueueScreen>}: nó phụ thuộc vào
 * phạm vi chi/ngành (`GET /api/v1/me`) chứ không chỉ vai trò — một dữ kiện chỉ
 * biết được ở phía client sau khi gọi API, nên không dựng được ở tầng trang.
 */
export default async function DonGiaNhapPage({
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
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("queue.title")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("queue.subtitle")}</p>
        <MembershipQueueScreen />
      </KhungTrang>
    </AppShell>
  );
}

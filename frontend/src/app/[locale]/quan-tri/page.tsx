import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ClanSetupWizard } from "@/components/clan-setup/clan-setup-wizard";

/**
 * **Khởi tạo dòng họ** — `/quan-tri` (và `/en/quan-tri`), chỉ vai `ADMIN`.
 *
 * Quyết định 26/09/2026 của chủ dự án: hôm nay không có màn hình nào để đưa
 * một dòng họ mới vào hệ thống — mọi thứ làm tay dưới CSDL. Đây là bước 1:
 * hồ sơ dòng họ, Hội đồng đầu tiên, nhập phả, phát mã mời, rồi sẵn sàng.
 *
 * Đường dẫn giữ tiếng Việt ở cả hai ngôn ngữ, cùng quy ước với `/quan-ly`,
 * `/nhap-lieu`, `/danh-ba`. Gác vai nằm trong `<ClanSetupWizard>`, không ở
 * đây — cùng khuôn `EventCreateScreen`/`MembershipQueueScreen`: trang chỉ
 * dựng khung, màn hình con tự quyết định ai vào được.
 */
export default async function ClanSetupPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <ClanSetupWizard />
      </KhungTrang>
    </AppShell>
  );
}

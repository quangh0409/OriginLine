import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ImportIntroScreen } from "@/components/import/import-intro-screen";

/**
 * B-nhập-liệu màn 1 — quy trình năm bước.
 *
 * <h2>Về cách đặt tuyến đường ở nhánh này</h2>
 * `/nhap-lieu/mau`, `/nhap-lieu/tai-len`, `/nhap-lieu/tien-do` là ba đoạn tĩnh
 * đứng cạnh đoạn động `/nhap-lieu/[loId]`. App Router luôn ưu tiên đoạn tĩnh
 * trước đoạn động, nên ba đường ấy không bao giờ bị nuốt — kể cả nếu một mã lô
 * trùng chữ. Mã lô do máy chủ sinh theo dạng `2026-014`, nên khả năng trùng là
 * lý thuyết; ghi ra đây để người sửa sau không phải tự kiểm chứng lại.
 *
 * Ba đường dẫn lấy nguyên từ kế hoạch 04 §3.1 (mục B6–B10) để tài liệu và mã
 * nguồn không phải dịch qua lại.
 */
export default async function DataImportPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <ImportIntroScreen />
      </KhungTrang>
    </AppShell>
  );
}

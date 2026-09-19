import { Suspense } from "react";
import { setRequestLocale, getTranslations } from "next-intl/server";
import { Skeleton } from "antd";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { DirectoryScreen } from "@/components/directory/directory-screen";

/**
 * Danh bạ dòng họ — `/danh-ba`.
 *
 * Đường dẫn giữ nguyên tiếng Việt ở cả hai ngôn ngữ, như `/su-kien`, `/tim-kiem`
 * sẽ theo sau: một liên kết dán vào nhóm chat của dòng họ phải mở ra đúng một
 * chỗ, bất kể người gửi và người nhận đang để giao diện ở ngôn ngữ nào.
 *
 * `DirectoryScreen` đọc `?q=&province=&occupation=&branchId=` qua
 * `useSearchParams`, mà App Router bắt buộc phải nằm trong một ranh giới
 * Suspense thì phần khung quanh nó mới dựng trước được.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "directory" });
  return {
    title: t("title"),
    // Mô tả cố ý chỉ nói về KHU VỰC, không nói về một ai. Thẻ xem trước là thứ
    // bot của Zalo/Facebook đọc được mà không cần đăng nhập — đó là chỗ rò rỉ
    // đã được nêu đích danh trong tài liệu kiến trúc thông tin §7.4.
    description: t("subtitle"),
  };
}

export default async function DirectoryPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang>
        <Suspense fallback={<Skeleton active paragraph={{ rows: 6 }} />}>
          <DirectoryScreen />
        </Suspense>
      </KhungTrang>
    </AppShell>
  );
}

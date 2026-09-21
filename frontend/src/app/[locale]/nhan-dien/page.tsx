import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Skeleton } from "antd";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { ClaimScreen } from "@/components/claim/claim-screen";

/**
 * Màn **"tôi là ai trong phả"** — `/nhan-dien` (và `/en/nhan-dien`).
 *
 * <h2>Đường dẫn giữ nguyên tiếng Việt ở cả hai ngôn ngữ</h2>
 * Cùng lý do với `/moi` và `/danh-ba`: đây là địa chỉ người bác gửi qua Zalo
 * cho đứa cháu ở nước ngoài, và nó phải mở ra đúng một chỗ bất kể hai người
 * đang để giao diện ở ngôn ngữ nào.
 *
 * <h2>`<Suspense>` là BẮT BUỘC, và thiếu nó thì lỗi báo sai chỗ</h2>
 * {@code ClaimScreen} đọc `?nguoi=` bằng {@code useSearchParams()}. App Router
 * đòi mọi nhánh đọc chuỗi truy vấn phải nằm trong một ranh giới Suspense, nếu
 * không {@code next build} đỏ — và thông báo lỗi <b>nêu tên một trang vô can</b>
 * chứ không nêu thành phần có lỗi. Đó là cái bẫy đã ghi trong CLAUDE.md và đã
 * từng để một lỗi nằm im trong `main` qua vài đợt "tất cả đều xanh".
 *
 * <h2>Không đưa mã nhân khẩu vào tiêu đề trang</h2>
 * Tiêu đề đi vào lịch sử duyệt web, danh sách tab và ảnh chụp màn hình. Mã nhân
 * khẩu không phải bí mật như mã mời, nhưng tiêu đề "Tôi là ai trong phả" thì
 * đúng nghĩa hơn với người đọc, và không có lý do nào để đánh đổi.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "claim" });
  return {
    title: t("pageTitle"),
    description: t("pageDescription"),
    // Trang của một cá nhân đang trong một quy trình; không bao giờ là thứ để
    // tìm thấy từ máy tìm kiếm.
    robots: { index: false, follow: false },
  };
}

export default async function NhanDienPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      {/* `hep`: một cột, một quyết định. Bề rộng đọc hẹp giữ mắt không phải quét
          ngang giữa tên người và nút bấm — và người dùng chính của màn này là
          người mở hệ thống lần đầu, rất nhiều trên điện thoại. */}
      <KhungTrang beRong="hep">
        <Suspense fallback={<Skeleton active paragraph={{ rows: 5 }} />}>
          <ClaimScreen />
        </Suspense>
      </KhungTrang>
    </AppShell>
  );
}

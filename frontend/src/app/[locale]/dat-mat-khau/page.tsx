import { Suspense } from "react";
import type { Metadata } from "next";
import { Skeleton } from "antd";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { SetPasswordScreen } from "@/components/auth/set-password-screen";

/**
 * Đặt mật khẩu — `/dat-mat-khau` (và `/en/dat-mat-khau`).
 *
 * <h2>Đường dẫn giữ nguyên tiếng Việt ở cả hai ngôn ngữ</h2>
 * Cùng lý do với `/moi/<mã>` và `/danh-ba`: khuôn địa chỉ này được **backend**
 * đúc vào `setPasswordUrl` rồi gửi qua tin nhắn. Nó phải mở ra đúng một chỗ bất
 * kể giao diện đang để ngôn ngữ nào, và nó không được đổi theo một thiết lập
 * phía client mà máy chủ không nhìn thấy.
 *
 * <h2>Ba đường `token` có thể rò, và cách chặn từng đường</h2>
 * `token` là chứng chỉ đặt mật khẩu cho một tài khoản thật. Nó đến trong đường
 * dẫn vì đó là thứ gửi được qua một tin nhắn; việc của trang này là không nhân
 * thêm một bản sao nào.
 * <ol>
 *   <li><b>Tiêu đề trang.</b> {@code generateMetadata} không nhận tham số truy
 *       vấn nào vào tiêu đề. Tiêu đề đi vào lịch sử duyệt web, danh sách tab,
 *       ảnh chụp màn hình và thẻ xem trước.</li>
 *   <li><b>Referer.</b> {@code referrer: "no-referrer"} để mọi lượt rời trang —
 *       kể cả cú chuyển sang Keycloak để đăng nhập — không mang URL chứa token
 *       sang máy chủ bên kia và vào nhật ký của nó.</li>
 *   <li><b>Bọ tìm kiếm và thẻ xem trước.</b> {@code robots} chặn cả lập chỉ mục
 *       lẫn lưu bản sao.</li>
 * </ol>
 * Đường thứ tư — **thanh địa chỉ và lịch sử trình duyệt** — được chặn ở tầng
 * thành phần: {@code SetPasswordScreen} gỡ token khỏi URL ngay sau lượt đọc đầu
 * tiên. Đó là chỗ duy nhất làm được việc ấy, vì nó phải chạy ở trình duyệt.
 *
 * <h2>Token đọc ở CLIENT, không qua `searchParams` của trang</h2>
 * Nhận nó qua {@code searchParams} rồi truyền xuống làm prop sẽ tuần tự hoá nó
 * vào payload RSC và vào HTML gửi về — một bản sao nữa, nằm trong thứ mọi bộ
 * đệm trung gian đều thấy. Đọc bằng {@code useSearchParams} giữ nó ở đúng một
 * nơi: bộ nhớ của trình duyệt.
 *
 * <h2>`<Suspense>` là BẮT BUỘC</h2>
 * {@code useSearchParams} buộc nhánh chứa nó rời khỏi lượt dựng tĩnh. Thiếu
 * biên Suspense thì `next build` gãy — và thông báo lỗi nó in ra **nêu tên một
 * trang vô can**, chứ không nêu thành phần có lỗi. Không một ca kiểm nào bắt
 * được chuyện này: bộ E2E chỉ chạy được trên `next dev`, nơi lượt dựng tĩnh
 * không diễn ra.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "auth.setPassword" });

  return {
    // KHÔNG chèn token vào đây. Xem javadoc ở trên.
    title: t("pageTitle"),
    description: t("pageDescription"),
    referrer: "no-referrer",
    robots: { index: false, follow: false, nocache: true },
  };
}

export default async function SetPasswordPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      {/* `hep`: một biểu mẫu một cột, mở lần đầu trên điện thoại, bởi người
          đang làm một việc họ chưa từng làm. Bề rộng đọc hẹp giữ mắt không phải
          quét ngang giữa nhãn và ô. */}
      <KhungTrang beRong="hep">
        <Suspense fallback={<Skeleton active paragraph={{ rows: 5 }} />}>
          <SetPasswordScreen />
        </Suspense>
      </KhungTrang>
    </AppShell>
  );
}

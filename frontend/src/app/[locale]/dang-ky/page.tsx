import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { RegisterScreen } from "@/components/auth/register-screen";

/**
 * Đăng ký bằng mã mời dòng họ — `/dang-ky` (và `/en/dang-ky`).
 *
 * <h2>Đường dẫn giữ nguyên tiếng Việt ở cả hai ngôn ngữ</h2>
 * Cùng lý do với `/moi/<mã>`, `/dat-mat-khau` và `/danh-ba`: địa chỉ này được
 * đúc vào **`theme.properties` của Keycloak** (`giaphaRegisterUrl`) và vào tin
 * nhắn Hội đồng gửi kèm mã. Nó phải mở ra đúng một chỗ bất kể người gửi lẫn
 * người nhận đang để giao diện ở ngôn ngữ nào, và nó không được đổi theo một
 * thiết lập phía client mà Keycloak không nhìn thấy.
 *
 * <h2>KHÔNG nhận mã qua tham số truy vấn — và đó là một quyết định</h2>
 * Trang này cố ý **không** đọc `?ma=…`, khác hẳn `/moi/<mã>`. Ở đó mã nằm
 * trong đường dẫn vì không tránh được — đó là thứ in trên phiếu giấy. Ở đây thì
 * tránh được: người dùng gõ mã vào một ô, và mã đi trong **thân POST**. Nhận
 * thêm một lối qua URL là tự tạo ra bốn bản sao của một bí mật (thanh địa chỉ,
 * lịch sử duyệt web, header `Referer`, nhật ký của mọi proxy trên đường) để đổi
 * lấy việc đỡ cho người dùng đúng một lần gõ.
 *
 * Và ở mã **dòng họ** cái giá ấy nặng hơn mã cá nhân: mã cá nhân chết sau một
 * lần dùng, còn mã dòng họ **sống suốt hạn** và mở cửa cho *cả dòng họ*.
 *
 * Hệ quả phụ, có thật và đáng giá: trang không dùng `useSearchParams`, nên nó
 * không cần biên `<Suspense>` — tức nó không thể sập vào đúng cái bẫy đã ngồi
 * im trong `main` qua mấy đợt "tất cả đều xanh", nơi `next build` gãy và thông
 * báo lỗi nêu tên một trang vô can.
 *
 * <h2>`robots: index:false`</h2>
 * Cổng này của một dòng họ cụ thể; một trang đăng ký lọt vào kết quả tìm kiếm
 * chỉ mời người lạ tới gõ thử mã, tức bơm thẳng lưu lượng vào chốt giới hạn
 * tần suất. Không đặt `referrer: "no-referrer"` như hai trang kia, và đó là có
 * chủ ý — ở đây **không có bí mật nào trong URL để mà rò**.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "auth.register" });

  return {
    title: t("pageTitle"),
    description: t("pageDescription"),
    robots: { index: false, follow: false, nocache: true },
  };
}

export default async function DangKyPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      {/* `hep`: một biểu mẫu một cột, mở lần đầu trên điện thoại, bởi người
          đang làm một việc họ chưa từng làm. Cùng bề rộng với `/moi` và
          `/dat-mat-khau` — ba màn của cùng một buổi. */}
      <KhungTrang beRong="hep">
        <RegisterScreen />
      </KhungTrang>
    </AppShell>
  );
}

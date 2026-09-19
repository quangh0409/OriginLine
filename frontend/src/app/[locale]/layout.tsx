import type { Metadata, Viewport } from "next";
import { colorTokens, darkColorTokens } from "@/styles/tokens";
import { ThemeScript } from "@/lib/theme/theme-script";
import { Be_Vietnam_Pro, Noto_Serif } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { routing, type AppLocale } from "@/i18n/routing";
import { Providers } from "../providers";
import "../globals.css";

// Both fonts declare the "vietnamese" Google Fonts subset explicitly, which
// covers stacked diacritics (ữ, ỹ, ặ, ...) — do not swap for a font lacking
// that subset. Be Vietnam Pro (UI/body) was designed in Vietnam specifically
// for this; Noto Serif carries the warm, traditional heading tone.
const beVietnamPro = Be_Vietnam_Pro({
  subsets: ["vietnamese", "latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-be-vietnam-pro",
  display: "swap",
});

const notoSerif = Noto_Serif({
  subsets: ["vietnamese", "latin"],
  weight: ["600", "700"],
  variable: "--font-noto-serif",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Cổng Thông Tin Gia Phả Dòng Họ",
  description:
    "Hệ thống quản lý gia phả và cổng thông tin dòng họ — cây phả đồ, tra danh xưng, nhắc giỗ.",
  manifest: "/manifest.json",
  icons: {
    icon: "/icons/icon-192.png",
    apple: "/icons/apple-touch-icon.png",
  },
};

export const viewport: Viewport = {
  // Màu thanh trạng thái trình duyệt phải đổi theo chế độ. Một giá trị cứng khiến người dùng
  // chế độ tối thấy một vạch đỏ trầm sáng chói nằm trên nền tối — chính chỗ mà giao diện đáng lẽ
  // phải liền mạch với hệ điều hành.
  themeColor: [
    // Lấy từ token, không viết hex. `themeColor` là siêu dữ liệu HTML nên KHÔNG dùng được
    // `var(--…)` — nhưng đó là lý do phải nhập từ nguồn, không phải lý do để chép tay: chép tay
    // là chỗ bảng màu bắt đầu trôi khỏi sản phẩm.
    { media: "(prefers-color-scheme: light)", color: colorTokens.primary },
    { media: "(prefers-color-scheme: dark)", color: darkColorTokens.bgPage },
  ],
  width: "device-width",
  initialScale: 1,
};

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!routing.locales.includes(locale as AppLocale)) {
    notFound();
  }
  setRequestLocale(locale);

  const messages = await getMessages();

  return (
    <html
      lang={locale}
      className={`${beVietnamPro.variable} ${notoSerif.variable}`}
      // <ThemeScript> ghi `class` và `style.colorScheme` lên chính thẻ này TRƯỚC khi React
      // hydrate — đó là mục đích của nó. Nên HTML từ máy chủ và HTML ở trình duyệt khác nhau
      // một cách CÓ CHỦ Ý, và React sẽ cảnh báo lệch hydrate nếu không nói trước.
      //
      // Cảnh báo đó không vô hại: sáu kịch bản e2e của phả đồ đỏ vì nó, dù mọi khẳng định về
      // hình học lẫn hành vi đều xanh — chúng chỉ đỏ vì có lỗi in ra console.
      //
      // Chỉ áp cho ĐÚNG thẻ này, không lan xuống con.
      suppressHydrationWarning
    >
      <head>
        {/* Phải chạy TRƯỚC khung hình đầu tiên, nếu không người chọn chế độ Tối sẽ thấy trang
            loé sáng trắng một nhịp rồi mới tối lại. Với người lão thị đó không chỉ khó chịu — nó
            chói. Xem lib/theme/theme-script.tsx. */}
        <ThemeScript />
      </head>
      <body className="font-sans bg-bg-page text-text-main min-h-screen antialiased">
        <NextIntlClientProvider messages={messages}>
          <Providers locale={locale as AppLocale}>{children}</Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

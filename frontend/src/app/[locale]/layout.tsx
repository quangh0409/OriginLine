import type { Metadata, Viewport } from "next";
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
  themeColor: "#8c2d19",
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
    <html lang={locale} className={`${beVietnamPro.variable} ${notoSerif.variable}`}>
      <body className="font-sans bg-bg-page text-text-main min-h-screen antialiased">
        <NextIntlClientProvider messages={messages}>
          <Providers locale={locale as AppLocale}>{children}</Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

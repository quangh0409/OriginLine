import { setRequestLocale, getTranslations } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { Link } from "@/i18n/navigation";

/**
 * Lối vào phần **quản lý** — Trưởng chi và Hội đồng Tộc biểu.
 *
 * <h2>Vì sao có trang chỉ mục này</h2>
 * Hai màn bên dưới không có mục nào trên thanh điều hướng (thanh ấy thuộc mạch
 * việc khác và đang có người sửa song song). Không có trang này thì cả hai chỉ
 * tới được bằng cách gõ tay đường dẫn — tức lặp lại đúng vấn đề mà design 07 §4
 * đã ghi nhận một lần: "khi một tính năng đã xong mà người dùng không biết, vấn
 * đề nằm ở **lối vào**, không phải ở tính năng".
 *
 * Đây là chỗ đứng tạm. Khi thanh điều hướng mở cho nhóm này, trang vẫn dùng
 * được làm trang đích.
 */
export default async function QuanLyPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale as AppLocale);
  const t = await getTranslations("membership");
  const tPosts = await getTranslations("posts");
  const tImport = await getTranslations("dataImport");

  const muc = [
    {
      href: "/quan-ly/don-gia-nhap",
      title: t("queue.title"),
      desc: t("queue.subtitle"),
    },
    {
      href: "/quan-ly/phat-ma",
      title: t("invite.title"),
      desc: t("invite.subtitle"),
    },
    {
      href: "/quan-ly/bai-viet",
      title: tPosts("queue.title"),
      desc: tPosts("queue.subtitle"),
    },
    {
      href: "/quan-ly/bao-cao-anh",
      title: tPosts("mediaReportQueue.title"),
      desc: tPosts("mediaReportQueue.subtitle"),
    },
    // Trước bản sửa này, `/nhap-lieu` — toàn bộ đường ống nạp phả từ Excel —
    // không có MỘT liên kết nào trong toàn bộ giao diện trỏ tới, kể cả từ
    // đây. Tám tệp dựng nên nó chỉ tự trỏ vào nhau; gõ tay đường dẫn là cách
    // duy nhất tới được nó. Đây là đường ống mà CLAUDE.md gọi là "thứ khiến
    // hệ thống dùng được với một dòng họ thật" — không có nó thì không ai
    // nhập nổi 1.500 người bằng tay từng dòng.
    {
      href: "/nhap-lieu",
      title: tImport("title"),
      desc: tImport("subtitle"),
    },
  ];

  return (
    <AppShell>
      <KhungTrang>
        <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">{t("hub.title")}</h1>
        <p className="mb-4 mt-0 text-than text-text-muted">{t("hub.subtitle")}</p>

        <ul className="m-0 flex list-none flex-col gap-3 p-0">
          {muc.map((m) => (
            <li key={m.href}>
              <Link
                href={m.href}
                className="block rounded-lg border border-border bg-bg-card px-4 py-3 no-underline"
              >
                <span className="block font-serif text-de font-semibold text-primary">
                  {m.title}
                </span>
                <span className="mt-1 block text-than text-text-muted">{m.desc}</span>
              </Link>
            </li>
          ))}
        </ul>
      </KhungTrang>
    </AppShell>
  );
}

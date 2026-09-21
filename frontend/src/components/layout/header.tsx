"use client";

import { Suspense } from "react";
import { Layout } from "antd";
import { SettingOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { LanguageSwitcher } from "./language-switcher";
import { MoreMenu } from "./more-menu";
import { NotificationBell } from "@/components/notifications/notification-bell";
import { CorrectionQueueLink } from "@/components/correction/correction-queue-link";
import { ManagementQueueLink } from "@/components/membership";
import { MockRoleSwitcher } from "@/components/dev/mock-role-switcher";
import { AuthMenu } from "./auth-menu";
import { useMe } from "@/hooks/use-me";

const { Header: AntHeader } = Layout;

/**
 * Desktop nav. Every entry is a real route now that F6 (search) and F7
 * (events, notifications) have landed — the placeholder <Tag>s are gone. On
 * phones these are hidden and <MobileNav> + <MoreMenu> take over.
 *
 * `posts` and `honours` were missing here until this fix, and the gap was
 * real: `<MoreMenu>` — the only other place either route was linked from —
 * has a trigger that is `md:hidden`, so on a computer the sole way back to
 * `/bai-viet` or `/vinh-danh` was the "Xem tất cả" link on the home page.
 * Leave the home page, and both screens become unreachable again until the
 * next visit home. These are two of the product's four main content areas
 * (design 07 §2's "trang chủ thật": posts · honours · upcoming events ·
 * tree/kinship) — the other two (`events`, and tree/kinship) already had a
 * permanent nav entry; these two did not.
 */
const NAV_ITEMS = [
  { key: "tree", href: "/tree" },
  // Khách bớt: `/kinship` đòi một tài khoản (xem `KinshipGuestNotice`) —
  // trước bản sửa này mục này hiện cho mọi người, kể cả khách, và bộ chọn
  // người của màn ấy chỉ lặng lẽ báo "không tìm thấy" ở mọi lượt gõ của họ.
  { key: "kinship", href: "/kinship", membersOnly: true },
  { key: "search", href: "/search" },
  { key: "directory", href: "/danh-ba" },
  { key: "events", href: "/events" },
  { key: "posts", href: "/bai-viet" },
  { key: "honours", href: "/vinh-danh" },
] as const;

/**
 * Lớp dùng chung cho mọi điều khiển dạng liên kết trên thanh đầu trang.
 *
 * `min-h-11` = 44px là thứ THẬT SỰ tạo ra vùng chạm. Trước đợt sửa này các mục ở
 * đây là thẻ <Link> trần, nên chiều cao của chúng đúng bằng chiều cao một dòng
 * chữ — đo được 20px, và 18×20px với hai mục chỉ có biểu tượng. `items-center`
 * là phần thứ hai của cùng một việc: không có nó thì chữ dính lên mép trên của
 * ô 44px và ô cao ra chỉ để làm khoảng trống bên dưới.
 */
const NAV_LINK =
  "flex min-h-11 items-center rounded px-2 text-text-main no-underline hover:bg-primary-light hover:text-primary";

export function Header() {
  const t = useTranslations("nav");
  const tCommon = useTranslations("common");
  const { data: me } = useMe();
  const hasAccount = Boolean(me?.appUserId);
  const navItems = NAV_ITEMS.filter((item) => !("membersOnly" in item) || hasAccount);

  return (
    // `!leading-normal` — nhỏ mà quyết định, và nó là số ĐO ĐƯỢC.
    // Ant Design đặt `line-height` của <Layout.Header> bằng CHIỀU CAO thanh, mà chiều
    // cao ấy nó suy ra từ `controlHeight`. Bộ chủ đề của sản phẩm nâng `controlHeight`
    // lên 44px cho đạt sàn vùng chạm, nên header thừa hưởng `line-height: 88px` —
    // và MỌI hộp chữ trong thanh đầu trang cao 88px thay vì 44px, kể cả những liên
    // kết đã khai `min-h-11`.
    // Hậu quả không dừng ở thẩm mỹ: thanh đầu trang phình lên 113px trên điện thoại,
    // canvas phả đồ ngắn đi đúng chừng ấy, React Flow cắt bớt nút và đường nối, và ca
    // kiểm "phả đồ trên điện thoại" đỏ vì chỉ còn MỘT đường nối trên canvas.
    <AntHeader className="!flex !h-auto !items-center !justify-between !border-b !border-border !bg-bg-card !px-4 !py-3 !leading-normal sm:!px-6">
      {/* Tên đầy đủ dài 20+ ký tự; ở 393px nó xuống ba dòng và đẩy thanh đầu trang lên 169px —
          gần một phần tư màn hình điện thoại chỉ để làm khung. Điện thoại dùng tên rút gọn, một
          dòng; từ `sm` trở lên mới hiện tên đầy đủ. */}
      <Link
        href="/"
        className="flex min-h-11 shrink-0 items-center font-serif font-bold text-primary no-underline"
      >
        <span className="whitespace-nowrap text-base sm:hidden">{tCommon("appNameShort")}</span>
        <span className="hidden text-lg sm:inline sm:text-xl">{tCommon("appName")}</span>
      </Link>

      {/* FLEX THUẦN, KHÔNG DÙNG <Space> CỦA ANT DESIGN — và đây là số đo, không phải sở thích.
          <Space> bọc mỗi con trong một `.ant-space-item` và tự quản lý khoảng cách. Với
          `wrap`, những mục đang `display: none` (bộ chuyển vai trò dev, cụm liên kết điện
          thoại không dùng, cụm chỉ-máy-tính, ô ngôn ngữ) VẪN chiếm một dòng flex riêng: đo trên
          Pixel 5 thấy bốn mục 0×0 nằm ở y=56, tức dòng thứ hai, và thanh đầu trang cao 113px
          thay vì 69px. 44px ấy bị lấy thẳng từ chiều cao phả đồ — canvas tụt xuống, React Flow
          cắt bớt nút và đường nối, và ca kiểm "phả đồ trên điện thoại" đỏ vì canvas chỉ còn một
          đường nối.

          Flex thuần thì `display: none` thật sự không chiếm gì: không dòng, không khoảng cách.
          Bỏ <Space> cũng gỡ luôn cái bẫy độ đặc hiệu đã ghi bên dưới — Ant Design tiêm
          `.ant-space{display:inline-flex}` vào <head> LÚC CHẠY, sau stylesheet của Tailwind, nên
          `hidden` đặt thẳng lên <Space> không bao giờ có tác dụng. */}
      <div className="flex flex-wrap items-center justify-end gap-2">
        <div className="hidden items-center gap-1 md:flex">
          {navItems.map((item) => (
            <Link key={item.key} href={item.href} className={NAV_LINK}>
              {t(item.key)}
            </Link>
          ))}
        </div>

        <MockRoleSwitcher />

        {/* Bốn điều khiển dưới đây CHỈ có trên máy tính (`hidden md:flex`).
            Trên điện thoại: chuông thông báo đã có mặt trên thanh tab dưới, còn
            Quản lý, Yêu cầu đính chính và Cài đặt chuyển sang <MoreMenu> — nơi
            chúng có chỗ cho nhãn chữ đầy đủ và một hàng cao 44px. Giữ chúng ở
            đây dưới dạng biểu tượng câm 18×20px là giữ nguyên cả hai lỗi cùng
            lúc.

            <ManagementQueueLink> là bản sửa: trước nó, `/quan-ly` (hàng chờ
            đơn tự nhận + màn phát mã) không có lối vào nào trên máy tính —
            <MoreMenu> là nơi DUY NHẤT trỏ tới, và nút mở ngăn kéo ấy tự khai
            `md:hidden`. Tự ẩn/hiện theo `canReview`, giống hệt
            <CorrectionQueueLink>, nên Khách và thành viên thường không thấy
            gì thêm. */}
        <div className="hidden items-center gap-2 md:flex">
          <ManagementQueueLink />
          <CorrectionQueueLink />
          <NotificationBell />
          <Link href="/settings" className={NAV_LINK} data-testid="header-settings">
            <SettingOutlined aria-hidden className="mr-1.5" />
            {t("settings")}
          </Link>
        </div>

        {/* Ô chuyển ngôn ngữ CHỈ có trên máy tính. Đo trên Pixel 5 (393px): với nó
            ở đây, cụm bên phải không nằm vừa một dòng và thanh đầu trang nở lên
            209px — gần một phần ba màn hình, và nó ăn thẳng vào chiều cao của phả
            đồ (canvas tụt xuống dưới thanh tab đáy, nút bung nhánh không chạm nổi).
            Trên điện thoại nó nằm trong <MoreMenu>, nơi có chỗ cho một nhãn chữ
            thật thay vì hai chữ "VI / EN" không nói gì với người chưa quen. */}
        <div className="hidden md:flex">
          {/*
            <Suspense> là BẮT BUỘC, không phải trang trí — và lỗi nó chặn chỉ hiện ra ở bản
            dựng production, `next dev` im lặng cho qua.

            `LanguageSwitcher` gọi `useSearchParams()` để giữ lại chuỗi truy vấn khi đổi ngôn
            ngữ. Thanh đầu trang có mặt trên MỌI trang, nên nếu không có ranh giới này thì mọi
            trang đều bị kéo sang kết xuất phía client, và `next build` dừng hẳn ở
            "useSearchParams() should be wrapped in a suspense boundary" — đo được ở
            /vi/notifications, nhưng nguyên nhân không nằm ở trang ấy chút nào.

            Bọc đúng ô này chứ đừng bọc cả trang: phần còn lại của thanh đầu trang vẫn kết xuất
            sẵn ở máy chủ như cũ.

            `fallback` giữ đúng 44×28px của Segmented để thanh đầu trang không giật một nhịp
            khi ô hiện ra — dịch chuyển bố cục ở thanh trên cùng là thứ người dùng cảm nhận
            được rõ nhất.
          */}
          <Suspense fallback={<div aria-hidden className="h-7 w-[72px]" />}>
            <LanguageSwitcher />
          </Suspense>
        </div>

        {/* F8: luồng Keycloak thật. Ba trạng thái (đang kiểm tra / khách /
            đã đăng nhập) nằm gọn trong <AuthMenu>. */}
        <AuthMenu />

        <MoreMenu />
      </div>
    </AntHeader>
  );
}

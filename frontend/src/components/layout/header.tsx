"use client";

import { Layout, Space, Tooltip } from "antd";
import { SettingOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { LanguageSwitcher } from "./language-switcher";
import { NotificationBell } from "@/components/notifications/notification-bell";
import { MockRoleSwitcher } from "@/components/dev/mock-role-switcher";
import { AuthMenu } from "./auth-menu";

const { Header: AntHeader } = Layout;

/**
 * Desktop nav. Every entry is a real route now that F6 (search) and F7
 * (events, notifications) have landed — the placeholder <Tag>s are gone. On
 * phones these are hidden and <MobileNav> takes over.
 */
const NAV_ITEMS = [
  { key: "tree", href: "/tree" },
  { key: "kinship", href: "/kinship" },
  { key: "search", href: "/search" },
  { key: "events", href: "/events" },
] as const;

export function Header() {
  const t = useTranslations("nav");
  const tCommon = useTranslations("common");

  return (
    <AntHeader className="!flex !h-auto !items-center !justify-between !border-b !border-border !bg-bg-card !px-4 !py-3 sm:!px-6">
      {/* Tên đầy đủ dài 20+ ký tự; ở 393px nó xuống ba dòng và đẩy thanh đầu trang lên 169px —
          gần một phần tư màn hình điện thoại chỉ để làm khung. Điện thoại dùng tên rút gọn, một
          dòng; từ `sm` trở lên mới hiện tên đầy đủ. */}
      <Link href="/" className="shrink-0 font-serif font-bold text-primary no-underline">
        <span className="whitespace-nowrap text-base sm:hidden">{tCommon("appNameShort")}</span>
        <span className="hidden text-lg sm:inline sm:text-xl">{tCommon("appName")}</span>
      </Link>

      {/* `wrap` chứ không phải `wrap={false}`: nếu sau này có thêm một điều khiển nữa thì nó
          xuống dòng, chứ không đẩy cả trang tràn ngang. <AntHeader> đã là `!h-auto` nên chiều cao
          tự nở theo.

          Khoảng cách 8px chứ không phải "middle" (16px): ở 393px, cụm bên phải chỉ còn 226px, và
          với 16px thì nút đăng nhập bị đẩy xuống dòng hai, khiến thanh đầu trang cao 169px —
          gần một phần tư màn hình điện thoại. Lưu ý <Space> vẫn tính khoảng cách cho cả những
          mục đang `display:none` (bộ chuyển vai trò dev, cụm liên kết điều hướng), nên mỗi pixel
          khoảng cách bị nhân lên năm lần. */}
      <Space size={8} align="center" wrap>
        {/* Bọc trong <div> thay vì đặt "hidden md:flex" thẳng lên <Space>: Ant Design tiêm
            `.ant-space{display:inline-flex}` vào <head> lúc chạy, tức là SAU stylesheet của
            Tailwind. Hai bên cùng độ đặc hiệu nên antd thắng, `hidden` không bao giờ có tác dụng,
            và trên Pixel 5 (393px) bốn liên kết điều hướng vẫn nằm nguyên khiến MỌI trang tràn
            ngang 193px. */}
        <div className="hidden md:flex">
        <Space size={12}>
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.key}
              href={item.href}
              className="text-text-main no-underline hover:text-primary"
            >
              {t(item.key)}
            </Link>
          ))}
        </Space>
        </div>

        <MockRoleSwitcher />
        <NotificationBell />
        <LanguageSwitcher />

        <Tooltip title={t("settings")}>
          <Link href="/settings" aria-label={t("settings")} className="text-text-main">
            <SettingOutlined className="text-lg hover:text-primary" />
          </Link>
        </Tooltip>

        {/* F8: luồng Keycloak thật. Ba trạng thái (đang kiểm tra / khách /
            đã đăng nhập) nằm gọn trong <AuthMenu>. */}
        <AuthMenu />
      </Space>
    </AntHeader>
  );
}

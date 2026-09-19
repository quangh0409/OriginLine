"use client";

import { useState } from "react";
import { Badge, Button, Drawer } from "antd";
import {
  ApartmentOutlined,
  ContactsOutlined,
  FileSyncOutlined,
  MenuOutlined,
  SettingOutlined,
} from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { LanguageSwitcher } from "./language-switcher";
import { usePendingChangeRequestCount } from "@/hooks/use-change-requests";
import { useMe } from "@/hooks/use-me";

/**
 * Ngăn kéo "Thêm" — CHỈ có trên điện thoại.
 *
 * <h2>Vì sao nó tồn tại</h2>
 * Thanh đầu trang có bốn điều khiển chỉ-biểu-tượng (Cài đặt, Yêu cầu đính chính,
 * chuông thông báo, tài khoản). Hai lỗi chồng lên nhau ở đó:
 * <ul>
 *   <li>vùng chạm 18×20px — dưới sàn 44px của 00 §2.2;</li>
 *   <li>biểu tượng không nhãn, tức vi phạm nguyên tắc 3 của định hướng ("biểu
 *       tượng luôn đi kèm chữ"). Tooltip KHÔNG cứu được: nó không tồn tại trên
 *       màn hình cảm ứng, và <code>aria-label</code> chỉ phục vụ trình đọc màn
 *       hình chứ không phục vụ cụ ông 70 tuổi đang nhìn vào cái bánh răng.</li>
 * </ul>
 *
 * Sửa vùng chạm mà giữ biểu tượng câm là sửa nửa lỗi. Nhưng thêm nhãn chữ vào
 * thanh đầu trang thì ở 393px nó tràn — đã có tiền lệ đo được: thanh này từng cao
 * 169px và mọi trang trượt ngang 193px. Cái giá phải trả ở đâu đó, và design/03
 * §2.1 chọn đúng chỗ: trên điện thoại, những điều khiển này RỜI thanh đầu trang.
 *
 * Ở đây chúng rời sang một ngăn kéo chứ không sang một trang mới — cố ý. Một
 * trang /muc-luc là MỘT MÀN HÌNH MỚI, phải nằm trong danh sách màn đã chốt và
 * phải được Hội đồng duyệt; một ngăn kéo là cách trình bày của thanh đầu trang
 * đang có, quyết định được ở tầng kỹ thuật. Nếu sau này Hội đồng duyệt /muc-luc
 * thì nội dung ngăn kéo này chuyển thẳng sang đó.
 *
 * <h2>Ô chuyển ngôn ngữ cũng ở đây</h2>
 * Trên điện thoại nó rời thanh đầu trang vì hai lý do, và lý do thứ hai mới là lý
 * do quyết định: (1) "VI / EN" là hai chữ viết tắt, không phải nhãn — nguyên tắc 3
 * của định hướng 00; (2) đo được trên Pixel 5, để nó lại trên thanh đầu trang thì
 * cụm bên phải không nằm vừa một dòng, thanh đầu trang nở lên 209px và phả đồ bị
 * đẩy xuống dưới thanh tab đáy tới mức nút bung nhánh không chạm được nữa. Chiều
 * cao thanh đầu trang không phải chuyện thẩm mỹ — nó là chiều cao của phả đồ.
 *
 * <h2>Vì sao chỉ ba mục</h2>
 * Cây phả đồ · Tìm kiếm · Sự kiện · Thông báo đã nằm trên thanh tab dưới
 * (&lt;MobileNav&gt;), nên đưa vào đây nữa là hai lối vào cho cùng một chỗ. Chỉ ba
 * mục KHÔNG có lối vào nào khác trên điện thoại mới xuất hiện ở đây.
 */
export function MoreMenu() {
  const t = useTranslations("nav");
  const tCorrection = useTranslations("correction");
  const [open, setOpen] = useState(false);
  const { data: me } = useMe();

  const hasAccount = Boolean(me?.appUserId);
  const canReview =
    me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";
  const pendingCount = usePendingChangeRequestCount({ enabled: hasAccount && canReview });
  const badgeCount = canReview ? pendingCount : 0;

  const rows = [
    { key: "kinship", href: "/kinship", icon: <ApartmentOutlined />, label: t("kinship"), count: 0 },
    // Danh bạ vào ĐÂY chứ không vào thanh tab dưới: thanh ấy đã kín 5 ô, và ô thứ sáu bóp
    // mỗi ô xuống dưới ngưỡng chạm 44px — đổi một lối tắt lấy năm lối tắt khó bấm.
    {
      key: "directory",
      href: "/danh-ba",
      icon: <ContactsOutlined />,
      label: t("directory"),
      count: 0,
    },
    ...(hasAccount
      ? [
          {
            key: "corrections",
            href: "/correction-requests",
            icon: <FileSyncOutlined />,
            label: tCorrection("nav.label"),
            count: badgeCount,
          },
        ]
      : []),
    { key: "settings", href: "/settings", icon: <SettingOutlined />, label: t("settings"), count: 0 },
  ];

  return (
    <>
      {/* Nút mở: biểu tượng KÈM CHỮ, đúng nguyên tắc 3. `min-h-11` là thứ bảo đảm
          vùng chạm — không phải `size`, vì `size` chỉ đổi cỡ chữ và padding.

          `md:hidden` đặt trên <div> BỌC NGOÀI chứ không trên <Button>: <Badge> của
          Ant Design dựng một <span> bọc quanh con của nó, và cái <span> ấy ở lại
          trong bố cục kể cả khi con đã `display: none` — một ô rỗng vẫn chiếm chỗ
          và vẫn kéo dòng flex của thanh đầu trang cao lên. */}
      {/* `flex items-center` chứ không để <div> ở dạng khối thường: <Badge> của Ant
          Design là `inline-block`, và một hộp khối chứa nội dung inline sẽ cao theo
          HỘP DÒNG chứ không theo con của nó — đo trên Pixel 5 được 88px cho một cái
          nút 44px. 44px thừa ấy đội thanh đầu trang lên, và chiều cao thanh đầu trang
          là chiều cao bị lấy mất của phả đồ: canvas ngắn lại thì React Flow cắt bớt
          nút và đường nối, tới mức ca kiểm "phả đồ trên điện thoại" chỉ còn thấy MỘT
          đường nối. */}
      <div className="flex items-center md:hidden">
        <Badge count={badgeCount} size="small" offset={[-4, 4]}>
          <Button
            type="text"
            icon={<MenuOutlined />}
            onClick={() => setOpen(true)}
            data-testid="more-menu-trigger"
            className="!min-h-11"
            aria-expanded={open}
          >
            {t("more")}
          </Button>
        </Badge>
      </div>

      <Drawer
        title={t("more")}
        placement="right"
        open={open}
        onClose={() => setOpen(false)}
        width="min(20rem, 85vw)"
        rootClassName="md:!hidden"
      >
        <ul className="m-0 flex list-none flex-col gap-1 p-0">
          {rows.map((row) => (
            <li key={row.key}>
              <Link
                href={row.href}
                onClick={() => setOpen(false)}
                className="flex min-h-11 items-center gap-3 rounded px-2 text-than text-text-main no-underline hover:bg-primary-light hover:text-primary"
              >
                <span aria-hidden className="text-dan">
                  {row.icon}
                </span>
                <span className="min-w-0 flex-1">{row.label}</span>
                {row.count > 0 && (
                  <Badge count={row.count} size="small" data-testid="more-menu-count" />
                )}
              </Link>
            </li>
          ))}
        </ul>

        <div className="mt-4 border-t border-border pt-4">
          <p className="mb-2 mt-0 text-than font-medium text-text-main">{t("language")}</p>
          <LanguageSwitcher />
        </div>
      </Drawer>
    </>
  );
}

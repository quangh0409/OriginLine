"use client";

import { Button, Dropdown, Skeleton, Tag, Tooltip, Typography } from "antd";
import { LoginOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { useAuth } from "@/lib/auth/auth-context";
import { AUTH_ENABLED } from "@/lib/auth/keycloak";

/**
 * Cụm đăng nhập / đăng xuất trên thanh đầu trang (F8).
 *
 * Ba trạng thái, không có trạng thái thứ tư:
 *   loading  - `check-sso` chưa trả lời. Hiện khung xám, KHÔNG hiện "Đăng
 *              nhập": nháy nút đăng nhập rồi đổi thành tên người dùng ngay
 *              sau đó là một cú giật mắt ở mọi lần tải trang.
 *   guest    - nút "Đăng nhập". Khách xem được phần công khai, đây chỉ là lối
 *              vào chứ không phải rào chắn (BA v2 §10).
 *   đã đăng nhập - tên hiển thị + vai, bấm ra menu "Đăng xuất".
 *
 * Ở chế độ MSW (`npm run dev:mock`) không có Keycloak nào để gọi, nên nút bị
 * vô hiệu hoá và chỉ dẫn sang bộ chuyển vai dev bên cạnh.
 */
export function AuthMenu() {
  const t = useTranslations("auth");
  const { status, isAuthenticated, displayName, username, role, login, logout } = useAuth();

  if (!AUTH_ENABLED) {
    return (
      <Tooltip title={t("mockModeHint")}>
        {/* <span> bọc ngoài: Ant Design không gắn được tooltip lên nút đang
            disabled vì nút ấy không phát sinh sự kiện chuột.

            `inline-flex` chứ không để mặc định: <span> là phần tử INLINE, nên hộp
            của nó cao theo ngữ cảnh dòng chứ không cao theo cái nút bên trong — đo
            trên Pixel 5 được 88px cho một cái nút 44px. 44px thừa ra ấy đội thanh
            đầu trang lên 113px, và chiều cao thanh đầu trang chính là chiều cao bị
            lấy mất của phả đồ bên dưới. */}
        <span className="inline-flex">
          {/* `aria-label`: dưới 640px cái <span> mang nhãn bị `hidden`, tức
              `display: none`, tức KHÔNG có trong cây trợ năng. Nút khi ấy là một
              biểu tượng trần, và trình đọc màn hình đọc ra "button" — không tên.
              Xem chú thích dài ở nút Đăng nhập bên dưới. */}
          <Button type="text" icon={<LoginOutlined />} disabled aria-label={t("login")}>
            <span className="hidden sm:inline">{t("login")}</span>
          </Button>
        </span>
      </Tooltip>
    );
  }

  if (status === "loading") {
    return (
      <Skeleton.Button active size="small" style={{ width: 88 }} aria-label={t("checking")} />
    );
  }

  if (!isAuthenticated) {
    // `aria-label` là BẮT BUỘC ở đây, không phải trang trí.
    //
    // Dưới 640px nhãn bị `hidden` — `display: none` gỡ hẳn phần tử khỏi cây trợ
    // năng, nên nút còn lại đúng một biểu tượng và trình đọc màn hình đọc ra
    // "button", không tên. Đó là lỗi nặng hơn cái nó đang đánh đổi.
    //
    // VÌ SAO KHÔNG BỎ LUÔN `hidden sm:inline` cho nhãn hiện ra:
    // design/06 §11 mục 3 muốn thế, và nguyên tắc 3 của định hướng 00 ("biểu
    // tượng phải có nhãn") đứng về phía ấy. Nhưng cái giá đã được ĐO: cụm bên
    // phải thanh đầu trang không nằm vừa một dòng trên Pixel 5, thanh đầu trang
    // nở lên 209px, và chiều cao thanh đầu trang CHÍNH LÀ chiều cao bị lấy mất
    // của phả đồ bên dưới — tới mức nút bung nhánh không chạm được nữa. Đó là
    // lý do bộ chuyển ngôn ngữ đã phải rời khỏi đây.
    //
    // Nên: vá phần CHỨNG MINH ĐƯỢC là hỏng (không có tên cho trình đọc màn
    // hình), và để phần cần ĐO lại cho một lượt đo thật. Đổi nhãn thành hiện
    // mà không đo lại chiều cao là đánh đổi một lỗi trợ năng lấy một lỗi trợ
    // năng khác, ở đúng màn hình quan trọng nhất của sản phẩm.
    return (
      <Button
        type="text"
        icon={<LoginOutlined />}
        onClick={login}
        aria-label={t("login")}
        data-testid="login-button"
      >
        <span className="hidden sm:inline">{t("login")}</span>
      </Button>
    );
  }

  const label = displayName || username;

  return (
    <Dropdown
      trigger={["click"]}
      placement="bottomRight"
      menu={{
        items: [
          {
            key: "identity",
            disabled: true,
            label: (
              <div className="py-1">
                <Typography.Text strong className="block">
                  {label}
                </Typography.Text>
                {role && (
                  <Tag color="warning" className="mt-1">
                    {t(`role.${role}`)}
                  </Tag>
                )}
              </div>
            ),
          },
          { type: "divider" },
          {
            key: "logout",
            icon: <LogoutOutlined />,
            label: t("logout"),
            onClick: logout,
          },
        ],
      }}
    >
      {/* Cùng lý do với nút Đăng nhập: dưới 640px `label` bị `hidden` nên nút
          không còn tên nào cho trình đọc màn hình. Nhãn trợ năng mang luôn tên
          người dùng, vì "tài khoản" trần thì không phân biệt được với nút nào
          khác khi người ta duyệt qua danh sách nút. */}
      <Button
        type="text"
        icon={<UserOutlined />}
        aria-label={label}
        data-testid="account-button"
      >
        {/* Tên đầy đủ tiếng Việt dễ dài quá khổ; cắt ở 14rem chứ không để nó
            đẩy thanh đầu trang xuống dòng thứ hai trên điện thoại. */}
        <span
          className="hidden max-w-[14rem] truncate align-middle sm:inline"
          data-testid="account-name"
        >
          {label}
        </span>
      </Button>
    </Dropdown>
  );
}

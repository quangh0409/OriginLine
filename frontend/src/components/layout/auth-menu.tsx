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
          <Button type="text" icon={<LoginOutlined />} disabled>
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
    return (
      <Button type="text" icon={<LoginOutlined />} onClick={login} data-testid="login-button">
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
      <Button type="text" icon={<UserOutlined />} data-testid="account-button">
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

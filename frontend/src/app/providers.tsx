"use client";

import { useState, type ReactNode } from "react";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import { ConfigProvider, App as AntdApp } from "antd";
import viVN from "antd/locale/vi_VN";
import enUS from "antd/locale/en_US";
import { QueryClientProvider } from "@tanstack/react-query";
import { createQueryClient } from "@/lib/query/query-client";
import { setApiLocale } from "@/lib/api/http";
import { antdTheme } from "@/styles/antd-theme";
import { MswProvider } from "@/mocks/msw-provider";
import { AuthProvider } from "@/lib/auth/auth-context";
import type { AppLocale } from "@/i18n/routing";

/**
 * Client-side provider stack, in mount order (outermost first):
 *  AntdRegistry   - SSR style extraction for Ant Design (must wrap ConfigProvider)
 *  ConfigProvider - single design-token source shared with Tailwind (src/styles)
 *  QueryClientProvider - React Query for all server state
 *  MswProvider    - starts the mock API worker before children render (dev only)
 *  AuthProvider   - phiên Keycloak thật (F8)
 *
 * <AuthProvider> nằm TRONG QueryClientProvider vì nó phải xoá cache khi phiên
 * đổi (mỗi phản hồi đã cache đều được định hình bởi vai cũ), và nằm TRONG
 * MswProvider để ở chế độ MSW nó dựng sau khi worker đã sẵn sàng. Nó KHÔNG
 * chặn việc dựng con: khách chưa đăng nhập phải mở được cổng thông tin ngay,
 * còn lớp API tự chờ phiên ổn định (xem lib/auth/token-bridge.ts).
 */
export function Providers({
  children,
  locale,
}: {
  children: ReactNode;
  locale: AppLocale;
}) {
  const [queryClient] = useState(() => createQueryClient());

  // Ghi ngôn ngữ hiện tại cho lớp API trước khi bất kỳ con nào kịp gọi mạng,
  // để mọi yêu cầu mang `Accept-Language` đúng ngay từ lần fetch đầu tiên.
  // Đặt trong thân component chứ không trong useEffect: effect chạy SAU lượt
  // render đầu, nghĩa là các truy vấn khởi động cùng trang sẽ lỡ mất header.
  // `setApiLocale` tự bỏ qua khi chạy trên server (xem lib/api/http.ts).
  setApiLocale(locale);

  return (
    <AntdRegistry>
      <ConfigProvider theme={antdTheme} locale={locale === "vi" ? viVN : enUS}>
        <AntdApp>
          <QueryClientProvider client={queryClient}>
            <MswProvider>
              <AuthProvider>{children}</AuthProvider>
            </MswProvider>
          </QueryClientProvider>
        </AntdApp>
      </ConfigProvider>
    </AntdRegistry>
  );
}

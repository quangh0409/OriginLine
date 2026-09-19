"use client";

import type { ReactNode } from "react";
import { Layout } from "antd";
import { Header } from "./header";
import { MobileNav } from "./mobile-nav";
import { PushPermissionPrompt } from "@/components/notifications/push-permission-prompt";
import { AccountStateGate } from "@/components/auth/account-state-gate";
import { MAIN_CONTENT_ID, SkipLink } from "./skip-link";

/**
 * IMPORTANT — must stay a Client Component ("use client" above), not a
 * Server Component. Ant Design attaches compound sub-components (here:
 * `Layout.Content`, `Layout.Footer`) as runtime static properties on the
 * default export, inside a module marked "use client" by antd itself. When
 * a *Server* Component imports such a module, Next.js substitutes the
 * import with a client-reference placeholder that supports being rendered
 * as a JSX tag (`<Layout>` works) but does NOT proxy arbitrary property
 * access (`Layout.Content`/`Layout.Footer` silently become `undefined`,
 * surfacing as "Element type is invalid ... got: undefined" at render time).
 * The same rule applies to any other antd compound API — `Typography.Text`,
 * `List.Item`, `Form.Item`, `Menu.Item`, etc. — never destructure/access
 * those from a plain Server Component; keep the access inside a "use
 * client" file.
 *
 * Top-level page chrome. Deliberately thin — most of the interesting logic
 * lives in <Header>. Sprint 2+ pages (tree canvas, kinship lookup, ...) wrap
 * their content in this same shell.
 *
 * <SkipLink> + <main> (C-4.5). Trước đợt sửa này `grep -rn '<main' src/` trả về
 * KHÔNG kết quả nào: lỗi không phải "thiếu liên kết bỏ qua" mà sâu hơn một tầng —
 * KHÔNG CÓ MỐC NÀO ĐỂ BỎ QUA TỚI. Hai thứ phải đi cùng nhau, và <main> phải mang
 * `tabIndex={-1}` thì tiêu điểm bàn phím mới thật sự nhảy sang nó.
 *
 * Two F7 pieces are mounted app-wide from here rather than per page:
 *  - <MobileNav>, because on a phone the header's links are hidden and the
 *    notification centre must not be buried in a popover.
 *  - <PushPermissionPrompt>, which decides for itself whether it is due; it
 *    renders null until the user has actually read a profile or a giỗ, so
 *    mounting it everywhere does not mean asking everywhere.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <Layout className="min-h-screen !bg-bg-page">
      <SkipLink />
      <Header />
      {/* `tabIndex={-1}` KHÔNG đưa <main> vào vòng Tab (giá trị âm loại nó ra); nó
          làm cho phần tử NHẬN ĐƯỢC tiêu điểm khi được gọi tên. Thiếu nó, liên kết
          bỏ qua chỉ cuộn trang còn tiêu điểm vẫn nằm ở thanh đầu trang — trông như
          hoạt động và không hoạt động. */}
      <Layout.Content id={MAIN_CONTENT_ID} tabIndex={-1} className="outline-none">
        {/* <AccountStateGate> dựng ở đây chứ không ở từng trang, vì lỗi nó bắt
            KHÔNG đến từ một màn hình nào cả: tài khoản chưa nối với nhân khẩu
            làm lời gọi API tiếp theo trả 404 trên bất kỳ trang nào người dùng
            vừa mở, và bộ xử lý lỗi chung vẽ "không tìm thấy trang". Nó trả
            `children` nguyên vẹn khi mọi thứ bình thường. Xem
            src/components/auth/account-state-gate.tsx. */}
        <AccountStateGate>{children}</AccountStateGate>
      </Layout.Content>
      <Layout.Footer className="text-center text-than text-text-muted !bg-transparent">
        © {new Date().getFullYear()} Cổng Thông Tin Gia Phả Dòng Họ
      </Layout.Footer>
      {/* Chừa chỗ cho thanh tab cố định ở đáy, để chân trang không bị nó nuốt.
          5rem chứ không phải 3,5rem: nhãn tab lên 16px (sàn 00 §2.2) nên nhãn dài
          nhất xuống hai dòng và thanh cao thêm một nấc. Con số này phải đi đôi với
          `min-h-14` trong <MobileNav> và với `calc(80vh-5rem)` của canvas phả đồ. */}
      <div className="h-20 md:hidden" aria-hidden />
      <MobileNav />
      <PushPermissionPrompt />
    </Layout>
  );
}

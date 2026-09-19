"use client";

import { MOCKING_ENABLED, getDevRole } from "@/lib/api/dev-role";
import { useAuth } from "@/lib/auth/auth-context";
import type { TreeAudience } from "@/lib/api/tree";

/**
 * Phả đồ này đang được vẽ cho ai — quyết định **bản nào** của endpoint được gọi.
 *
 * <h2>Vì sao không đoán bằng cách cứ gọi rồi bắt 401</h2>
 * Gọi `/api/v1/tree` trước rồi rơi về `/api/v1/public/tree` khi nhận 401 sẽ tốn
 * một vòng mạng thừa cho **mọi** khách, làm bẩn nhật ký máy chủ bằng những 401
 * không phải sự cố, và — tệ nhất — để lại một khoảng thời gian màn hình không
 * biết mình đang ở trạng thái nào. Phiên là thứ trình duyệt tự biết, nên hỏi
 * thẳng nó.
 *
 * <h2>`null` nghĩa là CHƯA NGÃ NGŨ, không phải "khách"</h2>
 * `check-sso` của Keycloak mất một nhịp. Nếu coi khoảng ấy là khách thì một
 * thành viên vừa tải trang sẽ bắn một lượt gọi công khai (cây thưa hơn thật),
 * rồi bắn tiếp lượt gọi thành viên — hai lần vẽ, hai lần nhảy khung nhìn, và
 * một bản cây thiếu người hiện ra chớp nhoáng. Người gọi phải **hoãn** truy vấn
 * cho tới khi có câu trả lời.
 *
 * <h2>Chế độ MSW</h2>
 * `MOCKING_ENABLED` và `AUTH_ENABLED` loại trừ nhau (xem `dev-role.ts`), nên
 * dưới MSW không có phiên Keycloak nào để hỏi và `useAuth()` luôn trả "khách".
 * Ở đó vai đến từ bộ chuyển vai dev — cùng nguồn với header `x-mock-role` mà
 * lớp API đang gửi, nên hai thứ không bao giờ lệch nhau.
 */
export function useTreeAudience(): TreeAudience | null {
  const { status } = useAuth();

  if (MOCKING_ENABLED) {
    return getDevRole() === "guest" ? "public" : "member";
  }

  if (status === "loading") return null;
  return status === "authenticated" ? "member" : "public";
}

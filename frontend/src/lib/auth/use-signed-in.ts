"use client";

import { MOCKING_ENABLED, getDevRole } from "@/lib/api/dev-role";
import { useTreeAudience } from "@/hooks/use-tree-audience";
import { getAuthToken } from "./token-bridge";

/**
 * Người đang xem có phiên đăng nhập không — hỏi TRÌNH DUYỆT, không hỏi máy chủ.
 *
 * <h2>Vì sao phải có, và vì sao không "cứ gọi rồi bắt 401"</h2>
 * Đo trên hệ thật ngày 24/09: một vị khách mở trang chủ bắn ra năm lượt gọi cần
 * token (`/me`, `/notifications`, `/posts/feed`, `/honours`, `/events`), cả năm
 * nhận `401`, và cả năm để lại một dòng đỏ "Failed to load resource" trong
 * console. Tệ hơn tiếng ồn: trang chủ đọc `401` thành "danh sách rỗng" rồi in
 * "Chưa có bài viết nào được đăng." trong khi họ đã đăng hai bài — tức một câu
 * sai về dữ liệu, nói với đúng người không có cách nào kiểm lại.
 *
 * Phiên là thứ trình duyệt tự biết (`check-sso` của Keycloak), nên câu hỏi
 * "khách hay thành viên" có câu trả lời TRƯỚC khi gọi mạng. Cùng lập luận với
 * `useTreeAudience()`, và dùng lại chính nó để hai chỗ không thể lệch nhau.
 *
 * `null` = CHƯA NGÃ NGŨ (`check-sso` đang chạy). Người gọi hoãn lại, không
 * được coi là khách — coi là khách thì một thành viên vừa tải trang sẽ thấy
 * chớp qua một khối "cần đăng nhập".
 */
export function useSignedIn(): boolean | null {
  const audience = useTreeAudience();
  return audience === null ? null : audience === "member";
}

/**
 * Bản bất đồng bộ của {@link useSignedIn}, dùng bên trong `queryFn`.
 *
 * `getAuthToken()` tự CHỜ `check-sso` xong rồi mới trả lời (xem
 * `token-bridge.ts`), nên ở đây không có khoảng "chưa ngã ngũ". Dưới MSW không
 * có Keycloak nào: vai đến từ bộ chuyển vai dev — cùng nguồn với header
 * `x-mock-role` mà lớp API gửi đi.
 */
export async function probeSignedIn(): Promise<boolean> {
  if (MOCKING_ENABLED) return getDevRole() !== "guest";
  return (await getAuthToken()) !== null;
}

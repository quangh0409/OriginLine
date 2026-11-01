"use client";

import { useQuery } from "@tanstack/react-query";
import { meApi, type MeView } from "@/lib/api/change-requests";
import { probeSignedIn } from "@/lib/auth/use-signed-in";

/**
 * Câu trả lời của hook cho người CHƯA đăng nhập — dựng ở client, không tới từ
 * máy chủ (máy chủ trả `401` cho khách). Đúng hình dạng mà các màn đã quen đọc:
 * `appUserId: null` = "chưa có tài khoản".
 */
export const GUEST_ME: MeView = Object.freeze({
  appUserId: null,
  personId: null,
  role: "GUEST",
  clanWide: false,
  managedBranches: [],
  homeBranch: null,
  linkedToTree: false,
}) as MeView;

/** Khoá React Query cho hồ sơ phiên làm việc. */
export const meQueryKey = ["me"] as const;

/**
 * Hồ sơ phiên làm việc: vai **và** phạm vi chi/ngành của người đang đăng nhập.
 *
 * <h2>Vì sao giao diện cần phạm vi chứ không chỉ vai</h2>
 * Một Trưởng chi thấy cụm "Duyệt đính chính" cho chi mình và không thấy cho chi
 * khác. Nếu chỉ có vai thì giao diện sẽ hiện nút cho mọi hồ sơ rồi để backend
 * trả 403 — đúng về bảo mật nhưng tệ về trải nghiệm, và chuỗi lỗi lại rò rỉ
 * chính cấu trúc quyền mà nó định giấu.
 *
 * <h2>Đây KHÔNG phải phân quyền</h2>
 * Mọi thứ ở đây chỉ để vẽ giao diện. Người dùng sửa được phản hồi này trong
 * DevTools; sửa xong vẫn không ghi được gì, vì phép kiểm thật nằm ở
 * `ChangeRequestService` → `BranchScopeGuard` → so `ltree`.
 *
 * <h2>Khách KHÔNG gọi endpoint này — và câu cũ ở đây đã sai</h2>
 * Chú thích trước viết rằng `/me` "trả `200` cả với Khách, `appUserId: null`".
 * Không đúng với máy chủ thật: `/api/v1/me` rơi vào `anyRequest().authenticated()`
 * của `SecurityConfig`, nên khách nhận `401 UNAUTHENTICATED` (đo ngày 24/09,
 * `SecurityProblemSupport: 401 /api/v1/me`). Câu sai ấy sống được vì bộ giả lập
 * MSW trả `200` cho khách — mock "dễ hơn thật", và nay đã sửa theo thật.
 *
 * Vì vậy hook tự trả lời cho khách bằng {@link GUEST_ME} mà KHÔNG gọi mạng:
 * phiên là thứ trình duyệt đã biết (`probeSignedIn`). Giữ nguyên hình dạng
 * `appUserId: null` mà mọi nơi dùng hook vẫn đọc — đổi sang một truy vấn bị tắt
 * (`enabled: false`) thì `isPending` treo mãi ở `true`, và sáu màn đang vẽ
 * khung xương khi `isPending` sẽ quay vô tận trước mặt khách.
 *
 * Hook không `retry`: `401`/`403` ở đây là câu trả lời cuối của máy chủ.
 */
export function useMe() {
  return useQuery<MeView>({
    queryKey: meQueryKey,
    queryFn: async () => ((await probeSignedIn()) ? meApi.get() : GUEST_ME),
    // Phạm vi chi/ngành đổi rất hiếm (Hội đồng phân công lại), nhưng cũng
    // không được cache vĩnh viễn: đổi vai trong bộ giả lập phải thấy ngay.
    staleTime: 60_000,
    retry: false,
  });
}

/** Người gọi đã có tài khoản trong hệ thống chưa (khách thì chưa). */
export function useHasAccount(): boolean {
  const { data } = useMe();
  return Boolean(data?.appUserId);
}

/**
 * Người này có **khả năng** duyệt đính chính không — vai đúng *và* có ít nhất
 * một phạm vi.
 *
 * `managedBranches` rỗng với một Trưởng chi nghĩa là "chưa được giao chi nào",
 * và khi ấy hàng đợi luôn rỗng. Hiện một mục điều hướng dẫn tới màn hình chắc
 * chắn trống là một lời hứa suông, nên chỗ này trả `false`.
 */
export function useCanReviewCorrections(): boolean {
  const { data } = useMe();
  if (!data?.appUserId) return false;
  if (data.role === "ADMIN" || data.role === "COUNCIL") return true;
  return data.role === "BRANCH_HEAD" && data.managedBranches.length > 0;
}

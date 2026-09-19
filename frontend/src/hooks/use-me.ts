"use client";

import { useQuery } from "@tanstack/react-query";
import { meApi, type MeView } from "@/lib/api/change-requests";

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
 * Endpoint này cũng là chỗ backend khởi tạo dòng `app_user` lần đầu, nên nó trả
 * `200` cả với Khách — `appUserId: null` là câu trả lời hợp lệ, không phải lỗi.
 * Vì thế hook không `retry` và không coi trạng thái chưa có tài khoản là lỗi.
 */
export function useMe() {
  return useQuery<MeView>({
    queryKey: meQueryKey,
    queryFn: () => meApi.get(),
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

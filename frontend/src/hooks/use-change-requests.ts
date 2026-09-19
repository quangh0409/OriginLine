"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  changeRequestsApi,
  type ChangeRequestView,
  type ReviewChangeRequestBody,
  type SubmitChangeRequestBody,
} from "@/lib/api/change-requests";
import { ApiError } from "@/lib/api/http";
import { queryKeys } from "@/lib/query/keys";

/**
 * Server-state cho luồng **đính chính**.
 *
 * Khoá query khai báo tại chỗ chứ không nằm trong `lib/query/keys.ts`: nhóm
 * `change-requests` là một mảnh mới, và gom khoá ngay cạnh hook dùng nó giữ
 * cho việc vô hiệu hoá cache đọc được trong một màn hình. Khi nhóm này ổn định
 * thì chuyển sang factory chung.
 */
export const changeRequestKeys = {
  all: ["change-requests"] as const,
  mine: () => ["change-requests", "mine"] as const,
  pending: () => ["change-requests", "pending"] as const,
  pendingCount: () => ["change-requests", "pending", "count"] as const,
  byId: (id: string) => ["change-requests", "detail", id] as const,
};

/** Đề nghị của chính người đang đăng nhập. */
export function useMyChangeRequests() {
  return useQuery<ChangeRequestView[]>({
    queryKey: changeRequestKeys.mine(),
    queryFn: () => changeRequestsApi.mine({ size: 50 }),
    retry: false,
  });
}

/**
 * Hàng đợi chờ duyệt trong phạm vi được giao.
 *
 * Danh sách rỗng có **hai** nghĩa hoàn toàn khác nhau — "chưa được giao chi
 * nào" và "không còn gì chờ duyệt" — nên màn hình gọi hook này phải phân biệt
 * bằng `useMe().managedBranches`, tuyệt đối không bằng độ dài mảng.
 */
export function usePendingChangeRequests(options: { enabled?: boolean } = {}) {
  return useQuery<ChangeRequestView[]>({
    queryKey: changeRequestKeys.pending(),
    queryFn: () => changeRequestsApi.pending({ size: 50 }),
    enabled: options.enabled ?? true,
    retry: false,
  });
}

/**
 * Số yêu cầu chờ duyệt — cho badge.
 *
 * Backend trả `0` thay vì `403` cho người không có quyền, nên hook này an toàn
 * để gọi ở thanh đầu trang mà không cần biết trước vai của ai.
 */
export function usePendingChangeRequestCount(options: { enabled?: boolean } = {}) {
  const query = useQuery<{ count: number }>({
    queryKey: changeRequestKeys.pendingCount(),
    queryFn: () => changeRequestsApi.pendingCount(),
    enabled: options.enabled ?? true,
    retry: false,
    staleTime: 30_000,
  });
  return query.data?.count ?? 0;
}

/**
 * Gửi một đề nghị đính chính.
 *
 * Không kiểm phạm vi ở phía client, và cũng không nên: backend cố ý cho **mọi**
 * thành viên có tài khoản gửi đề nghị về bất kỳ ai — một người con gái lấy
 * chồng xa vẫn phải báo được rằng ngày mất của cụ ghi sai. Cửa kiểm chặt nằm ở
 * bước duyệt.
 */
export function useSubmitChangeRequest() {
  const queryClient = useQueryClient();
  return useMutation<ChangeRequestView, ApiError, SubmitChangeRequestBody>({
    mutationFn: (body) => changeRequestsApi.submit(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: changeRequestKeys.all });
    },
  });
}

export interface ReviewInput extends ReviewChangeRequestBody {
  id: string;
}

/**
 * Duyệt hoặc từ chối.
 *
 * Sau khi duyệt, hồ sơ nhân khẩu **có thể** đã đổi — nên vô hiệu hoá cache của
 * người liên quan và cả phóng chiếu cây. "Có thể" chứ không phải "chắc chắn":
 * backend hiện chưa có context nào nhận `ChangeRequestApprovedEvent`, nên việc
 * duyệt mới chỉ ghi nhận quyết định. Đọc lại từ máy chủ là cách duy nhất đúng
 * cho cả trước và sau khi mắt xích W2×W6 được nối.
 */
export function useReviewChangeRequest() {
  const queryClient = useQueryClient();
  return useMutation<ChangeRequestView, ApiError, ReviewInput>({
    mutationFn: ({ id, ...body }) => changeRequestsApi.review(id, body),
    onSuccess: (reviewed) => {
      void queryClient.invalidateQueries({ queryKey: changeRequestKeys.all });
      if (reviewed.personId) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.person(reviewed.personId) });
      }
      void queryClient.invalidateQueries({ queryKey: ["tree"] });
      void queryClient.invalidateQueries({ queryKey: ["tree-branch"] });
    },
  });
}

/** Người gửi tự rút lại. Không xoá — bản ghi chuyển sang `CANCELLED`. */
export function useCancelChangeRequest() {
  const queryClient = useQueryClient();
  return useMutation<ChangeRequestView, ApiError, string>({
    mutationFn: (id) => changeRequestsApi.cancel(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: changeRequestKeys.all });
    },
  });
}

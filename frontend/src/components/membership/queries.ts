"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/http";
import {
  claimReviewApi,
  clanInvitesApi,
  type ClaimReviewView,
  type ClanInviteListDto,
  type IssueClanInviteBody,
  type IssuedClanInvite,
  type ReviewClaimBody,
} from "@/lib/api/membership-admin";
import {
  invitationApi,
  type InvitationDto,
  type IssueInvitationBody,
  type IssuedInvitationDto,
} from "@/lib/api/invitation";
import { queryKeys } from "@/lib/query/keys";

/**
 * Server-state cho **phần quản trị tư cách thành viên**.
 *
 * Khoá query khai tại chỗ chứ không nằm trong `lib/query/keys.ts`, theo đúng
 * tiền lệ của `use-change-requests.ts`: nhóm này là một mảnh mới, và gom khoá
 * ngay cạnh hook dùng nó giữ cho việc vô hiệu hoá cache đọc được trong một màn
 * hình. Khi nhóm ổn định thì chuyển sang factory chung.
 *
 * Tiền tố `membership` **không** giẫm lên khoá `claim` của nửa người gửi: hai
 * bên đọc hai endpoint khác nhau và thấy hai bản khác nhau của cùng một lá đơn,
 * nên dùng chung một khoá cache sẽ làm bản nghèo ghi đè bản đầy.
 */
export const membershipKeys = {
  all: ["membership"] as const,
  claims: () => ["membership", "claims"] as const,
  pending: () => ["membership", "claims", "pending"] as const,
  pendingCount: () => ["membership", "claims", "pending", "count"] as const,
  clanInvites: () => ["membership", "clan-invites"] as const,
  invitations: () => ["membership", "invitations"] as const,
};

// ---------------------------------------------------------------------------
// Hàng chờ duyệt
// ---------------------------------------------------------------------------

/**
 * Đơn tự nhận chờ duyệt trong phạm vi được giao.
 *
 * `retry: false`: một `403` ở đây là câu trả lời dứt khoát về quyền, không phải
 * trục trặc tạm thời — thử lại ba lần chỉ làm màn hình treo lâu hơn trước khi
 * nói cùng một câu.
 */
export function usePendingClaims(options: { enabled?: boolean } = {}) {
  return useQuery<ClaimReviewView[]>({
    queryKey: membershipKeys.pending(),
    queryFn: () => claimReviewApi.pending({ size: 50 }),
    enabled: options.enabled ?? true,
    retry: false,
  });
}

/** Số đơn chờ duyệt — cho badge. Máy chủ trả `0` cho người không có quyền. */
export function usePendingClaimCount(options: { enabled?: boolean } = {}) {
  const query = useQuery<{ count: number }>({
    queryKey: membershipKeys.pendingCount(),
    queryFn: () => claimReviewApi.pendingCount(),
    enabled: options.enabled ?? true,
    retry: false,
    staleTime: 30_000,
  });
  return query.data?.count ?? 0;
}

export interface ReviewClaimInput extends ReviewClaimBody {
  id: string;
}

/**
 * Duyệt hoặc từ chối một lá đơn.
 *
 * Sau khi duyệt, **cây phả hệ có thể đã đổi thật** — khác hẳn luồng đính chính,
 * nơi việc duyệt mới chỉ ghi nhận quyết định. Một đơn `NEW_PERSON` được duyệt
 * ghi thêm một nhân khẩu và một cạnh quan hệ, nên cache của cây phải bỏ đi chứ
 * không được vá tại chỗ: chỉ máy chủ biết đời, chi và danh xưng của người mới.
 *
 * Duyệt một đơn cũng **đóng các đơn tranh chấp** cùng trỏ vào nhân khẩu ấy, nên
 * cả hàng đợi phải đọc lại — vá một phần tử sẽ để lại lá đơn kia nằm trên màn
 * hình với hai nút bấm không còn tác dụng.
 */
export function useReviewClaim() {
  const queryClient = useQueryClient();
  return useMutation<ClaimReviewView, ApiError, ReviewClaimInput>({
    mutationFn: ({ id, ...body }) => claimReviewApi.review(id, body),
    onSuccess: (reviewed) => {
      void queryClient.invalidateQueries({ queryKey: membershipKeys.claims() });
      // `PersonClaim` chỉ mang KHOÁ của nhân khẩu — không có khối `person` lồng,
      // và đó là chủ ý riêng tư của contract. Với đơn `NEW_PERSON` được duyệt
      // thì `createdPersonId` mới là nhân khẩu vừa sinh ra.
      const touched = reviewed.personId ?? reviewed.createdPersonId;
      if (touched) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.person(touched) });
      }
      void queryClient.invalidateQueries({ queryKey: ["tree"] });
      void queryClient.invalidateQueries({ queryKey: ["tree-branch"] });
    },
  });
}

// ---------------------------------------------------------------------------
// Mã mời dòng họ
// ---------------------------------------------------------------------------

export function useClanInvites(options: { enabled?: boolean } = {}) {
  return useQuery<ClanInviteListDto>({
    queryKey: membershipKeys.clanInvites(),
    queryFn: () => clanInvitesApi.list({ size: 50 }),
    enabled: options.enabled ?? true,
    retry: false,
  });
}

/**
 * Phát một mã dòng họ.
 *
 * Kết quả chứa mã thô và **không được đưa vào cache**: cache của React Query
 * sống qua các lần chuyển màn và bị DevTools đọc được, còn mã thì chỉ tồn tại
 * đúng một lần. Nó ở lại trong `mutation.data` của component phát mã và mất khi
 * component gọi `reset()`.
 */
export function useIssueClanInvite() {
  const queryClient = useQueryClient();
  return useMutation<IssuedClanInvite, ApiError, IssueClanInviteBody>({
    mutationFn: (body) => clanInvitesApi.issue(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: membershipKeys.clanInvites() });
    },
  });
}

export function useRevokeClanInvite() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, { id: string; reason?: string }>({
    mutationFn: ({ id, reason }) => clanInvitesApi.revoke(id, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: membershipKeys.clanInvites() });
    },
  });
}

// ---------------------------------------------------------------------------
// Lời mời cá nhân — lớp API đã có sẵn, đây chỉ là server-state quanh nó
// ---------------------------------------------------------------------------

/**
 * Lời mời cá nhân trong phạm vi người gọi.
 *
 * Gọi thẳng `invitationApi.list` — nhóm endpoint này backend đã dựng xong và đã
 * kiểm chạy thật, chỉ chưa có màn hình nào gọi tới. Không có lớp API thứ hai.
 */
export function useInvitations(options: { enabled?: boolean } = {}) {
  return useQuery<InvitationDto[]>({
    queryKey: membershipKeys.invitations(),
    queryFn: () => invitationApi.list({ size: 50 }),
    enabled: options.enabled ?? true,
    retry: false,
  });
}

export function useIssueInvitation() {
  const queryClient = useQueryClient();
  return useMutation<IssuedInvitationDto, ApiError, IssueInvitationBody>({
    mutationFn: (body) => invitationApi.issue(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: membershipKeys.invitations() });
    },
  });
}

export function useRevokeInvitation() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, { id: string; reason?: string }>({
    mutationFn: ({ id, reason }) => invitationApi.revoke(id, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: membershipKeys.invitations() });
    },
  });
}

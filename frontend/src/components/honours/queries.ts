"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/http";
import {
  honoursApi,
  type HonourDto,
  type HonourWriteBody,
  type ListHonoursParams,
  type ReviewHonourBody,
} from "@/lib/api/honours";

/** Khoá React Query của nhóm vinh danh — cùng quy ước với `src/components/posts/queries.ts`. */
export const honourKeys = {
  list: (params: ListHonoursParams) => ["honours-list", params] as const,
};

export function useHonoursList(params: ListHonoursParams) {
  return useQuery<{ items: HonourDto[] }>({
    queryKey: honourKeys.list(params),
    queryFn: () => honoursApi.list(params),
    staleTime: 0,
  });
}

function invalidateHonourLists(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["honours-list"] });
}

export function useCreateHonour() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: HonourWriteBody) => honoursApi.create(body),
    onSuccess: () => invalidateHonourLists(queryClient),
  });
}

export function useReviewHonour() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ReviewHonourBody }) => honoursApi.review(id, body),
    onSuccess: () => invalidateHonourLists(queryClient),
  });
}

export function useRemoveHonour() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => honoursApi.remove(id),
    onSuccess: () => invalidateHonourLists(queryClient),
  });
}

/**
 * Nhánh câu chữ theo `code` (contracts/README §3). `SELF_REVIEW_FORBIDDEN`
 * tách khỏi `FORBIDDEN` thường — cùng tiền lệ `ChangeRequest`, câu chữ phải
 * nói đúng lý do ("chính bạn không tự duyệt được"), không phải một "không có
 * quyền" chung chung khiến người dùng tưởng mình sai phạm vi chi/ngành.
 */
export function honourErrorKey(error: unknown): string {
  if (!(error instanceof ApiError)) return "unknown";
  switch (error.code) {
    case "SELF_REVIEW_FORBIDDEN":
      return "selfReview";
    case "FORBIDDEN":
    case "BRANCH_SCOPE_VIOLATION":
      return "forbidden";
    case "OPTIMISTIC_LOCK_CONFLICT":
      return "staleRecord";
    case "VALIDATION_FAILED":
      return "validation";
    case "NOT_FOUND":
      return "notFound";
    default:
      return "unknown";
  }
}

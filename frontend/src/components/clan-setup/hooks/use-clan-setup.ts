"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  clanProfileApi,
  type ClanProfileDto,
  type ClanProfileWriteBody,
  type ClanReadinessDto,
  type CouncilAccountCreatedDto,
  type CreateCouncilAccountBody,
} from "@/lib/api/clan-profile";
import { ApiError, type ApiResult } from "@/lib/api/http";
import { queryKeys } from "@/lib/query/keys";

/**
 * Server-state cho **màn "Khởi tạo dòng họ"** (`/quan-tri`) — quyết định
 * 26/09/2026 của chủ dự án.
 *
 * Khoá query gom tại đây, không ở `lib/query/keys.ts`, cùng tiền lệ
 * `use-data-import.ts`/`membership/queries.ts`: một mảnh mới, gom khoá cạnh
 * hook dùng nó để việc vô hiệu hoá đọc được trong một màn hình. Ngoại lệ:
 * `queryKeys.clanProfilePublic()` — khoá ấy được `useClanProfile()` (header,
 * trang chủ) dùng chung, nên nó ở `lib/query/keys.ts`, và `useSaveClanProfile`
 * vô hiệu hoá luôn khoá ấy để tên mới hiện ngay, không đợi 24 giờ `staleTime`.
 */
export const clanSetupKeys = {
  all: ["clan-setup"] as const,
  profile: () => ["clan-setup", "profile"] as const,
  readiness: () => ["clan-setup", "readiness"] as const,
};

/**
 * Hồ sơ dòng họ, bản QUẢN TRỊ. `404 CLAN_NOT_INITIALIZED` KHÔNG bị nuốt ở đây
 * — nó vẫn là một lỗi React Query bình thường (`query.isError`), và nơi gọi
 * (`ClanProfileStep`) đọc `query.error` để phân biệt "chưa khởi tạo" (hiện
 * form trống) khỏi một trục trặc thật (hiện `Alert` lỗi). Xem javadoc
 * `clanProfileApi.getAdmin`.
 */
export function useClanProfileAdmin(options: { enabled?: boolean } = {}) {
  return useQuery<ApiResult<ClanProfileDto>, ApiError>({
    queryKey: clanSetupKeys.profile(),
    queryFn: () => clanProfileApi.getAdmin(),
    enabled: options.enabled ?? true,
    retry: false,
  });
}

export function useClanReadiness(options: { enabled?: boolean } = {}) {
  return useQuery<ClanReadinessDto, ApiError>({
    queryKey: clanSetupKeys.readiness(),
    queryFn: () => clanProfileApi.readiness(),
    enabled: options.enabled ?? true,
    retry: false,
  });
}

export interface SaveClanProfileInput {
  body: ClanProfileWriteBody;
  /** Vắng khi TẠO MỚI; bắt buộc khi CẬP NHẬT (ETag vừa đọc từ `useClanProfileAdmin`). */
  ifMatch?: string;
}

/**
 * `PUT /admin/clan-profile`. Thành công thì vô hiệu hoá BA khoá: hồ sơ quản
 * trị của chính màn này (ETag mới cho lượt lưu kế tiếp), `readiness` (bước 1
 * vừa xong có thể đổi `steps[]`), và bản CÔNG KHAI mà header/trang chủ đang
 * đọc — thiếu khoá thứ ba thì tên dòng họ mới lưu xong sẽ không hiện ra cho
 * tới khi `staleTime` 24 giờ của `useClanProfile` hết hạn.
 */
export function useSaveClanProfile() {
  const queryClient = useQueryClient();
  return useMutation<ApiResult<ClanProfileDto>, ApiError, SaveClanProfileInput>({
    mutationFn: ({ body, ifMatch }) => clanProfileApi.save(body, ifMatch),
    onSuccess: (result) => {
      queryClient.setQueryData(clanSetupKeys.profile(), result);
      void queryClient.invalidateQueries({ queryKey: clanSetupKeys.readiness() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.clanProfilePublic() });
    },
  });
}

/**
 * `POST /admin/clan-profile/council-accounts`. Trả liên kết đặt mật khẩu MỘT
 * LẦN — cùng khuôn `useIssuePasswordReset`/`useIssueInvitation`: không đưa
 * `CouncilAccountCreatedDto` vào cache, giữ trong `mutation.data`, mất khi
 * component gọi `reset()`.
 */
export function useCreateCouncilAccount() {
  const queryClient = useQueryClient();
  return useMutation<CouncilAccountCreatedDto, ApiError, CreateCouncilAccountBody>({
    mutationFn: (body) => clanProfileApi.createCouncilAccount(body),
    onSuccess: () => {
      // `readiness.council.count` phải nhích lên ngay — bước 2 dựa vào con số
      // này để biết "đã có bao nhiêu tài khoản Hội đồng" mà không giữ một bộ
      // đếm cục bộ (F5 giữa chừng không được làm mất số đã tạo).
      void queryClient.invalidateQueries({ queryKey: clanSetupKeys.readiness() });
    },
  });
}

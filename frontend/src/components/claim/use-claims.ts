"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { personsApi } from "@/lib/api";
import {
  claimApi,
  type ClaimView,
  type MyClaimsDto,
  type SubmitClaimBody,
} from "@/lib/api/claim";

/**
 * Khoá React Query của nhóm `claims`.
 *
 * Khai ở đây chứ không ở `src/lib/query/keys.ts` là **cố ý**: tệp ấy dùng chung
 * cho cả sản phẩm, còn nhóm này là một mảnh mới và gom khoá ngay cạnh hook dùng
 * nó giữ cho việc vô hiệu hoá cache đọc được trong một màn hình. Khi nhóm ổn
 * định thì chuyển sang factory chung.
 */
export const claimQueryKeys = {
  mine: () => ["claim-mine"] as const,
  target: (personId: string) => ["claim-target", personId] as const,
};

/**
 * Đơn của chính người gọi — **nguồn duy nhất** giao diện đọc về trạng thái tài
 * khoản trước khi dựng biểu mẫu.
 *
 * Hai điều nó trả lời, và cả hai đều nói về <em>chính người đang gọi</em> nên
 * nói rõ được mà không lộ gì về ai khác: <b>đang có đơn mở chưa</b>, và
 * <b>đã bị từ chối mấy lần</b>.
 *
 * {@code staleTime: 0} vì cả hai đổi được giữa chừng — một Trưởng chi có thể
 * vừa duyệt hoặc vừa từ chối trong lúc người dùng còn đang mở màn hình.
 */
export function useMyClaims() {
  return useQuery<MyClaimsDto>({
    queryKey: claimQueryKeys.mine(),
    queryFn: () => claimApi.mine(),
    retry: false,
    staleTime: 0,
  });
}

/** Ô trên phả đồ, ở mức vừa đủ để người dùng nhận ra là mình. */
export interface ClaimTarget {
  id: string;
  displayName: string;
  generation: number | null;
  branchName: string | null;
  /** `false` ⇒ chặn cứng ngay lúc chọn (checklist §1.4). */
  isAlive: boolean;
}

/**
 * Tra ô đã chọn qua {@code GET /persons/&#123;id&#125;} — **không có endpoint
 * riêng nào cho việc này, và đó là chủ ý**.
 *
 * <h2>Vì sao không xin một lối "ô này nhận đơn không"</h2>
 * Một lối hỏi trả lời được câu ấy chính là <b>công cụ dò xem ai đã vào hệ
 * thống</b>: gõ lần lượt từng ô trên phả đồ và đọc câu trả lời. Máy chủ cố ý
 * để lý do ấy chỉ lộ ra ở bước <em>gửi</em> và gộp nó với "nhân khẩu đã xoá
 * mềm" vào một câu mơ hồ. Giao diện không được dựng một lối vòng qua quyết định
 * ấy.
 *
 * <h2>Vì sao ca "đã khuất" thì lại chặn được trước</h2>
 * {@code isAlive} đã nằm sẵn trong hồ sơ, và hồ sơ người đã khuất <b>công khai
 * với cả khách</b> — nên đọc nó không lộ thêm gì. Đó đúng là lập luận checklist
 * §1.4 dùng để đòi "chặn cứng ngay lúc chọn".
 *
 * {@code retry: false}: 404 ở đây là một câu trả lời <b>đúng luật</b> (khách
 * hoặc người chưa đủ quyền hỏi một người còn sống), không phải sự cố. Thử lại
 * ba lượt chỉ bắt người dùng chờ để nhận cùng một câu.
 */
export function useClaimTarget(personId: string | null) {
  return useQuery<ClaimTarget | null>({
    queryKey: claimQueryKeys.target(personId ?? ""),
    queryFn: async () => {
      const { data } = await personsApi.getById(personId as string);
      return {
        id: data.id,
        // Hợp đồng khai `displayName` là tuỳ chọn; một tấm thẻ nhận diện không
        // có tên thì mất hết nghĩa, nên chỗ này rơi về `null` để màn hình xử
        // như "không tra ra" — chứ KHÔNG in mã nhân khẩu ra màn hình.
        displayName: data.displayName ?? "",
        generation: data.generation ?? null,
        branchName: data.primaryBranch?.name ?? null,
        isAlive: data.isAlive,
      };
    },
    enabled: personId !== null && personId.length > 0,
    retry: false,
    staleTime: 0,
  });
}

/**
 * Gửi đơn.
 *
 * Gửi xong thì dọn khoá {@code mine}: tài khoản vừa chuyển sang trạng thái
 * "đang có một đơn mở", nên mọi màn đọc trạng thái ấy phải thấy ngay. Không dọn
 * thì người dùng bấm quay lại phả đồ, chọn ô khác, và màn hình mời họ gõ một lá
 * đơn thứ hai mà máy chủ chắc chắn từ chối.
 */
export function useSubmitClaim() {
  const queryClient = useQueryClient();
  return useMutation<ClaimView, unknown, SubmitClaimBody>({
    mutationFn: (body) => claimApi.submit(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: claimQueryKeys.mine() });
    },
  });
}

/**
 * Tự rút đơn.
 *
 * Nó có mặt vì chính câu từ chối của máy chủ bảo người dùng làm việc này ("hãy
 * rút đơn ấy trước khi gửi đơn khác"), và vì rút <b>không tiêu một lượt</b> —
 * bộ đếm đếm đơn bị từ chối. Thiếu nút này thì lời khuyên của máy chủ dẫn vào
 * ngõ cụt.
 */
export function useCancelClaim() {
  const queryClient = useQueryClient();
  return useMutation<ClaimView, unknown, string>({
    mutationFn: (claimId) => claimApi.cancel(claimId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: claimQueryKeys.mine() });
    },
  });
}

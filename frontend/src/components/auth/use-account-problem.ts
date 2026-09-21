"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/http";
import { MOCKING_ENABLED, getDevRole } from "@/lib/api/dev-role";
import { useAuth } from "@/lib/auth/auth-context";
import type { ProblemCode } from "@/types/api";

/**
 * Hai trạng thái tài khoản mà giao diện phải nói ra thành lời thay vì để người
 * dùng đoán — trạng thái 3 và 4 của bảng năm trạng thái lỗi (thiết kế 06 §7).
 *
 * Trạng thái 1 và 2 (sai mật khẩu, khoá tạm) sống trên trang đăng nhập của
 * Keycloak và không đi qua mã này. Trạng thái 5 (phiên hết hạn giữa chừng) do
 * {@code lib/auth/auth-context.tsx} xử.
 */
export type AccountProblem = "NOT_PROVISIONED" | "NOT_ACTIVE";

/**
 * Ánh xạ **mã lỗi → nhánh giao diện**.
 *
 * <h2>Vì sao theo `code` chứ không theo mã HTTP — đây là cái bẫy đã bắt người</h2>
 * {@code AppUserProvisioningService.requireCurrentUser()} ném
 * {@code NotFoundException}, tức <b>HTTP 404</b>. Người dùng đã đăng nhập
 * thành công; lỗi nổ ra ở lời gọi API <i>tiếp theo</i>, trên bất kỳ màn hình
 * nào họ vừa mở. Một bộ xử lý lỗi chung nhìn thấy 404 sẽ vẽ "không tìm thấy
 * trang" — và người dùng kết luận là trang hỏng, tải lại, rồi vẫn thế.
 * Tài khoản {@code chuaduyet} tái hiện đúng ca này.
 *
 * Backend đang sửa mã HTTP ấy. Bảng dưới đây <b>không quan tâm</b> nó đổi thành
 * 403, 409 hay ở nguyên 404: nhánh giao diện bám vào {@code code}, nên nó đúng
 * trước và sau đợt sửa, và không có một nhịp nào hai bên lệch nhau.
 *
 * <h2>Vì sao `ACCOUNT_NOT_LINKED` KHÔNG có trong bảng này</h2>
 * Nó là mã thứ ba cùng họ, và thoạt nhìn nên gộp vào đây. Nhưng
 * {@code src/components/notifications/notification-center.tsx} đã có sẵn một
 * câu riêng cho nó, <b>ngay trong hộp thư</b> — đúng chỗ người dùng đang nhìn,
 * và cụ thể hơn một màn toàn trang. Thêm nó vào bảng này là chiếm quyền của
 * một thành phần đang xử lý đúng, và làm mất một câu chữ tốt hơn.
 */
// `Partial<Record<ProblemCode | "UNKNOWN", …>>` chứ không `Record<string, …>` — cùng cái bẫy
// contracts/README §3 đã bắt ở nơi khác (xem `lib/api/membership-admin.ts`, javadoc
// `clanInviteFailureOf`): nới kiểu về `string` thì gõ sai MỘT chữ trong khoá bên dưới biên dịch
// vẫn xanh và bản đồ lặng lẽ mất một nhánh. Giữ khoá đúng kiểu `ProblemCode` thì trình biên dịch
// tự bắt lỗi gõ, không cần một bài kiểm riêng để canh chuyện đó.
const MA_LOI: Readonly<Partial<Record<ProblemCode | "UNKNOWN", AccountProblem>>> = {
  ACCOUNT_NOT_PROVISIONED: "NOT_PROVISIONED",
  ACCOUNT_NOT_ACTIVE: "NOT_ACTIVE",
};

/**
 * Lỗi này có phải "tài khoản chưa dùng được" không.
 *
 * Hàm thuần, không phụ thuộc React — nên ca kiểm dựng được thẳng một
 * {@code ApiError} mà không phải dựng cả cây provider.
 *
 * Cố ý <b>không</b> có nhánh "404 trần thì coi như chưa nối". Một 404 không
 * mang mã là câu trả lời hợp lệ và thường gặp của cả sản phẩm — "khách hỏi hồ
 * sơ một người còn sống" trả đúng 404 không phân biệt được với "không có". Nếu
 * đọc mọi 404 thành trạng thái tài khoản thì một hồ sơ đã xoá mềm sẽ làm trắng
 * toàn bộ ứng dụng, và lỗi ấy sẽ đổ cho đúng thành phần này.
 */
export function accountProblemOf(error: unknown): AccountProblem | null {
  if (!(error instanceof ApiError)) return null;
  // Không còn ép kiểu `as string`: `error.code` đã đúng kiểu `ProblemCode | "UNKNOWN"`,
  // và bảng trên khai đúng kiểu ấy nên tra thẳng được, không cần nới.
  return MA_LOI[error.code] ?? null;
}

/**
 * Theo dõi mọi truy vấn và mutation đang sống để bắt trạng thái tài khoản hỏng.
 *
 * <h2>Vì sao nghe ở bộ nhớ đệm chứ không cài vào `apiFetch`</h2>
 * Bản thiết kế đề xuất bắt ngay ở {@code apiFetch} rồi điều hướng. Cách ấy
 * đúng về nguyên tắc nhưng đặt một quyết định <b>điều hướng</b> vào tầng mạng —
 * tầng chạy ngoài cây React, không biết gì về ngôn ngữ đang hiển thị, và được
 * dùng chung bởi mọi màn đang do người khác dựng. Nghe ở bộ nhớ đệm cho đúng
 * kết quả ấy mà không phải sửa một tệp dùng chung.
 *
 * <h2>Đã bắt được thì GIỮ</h2>
 * Trạng thái dính lại cho tới khi tải lại trang. Nếu thả ra khi lỗi bị dọn khỏi
 * cache — {@code gcTime} hết hạn, hoặc một truy vấn khác thành công — màn hình
 * sẽ nhấp nháy qua lại giữa lời giải thích và cái "không tìm thấy trang" vô
 * nghĩa. Cái giá: người vừa được Hội đồng nối tài khoản phải tải lại trang mới
 * thấy đổi. Đó là một lượt F5 cho một sự kiện xảy ra vài lần trong đời tài
 * khoản, đổi lấy một màn hình không giật.
 */
/**
 * Phiên này đã đăng nhập chưa — dùng chung đúng một nguồn với
 * {@code useTreeAudience}, để hai chỗ không bao giờ trả lời khác nhau.
 *
 * Dưới MSW không có Keycloak nào để hỏi và {@code useAuth()} luôn nói "khách";
 * vai ở đó đến từ bộ chuyển vai dev, cùng nguồn với header {@code x-mock-role}
 * mà lớp API đang gửi.
 */
function daDangNhap(status: string): boolean {
  if (MOCKING_ENABLED) return getDevRole() !== "guest";
  return status === "authenticated";
}

export function useAccountProblem(): AccountProblem | null {
  const queryClient = useQueryClient();
  const { status } = useAuth();
  const [problem, setProblem] = useState<AccountProblem | null>(null);

  /**
   * <h2>Khách KHÔNG bao giờ rơi vào màn này, và đây là ràng buộc bắt buộc</h2>
   * Máy chủ trả {@code ACCOUNT_NOT_PROVISIONED} cho <b>cả khách vãng lai</b> —
   * họ cũng chưa có dòng {@code app_user} nào (xem
   * {@code mocks/handlers/change-requests.ts} → {@code requireAccount}). Nhưng
   * câu "Chưa tìm thấy ông/bà trong phả" nói sai hoàn toàn với người chưa hề
   * đăng nhập: họ không phải một thành viên bị thiếu mắt xích, họ là khách, và
   * việc cần làm của họ là đăng nhập. Các màn liên quan đã có sẵn câu riêng
   * cho khách; thành phần này không được giành mất chúng.
   *
   * Ca thật cần chữa là người <b>đã đăng nhập được</b> mà tài khoản chưa nối —
   * đúng tài khoản {@code chuaduyet}.
   */
  const batLoi = daDangNhap(status);

  useEffect(() => {
    if (!batLoi) return;

    const scan = () => {
      const errors: unknown[] = [
        ...queryClient.getQueryCache().getAll().map((q) => q.state.error),
        ...queryClient.getMutationCache().getAll().map((m) => m.state.error),
      ];
      for (const error of errors) {
        const found = accountProblemOf(error);
        if (found) {
          setProblem((truoc) => truoc ?? found);
          return;
        }
      }
    };

    // Quét một lượt ngay: lỗi có thể đã nằm sẵn trong cache trước khi thành
    // phần này được gắn (ví dụ người dùng điều hướng sang một trang khác).
    scan();
    const boQuery = queryClient.getQueryCache().subscribe(scan);
    const boMutation = queryClient.getMutationCache().subscribe(scan);
    return () => {
      boQuery();
      boMutation();
    };
  }, [queryClient, batLoi]);

  return problem;
}

"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useLocale } from "next-intl";
import {
  invitationApi,
  passwordRejectionDetail,
  setPasswordFailureOf,
  type SetPasswordFailure,
} from "@/lib/api/invitation";
import { AUTH_ENABLED, getKeycloakInstance } from "@/lib/auth/keycloak";
import { getPathname } from "@/i18n/navigation";
import type { AppLocale } from "@/i18n/routing";
import { SetPasswordForm } from "./set-password-form";
import {
  SetPasswordDone,
  SetPasswordLinkExpired,
  SetPasswordNoLink,
} from "./set-password-outcome";

/** Tên tham số truy vấn mà backend đúc vào `setPasswordUrl`. */
const THAM_SO_TOKEN = "token";

/**
 * Đích đến sau khi đăng nhập: **phả đồ**, không phải chính trang này.
 *
 * Quay về `/dat-mat-khau` sau đăng nhập là một vòng tròn — liên kết đã tiêu, và
 * người dùng sẽ gặp lại một màn nói "tài khoản của ông/bà đã có mật khẩu rồi".
 */
function duongDanPhaDo(locale: AppLocale): string {
  return getPathname({ href: "/tree", locale });
}

/**
 * Rời sang Keycloak để đăng nhập, quay lại `duongDanVe` sau khi xong.
 *
 * **Không dùng `useAuth().login()` ở màn này, và đó là một quyết định bảo mật.**
 * Hàm ấy đặt `redirectUri: window.location.href` — đúng chỗ `token` đang nằm.
 * Gửi nó đi làm `redirect_uri` là chép bí mật vào nhật ký truy cập của
 * Keycloak, vào tham số của một lượt chuyển hướng, và vào lịch sử trình duyệt
 * một lần nữa. Ở mọi màn khác thì "quay lại đúng chỗ đang đứng" là hành vi
 * đúng; ở riêng màn này nó là một đường rò.
 */
function dangNhapVaQuayVe(duongDanVe: string): void {
  if (!AUTH_ENABLED) {
    // Bản chạy MSW không có Keycloak. Đi thẳng tới đích còn hơn bấm vào một nút
    // im lặng không làm gì.
    window.location.assign(duongDanVe);
    return;
  }
  void getKeycloakInstance().login({
    redirectUri: new URL(duongDanVe, window.location.origin).toString(),
  });
}

export interface SetPasswordScreenProps {
  /**
   * Rời sang màn đăng nhập. Tiêm được vào là cố ý: jsdom không thực hiện được
   * một lượt rời trang, nên nếu không có tham số này thì ca kiểm chỉ in một
   * cảnh báo "Not implemented: navigation" rồi <b>xanh mà không kiểm được gì</b>.
   */
  readonly onGoToLogin?: (duongDanVe: string) => void;
}

/**
 * **Đặt mật khẩu qua liên kết một lần** — `/dat-mat-khau?token=…`, đích đến của
 * `setPasswordUrl` mà `POST /invitations/accept` trả về.
 *
 * <h2>Bốn màn, và mỗi màn có đúng một việc cần làm</h2>
 * <ul>
 *   <li><b>có liên kết</b> → biểu mẫu hai ô, nút "Hiện", không kiểm chính sách
 *       ở client;</li>
 *   <li><b>`410`</b> → liên kết hết hạn hoặc đã dùng. Ca <b>bình thường</b>
 *       (hạn ba mươi phút), nên là một lối đi tiếp chứ không phải một dải đỏ;</li>
 *   <li><b>`204`</b> → xong, và bước kế tiếp là đăng nhập để vào phả đồ;</li>
 *   <li><b>không có liên kết trong đường dẫn</b> → tài khoản đã có mật khẩu
 *       (backend cố ý không phát liên kết cho tài khoản như vậy). Đưa tới đăng
 *       nhập, <b>không</b> hiện lỗi.</li>
 * </ul>
 *
 * <h2>`token` bị XOÁ khỏi thanh địa chỉ ngay khi đọc xong</h2>
 * Trang lời mời không xoá được mã khỏi URL vì mã <i>là</i> đường dẫn
 * (`/moi/<mã>`). Ở đây thì khác: token là một tham số truy vấn, nên nó gỡ ra
 * được — và gỡ nó là lớp phòng vệ mạnh nhất màn này có. Một `history.replaceState`
 * ngay sau lượt đọc đầu tiên đóng bốn đường rò cùng lúc: mục lịch sử duyệt web
 * không còn mang token, ảnh chụp màn hình thanh địa chỉ không còn mang nó, một
 * lượt F5 không gửi lại nó, và mọi `Referer` sinh ra từ đây (nếu một thẻ meta
 * nào đó hỏng) cũng sạch. Token đã được giữ trong state trước đó, nên biểu mẫu
 * vẫn gửi đi được.
 *
 * Ba lớp còn lại nằm ở tầng trang: tiêu đề trang <b>không</b> mang token,
 * {@code referrer: "no-referrer"}, và {@code robots: noindex}. Sản phẩm hiện
 * không nhúng công cụ phân tích nào; nếu sau này thêm, `/dat-mat-khau` phải nằm
 * trong danh sách loại trừ.
 *
 * <h2>Không tự thử lại</h2>
 * {@code retry} của React Query tắt sẵn ở tầng biểu mẫu — thử lại một `410` chỉ
 * lặp lại cùng một câu trả lời, còn thử lại một `429` là đổ thêm dầu vào đúng
 * bộ đếm vừa chặn mình.
 */
export function SetPasswordScreen({ onGoToLogin }: SetPasswordScreenProps) {
  const locale = useLocale() as AppLocale;
  const searchParams = useSearchParams();

  /**
   * Đọc token đúng MỘT lần, vào lượt dựng đầu tiên. Không đọc lại ở lượt sau:
   * ngay dưới đây nó bị gỡ khỏi thanh địa chỉ, và một lượt đọc thứ hai sẽ thấy
   * chuỗi rỗng rồi lật màn hình sang "không có liên kết" giữa chừng.
   */
  const [token] = useState<string>(() => searchParams.get(THAM_SO_TOKEN) ?? "");

  useEffect(() => {
    if (token.length === 0) return;
    if (typeof window === "undefined") return;
    // `replaceState`, không phải `pushState`: nút Quay lại không được đưa token
    // trở lại thanh địa chỉ. Dùng thẳng API trình duyệt chứ không `router.replace`
    // — router của Next sẽ dựng lại cây và thổi bay chữ người dùng đang gõ dở.
    window.history.replaceState(null, "", window.location.pathname);
  }, [token]);

  const [xong, setXong] = useState(false);
  const [failure, setFailure] = useState<SetPasswordFailure | null>(null);
  const [rejection, setRejection] = useState<string | null>(null);

  const dat = useMutation({
    mutationFn: (newPassword: string) => invitationApi.setPassword({ token, newPassword }),
    onSuccess: () => {
      setFailure(null);
      setRejection(null);
      setXong(true);
    },
    onError: (error: unknown) => {
      const nhanh = setPasswordFailureOf(error);
      setFailure(nhanh);
      // Câu của MÁY CHỦ về mật khẩu vừa gõ. Chỉ có nghĩa ở `422`; ở các nhánh
      // khác giữ `null` để biểu mẫu không dán một câu về mật khẩu lên một sự cố
      // đường truyền.
      setRejection(nhanh === "PASSWORD_REJECTED" ? passwordRejectionDetail(error) : null);
    },
  });

  const roiSangDangNhap = useCallback(() => {
    const ve = duongDanPhaDo(locale);
    (onGoToLogin ?? dangNhapVaQuayVe)(ve);
  }, [locale, onGoToLogin]);

  if (token.length === 0) {
    return <SetPasswordNoLink onGoToLogin={roiSangDangNhap} />;
  }

  if (xong) {
    return <SetPasswordDone onGoToLogin={roiSangDangNhap} />;
  }

  if (failure === "LINK_INVALID") {
    return <SetPasswordLinkExpired onGoToLogin={roiSangDangNhap} />;
  }

  return (
    <SetPasswordForm
      onSubmit={(newPassword) => dat.mutate(newPassword)}
      submitting={dat.isPending}
      failure={failure}
      rejectionDetail={rejection}
    />
  );
}

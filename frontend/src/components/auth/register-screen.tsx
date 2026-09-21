"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  clanInviteApi,
  clanInviteFailureOf,
  clanInviteValidationDetail,
  isClanInviteValidationError,
  loginIdentifierKind,
  retryAfterSecondsOf,
  type ClanInviteFailure,
  type ClanInvitePreviewDto,
  type ClanRegistrationDto,
} from "@/lib/api/clan-invite";
import { RegisterAccountForm } from "./register-account-form";
import { RegisterCodeForm } from "./register-code-form";
import { RegisterDone } from "./register-done";
import { RegisterProblem } from "./register-problem";

export interface RegisterScreenProps {
  /** Mã điền sẵn — ví dụ `/dang-ky?ma=K7M2QD`. Người dùng vẫn sửa được. */
  readonly initialCode?: string;
  /** Xem {@link RegisterDone.onLeaveForPasswordSetup}. */
  readonly onLeaveForPasswordSetup?: (url: string) => void;
}

/**
 * **Màn đăng ký bằng mã mời dòng họ** — `/dang-ky`, design 07 §1.3
 * ("Bật tự đăng ký, có kiểm mã") dựng trên nhóm `clan-invites` của backend.
 *
 * <h2>Vì sao màn này là việc chặn, nói thẳng một lần</h2>
 * Máy chủ đã có `POST /clan-invites/register` với đủ bốn chốt của checklist
 * §1.2 — mã lưu dạng <b>băm</b>, có <b>hạn</b>, có <b>bộ đếm lượt dùng</b>, có
 * <b>giới hạn tần suất</b>. Nhưng cho tới khi màn này tồn tại, cánh cửa duy
 * nhất chạy được là trang đăng ký của Keycloak, và tài khoản tạo qua đường ấy
 * <b>không đi qua bộ đếm của máy chủ</b>. Bộ đếm là chốt quan trọng nhất trong
 * bốn — chủ dự án đã chọn cấp mã cho <i>cả họ</i>, nên nó là lớp bảo vệ duy
 * nhất: Hội đồng thấy mã đã dùng 400 lượt trong khi dòng họ có 600 người thì
 * <i>biết</i> mà thu hồi. Một bộ đếm đếm thiếu trong im lặng còn tệ hơn không
 * có bộ đếm, vì nó tạo cảm giác an toàn giả.
 *
 * <h2>HAI bước, và thứ tự của chúng là một luật nghiệp vụ</h2>
 * {@code /lookup} rồi mới {@code /register}. Gõ sai mã mà vẫn để lại một tài
 * khoản rác trong hệ thống là lỗi không ai đi dọn — và tệ hơn: realm đặt
 * {@code duplicateEmailsAllowed: false}, nên cái tài khoản rác ấy sẽ chặn chính
 * lần thử lại của người vừa gõ sai. Bước 1 <b>không tiêu lượt nào</b> của mã.
 *
 * <h2>Nhánh hỏng đi theo `code` của RFC 7807</h2>
 * Không theo mã HTTP, không theo {@code detail}. Bốn ca của mã (sai · hết hạn ·
 * thu hồi · hết lượt) cộng ba ca không phải lỗi của mã (tần suất · cổng danh
 * tính im lặng · mất mạng) — mỗi ca một câu và một lối đi tiếp riêng, xem
 * {@code register-problem.tsx}.
 *
 * <h2>Không tự thử lại</h2>
 * {@code retry: false} ở cả hai mutation. Gần hết các nhánh hỏng là câu trả lời
 * <b>cuối cùng</b> của máy chủ, và thử lại chỉ bắt người dùng chờ ba lượt mạng
 * để nhận cùng một câu — tệ hơn nữa là ba lượt ấy đi thẳng vào đúng bộ đếm
 * chặn tần suất đang canh họ.
 *
 * <h2>Mã mời là bí mật; màn này không nhân bản nó</h2>
 * Mã <b>không</b> vào khoá React Query (khoá đi vào DevTools và mọi ảnh chụp
 * trạng thái), không vào tiêu đề trang, và đi trong <b>thân POST</b> chứ không
 * trong đường dẫn. Đây cũng là lý do hai lượt gọi là {@code useMutation} chứ
 * không phải {@code useQuery}: một truy vấn sẽ tự nạp lại khi cửa sổ lấy lại
 * tiêu điểm, và mỗi lượt như vậy là một lượt nữa vào bộ đếm chặn tần suất.
 */
export function RegisterScreen({ initialCode, onLeaveForPasswordSetup }: RegisterScreenProps) {
  const [code, setCode] = useState(initialCode ?? "");
  const [preview, setPreview] = useState<ClanInvitePreviewDto | null>(null);
  const [registration, setRegistration] = useState<ClanRegistrationDto | null>(null);
  const [failure, setFailure] = useState<ClanInviteFailure | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | null>(null);
  /** `detail` của một `VALIDATION_FAILED` — thuộc về TRONG Ô, không thay cả màn. */
  const [loiTrongO, setLoiTrongO] = useState<string | null>(null);
  /** Dữ liệu của lần gửi gần nhất, để nút "Thử lại" ở ca 503 gửi lại đúng nó. */
  const [lanGuiCuoi, setLanGuiCuoi] = useState<{
    loginId: string;
    displayName: string;
  } | null>(null);

  const kiemMa = useMutation({
    mutationFn: () => clanInviteApi.lookup(code),
    onSuccess: (data) => {
      setFailure(null);
      setRetryAfter(null);
      setPreview(data);
    },
    onError: (error: unknown) => {
      setRetryAfter(retryAfterSecondsOf(error));
      setFailure(clanInviteFailureOf(error));
    },
  });

  const lapTaiKhoan = useMutation({
    mutationFn: (values: { loginId: string; displayName: string }) =>
      clanInviteApi.register({
        code,
        // `loginId`, KHÔNG `email`: contract đã đổi tên trường và đánh dấu tên
        // cũ là bỏ dần. Tên cũ nói dối khi giá trị là một số điện thoại, và
        // chính lời nói dối ấy là thứ đã khiến màn này chặn số máy.
        loginId: values.loginId,
        // Chuỗi rỗng KHÔNG gửi đi: `displayName` tuỳ chọn, và gửi `""` là bảo
        // máy chủ đặt tên hiển thị bằng một chuỗi rỗng thay vì bỏ qua trường.
        displayName: values.displayName.length > 0 ? values.displayName : undefined,
      }),
    onSuccess: (data) => {
      setFailure(null);
      setLoiTrongO(null);
      setRegistration(data);
    },
    onError: (error: unknown) => {
      // `VALIDATION_FAILED` là ca DUY NHẤT người dùng sửa được ngay tại chỗ, nên
      // nó ở lại trên biểu mẫu với chữ đã gõ còn nguyên — không thay cả màn
      // hình. Thay màn ở đây là bắt họ gõ lại mã cho một lỗi chính tả trong ô
      // định danh.
      if (isClanInviteValidationError(error)) {
        setLoiTrongO(clanInviteValidationDetail(error));
        return;
      }
      setRetryAfter(retryAfterSecondsOf(error));
      setFailure(clanInviteFailureOf(error));
    },
  });

  /** Quay về ô mã. Xoá mọi thứ bước 2 đã dựng — kể cả bản xem trước. */
  function veOMa() {
    setFailure(null);
    setRetryAfter(null);
    setLoiTrongO(null);
    setPreview(null);
    setLanGuiCuoi(null);
    kiemMa.reset();
    lapTaiKhoan.reset();
  }

  if (registration) {
    return (
      <RegisterDone
        registration={registration}
        // Giới hạn của KHÔI PHỤC, không của đăng ký: tài khoản lập bằng số điện
        // thoại không nhận được thư đặt lại mật khẩu. Nói ở khối đặt mật khẩu,
        // không ở ô nhập — xem javadoc `register-account-form.tsx`.
        loginIdIsPhone={
          lanGuiCuoi !== null && loginIdentifierKind(lanGuiCuoi.loginId) === "PHONE"
        }
        onLeaveForPasswordSetup={onLeaveForPasswordSetup}
      />
    );
  }

  if (failure) {
    return (
      <RegisterProblem
        failure={failure}
        onEditCode={veOMa}
        retryAfterSeconds={retryAfter}
        retrying={kiemMa.isPending || lapTaiKhoan.isPending}
        // "Thử lại" gửi lại ĐÚNG bước vừa hỏng. Ở ca `PROVIDER_DOWN` điều đó
        // quan trọng hẳn: mã CHƯA bị tiêu, nên bấm lại là việc đúng — nhưng chỉ
        // khi nó gửi lại `register` với cùng dữ liệu, không phải bắt đầu lại từ
        // ô mã.
        onRetry={
          lanGuiCuoi
            ? () => {
                setFailure(null);
                setRetryAfter(null);
                lapTaiKhoan.mutate(lanGuiCuoi);
              }
            : () => {
                setFailure(null);
                setRetryAfter(null);
                kiemMa.mutate();
              }
        }
      />
    );
  }

  if (preview) {
    return (
      <RegisterAccountForm
        preview={preview}
        submitting={lapTaiKhoan.isPending}
        serverFieldError={loiTrongO}
        onEditCode={veOMa}
        onSubmit={(values) => {
          setLoiTrongO(null);
          setLanGuiCuoi(values);
          lapTaiKhoan.mutate(values);
        }}
      />
    );
  }

  return (
    <RegisterCodeForm
      value={code}
      onChange={setCode}
      checking={kiemMa.isPending}
      onSubmit={() => kiemMa.mutate()}
    />
  );
}

"use client";

import { useId, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";

export interface SetPasswordFieldProps {
  readonly name: string;
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  /** Câu gợi ý dưới ô. Nối vào `aria-describedby`, không phải một dòng chữ trôi nổi. */
  readonly hint?: string;
  /** Câu từ chối của **máy chủ** hoặc của phép dò gõ lệch. Đặt `aria-invalid`. */
  readonly error?: string | null;
  readonly autoFocus?: boolean;
  readonly disabled?: boolean;
}

/**
 * Một ô mật khẩu **có nút "Hiện" bằng CHỮ**, dựng cho người gõ một ngón trên
 * điện thoại.
 *
 * <h2>Nút "Hiện" là bắt buộc, không phải trang trí</h2>
 * Gõ mù trên bàn phím điện thoại là lý do số một khiến người lớn tuổi bỏ dở một
 * biểu mẫu mật khẩu: họ không sai chính tả, họ chỉ không biết mình đã gõ gì.
 * Và ở màn này cái giá của một lỗi gõ không phải là "thử lại" — nó là một mật
 * khẩu <b>đặt xong mà chính chủ không biết</b>, tức một cú điện thoại nữa cho
 * Trưởng chi.
 *
 * <h2>Giữ giống hệt giao diện Keycloak — người dùng gặp cả hai trong một buổi</h2>
 * Theme `infra/keycloak/themes/giapha/login/` đã đặt tiền lệ, và tệp này chép
 * đúng năm chi tiết của nó chứ không dựng lại một bản na ná:
 * <ol>
 *   <li><b>68×44px</b> — bề rộng 68 là con số của {@code .gp-hien} trong
 *       {@code giapha.css}; 44 là sàn vùng chạm WCAG 2.2, đo <b>hộp bao</b> chứ
 *       không đo chữ;</li>
 *   <li><b>nhãn CHỮ đổi theo trạng thái</b> — "Hiện" ⇄ "Ẩn". Con mắt gạch chéo
 *       là một câu đố: nó không nói được nó đang mô tả trạng thái hiện tại hay
 *       hành động sắp xảy ra;</li>
 *   <li><b>{@code aria-pressed}</b> để trình đọc màn hình biết đây là công tắc
 *       hai trạng thái, không phải một nút gây hành động;</li>
 *   <li><b>{@code aria-controls}</b> nói nút nào điều khiển ô nào — cùng một dữ
 *       kiện phục vụ cả trình đọc màn hình lẫn phép kiểm;</li>
 *   <li><b>con trỏ ở lại trong ô</b> sau khi bấm: người vừa bấm "Hiện" là để
 *       đọc tiếp chỗ đang gõ dở, không phải để bắt đầu lại.</li>
 * </ol>
 *
 * <h2>Ô thật, không phải `Input.Password` của Ant Design</h2>
 * Nút mở/đóng của AntD là một biểu tượng con mắt chừng 16px, không nhãn chữ và
 * không đạt sàn vùng chạm — vi phạm cả hai luật cứng cùng lúc. Ghi đè nó tốn
 * nhiều mã hơn là dựng thẳng một ô gốc, và bản ghi đè sẽ trôi mỗi lần AntD lên
 * phiên bản.
 */
export function SetPasswordField({
  name,
  label,
  value,
  onChange,
  hint,
  error,
  autoFocus,
  disabled,
}: SetPasswordFieldProps) {
  const t = useTranslations("auth.setPassword");
  const reactId = useId();
  const inputId = `mat-khau-${name}-${reactId}`;
  const hintId = `${inputId}-goi-y`;
  const errorId = `${inputId}-loi`;
  const inputRef = useRef<HTMLInputElement>(null);
  const [dangHien, setDangHien] = useState(false);

  const describedBy = [hint ? hintId : null, error ? errorId : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div className="min-w-0">
      <label
        htmlFor={inputId}
        className="mb-1 block text-than font-semibold text-text-main"
      >
        {label}
      </label>

      <div className="relative min-w-0">
        <input
          ref={inputRef}
          id={inputId}
          name={name}
          // Bắt đầu ở `password`: người dùng bật "Hiện" khi HỌ muốn. Mở sẵn là
          // quyết định thay họ về chuyện ai đang đứng sau lưng.
          type={dangHien ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          // `new-password`: trình quản lý mật khẩu đề nghị tạo mới thay vì điền
          // lại mật khẩu cũ của một trang khác.
          autoComplete="new-password"
          autoFocus={autoFocus}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy.length > 0 ? describedBy : undefined}
          // Bề rộng ô trừ chỗ của nút: 68 + 8 lề mỗi bên, đúng như `.gp-o-boc`.
          className="min-h-[44px] w-full rounded-lg border bg-bg-card pl-3 text-dan text-text-main outline-none"
          style={{
            paddingRight: 84,
            borderColor: error ? colorVars.danger : colorVars.borderInput,
          }}
        />

        <button
          type="button"
          // Công tắc hai trạng thái, không phải nút gây hành động.
          aria-pressed={dangHien}
          aria-controls={inputId}
          onClick={() => {
            setDangHien((truoc) => !truoc);
            inputRef.current?.focus();
          }}
          disabled={disabled}
          // 68×44 — hộp bao, không phải chữ. Xem javadoc.
          className="absolute right-1 top-1/2 inline-flex h-[44px] w-[68px] -translate-y-1/2 items-center justify-center rounded-lg border text-than font-medium"
          style={{
            background: colorVars.bgDeceased,
            borderColor: colorVars.borderDark,
            color: colorVars.textMain,
          }}
        >
          {dangHien ? t("hide") : t("show")}
        </button>
      </div>

      {hint && (
        <p id={hintId} className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
          {hint}
        </p>
      )}

      {error && (
        <p
          id={errorId}
          // `role="alert"` chỉ ở ĐÂY, nơi câu chữ là thứ người dùng sửa được
          // ngay. Các khối "chưa gửi đi được" ở màn ngoài dùng `role="status"`:
          // chúng không phải lỗi của người đang đọc.
          role="alert"
          className="m-0 mt-2 max-w-prose text-than font-medium leading-relaxed"
          style={{ color: colorVars.danger }}
        >
          {error}
        </p>
      )}
    </div>
  );
}

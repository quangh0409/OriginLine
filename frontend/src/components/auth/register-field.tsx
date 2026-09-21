"use client";

import { useId, type InputHTMLAttributes } from "react";
import { colorVars } from "@/styles/tokens";

export interface RegisterFieldProps {
  readonly name: string;
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  /** Câu gợi ý dưới ô. Nối vào `aria-describedby`, không phải chữ trôi nổi. */
  readonly hint?: string;
  /** Câu từ chối. Đặt `aria-invalid` và `role="alert"`. */
  readonly error?: string | null;
  readonly autoFocus?: boolean;
  readonly disabled?: boolean;
  readonly inputMode?: InputHTMLAttributes<HTMLInputElement>["inputMode"];
  readonly autoComplete?: string;
  readonly maxLength?: number;
  readonly placeholder?: string;
  /**
   * Chữ hoa, giãn ký tự — chỉ dùng cho ô mã mời.
   *
   * Chỉ là **lớp trình bày**: giá trị trong state giữ nguyên thứ người dùng gõ,
   * để cái họ thấy và cái họ nhớ mình vừa gõ là một. Phép chuẩn hoá thật nằm ở
   * {@code normalizeClanInviteCode}, chạy lúc gửi.
   */
  readonly monospace?: boolean;
}

/**
 * Một ô nhập chữ của luồng đăng ký.
 *
 * <h2>Vì sao không dùng `Input` của Ant Design</h2>
 * Cùng lý do đã ghi ở {@code set-password-field.tsx}: cỡ chữ và chiều cao của
 * AntD đến từ thuật toán sinh của nó, và ghi đè chúng cho đạt **16px · 44px**
 * tốn nhiều mã hơn dựng thẳng một ô gốc — bản ghi đè lại trôi mỗi lần AntD lên
 * phiên bản. Ô ở đây đứng cạnh hai ô mật khẩu của màn đặt mật khẩu trong cùng
 * một buổi của cùng một người, nên chúng phải trông như nhau.
 *
 * <h2>Nhãn là `<label for>` thật, không phải placeholder</h2>
 * Placeholder biến mất ngay khi người dùng gõ chữ đầu tiên — tức nhãn biến mất
 * đúng lúc người ta cần kiểm lại mình đang điền ô nào. Với người lớn tuổi gõ
 * một ngón trên điện thoại thì đó là lỗi tốn một cuộc điện thoại.
 */
export function RegisterField({
  name,
  label,
  value,
  onChange,
  hint,
  error,
  autoFocus,
  disabled,
  inputMode,
  autoComplete,
  maxLength,
  placeholder,
  monospace,
}: RegisterFieldProps) {
  const reactId = useId();
  const inputId = `dang-ky-${name}-${reactId}`;
  const hintId = `${inputId}-goi-y`;
  const errorId = `${inputId}-loi`;

  const describedBy = [hint ? hintId : null, error ? errorId : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div className="min-w-0">
      <label htmlFor={inputId} className="mb-1 block text-than font-semibold text-text-main">
        {label}
      </label>

      <input
        id={inputId}
        name={name}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoFocus={autoFocus}
        disabled={disabled}
        inputMode={inputMode}
        autoComplete={autoComplete}
        maxLength={maxLength}
        placeholder={placeholder}
        // `autoCapitalize`/`autoCorrect` tắt ở ô mã: bàn phím điện thoại tự sửa
        // "K7M2QD" thành một từ tiếng Việt là chuyện có thật, và người dùng
        // không nhìn thấy nó xảy ra.
        autoCapitalize={monospace ? "characters" : undefined}
        autoCorrect={monospace ? "off" : undefined}
        spellCheck={monospace ? false : undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy.length > 0 ? describedBy : undefined}
        // `font-mono`: phông đơn cách gạch chéo số 0 và phân biệt `1`/`l`/`I` —
        // đúng cặp ký tự làm hỏng mọi lần gõ mã trên màn này. `break-all` đi
        // kèm là luật L4 của kho (tests/unit/a11y/layout-containment.test.ts);
        // trên một `<input>` nó không có tác dụng gì, nhưng giữ nó rẻ hơn nhiều
        // so với việc mở một danh sách miễn trừ cho một luật quét toàn kho.
        className={`min-h-[44px] w-full rounded-lg border bg-bg-card px-3 text-dan text-text-main outline-none${
          monospace ? " break-all font-mono uppercase tracking-[0.12em]" : ""
        }`}
        style={{ borderColor: error ? colorVars.danger : colorVars.borderInput }}
      />

      {hint && (
        <p id={hintId} className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
          {hint}
        </p>
      )}

      {error && (
        <p
          id={errorId}
          // `role="alert"` chỉ ở ĐÂY, nơi câu chữ là thứ người dùng sửa được
          // ngay trong ô. Các khối giải thích ở màn ngoài dùng `role="status"`.
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

"use client";

import { useId, type ReactNode } from "react";
import { colorTokens } from "@/styles/tokens";

export interface FormFieldProps {
  label: string;
  /** Validation message from react-hook-form, already localized by the schema. */
  error?: string;
  hint?: string;
  required?: boolean;
  children: (props: { id: string; status: "error" | undefined; describedBy?: string }) => ReactNode;
}

/**
 * Label + control + error, wired for accessibility.
 *
 * Written by hand instead of using antd's `<Form.Item>`: antd's Form owns its
 * own value/validation store, which would compete with react-hook-form for
 * the same state. Here RHF stays the single source of truth and antd
 * contributes only the input widgets.
 *
 * The error is `role="alert"` and linked with `aria-describedby` so a screen
 * reader announces it on blur rather than leaving a red border as the only
 * signal — the same reason `status` is passed down instead of only colouring.
 */
export function FormField({ label, error, hint, required, children }: FormFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(" ");

  return (
    <div className="flex flex-col gap-1">
      {/* Dấu * nằm NGOÀI <label>. Để bên trong thì tên khả truy cập của ô nhập trở thành
          "Họ và tên*" — trình đọc màn hình đọc thừa một ký tự vô nghĩa, và mọi phép tra theo nhãn
          (`getByLabelText("Họ và tên")`) đều trượt. Dấu * chỉ là tín hiệu thị giác; thông tin
          "bắt buộc" đã được truyền đúng cách qua thuộc tính `required` của chính ô nhập. */}
      <div className="flex items-baseline gap-1">
        <label htmlFor={id} className="text-[13px] font-medium text-text-main">
          {label}
        </label>
        {required && (
          <span aria-hidden style={{ color: colorTokens.primary }}>
            *
          </span>
        )}
      </div>

      {children({ id, status: error ? "error" : undefined, describedBy: describedBy || undefined })}

      {hint && (
        <p id={hintId} className="m-0 text-[12px] leading-snug text-text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="m-0 text-[12px]" style={{ color: colorTokens.danger }}>
          {error}
        </p>
      )}
    </div>
  );
}

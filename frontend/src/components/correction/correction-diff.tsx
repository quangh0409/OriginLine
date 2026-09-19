"use client";

import { useTranslations } from "next-intl";
import type { PersonDto } from "@/types/api";
import { fieldSpec, isCorrectableField } from "./correctable-fields";
import { CorrectionValue } from "./correction-value";

export interface CorrectionDiffProps {
  /** Nội dung đề nghị. Rỗng khi yêu cầu đã bị cắt `payload` hoặc thuộc loại `OTHER`. */
  payload: Record<string, unknown>;
  /** Tên trường khi `payload` bị cắt — vẫn đủ để tóm tắt mà không lộ giá trị. */
  payloadFields: string[];
  /**
   * Hồ sơ hiện tại, để dựng cột "đang ghi". `undefined` khi chưa tải xong hoặc
   * người xem không được phép đọc hồ sơ ấy — khi đó chỉ còn cột "đề nghị", và
   * đó là một màn hình hợp lệ, không phải một màn hình lỗi.
   */
  person?: PersonDto;
}

/**
 * **Trước / sau, đặt cạnh nhau.** Đây là thứ người duyệt thật sự cần.
 *
 * <h2>Vì sao hai cột chứ không phải một dòng "đã đổi X"</h2>
 * Trưởng chi quyết trong vài giây, trên điện thoại, giữa lúc bận. Bắt họ nhớ
 * giá trị cũ rồi so trong đầu với giá trị mới là cách để một con số gõ nhầm
 * lọt qua. Hai cột cạnh nhau biến việc so sánh thành việc của mắt.
 *
 * <h2>Cột "đang ghi" vắng thì bỏ hẳn cột, không thay bằng gạch ngang</h2>
 * Hồ sơ trả về đã được máy chủ lọc theo tầng riêng tư của **người đang xem**.
 * Một trường vắng mặt có thể là "chưa ai ghi" mà cũng có thể là "người này
 * không được phép thấy" — hợp đồng cố ý không cho phân biệt. In "chưa có" hay
 * "—" là tự bịa ra câu trả lời cho một câu hỏi mà máy chủ đã từ chối trả lời.
 */
export function CorrectionDiff({ payload, payloadFields, person }: CorrectionDiffProps) {
  const t = useTranslations("correction");
  const keys = Object.keys(payload).length > 0 ? Object.keys(payload).sort() : payloadFields;

  if (keys.length === 0) return null;

  const payloadHidden = Object.keys(payload).length === 0 && payloadFields.length > 0;

  return (
    <div className="flex flex-col gap-2">
      {payloadHidden && (
        <p className="m-0 text-than text-text-muted">{t("card.payloadHidden")}</p>
      )}

      {keys.map((key) => {
        const spec = isCorrectableField(key) ? fieldSpec(key) : undefined;
        const label = spec ? t(`fields.${spec.key}`) : key;
        const before = spec && person ? spec.read(person) : undefined;
        const after = payload[key];

        return (
          <div
            key={key}
            className="rounded-md border border-border bg-bg-page px-3 py-2"
          >
            <p className="m-0 text-than font-medium uppercase tracking-wide text-text-muted">
              {label}
            </p>
            <div className="mt-1 grid gap-2 sm:grid-cols-2">
              {/* Cột trái chỉ tồn tại khi thật sự có giá trị để đối chiếu. */}
              {before !== undefined && (
                <div>
                  <p className="m-0 text-than text-text-muted">{t("card.before")}</p>
                  <div className="text-than text-text-main">
                    <CorrectionValue fieldKey={key} value={before} />
                  </div>
                </div>
              )}
              {!payloadHidden && (
                <div>
                  <p className="m-0 text-than text-text-muted">{t("card.after")}</p>
                  <div className="text-than font-medium text-primary">
                    {/* `null` là một đề nghị hợp lệ: "xin bỏ trường này đi" —
                        ví dụ ngày mất bị ghi nhầm cho một người còn sống. Phải
                        nói thành lời, vì một ô trống thì người duyệt không
                        phân biệt được với một đề nghị hỏng. */}
                    {after === null || after === undefined || after === "" ? (
                      <span className="italic">{t("card.afterCleared")}</span>
                    ) : (
                      <CorrectionValue fieldKey={key} value={after} />
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

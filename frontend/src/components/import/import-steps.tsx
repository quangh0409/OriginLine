"use client";

import { useTranslations } from "next-intl";
import { CheckOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";

/** Bước nghiệp vụ hiện tại, 1–5. `0` nghĩa là chưa vào bước nào. */
export type StepNumber = 0 | 1 | 2 | 3 | 4 | 5;

export interface ImportStepsProps {
  current: StepNumber;
  /** Rút gọn: chỉ tên bước, không kèm mô tả. Dùng ở đầu các màn con. */
  compact?: boolean;
}

const STEP_KEYS = ["s1", "s2", "s3", "s4", "s5"] as const;

/**
 * Quy trình năm bước, vẽ dưới dạng danh sách có thứ tự.
 *
 * <h2>Vì sao là `<ol>` chứ không phải `<Steps>` của Ant Design</h2>
 * `<Steps>` vẽ ra một dải nút tròn nối bằng đường kẻ, và trên khung nhìn điện
 * thoại nó ép năm nhãn tiếng Việt có dấu vào những cột hẹp đến mức phải cắt
 * chữ. Người dùng của màn này 45–65 tuổi và phần lớn mở bằng điện thoại; một
 * nhãn bị cắt là một bước họ không hiểu. Danh sách dọc thì mỗi bước có đủ chỗ
 * cho cả tên, mô tả và ai làm.
 *
 * Nó cũng đúng hơn về ngữ nghĩa: đây là một trình tự các việc, và trình đọc
 * màn hình đọc được "mục 3 trên 5" mà không cần thêm `aria` nào.
 *
 * <h2>Trạng thái nói bằng chữ, không chỉ bằng màu</h2>
 * Bước đã xong mang dấu tích **kèm chữ** "Đã xong"; bước đang làm mang chữ
 * "Đang ở bước này". Sàn tiếp cận của tài liệu 00: biểu tượng luôn đi kèm chữ,
 * và màu không bao giờ là kênh thông tin duy nhất.
 */
export function ImportSteps({ current, compact = false }: ImportStepsProps) {
  const t = useTranslations("dataImport.steps");

  return (
    <nav aria-label={t("legend")}>
      <ol className="m-0 list-none space-y-2 p-0">
        {STEP_KEYS.map((key, index) => {
          const number = index + 1;
          const done = current > number;
          const active = current === number;

          return (
            <li
              key={key}
              className="flex gap-3 rounded-lg border px-3 py-2"
              style={{
                borderColor: active ? colorVars.primary : colorVars.border,
                backgroundColor: active ? colorVars.primaryLight : colorVars.bgCard,
              }}
              aria-current={active ? "step" : undefined}
            >
              <span
                aria-hidden
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full font-semibold"
                style={{
                  backgroundColor: done
                    ? colorVars.successBg
                    : active
                      ? colorVars.primary
                      : colorVars.bgPage,
                  color: done
                    ? colorVars.successText
                    : active
                      ? colorVars.bgCard
                      : colorVars.textMuted,
                  border: `1px solid ${done ? colorVars.successText : colorVars.border}`,
                }}
              >
                {done ? <CheckOutlined /> : number}
              </span>

              <div className="min-w-0">
                <p className="m-0 text-than font-semibold" style={{ color: colorVars.textMain }}>
                  {t(`${key}.name`)}
                </p>
                {/* Chữ trạng thái — phần không thể thay bằng màu. */}
                {(done || active) && (
                  <p
                    className="m-0 text-than font-medium"
                    style={{ color: done ? colorVars.successText : colorVars.primary }}
                  >
                    {done ? t("done") : t("current")}
                  </p>
                )}
                {!compact && (
                  <>
                    <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
                      {t(`${key}.detail`)}
                    </p>
                    <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
                      {t(`${key}.who`)}
                    </p>
                  </>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

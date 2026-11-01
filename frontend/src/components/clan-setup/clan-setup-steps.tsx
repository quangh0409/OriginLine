"use client";

import { useTranslations } from "next-intl";
import { CheckOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import type { ClanReadinessStepDto } from "@/lib/api/clan-profile";

export type ClanSetupStepNumber = 1 | 2 | 3 | 4 | 5;

const STEP_ORDER: readonly { number: ClanSetupStepNumber; key: ClanReadinessStepDto["key"] }[] = [
  { number: 1, key: "profile" },
  { number: 2, key: "council" },
  { number: 3, key: "import" },
  { number: 4, key: "invite" },
  { number: 5, key: "done" },
];

export interface ClanSetupStepsProps {
  /** `undefined` khi `readiness` chưa tải xong — mỗi mục vẽ như CHƯA XONG, không đoán. */
  steps: ClanReadinessStepDto[] | undefined;
  current: ClanSetupStepNumber;
  onSelect: (step: ClanSetupStepNumber) => void;
}

/**
 * Thanh tiến độ 5 bước của `/quan-tri` — đọc `done`/`hint` từ
 * `GET .../readiness`, KHÔNG tự suy đoán bước nào xong.
 *
 * <h2>Khác `ImportSteps` ở đúng MỘT điểm: bấm được</h2>
 * `ImportSteps` (nhập liệu) là một trình tự NHIỀU TRANG, đi tuần tự; các thẻ ở
 * đây cùng nằm trên MỘT trang, nên mỗi mục là một nút bấm để nhảy thẳng tới thẻ
 * đó — một Quản trị quay lại sửa Bước 1 sau khi đã xong Bước 3 là chuyện bình
 * thường (sửa tên dòng họ sau khi đã tạo xong Hội đồng), không phải một luồng
 * phải đi lại từ đầu.
 *
 * <h2>`hint` của máy chủ hiện dưới tên bước CHƯA xong</h2>
 * Đây là câu do máy chủ soạn theo `Accept-Language` (`contracts/README §3`
 * quen thuộc: BE soạn sẵn chữ hiển thị) — ví dụ "còn thiếu gốc ltree". Bước ĐÃ
 * xong thì không cần hint nữa, dù máy chủ có gửi hay không.
 */
export function ClanSetupSteps({ steps, current, onSelect }: ClanSetupStepsProps) {
  const t = useTranslations("clanSetup.steps");
  const byKey = new Map((steps ?? []).map((s) => [s.key, s]));

  return (
    <nav aria-label={t("legend")}>
      <ol className="m-0 list-none space-y-2 p-0">
        {STEP_ORDER.map(({ number, key }) => {
          const step = byKey.get(key);
          const done = step?.done ?? false;
          const active = current === number;

          return (
            <li key={key}>
              <button
                type="button"
                onClick={() => onSelect(number)}
                aria-current={active ? "step" : undefined}
                className="flex w-full items-start gap-3 rounded-lg border px-3 py-2 text-left"
                style={{
                  borderColor: active ? colorVars.primary : colorVars.border,
                  backgroundColor: active ? colorVars.primaryLight : colorVars.bgCard,
                }}
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

                <span className="min-w-0">
                  <span
                    className="block text-than font-semibold"
                    style={{ color: colorVars.textMain }}
                  >
                    {t(`${key}.name`)}
                  </span>
                  <span
                    className="block text-than font-medium"
                    style={{ color: done ? colorVars.successText : colorVars.primary }}
                  >
                    {/* `doneLabel`, không phải `done`: khoá bước thứ năm của
                        chính hợp đồng đã LÀ `"done"` (`ClanReadinessStepKey`),
                        và `t(\`${key}.name\`)` bên trên tra `steps.done.name`
                        — hai khoá `steps.done` (chuỗi trạng thái) và
                        `steps.done.name` (tên bước) không thể cùng tồn tại
                        trong một cây bản dịch. */}
                    {done ? t("doneLabel") : active ? t("current") : t("pending")}
                  </span>
                  {!done && step?.hint && (
                    <span className="mt-0.5 block text-than" style={{ color: colorVars.textMuted }}>
                      {step.hint}
                    </span>
                  )}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

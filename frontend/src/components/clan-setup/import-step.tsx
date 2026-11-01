"use client";

import { Alert, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { useClanReadiness } from "./hooks/use-clan-setup";

/**
 * **Bước 3 — Nhập phả từ Excel**, màn `/quan-tri`.
 *
 * KHÔNG dựng lại đường ống nhập liệu ở đây — `/nhap-lieu` đã làm xong việc đó
 * (Reader → staging → 15 luật chặn/11 cảnh báo → dò trùng/kỵ-húy → ghi một
 * transaction). Thẻ này chỉ là LỐI VÀO cộng một câu trả lời nhanh cho "đã có
 * bao nhiêu người trong phả rồi", đọc từ `readiness` chứ không tự đếm lại.
 */
export function ImportStep() {
  const t = useTranslations("clanSetup.importStep");
  const readiness = useClanReadiness();

  return (
    <section aria-labelledby="nhap-pha-tieu-de" className="space-y-3">
      <h2 id="nhap-pha-tieu-de" className="sr-only">
        {t("title")}
      </h2>
      <p className="m-0 text-than text-text-muted">{t("lead")}</p>

      {readiness.isPending ? (
        <Skeleton active paragraph={{ rows: 1 }} />
      ) : readiness.error ? (
        <Alert type="error" showIcon message={t("countFailed")} />
      ) : (
        <p className="m-0 text-than font-medium text-text-main" data-testid="import-step-counts">
          {t("countLine", {
            persons: readiness.data?.persons.count ?? 0,
            branches: readiness.data?.branches.count ?? 0,
          })}
        </p>
      )}

      <Link
        href="/nhap-lieu"
        className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-4 text-than font-medium no-underline"
        style={{ backgroundColor: colorVars.primary, color: colorVars.bgCard }}
        data-testid="import-step-action"
      >
        {t("action")}
      </Link>
    </section>
  );
}

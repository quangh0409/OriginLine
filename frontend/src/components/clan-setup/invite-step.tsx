"use client";

import { Alert, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { useClanReadiness } from "./hooks/use-clan-setup";

/**
 * **Bước 4 — Phát mã mời**, màn `/quan-tri`.
 *
 * Không dựng lại `/quan-ly/phat-ma` (đã có, `ClanCodeCard`/`InviteScreen`) —
 * thẻ này chỉ là lối vào cộng số mã đang hoạt động, đọc từ `readiness` để
 * không gọi thêm một API liệt kê chỉ để đếm.
 */
export function InviteStep() {
  const t = useTranslations("clanSetup.inviteStep");
  const readiness = useClanReadiness();

  return (
    <section aria-labelledby="phat-ma-tieu-de" className="space-y-3">
      <h2 id="phat-ma-tieu-de" className="sr-only">
        {t("title")}
      </h2>
      <p className="m-0 text-than text-text-muted">{t("lead")}</p>

      {readiness.isPending ? (
        <Skeleton active paragraph={{ rows: 1 }} />
      ) : readiness.error ? (
        <Alert type="error" showIcon message={t("countFailed")} />
      ) : (
        <p className="m-0 text-than font-medium text-text-main" data-testid="invite-step-count">
          {t("countLine", { count: readiness.data?.inviteCodes.active ?? 0 })}
        </p>
      )}

      <Link
        href="/quan-ly/phat-ma"
        className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-4 text-than font-medium no-underline"
        style={{ backgroundColor: colorVars.primary, color: colorVars.bgCard }}
        data-testid="invite-step-action"
      >
        {t("action")}
      </Link>
    </section>
  );
}

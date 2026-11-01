"use client";

import { Alert, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { useClanProfileAdmin, useClanReadiness } from "./hooks/use-clan-setup";

/**
 * **Bước 5 — Sẵn sàng**, màn `/quan-tri`.
 *
 * <h2>Danh sách "việc còn nợ Hội đồng" chép nguyên văn từ CLAUDE.md</h2>
 * Bốn phán quyết dưới đây (dâu/rể sau ly hôn · màu huy hiệu dâu/rể · quyền đọc
 * số điện thoại `PRIVATE` · Hán-Nôm người còn sống) là đúng bốn điều mục
 * "Current state" của CLAUDE.md ghi là "the Hội đồng Tộc biểu owes rulings" —
 * KHÔNG bịa thêm, không rút bớt. Mục đầu tiên nặng nhất: nó phải xong TRƯỚC
 * khi nhập dữ liệu một dòng họ THẬT, vì đổi luật sau khi 1.500 người đã vào
 * phả không còn là một lượt sửa mã.
 *
 * Hai chuyện chặn tiến độ KHÁC mà CLAUDE.md cũng nêu (giấy phép pháp nhân cho
 * Zalo OA, danh mục 34 tỉnh thành) KHÔNG vào danh sách này — đó không phải
 * phán quyết của Hội đồng Tộc biểu, mà là thủ tục pháp lý/hành chính. Trộn
 * chung sẽ khiến Hội đồng tưởng nhầm cả bốn việc đều nằm trong quyền quyết
 * của họ.
 */
export function ReadyStep() {
  const t = useTranslations("clanSetup.readyStep");
  const profile = useClanProfileAdmin();
  const readiness = useClanReadiness();

  const pendingCouncilKeys = [
    "divorceDeletion",
    "badgeColours",
    "privatePhone",
    "hanNomVisibility",
  ] as const;

  return (
    <section aria-labelledby="san-sang-tieu-de" className="space-y-4">
      <h2 id="san-sang-tieu-de" className="sr-only">
        {t("title")}
      </h2>

      {profile.isPending || readiness.isPending ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : (
        <dl className="m-0 grid grid-cols-1 gap-2 rounded-lg border border-border bg-bg-card px-4 py-3 text-than sm:grid-cols-2">
          <div className="flex flex-wrap gap-x-2">
            <dt className="text-text-muted">{t("summaryProfileLabel")}</dt>
            <dd className="m-0 text-text-main">{profile.data?.data.name ?? "—"}</dd>
          </div>
          <div className="flex flex-wrap gap-x-2">
            <dt className="text-text-muted">{t("summaryCouncilLabel")}</dt>
            <dd className="m-0 text-text-main">{readiness.data?.council.count ?? 0}</dd>
          </div>
          <div className="flex flex-wrap gap-x-2">
            <dt className="text-text-muted">{t("summaryPersonsLabel")}</dt>
            <dd className="m-0 text-text-main">{readiness.data?.persons.count ?? 0}</dd>
          </div>
          <div className="flex flex-wrap gap-x-2">
            <dt className="text-text-muted">{t("summaryBranchesLabel")}</dt>
            <dd className="m-0 text-text-main">{readiness.data?.branches.count ?? 0}</dd>
          </div>
          <div className="flex flex-wrap gap-x-2">
            <dt className="text-text-muted">{t("summaryInvitesLabel")}</dt>
            <dd className="m-0 text-text-main">{readiness.data?.inviteCodes.active ?? 0}</dd>
          </div>
        </dl>
      )}

      <Alert
        type="warning"
        showIcon
        message={t("pendingCouncilTitle")}
        description={
          <div className="text-than">
            <p className="m-0 mb-2">{t("pendingCouncilIntro")}</p>
            <ul className="m-0 list-disc space-y-1 pl-5">
              {pendingCouncilKeys.map((key) => (
                <li key={key}>{t(`pendingCouncil.${key}`)}</li>
              ))}
            </ul>
          </div>
        }
      />

      <Link
        href="/"
        className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-4 text-than font-medium no-underline"
        style={{ backgroundColor: colorVars.primary, color: colorVars.bgCard }}
      >
        {t("openHome")}
      </Link>
    </section>
  );
}

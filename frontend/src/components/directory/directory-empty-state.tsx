"use client";

import { Button } from "antd";
import { TeamOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";

export interface DirectoryEmptyStateProps {
  /** Rỗng vì bộ lọc, hay rỗng vì chưa ai chọn hiện. Hai chuyện khác hẳn nhau. */
  reason: "no-one-shared" | "filtered-out";
  onClearFilters?: () => void;
  selfPersonId?: string | null;
}

/**
 * Danh bạ rỗng — và **không được trông như lỗi**.
 *
 * <h2>Hai lý do rỗng, hai màn hình khác nhau</h2>
 * Gộp chúng lại là cách nhanh nhất để nói sai. "Chưa ai chọn hiện" là một sự
 * thật về dòng họ và là một lời mời; "không ai khớp bộ lọc" là một sự thật về
 * ba cái ô người dùng vừa bấm, và lối ra của nó là bỏ bớt một ô.
 *
 * <h2>Không dùng màu lỗi, không dùng biểu tượng cảnh báo</h2>
 * Cùng lý do với khối hổ phách "xin đăng nhập để xem phả đồ": đây là hệ thống
 * chạy đúng luật, không phải sự cố. Một dải đỏ ở đây sẽ dạy người dùng rằng
 * quyền riêng tư của người khác là một trục trặc.
 */
export function DirectoryEmptyState({
  reason,
  onClearFilters,
  selfPersonId,
}: DirectoryEmptyStateProps) {
  const t = useTranslations("directory");

  return (
    <section
      data-directory-empty={reason}
      className="rounded-lg border border-dashed px-4 py-8 text-center sm:px-6"
      style={{ borderColor: colorVars.borderDark, background: colorVars.bgCard }}
    >
      <TeamOutlined
        aria-hidden
        className="text-2xl"
        style={{ color: colorVars.accentText }}
      />
      <h2 className="m-0 mt-2 font-serif text-de font-semibold text-text-main">
        {reason === "filtered-out" ? t("empty.filteredTitle") : t("empty.noOneTitle")}
      </h2>
      <p className="mx-auto m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
        {reason === "filtered-out" ? t("empty.filteredBody") : t("empty.noOneBody")}
      </p>

      <div className="mt-4 flex flex-wrap justify-center gap-3">
        {reason === "filtered-out" && onClearFilters && (
          <Button onClick={onClearFilters}>{t("empty.clearFilters")}</Button>
        )}
        {reason === "no-one-shared" && selfPersonId && (
          <Link href={`/persons/${selfPersonId}`} className="inline-flex">
            <Button type="primary">{t("empty.beFirst")}</Button>
          </Link>
        )}
      </div>
    </section>
  );
}

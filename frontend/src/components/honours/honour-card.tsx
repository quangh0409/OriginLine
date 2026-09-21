"use client";

import { Tag } from "antd";
import { useFormatter, useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { HonourDto } from "@/lib/api/honours";

const KIND_COLOR: Record<HonourDto["kind"], string> = {
  DO_DAT: "gold",
  CHUC_TUOC: "geekblue",
  THANH_TICH: "green",
  KHEN_THUONG: "purple",
};

/** Chung máy trạng thái với bài viết: `PENDING → PUBLISHED` (duyệt) hoặc `→ WITHDRAWN` (từ chối). */
const STATUS_COLOR: Record<HonourDto["status"], string> = {
  PENDING: "gold",
  PUBLISHED: "green",
  WITHDRAWN: "red",
};

/**
 * Một bản ghi vinh danh. `personDisplayName` có thể vắng mặt (người còn sống
 * chưa mở nhóm trường thứ sáu) — CHỈ đọc qua `isPresent`, và khi vắng mặt thì
 * **không vẽ gì thay vào chỗ đó**, đúng luật "vắng mặt là câu trả lời cuối,
 * không phải chỗ trống cần lấp" (`src/lib/privacy/present.ts`). Điều đó tự
 * nhiên vẫn để lộ tiêu đề/loại/năm — bản ghi vốn đã hiện ra nghĩa là các
 * trường ấy đã qua lọc; không tên không phải là ẩn thêm, chỉ là bớt một dòng.
 *
 * `reviewedByDisplayName` cùng luật: `null` ⇒ chỉ vẽ ngày, không vẽ tên, và
 * đó có thể là vì người duyệt không gắn nhân khẩu (Admin) hoặc vì người đọc
 * không được thấy người còn sống ấy (khách) — không phân biệt hai ca này ra
 * màn hình, giống hệt `PostReviewMeta`.
 */
export function HonourCard({ honour, children }: { honour: HonourDto; children?: React.ReactNode }) {
  const t = useTranslations("honours");
  const format = useFormatter();

  return (
    <article className="rounded-lg border border-border bg-bg-card px-4 py-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Tag color={KIND_COLOR[honour.kind]} className="!m-0 !text-than" style={{ color: colorVars.textMain }}>
            {t(`kind.${honour.kind}`)}
          </Tag>
          {honour.status !== "PUBLISHED" && (
            <Tag color={STATUS_COLOR[honour.status]} className="!m-0 !text-than" style={{ color: colorVars.textMain }}>
              {t(`status.${honour.status}`)}
            </Tag>
          )}
        </div>
        {isPresent(honour.year) && <span className="text-than text-text-muted">{honour.year}</span>}
      </div>

      <h3 className="m-0 mt-1 font-serif text-[16px] font-semibold leading-snug text-text-main">
        {honour.title}
      </h3>

      {isPresent(honour.personDisplayName) && (
        <p className="m-0 mt-1 text-than text-text-main">{t("card.person", { name: honour.personDisplayName })}</p>
      )}
      {isPresent(honour.issuer) && (
        <p className="m-0 mt-0.5 text-than text-text-muted">{t("card.issuer", { issuer: honour.issuer })}</p>
      )}
      {isPresent(honour.description) && (
        <p className="m-0 mt-2 max-w-prose text-dan leading-relaxed text-text-main">{honour.description}</p>
      )}
      {isPresent(honour.reviewedAt) && (
        <p className="m-0 mt-1 text-than text-text-muted">
          {isPresent(honour.reviewedByDisplayName)
            ? t("card.reviewedAtBy", {
                name: honour.reviewedByDisplayName,
                date: format.dateTime(new Date(honour.reviewedAt), { dateStyle: "medium" }),
              })
            : t("card.reviewedAt", {
                date: format.dateTime(new Date(honour.reviewedAt), { dateStyle: "medium" }),
              })}
        </p>
      )}

      {children && <div className="mt-3 flex flex-wrap gap-2">{children}</div>}
    </article>
  );
}

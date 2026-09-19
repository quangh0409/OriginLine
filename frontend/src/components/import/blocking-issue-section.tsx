"use client";

import { useTranslations } from "next-intl";
import { Button } from "antd";
import { CheckCircleOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import { IssueRow } from "./issue-row";
import { IssuesWorkbookButton } from "./issues-workbook-button";
import type { ImportIssue } from "@/lib/api/data-import";

export interface BlockingIssueSectionProps {
  issues: readonly ImportIssue[];
  /** Số lỗi chặn **máy chủ đếm**. Có thể khác `issues.length` nếu danh sách bị lọc. */
  count: number;
  batchId: string;
  /**
   * Đặt nút tải bản Excel ở khối này.
   *
   * Cả màn đối soát chỉ có **một** nút ấy — tệp có đủ hai nhóm trên hai trang,
   * và endpoint không nhận `severity`. Màn đối soát quyết định nó đứng cạnh
   * bảng nào đang là việc chính; xem {@link IssuesWorkbookButton}.
   */
  showWorkbookButton: boolean;
  onReupload: () => void;
  onRevalidate: () => void;
  revalidating: boolean;
}

/**
 * Nhóm **lỗi phải sửa** — nhóm chặn.
 *
 * <h2>Vì sao đây là một khối riêng, có viền riêng, có tiêu đề riêng</h2>
 * Bộ kiểm sinh ra hai loại kết quả khác nhau về **hệ quả**, không khác nhau về
 * mức độ nghiêm trọng: loại này bắt buộc phải sửa trong tệp rồi tải lại; loại
 * kia vẫn nhập được. Gộp chúng vào một danh sách có cột "mức độ" là cách trình
 * bày đúng về dữ liệu và sai về con người:
 *
 * <ul>
 *   <li>"17 vấn đề" nghe như hỏng cả tệp, và phản ứng tự nhiên là bỏ cuộc hoặc
 *       bấm bừa cho qua.</li>
 *   <li>"6 lỗi phải sửa" nghe như một việc làm được trong một buổi — và nó đúng
 *       là như vậy.</li>
 * </ul>
 *
 * <h2>Con số lấy từ máy chủ, không đếm lại mảng</h2>
 * `count` là `batch.blockingCount`. Đếm `issues.length` ở client sẽ lệch ngay
 * khi danh sách được lọc hoặc phân trang, và một con số lệch trên màn hình này
 * là con số phá vỡ lòng tin của Trưởng chi vào cả bộ kiểm.
 *
 * <h2>Hai lối ra, và lối ra chính không nằm trên trang web này</h2>
 * Sửa trong Excel rồi tải lại — Trưởng chi 45–65 tuổi đã quen tay ở đó. "Kiểm
 * lại" đứng cạnh cho trường hợp tệp chưa đổi mà chỉ muốn chạy lại bộ kiểm.
 */
export function BlockingIssueSection({
  issues,
  count,
  batchId,
  showWorkbookButton,
  onReupload,
  onRevalidate,
  revalidating,
}: BlockingIssueSectionProps) {
  const t = useTranslations("dataImport.reconcile");

  if (count === 0) {
    return (
      <section
        className="rounded-lg border px-4 py-3"
        style={{ borderColor: colorVars.successText, backgroundColor: colorVars.successBg }}
      >
        <h2
          className="m-0 flex items-center gap-2 font-serif text-de font-bold"
          style={{ color: colorVars.successText }}
        >
          <CheckCircleOutlined aria-hidden />
          {t("blocking.none")}
        </h2>
      </section>
    );
  }

  return (
    <section
      className="rounded-lg border-2 px-4 py-3"
      style={{ borderColor: colorVars.danger, backgroundColor: colorVars.dangerBg }}
      aria-labelledby="import-blocking-heading"
    >
      <h2
        id="import-blocking-heading"
        className="m-0 font-serif text-de font-bold"
        style={{ color: colorVars.danger }}
      >
        {t("blocking.title", { count })}
      </h2>
      <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
        {t("blocking.help")}
      </p>

      <ul
        className="m-0 mt-3 list-none rounded-lg border px-3 py-1"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      >
        {/* Khoá là `issue.id`, KHÔNG phải bộ ghép (sheet, rowNo, code, field):
            bộ ấy không duy nhất — hai luật vẫn có thể cùng trỏ vào một ô — nên
            bảng lỗi sẽ nhảy chỗ mỗi lần kiểm lại, đúng lúc người nhập đang dò
            theo nó để sửa tệp. */}
        {issues.map((issue) => (
          <IssueRow key={issue.id} issue={issue} />
        ))}
      </ul>

      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        <Button
          type="primary"
          danger
          size="large"
          className="min-h-[44px] min-w-[44px]"
          onClick={onReupload}
        >
          {t("actions.reupload")}
        </Button>
        <Button
          size="large"
          className="min-h-[44px] min-w-[44px]"
          loading={revalidating}
          onClick={onRevalidate}
        >
          {t("actions.revalidate")}
        </Button>
      </div>
      <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
        {t("actions.reuploadHint")}
      </p>

      {/* NGAY CẠNH BẢNG LỖI. Bản Excel là lối ra tự nhiên nhất cho người quen
          tay với Excel; giấu nó trong một menu phụ là để đường thoát tồn tại mà
          không ai tìm thấy. */}
      {showWorkbookButton && (
        <div className="mt-3">
          <IssuesWorkbookButton batchId={batchId} />
        </div>
      )}
    </section>
  );
}

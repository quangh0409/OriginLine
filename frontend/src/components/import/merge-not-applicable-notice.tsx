"use client";

import { useTranslations } from "next-intl";
import { StopOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import type { ImportIssue } from "@/lib/api/data-import";

export interface MergeNotApplicableNoticeProps {
  issue: ImportIssue;
}

/**
 * Ca **từ chối hợp nhất** — `IMP_MERGE_NOT_APPLICABLE`.
 *
 * <h2>Vì sao ca này đáng một khối riêng thay vì một dòng trong bảng lỗi</h2>
 * Nó là lỗi chặn **duy nhất** không sinh ra lúc đọc tệp mà sinh ra vì một cú
 * bấm của chính người đang đứng trước màn hình — và nó xuất hiện ở màn đối
 * chiếu, nơi bảng lỗi không có mặt. Để nó chỉ nằm im trong `GET /issues` nghĩa
 * là người vừa bấm "hợp nhất" thấy lô dừng lại mà không biết vì sao.
 *
 * <h2>Câu chữ phải nói được RANH GIỚI, không chỉ nói "không được"</h2>
 * Người dùng vừa làm một việc hợp lý: hai hồ sơ trong phả **đúng là** một người
 * thật, và họ muốn gộp. Câu trả lời đúng không phải "sai rồi" mà là "**đúng
 * việc, nhầm chỗ**": hợp nhất hai hồ sơ *đã nằm trong phả* là thao tác của màn
 * quản lý nhân khẩu, nơi có đủ lịch sử sửa đổi, đủ quyền trên cả hai chi, và đủ
 * ngữ cảnh để gộp hai node của phả đồ. Màn nhập liệu chỉ biết một tệp Excel và
 * một khu vực chờ; cho nó quyền gộp hai node là cho nó quyền sửa phả bằng một
 * lối đi không ai kiểm.
 *
 * Nếu khối này chỉ nói "không hợp nhất được", người dùng sẽ thử lại ba lần rồi
 * kết luận phần mềm hỏng.
 *
 * <h2>Ba mảnh, theo đúng thứ tự người đọc cần</h2>
 * Chuyện gì đã xảy ra → vì sao việc này không thuộc màn này → **làm gì tiếp**.
 * Mảnh thứ ba là mảnh duy nhất khiến người ta rời khỏi màn hình mà đi làm được
 * việc; hai mảnh đầu không có nó thì chỉ là một lời xin lỗi dài.
 */
export function MergeNotApplicableNotice({ issue }: MergeNotApplicableNoticeProps) {
  const t = useTranslations("dataImport.duplicates.mergeNotApplicable");
  const ti = useTranslations("dataImport.reconcile.blocking");
  const ts = useTranslations("dataImport.sheet");

  return (
    <section
      role="alert"
      data-testid="merge-not-applicable"
      className="rounded-lg border-2 px-4 py-3"
      style={{ borderColor: colorVars.danger, backgroundColor: colorVars.dangerBg }}
      aria-labelledby={`merge-na-${issue.id}`}
    >
      <h2
        id={`merge-na-${issue.id}`}
        className="m-0 flex items-center gap-2 font-serif text-de font-bold"
        style={{ color: colorVars.danger }}
      >
        {/* Biểu tượng LUÔN kèm chữ. */}
        <StopOutlined aria-hidden />
        {t("title")}
      </h2>

      {/* Toạ độ về đúng dòng trong tệp gốc — cùng bộ mà bảng lỗi dùng. */}
      <p className="m-0 mt-2 flex flex-wrap items-baseline gap-x-2 text-than">
        <span
          className="rounded px-2 py-0.5 font-semibold"
          style={{ backgroundColor: colorVars.bgCard, color: colorVars.textMain }}
        >
          {issue.rowNo === undefined
            ? ti("wholeBatch")
            : ti("sheetRow", { sheet: ts(issue.sheet), row: issue.rowNo })}
        </span>
        {issue.externalCode && (
          <span className="break-all font-mono" style={{ color: colorVars.textMain }}>
            {issue.externalCode}
          </span>
        )}
      </p>

      <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMain }}>
        {t("body")}
      </p>
      {/* RANH GIỚI: việc này có thật, nhưng nó ở màn khác. */}
      <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMain }}>
        {t("scope")}
      </p>
      <p className="m-0 mt-2 text-than font-medium" style={{ color: colorVars.textMain }}>
        {t("whatToDo")}
      </p>
      <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
        {t("batchStopped")}
      </p>

      {/* Câu của máy chủ, chỉ có tiếng Việt: nó được soạn lúc kiểm rồi lưu vào
          `import_issue.message`, nên nó không đổi theo `Accept-Language`. */}
      <p className="m-0 mt-2 text-than" lang="vi" style={{ color: colorVars.textMuted }}>
        {issue.message}
      </p>
    </section>
  );
}

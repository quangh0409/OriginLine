"use client";

import { useTranslations } from "next-intl";
import { ArrowRightOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import type { ImportIssue } from "@/lib/api/data-import";

export interface IssueRowProps {
  issue: ImportIssue;
}

/**
 * Một dòng lỗi hoặc cảnh báo.
 *
 * <h2>Ba mảnh dữ kiện, theo đúng thứ tự người nhập cần</h2>
 * <ol>
 *   <li><b>Chỗ phải sửa</b> — trang nào, dòng nào, cột nào. Thứ duy nhất đưa họ
 *       về đúng ô trong Excel. Đứng đầu, không nằm cuối câu.</li>
 *   <li><b>Câu tiếng Việt</b> do máy chủ soạn. Giao diện không viết lại theo mã
 *       lỗi: máy chủ có dữ liệu thật (tên cột, giá trị gõ sai) mà client không
 *       có.</li>
 *   <li><b>Phần dữ kiện có cấu trúc</b> — và đây là chỗ một dòng lỗi biến thành
 *       một cú sửa.</li>
 * </ol>
 *
 * <h2>`sheet === "LO"` là lỗi của CẢ LÔ, và nó không có số dòng</h2>
 * Hình dạng này rất dễ quên, và quên nó thì màn hình in ra "Dòng undefined" —
 * đúng loại chi tiết làm người nhập mất tin vào cả bộ kiểm. Ở đây nó ra một
 * nhãn riêng: "Cả lô".
 *
 * <h2>Câu lỗi chỉ có tiếng Việt, và ta nói ra điều đó với máy đọc màn hình</h2>
 * `message` được soạn *lúc kiểm* rồi lưu vào `import_issue.message`, nên nó
 * không đổi theo `Accept-Language`. Bọc `lang="vi"` để trình đọc màn hình của
 * một người đang xem bản tiếng Anh không đọc tiếng Việt bằng giọng Anh — thay
 * vì giả vờ rằng câu ấy đã được dịch.
 *
 * <h2>Khoá `context` giữ nguyên tên tiếng Việt của CSDL</h2>
 * `goiY` · `chuoi` · `cacDong` · `doiKhai`/`doiSuyRa` · `maKhongTimThay`. Đổi
 * tên ở tầng API nghĩa là bản xuất Excel và giao diện nói hai thứ tiếng khác
 * nhau về cùng một dữ kiện.
 */
export function IssueRow({ issue }: IssueRowProps) {
  const t = useTranslations("dataImport");
  const context = issue.context ?? {};
  const suggestions = context.goiY ?? [];
  const cycle = context.chuoi ?? [];
  const conflictingRows = context.cacDong ?? [];

  return (
    <li className="border-t py-3 first:border-t-0" style={{ borderColor: colorVars.border }}>
      <p className="m-0 flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <span
          className="rounded px-2 py-0.5 text-than font-semibold"
          style={{ backgroundColor: colorVars.bgPage, color: colorVars.textMain }}
        >
          {issue.rowNo === undefined
            ? t("reconcile.blocking.wholeBatch")
            : t("reconcile.blocking.sheetRow", {
                sheet: t(`sheet.${issue.sheet}`),
                row: issue.rowNo,
              })}
        </span>
        {issue.field && (
          <span className="text-than" style={{ color: colorVars.textMuted }}>
            {issue.field}
          </span>
        )}
        {issue.externalCode && (
          <span className="break-all font-mono text-than" style={{ color: colorVars.textMuted }}>
            {issue.externalCode}
          </span>
        )}
        <span className="text-than font-semibold" style={{ color: colorVars.textMain }}>
          {t(`issue.${issue.code}`)}
        </span>
      </p>

      <p className="m-0 mt-1 text-than" lang="vi" style={{ color: colorVars.textMain }}>
        {issue.message}
      </p>

      {context.maKhongTimThay && (
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
          {t("reconcile.blocking.notFoundCode")}:{" "}
          <span className="break-all font-mono" style={{ color: colorVars.textMain }}>
            {context.maKhongTimThay}
          </span>
        </p>
      )}

      {suggestions.length > 0 && (
        <p className="m-0 mt-2 flex flex-wrap items-center gap-2 text-than">
          {/* Gợi ý do MÁY CHỦ tính: nó thấy cả tệp lẫn `person_external_ref`.
              Giao diện không tự dò — nó chỉ thấy phần dữ liệu đã tải về nên sẽ
              gợi ý sai đúng vào lúc người dùng tin nhất, và một gợi ý sai ở đây
              dẫn thẳng tới một quan hệ cha–con sai trong phả. */}
          <span style={{ color: colorVars.textMuted }}>{t("reconcile.blocking.suggestions")}</span>
          {suggestions.map((code) => (
            <span
              key={code}
              // Phông đơn cách để phân biệt chữ O với số 0 — đúng cặp ký tự sinh
              // ra lỗi này nhiều nhất khi chép từ sổ giấy.
              className="break-all rounded border px-2 py-0.5 font-mono text-than"
              style={{
                borderColor: colorVars.borderDark,
                backgroundColor: colorVars.bgCard,
                color: colorVars.textMain,
              }}
            >
              {code}
            </span>
          ))}
        </p>
      )}

      {cycle.length > 0 && (
        <p className="m-0 mt-2 flex flex-wrap items-center gap-1 text-than">
          <span className="mr-1" style={{ color: colorVars.textMuted }}>
            {t("reconcile.blocking.cycle")}
          </span>
          {cycle.map((code, index) => (
            <span key={`${code}-${index}`} className="flex items-center gap-1">
              <span className="break-all font-mono" style={{ color: colorVars.textMain }}>
                {code}
              </span>
              {index < cycle.length - 1 && (
                <ArrowRightOutlined aria-hidden style={{ color: colorVars.textMuted }} />
              )}
            </span>
          ))}
        </p>
      )}

      {(context.doiKhai !== undefined || context.doiSuyRa !== undefined) && (
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
          {context.doiKhai !== undefined && (
            <>
              {t("reconcile.blocking.declared")}: <b>{context.doiKhai}</b>
            </>
          )}
          {context.doiKhai !== undefined && context.doiSuyRa !== undefined && " · "}
          {context.doiSuyRa !== undefined && (
            <>
              {t("reconcile.blocking.derived")}: <b>{context.doiSuyRa}</b>
            </>
          )}
        </p>
      )}

      {/* `cacDong` là một MẢNG: một mã trùng có thể dính ba dòng, và chỉ ra một
          dòng thì người nhập sửa xong vẫn thấy y nguyên lỗi. */}
      {conflictingRows.length > 0 && (
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
          {t("reconcile.blocking.conflictRows", {
            rows: conflictingRows.join(", "),
            count: conflictingRows.length,
          })}
        </p>
      )}
    </li>
  );
}

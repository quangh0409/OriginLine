"use client";

import { useTranslations, useFormatter } from "next-intl";
import { Checkbox } from "antd";
import { CheckCircleOutlined } from "@ant-design/icons";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { IssueRow } from "./issue-row";
import { IssuesWorkbookButton } from "./issues-workbook-button";
import type { ImportIssue } from "@/lib/api/data-import";

export interface WarningIssueSectionProps {
  issues: readonly ImportIssue[];
  /** Số cảnh báo **máy chủ đếm** — không đếm lại mảng. */
  count: number;
  batchId: string;
  /** Số **cặp** nghi trùng, không phải số dòng cảnh báo. */
  suspectDuplicateCount: number;
  /**
   * Lúc có người tick — **sự thật lịch sử**, không phải câu trả lời cho "đã xác
   * nhận chưa". Xem chú thích của thành phần.
   */
  acknowledgedAt?: string;
  /**
   * `batch.canApprove` — câu trả lời **duy nhất** cho việc cửa duyệt có mở
   * không, và là thứ duy nhất nói được rằng xác nhận còn hiệu lực.
   */
  canApprove: boolean;
  /**
   * Đặt nút tải bản Excel ở khối này — chỉ khi **không còn lỗi chặn**.
   *
   * Cả màn chỉ có một nút ấy. Khi còn lỗi chặn thì nó đứng cạnh bảng lỗi chặn,
   * vì đó là bảng đang là việc chính; khi hết lỗi chặn thì bảng này là bảng duy
   * nhất còn lại, và đường thoát bằng Excel vẫn phải ở trong tầm mắt.
   */
  showWorkbookButton: boolean;
  onAcknowledge: () => void;
  acknowledging: boolean;
}

/**
 * Nhóm **cần xem lại** — nhóm không chặn.
 *
 * <h2>Khác nhóm chặn ở đúng một điểm, và nói ra điểm ấy</h2>
 * Những mục này vẫn nhập được. Câu mở đầu nói thẳng điều đó, vì người nhập đang
 * sợ: không nói thì họ sẽ hiểu mọi thứ có màu cảnh báo là một thứ phải sửa, rồi
 * đi tìm cách sửa những việc không sửa được (một cụ mất năm 1943 mà cả họ không
 * còn ai nhớ ngày giỗ thì không có gì để sửa cả — chỉ có gì đó để ghi nhận).
 *
 * <h2>Ô xác nhận là một cái cửa, không phải một thủ tục</h2>
 * `IMP_MISSING_GIO` là cảnh báo đáng giá nhất trong nhóm: người thiếu ngày giỗ
 * vẫn nằm trong phả đồ nhưng sẽ **không bao giờ được nhắc giỗ** — mà nhắc giỗ
 * là lý do dòng họ mở ứng dụng này. Bắt người nhập tick "tôi đã xem" là cách
 * duy nhất bảo đảm con số ấy được một người thật nhìn qua.
 *
 * <h2>Xác nhận là một LỜI KHAI, và lời khai có thể hết hiệu lực</h2>
 * Máy chủ ghi lại ai tick, lúc nào, và **vân tay của tập cảnh báo** người ấy đã
 * đọc. Kiểm lại lô mà sinh cảnh báo **mới** thì xác nhận cũ hết hiệu lực —
 * nhưng `warningsAcknowledgedAt` **vẫn còn nguyên**, vì đã có một người thật
 * bấm nút và đó là sự thật lịch sử.
 *
 * Hệ quả cho màn hình, và đây là chỗ dễ làm sai nhất của cả nhánh: <b>dấu thời
 * gian KHÔNG được dùng để kết luận "đã xác nhận xong"</b>. Nếu dùng, người
 * duyệt sẽ thấy một dòng "đã xác nhận lúc 21:14" màu xanh trong khi nút duyệt
 * vẫn xám, không hiểu vì sao, và cuối cùng gọi điện cho đội kỹ thuật.
 *
 * Vì vậy ở đây dấu thời gian hiện ra như **lịch sử**, còn việc "còn phải làm
 * gì" đọc từ `canApprove`: cửa chưa mở thì ô tick vẫn còn đó để tick lại, kèm
 * một câu nói rõ vì sao lại phải tick lần nữa.
 */
export function WarningIssueSection({
  issues,
  count,
  batchId,
  suspectDuplicateCount,
  acknowledgedAt,
  canApprove,
  showWorkbookButton,
  onAcknowledge,
  acknowledging,
}: WarningIssueSectionProps) {
  const t = useTranslations("dataImport.reconcile");
  const format = useFormatter();

  if (count === 0) {
    return (
      <section
        className="rounded-lg border px-4 py-3"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      >
        <h2
          className="m-0 flex items-center gap-2 font-serif text-de font-bold"
          style={{ color: colorVars.textMain }}
        >
          <CheckCircleOutlined aria-hidden style={{ color: colorVars.successText }} />
          {t("warning.none")}
        </h2>
      </section>
    );
  }

  return (
    <section
      className="rounded-lg border px-4 py-3"
      style={{ borderColor: colorVars.accent, backgroundColor: colorVars.warningBg }}
      aria-labelledby="import-warning-heading"
    >
      <h2
        id="import-warning-heading"
        className="m-0 font-serif text-de font-bold"
        style={{ color: colorVars.accentText }}
      >
        {t("warning.title", { count })}
      </h2>
      <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
        {t("warning.help")}
      </p>

      {suspectDuplicateCount > 0 && (
        <p className="m-0 mt-2 text-than">
          <Link
            href={`/nhap-lieu/${batchId}/trung`}
            className="inline-flex min-h-[44px] items-center underline"
            style={{ color: colorVars.primary }}
          >
            {/* "cặp", không phải "dòng": một dòng có thể bị nghi trùng với
                nhiều hồ sơ, và người đối chiếu quyết từng cặp một. */}
            {t("warning.duplicatesLink", { count: suspectDuplicateCount })}
          </Link>
        </p>
      )}

      <ul
        className="m-0 mt-3 list-none rounded-lg border px-3 py-1"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      >
        {issues.map((issue) => (
          <IssueRow key={issue.id} issue={issue} />
        ))}
      </ul>

      {/* NGAY CẠNH BẢNG — cùng lý do như ở khối lỗi chặn. */}
      {showWorkbookButton && (
        <div className="mt-3">
          <IssuesWorkbookButton batchId={batchId} />
        </div>
      )}

      <div className="mt-3">
        {/* LỊCH SỬ: đã có người bấm, và điều đó đúng mãi mãi. */}
        {acknowledgedAt && (
          <p
            className="m-0 flex min-h-[44px] items-center gap-2 text-than font-medium"
            style={{ color: canApprove ? colorVars.successText : colorVars.textMuted }}
          >
            <CheckCircleOutlined aria-hidden />
            {t("warning.acked", {
              when: format.dateTime(new Date(acknowledgedAt), {
                dateStyle: "short",
                timeStyle: "short",
              }),
            })}
          </p>
        )}

        {/* VIỆC CÒN PHẢI LÀM: đọc từ `canApprove`, không đọc từ dấu thời gian. */}
        {!canApprove && (
          <>
            {acknowledgedAt && (
              <p className="m-0 mb-1 text-than" style={{ color: colorVars.textMain }}>
                {t("warning.mayBeStale")}
              </p>
            )}
            <Checkbox
              className="flex min-h-[44px] items-center text-than"
              disabled={acknowledging}
              onChange={(event) => {
                if (event.target.checked) onAcknowledge();
              }}
            >
              <span className="text-than" style={{ color: colorVars.textMain }}>
                {acknowledgedAt ? t("warning.ackAgain", { count }) : t("warning.ack", { count })}
              </span>
            </Checkbox>
          </>
        )}
      </div>
    </section>
  );
}

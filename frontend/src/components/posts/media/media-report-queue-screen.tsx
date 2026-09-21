"use client";

import { useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Alert, Button, Empty, Input, Skeleton } from "antd";
import { CheckOutlined, WarningOutlined } from "@ant-design/icons";
import { useMe } from "@/hooks/use-me";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { ApiError } from "@/lib/api/http";
import type { MediaReportDto } from "@/lib/api/media";
import { useDismissReport, useMediaReports, useTakedownReport } from "../queries";

/**
 * Màn duyệt **báo cáo ảnh/video** — `/quan-ly/bao-cao-anh`, phạm vi chi.
 *
 * <h2>Vì sao màn này PHẢI có, không phải "nên có"</h2>
 * Quyết định "ảnh đi theo quyền của bài" (chỉ tác giả/người duyệt sửa được
 * tệp đính kèm, người xem thường chỉ báo cáo) chỉ an toàn chừng nào có ai đó
 * THẬT SỰ xem báo cáo và hành động. Không có màn này, `MediaReportButton` chỉ
 * là một cái hộp thư không ai đọc.
 *
 * <h2>"Gỡ" là xoá byte ngay lập tức — KHÔNG có nút hoàn tác</h2>
 * Backend xoá trong cùng transaction, không có thời gian ân hạn. Vì vậy nút
 * gỡ không bấm-là-xong: bấm lần một MỞ một khối cảnh báo tường minh + ô ghi
 * chú, bấm "Xác nhận gỡ" mới thật sự gọi API — cùng khuôn với
 * `PostReviewQueueScreen`'s "Trả lại" (mở ô lý do trước, không âm thầm gửi).
 *
 * <h2>`mediaUrl` đã ký — người duyệt thấy ĐÚNG thứ đang bị báo</h2>
 * Không dẫn người duyệt sang xem cả bài rồi tự tìm ảnh; họ cần thấy chính xác
 * tệp bị báo cáo để quyết định nhanh và đúng.
 */
export function MediaReportQueueScreen() {
  const t = useTranslations("posts.mediaReportQueue");
  const me = useMe();
  const reportsQuery = useMediaReports();

  const roleCanReview =
    me.data?.role === "ADMIN" || me.data?.role === "COUNCIL" || me.data?.role === "BRANCH_HEAD";

  if (me.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (!me.data?.appUserId) return <Alert type="info" showIcon message={t("errors.notProvisioned")} />;
  if (!roleCanReview) return <Alert type="info" showIcon message={t("errors.noReviewRight")} />;

  if (reportsQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (reportsQuery.isError) return <Alert type="error" showIcon message={t("errors.loadFailed")} />;

  const reports = reportsQuery.data ?? [];
  if (reports.length === 0) {
    return <Empty description={<span style={{ color: colorVars.textMuted }}>{t("empty")}</span>} />;
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="m-0 text-than text-text-muted">{t("scopeNote", { n: reports.length })}</p>
      {reports.map((report) => (
        <ReportCard key={report.id} report={report} />
      ))}
    </div>
  );
}

function ReportCard({ report }: { report: MediaReportDto }) {
  const t = useTranslations("posts.mediaReportQueue");
  const format = useFormatter();
  const takedown = useTakedownReport();
  const dismiss = useDismissReport();

  const [confirmingTakedown, setConfirmingTakedown] = useState(false);
  const [note, setNote] = useState("");

  const busy = takedown.isPending || dismiss.isPending;
  const error = takedown.error ?? dismiss.error;

  return (
    <article
      className="flex flex-col gap-3 rounded-lg border px-4 py-4 sm:flex-row"
      style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
    >
      <div
        className="flex aspect-square w-full shrink-0 items-center justify-center overflow-hidden rounded-md sm:w-40"
        style={{ backgroundColor: colorVars.bgPage }}
      >
        {report.mediaKind === "IMAGE" ? (
          // eslint-disable-next-line @next/next/no-img-element -- URL đã ký, xem trước để duyệt
          <img src={report.mediaUrl} alt="" className="h-full w-full object-cover" />
        ) : (
          <video src={report.mediaUrl} controls preload="metadata" className="h-full w-full object-cover" />
        )}
      </div>

      <div className="min-w-0 flex-1 space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Link href={`/bai-viet/${report.postId}`} className="font-serif text-[16px] font-semibold text-primary">
            {report.postTitle}
          </Link>
          <span className="text-than text-text-muted">
            {t("reportedAt", { date: format.dateTime(new Date(report.createdAt), { dateStyle: "medium", timeStyle: "short" }) })}
          </span>
        </div>

        <p className="m-0 text-than" style={{ color: colorVars.textMain }}>
          <strong>{t(`reasons.${report.reason}`)}</strong>
          {report.reporterDisplayName ? t("byReporter", { name: report.reporterDisplayName }) : ""}
        </p>
        {report.note && (
          <p className="m-0 max-w-prose text-dan leading-relaxed text-text-main">{report.note}</p>
        )}

        {error && (
          <Alert type="error" showIcon message={describeMediaReportError(error, t)} />
        )}

        {confirmingTakedown ? (
          <div
            className="space-y-2 rounded-md border px-3 py-3"
            style={{ borderColor: colorVars.danger, backgroundColor: colorVars.dangerBg }}
          >
            <p className="m-0 flex items-center gap-2 text-than font-semibold" style={{ color: colorVars.danger }}>
              <WarningOutlined aria-hidden />
              {t("takedownWarning")}
            </p>
            <label htmlFor={`takedown-note-${report.id}`} className="block text-than font-medium">
              {t("noteLabel")}
            </label>
            <Input.TextArea
              id={`takedown-note-${report.id}`}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              autoSize={{ minRows: 2, maxRows: 4 }}
            />
            <div className="flex flex-wrap gap-2">
              <Button
                danger
                type="primary"
                loading={takedown.isPending}
                onClick={() => takedown.mutate({ reportId: report.id, note: note.trim() || undefined })}
              >
                {t("confirmTakedown")}
              </Button>
              <Button onClick={() => setConfirmingTakedown(false)} disabled={takedown.isPending}>
                {t("cancel")}
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            <Button danger loading={busy} disabled={busy} onClick={() => setConfirmingTakedown(true)}>
              {t("takedownAction")}
            </Button>
            <Button
              icon={<CheckOutlined aria-hidden />}
              loading={dismiss.isPending}
              disabled={busy}
              onClick={() => dismiss.mutate({ reportId: report.id })}
            >
              {t("dismissAction")}
            </Button>
          </div>
        )}
      </div>
    </article>
  );
}

function describeMediaReportError(error: unknown, t: ReturnType<typeof useTranslations>): string {
  if (error instanceof ApiError) {
    if (error.code === "FORBIDDEN" || error.code === "BRANCH_SCOPE_VIOLATION") return t("errors.forbidden");
    if (error.code === "NOT_FOUND") return t("errors.alreadyHandled");
  }
  return t("errors.actionFailed");
}

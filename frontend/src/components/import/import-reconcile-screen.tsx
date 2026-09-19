"use client";

import { useMemo, useState } from "react";
import { useTranslations, useFormatter } from "next-intl";
import { Alert, App, Progress, Skeleton } from "antd";
import { CheckCircleOutlined } from "@ant-design/icons";
import { Link, useRouter } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import {
  useAcknowledgeWarnings,
  useCommitBatch,
  useCommitProgress,
  useImportBatch,
  useImportIssues,
  useRevalidateBatch,
  useRollbackBatch,
} from "./hooks/use-data-import";
import { useImportProblemMessage } from "./problem-message";
import { NotWrittenYetBanner } from "./not-written-yet-banner";
import { BlockingIssueSection } from "./blocking-issue-section";
import { WarningIssueSection } from "./warning-issue-section";
import { StagedRowsPreview } from "./staged-rows-preview";
import { CommitBar } from "./commit-bar";
import { RollbackButton, RollbackDialog } from "./rollback-dialog";
import { ImportSteps } from "./import-steps";

export interface ImportReconcileScreenProps {
  batchId: string;
}

/**
 * Màn 3 — **tải lên và đối soát**. Màn quyết định thành bại của cả đợt nhập liệu.
 *
 * <h2>Năm quyết định thiết kế, theo thứ tự quan trọng</h2>
 *
 * <b>1. Lời trấn an đứng trên cùng, thường trực.</b> Người nhập sợ làm hỏng
 * phả. Bất biến "chưa ghi gì cho tới khi bấm duyệt" là thật nhưng vô hình; nói
 * ra thành chữ là thứ khiến họ dám tải lên lần thứ ba.
 *
 * <b>2. Hai nhóm lỗi tách hẳn ra hai khối.</b> "6 lỗi phải sửa" là một việc làm
 * được; "17 vấn đề" nghe như hỏng cả tệp. Cùng dữ liệu, hai số phận khác nhau.
 *
 * <b>3. Nút duyệt khoá theo `canApprove` của máy chủ</b>, không theo phép suy
 * của client. Ba con số vẫn hiện — nhưng để giải thích, không để quyết.
 *
 * <b>4. Nút gỡ lô hỏi `rollback-preflight` trước khi mở hộp xác nhận.</b> Xem
 * {@link RollbackDialog}: một hộp thoại không nói gì về việc đã có người sửa hồ
 * sơ do lô này sinh ra là một hộp thoại nói dối.
 *
 * <b>5. Trạng thái sống ở máy chủ.</b> Màn này không giữ gì trong `useState`
 * ngoài những thứ chết theo lượt render (hộp thoại đang mở, câu lỗi vừa nhận).
 *
 * <h2>Một lô đã gỡ vẫn mang trạng thái "Đã vào phả", và đó là chủ ý</h2>
 * Không có `ROLLED_BACK` trong máy trạng thái: lô ấy *đã từng* được ghi, và
 * `committedAt` là mốc mà mọi phép kiểm "ai đã động vào sau khi ghi" dựa vào.
 * Việc đã gỡ là một **cột riêng** — `rolledBackAt` — nên câu hỏi "đã gỡ chưa"
 * đọc bằng `committedAt != null && rolledBackAt != null`, không bằng trạng thái.
 */
export function ImportReconcileScreen({ batchId }: ImportReconcileScreenProps) {
  const t = useTranslations("dataImport");
  const format = useFormatter();
  const router = useRouter();
  const { message } = App.useApp();
  const describeProblem = useImportProblemMessage();

  const batchQuery = useImportBatch(batchId);
  const issuesQuery = useImportIssues(batchId);
  const batch = batchQuery.data;

  const acknowledge = useAcknowledgeWarnings(batchId);
  const revalidate = useRevalidateBatch(batchId);
  const commit = useCommitBatch(batchId);
  const rollback = useRollbackBatch(batchId);

  const [rollbackOpen, setRollbackOpen] = useState(false);
  const [rolledBack, setRolledBack] = useState(false);
  const [commitError, setCommitError] = useState<string | null>(null);

  const committing = batch?.status === "COMMITTING";
  const progressQuery = useCommitProgress(batchId, committing);

  const { blocking, warnings } = useMemo(() => {
    const all = issuesQuery.data ?? [];
    return {
      blocking: all.filter((i) => i.severity === "BLOCKING"),
      warnings: all.filter((i) => i.severity === "WARNING"),
    };
  }, [issuesQuery.data]);

  if (batchQuery.isPending) {
    return <Skeleton active paragraph={{ rows: 8 }} />;
  }

  if (batchQuery.isError || !batch) {
    return (
      <Alert
        type="warning"
        showIcon
        message={<span className="text-dan">{t("common.notFoundTitle")}</span>}
        description={
          <div className="text-than">
            <p className="m-0">{t("common.notFoundBody")}</p>
            <Link
              href="/nhap-lieu"
              className="mt-2 inline-flex min-h-[44px] items-center underline"
              style={{ color: colorVars.primary }}
            >
              {t("common.openBatches")}
            </Link>
          </div>
        }
      />
    );
  }

  const doCommit = async () => {
    setCommitError(null);
    try {
      await commit.mutateAsync();
    } catch (error) {
      setCommitError(describeProblem(error));
    }
  };

  const doRollback = async (reason: string | undefined) => {
    try {
      await rollback.mutateAsync(reason);
      setRollbackOpen(false);
      setRolledBack(true);
      message.success(t("reconcile.rollback.done"));
    } catch {
      // Câu lỗi và `blockers[]` được hộp thoại đọc thẳng từ `rollback.error`,
      // nên ở đây không nuốt cũng không dựng lại chuỗi nào.
    }
  };

  const when = format.dateTime(new Date(batch.uploadedAt), {
    dateStyle: "medium",
    timeStyle: "short",
  });

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold" style={{ color: colorVars.textMain }}>
          {t("reconcile.title", { batchId: batch.id })}
        </h1>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
          {t("reconcile.fileLine", {
            fileName: batch.fileName,
            // `branchName` là tuỳ chọn trong hợp đồng — rơi về `branchId` chứ
            // không in ra "undefined".
            branch: batch.branchName ?? batch.branchId,
            when,
          })}
        </p>
        <p className="m-0 mt-1 text-than font-medium" style={{ color: colorVars.textMain }}>
          {t(`status.${batch.status}`)}
        </p>
      </header>

      {/* Lô cũ đã bị thay: nói ngay, đừng để người ta ngồi sửa một lô không còn dùng. */}
      {batch.status === "SUPERSEDED" && (
        <Alert
          type="info"
          showIcon
          message={<span className="text-dan">{t("reconcile.superseded.title")}</span>}
          description={<span className="text-than">{t("reconcile.superseded.body")}</span>}
        />
      )}

      {batch.status === "FAILED" && batch.failureReason && (
        <Alert
          type="error"
          showIcon
          message={<span className="text-dan">{t("reconcile.failed.title")}</span>}
          description={
            <span className="text-than" lang="vi">
              {batch.failureReason}
            </span>
          }
        />
      )}

      {/* Trước khi ghi: lời trấn an. Sau khi ghi: nó không còn đúng, nên biến mất. */}
      {batch.status !== "COMMITTED" && batch.status !== "COMMITTING" && <NotWrittenYetBanner />}

      <section
        className="rounded-lg border px-4 py-3"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      >
        <ul className="m-0 list-none space-y-1 p-0 text-than" style={{ color: colorVars.textMain }}>
          <li>{t("reconcile.counts.persons", { count: batch.personRowCount })}</li>
          <li>{t("reconcile.counts.marriages", { count: batch.marriageRowCount })}</li>
          <li>{t("reconcile.counts.create", { count: batch.plannedCreateCount })}</li>
          <li>{t("reconcile.counts.update", { count: batch.plannedUpdateCount })}</li>
        </ul>
      </section>

      <StagedRowsPreview
        batchId={batch.id}
        plannedCreateCount={batch.plannedCreateCount}
        plannedUpdateCount={batch.plannedUpdateCount}
      />

      {batch.status === "COMMITTING" && (
        <section
          className="rounded-lg border px-4 py-3"
          style={{ borderColor: colorVars.accent, backgroundColor: colorVars.warningBg }}
          role="status"
        >
          <h2 className="m-0 font-serif text-de font-bold" style={{ color: colorVars.textMain }}>
            {t("reconcile.committing.title")}
          </h2>
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
            {t(`reconcile.committing.phase.${progressQuery.data?.phase ?? "QUEUED"}`)}
          </p>
          <Progress
            percent={
              progressQuery.data
                ? Math.round(
                    (progressQuery.data.processedRows / Math.max(1, progressQuery.data.totalRows)) *
                      100
                  )
                : 0
            }
            status="active"
            showInfo={false}
          />
          {/* `estimated` luôn true, và câu này nói thẳng điều đó: con số đếm
              ngoài giao dịch ghi nên nó là ước lượng theo BẢN CHẤT. Vẽ nó như
              một thanh chính xác là hứa một điều hệ thống không giữ được. */}
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
            {t("reconcile.committing.estimate", {
              done: progressQuery.data?.processedRows ?? 0,
              total: progressQuery.data?.totalRows ?? batch.personRowCount,
            })}
          </p>
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
            {t("reconcile.committing.leaveOk")}
          </p>
        </section>
      )}

      {batch.status === "COMMITTED" && (
        <section
          className="rounded-lg border px-4 py-3"
          style={{ borderColor: colorVars.successText, backgroundColor: colorVars.successBg }}
          role="status"
        >
          <h2
            className="m-0 flex items-center gap-2 font-serif text-de font-bold"
            style={{ color: colorVars.successText }}
          >
            <CheckCircleOutlined aria-hidden />
            {/* KHÔNG có `committedPersonCount` trong hợp đồng. Con số duy nhất
                có thật là số dòng nhân khẩu của lô. */}
            {t("reconcile.committed.title", { count: batch.personRowCount })}
          </h2>
          {batch.committedAt && (
            <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
              {t("reconcile.committed.when", {
                when: format.dateTime(new Date(batch.committedAt), {
                  dateStyle: "medium",
                  timeStyle: "short",
                }),
              })}
            </p>
          )}

          {(rolledBack || batch.rolledBackAt) && (
            <p className="m-0 mt-2 text-than font-medium" style={{ color: colorVars.textMain }}>
              {t("reconcile.rollback.doneNote")}
            </p>
          )}

          <div className="mt-3 flex flex-col gap-2 sm:flex-row">
            <Link
              href="/tree"
              className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-4 text-than font-medium"
              style={{ backgroundColor: colorVars.primary, color: colorVars.bgCard }}
            >
              {t("reconcile.committed.viewTree")}
            </Link>
            {/* Nút gỡ lô: thứ quyết định người ta có dám nhập lần thứ hai không. */}
            <RollbackButton onOpen={() => setRollbackOpen(true)} />
          </div>
        </section>
      )}

      {batch.status !== "COMMITTED" && batch.status !== "COMMITTING" && (
        <>
          {issuesQuery.isPending ? (
            <Skeleton active paragraph={{ rows: 6 }} />
          ) : (
            <>
              {/* MỘT nút tải bản Excel trên cả màn, đặt cạnh bảng đang là việc
                  chính: bảng lỗi chặn khi còn lỗi chặn, bảng cảnh báo khi đã
                  hết. Tệp có đủ hai nhóm trên hai trang và endpoint không nhận
                  `severity`, nên hai nút là sai — một bản xuất chỉ có cảnh báo
                  là tệp mang tên "Danh sách cần sửa" mà thiếu phần phải sửa. */}
              <BlockingIssueSection
                issues={blocking}
                count={batch.blockingCount}
                batchId={batch.id}
                showWorkbookButton={batch.blockingCount > 0}
                onReupload={() => router.push("/nhap-lieu/tai-len")}
                onRevalidate={() => revalidate.mutate()}
                revalidating={revalidate.isPending}
              />
              <WarningIssueSection
                issues={warnings}
                count={batch.warningCount}
                batchId={batch.id}
                showWorkbookButton={batch.blockingCount === 0 && batch.warningCount > 0}
                suspectDuplicateCount={batch.suspectDuplicateCount}
                acknowledgedAt={batch.warningsAcknowledgedAt}
                canApprove={batch.canApprove}
                acknowledging={acknowledge.isPending}
                onAcknowledge={() => acknowledge.mutate()}
              />
            </>
          )}

          <CommitBar
            batch={batch}
            committing={commit.isPending}
            error={commitError}
            onCommit={doCommit}
          />
        </>
      )}

      <details className="rounded-lg border px-4 py-3" style={{ borderColor: colorVars.border }}>
        <summary
          className="min-h-[44px] cursor-pointer text-than font-medium"
          style={{ color: colorVars.textMain }}
        >
          {t("steps.legend")}
        </summary>
        <div className="mt-3">
          <ImportSteps current={4} compact />
        </div>
      </details>

      <RollbackDialog
        batchId={batch.id}
        open={rollbackOpen}
        onClose={() => setRollbackOpen(false)}
        onConfirm={doRollback}
        rolling={rollback.isPending}
        error={rollback.error}
      />
    </div>
  );
}

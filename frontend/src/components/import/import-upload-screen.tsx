"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Alert, Button, Radio, Skeleton } from "antd";
import { Link, useRouter } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/http";
import { colorVars } from "@/styles/tokens";
import { useImportBranches, useUploadBatch } from "./hooks/use-data-import";
import { useImportProblemMessage } from "./problem-message";
import { NoBranchNotice } from "./no-branch-notice";

export interface ImportUploadScreenProps {
  /** Chi chọn sẵn, ví dụ khi đi từ màn mẫu Excel sang. */
  defaultBranchId?: string;
}

/**
 * Màn 3a — chọn chi và tải tệp lên.
 *
 * <h2>Danh sách chi là CẢ CÂY, và quyền nằm ở `canImport`</h2>
 * `GET /import/branches` trả mọi chi cho mọi tài khoản đã khởi tạo — danh sách
 * chi là cấu trúc tổ chức, vốn đã hiện trên phả đồ. Cái thay đổi theo người gọi
 * là cờ `canImport`, và nó tồn tại để giao diện khoá nút **trước** khi người
 * dùng chọn tệp, thay vì để họ chọn xong rồi nhận `403`.
 *
 * Vì vậy "không được giao chi nào" không còn là "danh sách rỗng" mà là **không
 * chi nào có `canImport`** — và câu trả lời cho tình huống ấy vẫn phải chỉ đúng
 * chỗ đi tiếp: việc giao chi là của Hội đồng Tộc biểu.
 *
 * <h2>Ô chọn tệp là `<input type="file">` thật, không phải `<Upload>` của AntD</h2>
 * `<Upload>` mang sẵn một hàng đợi, một thanh tiến trình và một cơ chế tự gửi —
 * ba thứ ta không dùng, vì việc gửi đi qua lớp API có sẵn. Quan trọng hơn: vùng
 * kéo-thả của nó không phải một điều khiển biểu mẫu thật, nên trên điện thoại —
 * nơi phần lớn người dùng của sản phẩm này đang đứng — nó không mở được trình
 * chọn tệp của hệ điều hành một cách đáng tin. Một `<input>` gắn với `<label>`
 * thì luôn mở đúng.
 *
 * <h2>Cảnh báo TRƯỚC khi họ nộp lô thứ hai cho cùng một chi</h2>
 * `ImportBranch.openBatchId` cho biết chi ấy còn một lô đang đối soát. Một
 * Trưởng chi quay lại sau hai hôm rất dễ tải lên lại từ đầu — và lô cũ, cùng
 * nửa buổi đối soát trong đó, lập tức thành `SUPERSEDED`. Không chặn (nộp lại
 * là hành vi hợp lệ của quy trình), nhưng phải nói ra, kèm một đường dẫn về
 * đúng chỗ cũ.
 *
 * <h2>`IMP_ALREADY_COMMITTED` không phải lỗi, mà là một câu hỏi</h2>
 * Nó nói đúng một điều: *"tệp y hệt này đã được ghi vào phả cho chi này rồi"* —
 * tức chốt chặn bấm-hai-lần. Nó **không** chặn việc tải lại để kiểm, và tải lại
 * để kiểm là bước đối soát bình thường của quy trình. Phản hồi mang
 * `overridable: true` với `overrideField: "force"`, nên câu trả lời phải kèm
 * một nút "vẫn tải lên" chứ không phải một ngõ cụt.
 */
export function ImportUploadScreen({ defaultBranchId }: ImportUploadScreenProps) {
  const t = useTranslations("dataImport.upload");
  const tc = useTranslations("dataImport.common");
  const router = useRouter();
  const describeProblem = useImportProblemMessage();

  const branchesQuery = useImportBranches();
  const upload = useUploadBatch();

  const [branchId, setBranchId] = useState<string | undefined>(defaultBranchId);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [alreadyCommitted, setAlreadyCommitted] = useState(false);

  if (branchesQuery.isPending) return <Skeleton active paragraph={{ rows: 5 }} />;
  if (branchesQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={<span className="text-dan">{tc("error")}</span>}
        description={<span className="text-than">{describeProblem(branchesQuery.error)}</span>}
      />
    );
  }

  const branches = branchesQuery.data ?? [];
  const writable = branches.filter((b) => b.canImport);
  if (writable.length === 0) return <NoBranchNotice />;

  // Lô đang dở của ĐÚNG chi đang chọn — hoặc, khi chưa chọn chi nào, của chi
  // đầu tiên người này quản. Nhắc trước khi họ bấm, không phải sau.
  const selected = branchId ? branches.find((b) => b.id === branchId) : undefined;
  const openBatchId = (selected ?? writable[0])?.openBatchId;

  const submit = async (force: boolean) => {
    setError(null);
    if (!branchId) {
      setError(t("needBranch"));
      return;
    }
    if (!file) {
      setError(t("needFile"));
      return;
    }
    try {
      const batch = await upload.mutateAsync({ branchId, file, force });
      setAlreadyCommitted(false);
      router.push(`/nhap-lieu/${batch.id}`);
    } catch (caught) {
      // So theo `code` chứ không theo `detail` (contracts/README §3).
      if (caught instanceof ApiError && caught.code === "IMP_ALREADY_COMMITTED") {
        setAlreadyCommitted(true);
        return;
      }
      setError(describeProblem(caught));
    }
  };

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold" style={{ color: colorVars.textMain }}>
          {t("title")}
        </h1>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
          {t("lead")}
        </p>
      </header>

      <section
        className="rounded-lg border px-4 py-3"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      >
        <fieldset className="m-0 border-0 p-0">
          <legend className="mb-2 text-than font-semibold" style={{ color: colorVars.textMain }}>
            {t("branchLabel")}
          </legend>
          <Radio.Group
            value={branchId}
            onChange={(event) => setBranchId(event.target.value as string)}
            className="flex flex-col gap-1"
          >
            {/* Cả cây, nhưng chi ngoài phạm vi bị KHOÁ kèm lý do thành chữ —
                giấu hẳn chúng đi thì người dùng không hiểu vì sao chi mình
                không có ở đây. */}
            {branches.map((branch) => (
              <Radio
                key={branch.id}
                value={branch.id}
                disabled={!branch.canImport}
                className="flex min-h-[44px] items-center text-than"
              >
                <span className="text-than" style={{ color: colorVars.textMain }}>
                  {branch.name}
                </span>{" "}
                <span className="break-all font-mono text-than" style={{ color: colorVars.textMuted }}>
                  {branch.path}
                </span>
                {!branch.canImport && (
                  <span className="ml-2 text-than" style={{ color: colorVars.textMuted }}>
                    {t("notYours")}
                  </span>
                )}
              </Radio>
            ))}
          </Radio.Group>
        </fieldset>
      </section>

      <section
        className="rounded-lg border px-4 py-3"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      >
        <p className="m-0 text-than font-semibold" style={{ color: colorVars.textMain }}>
          {t("fileLabel")}
        </p>
        {/* Input thật, ẩn khỏi mắt nhưng KHÔNG khỏi bàn phím/trình đọc màn hình:
            `sr-only` giữ nó trong luồng tiêu điểm, còn `display:none` thì không. */}
        <input
          id="import-file-input"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          className="sr-only"
          onChange={(event) => {
            setFile(event.target.files?.[0] ?? null);
            setError(null);
            setAlreadyCommitted(false);
          }}
        />
        <label
          htmlFor="import-file-input"
          className="mt-2 inline-flex min-h-[44px] min-w-[44px] cursor-pointer items-center justify-center rounded-md border px-4 text-than font-medium"
          style={{
            borderColor: colorVars.borderInput,
            backgroundColor: colorVars.bgPage,
            color: colorVars.textMain,
          }}
        >
          {t("pick")}
        </label>
        {file && (
          <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMain }}>
            {t("picked", { name: file.name })}
          </p>
        )}
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
          {t("limits")}
        </p>
      </section>

      {openBatchId && (
        <Alert
          type="info"
          showIcon
          data-testid="open-batch-warning"
          message={<span className="text-dan font-semibold">{t("openBatch.title")}</span>}
          description={
            <div className="text-than">
              <p className="m-0">{t("openBatch.body", { batchId: openBatchId })}</p>
              <Link
                href={`/nhap-lieu/${openBatchId}`}
                className="mt-2 inline-flex min-h-[44px] min-w-[44px] items-center underline"
                style={{ color: colorVars.primary }}
              >
                {t("openBatch.action")}
              </Link>
            </div>
          }
        />
      )}

      <p className="m-0 text-than" style={{ color: colorVars.textMain }}>
        {t("reassure")}
      </p>
      <p className="m-0 text-than" style={{ color: colorVars.textMuted }}>
        {t("supersede")}
      </p>

      {error && (
        <Alert
          type="error"
          showIcon
          role="alert"
          message={<span className="text-than">{error}</span>}
        />
      )}

      {alreadyCommitted && (
        <Alert
          type="warning"
          showIcon
          message={<span className="text-dan">{t("alreadyCommitted.title")}</span>}
          description={
            <div className="text-than">
              <p className="m-0">{t("alreadyCommitted.body")}</p>
              <Button
                className="mt-2 min-h-[44px] min-w-[44px]"
                size="large"
                loading={upload.isPending}
                onClick={() => void submit(true)}
              >
                {t("alreadyCommitted.force")}
              </Button>
            </div>
          }
        />
      )}

      <Button
        type="primary"
        size="large"
        block
        className="min-h-[44px]"
        loading={upload.isPending}
        onClick={() => void submit(false)}
      >
        {upload.isPending ? t("submitting") : t("submit")}
      </Button>
    </div>
  );
}

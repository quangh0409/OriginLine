"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Alert, Button, Input, Modal, Skeleton } from "antd";
import { WarningOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import { useRollbackPreflight } from "./hooks/use-data-import";
import { rollbackBlockersOf, useImportProblemMessage } from "./problem-message";

export interface RollbackDialogProps {
  batchId: string;
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string | undefined) => Promise<void>;
  rolling: boolean;
  /** Lỗi của lần bấm gần nhất — nguồn của `blockers[]` khi máy chủ từ chối. */
  error: unknown;
}

/**
 * Hộp xác nhận **gỡ cả lô** — và nó phải nói sự thật về cái đang đánh đổi.
 *
 * <h2>Một hộp thoại "Bạn chắc chứ?" ở đây là một hộp thoại nói dối</h2>
 * Gỡ lô nghĩa là xoá mềm hàng trăm nhân khẩu do lô ấy sinh ra. Nếu trong lúc đó
 * đã có người trong họ nhận hồ sơ của mình và sửa lại, thì gỡ lô làm mất công
 * của họ. Một câu hỏi chung chung không nói gì về việc đó là câu hỏi khiến
 * người bấm tưởng mình đang làm một việc vô hại.
 *
 * Vì vậy hộp thoại này **hỏi trước khi hỏi**: `GET /rollback-preflight` trả
 * `blockers[]` bằng tiếng Việt, và danh sách ấy đứng ngay trên nút.
 *
 * <h2>"Đã có người sửa" KHÁC "đã quá hạn", và hai câu dẫn tới hai việc</h2>
 * Điều kiện thật của `IMP_ROLLBACK_REFUSED` là *"đã có người khác động vào dữ
 * liệu lô này sinh ra"* (suy từ `person.version` tại lúc ghi) — **không** phải
 * một cửa sổ thời gian đã hết. *"12 người đã được sửa sau khi ghi"* thì người
 * ta đi hỏi 12 người ấy; *"đã quá hạn"* thì chịu. Giao diện chép nguyên câu của
 * máy chủ chứ không tóm tắt lại thành một câu chung.
 *
 * <h2>Lý do là TUỲ CHỌN</h2>
 * Hợp đồng để `reason` tuỳ chọn, nên giao diện không được tự dựng một ràng buộc
 * bắt buộc: chặn người ta gỡ một lô hỏng vì chưa gõ lý do là đặt thủ tục lên
 * trên việc cần làm. Vẫn hỏi, vì lý do đi vào nhật ký kiểm toán và người sau
 * cần biết — nhưng hỏi, không chặn.
 */
export function RollbackDialog({
  batchId,
  open,
  onClose,
  onConfirm,
  rolling,
  error,
}: RollbackDialogProps) {
  const t = useTranslations("dataImport.reconcile.rollback");
  const tc = useTranslations("dataImport.common");
  const describeProblem = useImportProblemMessage();
  const [reason, setReason] = useState("");

  const preflight = useRollbackPreflight(batchId, open);
  // Vướng mắc đến từ hai nguồn và cả hai đều phải hiện: lượt hỏi trước, và lượt
  // bị máy chủ từ chối (khi có ai đó sửa hồ sơ đúng lúc hộp thoại đang mở).
  const serverBlockers = rollbackBlockersOf(error);
  const blockers = serverBlockers.length > 0 ? serverBlockers : (preflight.data?.blockers ?? []);
  const canRollback = preflight.data?.canRollback === true && serverBlockers.length === 0;

  return (
    <Modal
      open={open}
      title={<span className="text-de">{t("confirmTitle", { batchId })}</span>}
      okText={t("confirmOk")}
      cancelText={tc("cancel")}
      okButtonProps={{
        danger: true,
        size: "large",
        className: "min-h-[44px] min-w-[44px]",
        loading: rolling,
        disabled: !canRollback,
      }}
      cancelButtonProps={{ size: "large", className: "min-h-[44px] min-w-[44px]" }}
      onCancel={onClose}
      onOk={() => void onConfirm(reason.trim() || undefined)}
    >
      {preflight.isPending ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : preflight.isError ? (
        <Alert
          type="error"
          showIcon
          message={<span className="text-than">{describeProblem(preflight.error)}</span>}
        />
      ) : (
        <>
          {blockers.length > 0 ? (
            <section
              data-testid="rollback-blockers"
              className="rounded-lg border px-3 py-2"
              style={{ borderColor: colorVars.danger, backgroundColor: colorVars.dangerBg }}
            >
              <h3
                className="m-0 flex items-center gap-2 text-than font-semibold"
                style={{ color: colorVars.danger }}
              >
                <WarningOutlined aria-hidden />
                {t("blockedTitle", { count: blockers.length })}
              </h3>
              <ul
                className="m-0 mt-1 list-disc pl-5 text-than"
                lang="vi"
                style={{ color: colorVars.textMain }}
              >
                {blockers.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
              <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMain }}>
                {t("blockedHint")}
              </p>
            </section>
          ) : (
            <p className="m-0 text-than" style={{ color: colorVars.textMain }}>
              {t("confirmBody")}
            </p>
          )}

          <label
            className="mt-3 block text-than font-medium"
            htmlFor="import-rollback-reason"
            style={{ color: colorVars.textMain }}
          >
            {t("reasonLabel")}
          </label>
          <Input.TextArea
            id="import-rollback-reason"
            className="mt-1 text-than"
            rows={3}
            value={reason}
            disabled={!canRollback}
            onChange={(event) => setReason(event.target.value)}
          />
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
            {t("reasonOptional")}
          </p>

          {error !== undefined && error !== null && serverBlockers.length === 0 && (
            <Alert
              type="error"
              showIcon
              className="mt-3"
              message={<span className="text-than">{describeProblem(error)}</span>}
            />
          )}
        </>
      )}
    </Modal>
  );
}

/** Nút mở hộp thoại — tách ra để màn đối soát không phải giữ thêm state nào. */
export function RollbackButton({ onOpen }: { onOpen: () => void }) {
  const t = useTranslations("dataImport.reconcile.rollback");
  return (
    <Button size="large" danger className="min-h-[44px] min-w-[44px]" onClick={onOpen}>
      {t("button")}
    </Button>
  );
}

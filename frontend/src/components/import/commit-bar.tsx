"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Alert, Button, Modal } from "antd";
import { colorVars } from "@/styles/tokens";
import type { ImportBatch } from "@/lib/api/data-import";

export interface CommitBarProps {
  batch: ImportBatch;
  onCommit: () => void;
  committing: boolean;
  /** Câu lỗi máy chủ trả về ở lần bấm gần nhất, `null` khi chưa có. */
  error: string | null;
}

/**
 * Nút duyệt — ranh giới ghi duy nhất của cả đường ống.
 *
 * <h2>Khoá theo `canApprove`, KHÔNG suy lại từ ba con số</h2>
 * `batch.canApprove` đọc thẳng từ cùng một bất biến mà cổng duyệt phía máy chủ
 * áp dụng. Suy lại ở client ("blocking === 0 && đã tick && undecided === 0")
 * dựng ra nguồn chân lý thứ hai, và khi hai nguồn lệch nhau thì bên thua luôn
 * là giao diện: nút hiện ra rồi máy chủ từ chối, hoặc nút khoá trong khi lẽ ra
 * đã mở. Ba con số vẫn được dùng — nhưng chỉ để **giải thích**, không để quyết.
 *
 * <h2>Nút nói ra hậu quả, không nói ra thao tác</h2>
 * Nhãn là "Duyệt và ghi 318 người vào phả", không phải "Xác nhận". Một nhãn
 * trung tính buộc người dùng phải nhớ mình đang ở bước nào để đoán chuyện gì
 * sắp xảy ra; ở màn hình này thì đoán sai là chuyện đắt nhất họ có thể làm.
 *
 * <h2>Bị khoá thì phải nói RÕ đang chờ điều gì</h2>
 * Một nút xám không lý do là cách tạo ra một cuộc gọi cho đội kỹ thuật. Bốn lý
 * do khoá đều được viết thành câu, ngay cạnh nút — đúng bốn điều kiện và đúng
 * thứ tự mà cổng duyệt kiểm, vì đó cũng là thứ tự người nhập phải xử lý.
 */
export function CommitBar({ batch, onCommit, committing, error }: CommitBarProps) {
  const t = useTranslations("dataImport.reconcile.commit");
  const tc = useTranslations("dataImport.common");
  const [confirming, setConfirming] = useState(false);

  /**
   * Chỉ để GIẢI THÍCH. Quyết định khoá/mở là `batch.canApprove`.
   *
   * Hai câu về xác nhận cảnh báo, và chúng KHÔNG thay nhau được:
   *
   * <ul>
   *   <li><b>Chưa ai tick</b> — suy từ `warningsAcknowledgedAt` vắng mặt. Chiều
   *       suy này an toàn: không có dấu thời gian thì chắc chắn chưa ai bấm.</li>
   *   <li><b>Tick cũ đã hết hiệu lực</b> — KHÔNG suy được từ trường lẻ nào, vì
   *       dấu thời gian vẫn còn nguyên. Chỉ kết luận được khi mọi điều kiện
   *       khác đã sạch mà máy chủ vẫn từ chối: lúc ấy thứ duy nhất còn lại là
   *       cái tick đã lạc hậu sau một lượt kiểm sinh cảnh báo mới.</li>
   * </ul>
   *
   * Chiều ngược lại — "có dấu thời gian ⇒ đã xác nhận" — là chiều sai, và sai
   * đúng vào ca nguy hiểm nhất.
   */
  const reasons: string[] = [];
  const explainedByOthers =
    batch.status !== "VALIDATED" || batch.blockingCount > 0 || batch.undecidedDuplicateCount > 0;

  if (batch.status !== "VALIDATED") {
    reasons.push(t("blockedByStatus", { status: batch.status }));
  }
  if (batch.blockingCount > 0) {
    reasons.push(t("blockedByIssues", { count: batch.blockingCount }));
  }
  if (batch.warningCount > 0 && batch.warningsAcknowledgedAt === undefined) {
    reasons.push(t("blockedByAck"));
  } else if (!batch.canApprove && !explainedByOthers && batch.warningCount > 0) {
    reasons.push(t("blockedByAckStale"));
  }
  if (batch.undecidedDuplicateCount > 0) {
    reasons.push(t("blockedByDuplicates", { count: batch.undecidedDuplicateCount }));
  }

  return (
    <section
      className="rounded-lg border px-4 py-3"
      style={{ borderColor: colorVars.borderDark, backgroundColor: colorVars.bgCard }}
    >
      {!batch.canApprove && (
        <ul className="m-0 mb-3 list-none space-y-1 p-0" data-testid="commit-blockers">
          {(reasons.length > 0 ? reasons : [t("blockedUnknown")]).map((line) => (
            <li key={line} className="text-than" style={{ color: colorVars.textMain }}>
              {line}
            </li>
          ))}
        </ul>
      )}

      {error && (
        <Alert
          type="error"
          showIcon
          className="mb-3 text-than"
          message={<span className="text-than">{t("failed")}</span>}
          description={<span className="text-than">{error}</span>}
        />
      )}

      <Button
        type="primary"
        size="large"
        block
        className="min-h-[44px]"
        disabled={!batch.canApprove}
        loading={committing}
        onClick={() => setConfirming(true)}
      >
        {t("button", { count: batch.personRowCount })}
      </Button>

      <Modal
        open={confirming}
        title={<span className="text-de">{t("confirmTitle", { count: batch.personRowCount })}</span>}
        okText={t("confirmOk")}
        cancelText={tc("cancel")}
        onCancel={() => setConfirming(false)}
        okButtonProps={{ size: "large", className: "min-h-[44px] min-w-[44px]" }}
        cancelButtonProps={{ size: "large", className: "min-h-[44px] min-w-[44px]" }}
        onOk={() => {
          setConfirming(false);
          onCommit();
        }}
      >
        {/* Hỏi lại một lần, và câu hỏi kèm lối thoát — nhưng KHÔNG hứa một hạn
            30 ngày: hợp đồng không có `rollbackDeadline`, và điều kiện gỡ thật
            là "chưa ai sửa hồ sơ sau khi ghi", không phải một cái đồng hồ. */}
        <p className="m-0 text-than" style={{ color: colorVars.textMain }}>
          {t("confirmBody")}
        </p>
      </Modal>
    </section>
  );
}

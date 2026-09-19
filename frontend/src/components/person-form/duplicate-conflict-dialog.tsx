"use client";

import { Alert, Button, Modal, Tag } from "antd";
import { WarningFilled } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { DuplicateCandidate } from "@/types/api";
import { ConflictParty } from "./conflict-party";

export interface DuplicateConflictDialogProps {
  candidates: DuplicateCandidate[] | null;
  submitting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * Nghi trùng nhân khẩu (`409 DUPLICATE_PERSON_SUSPECTED`) — hộp thoại song
 * sinh với {@link TabooConflictDialog}, cùng một khuôn và cùng một ranh giới:
 *
 *   **được phép nói trường nào của chính người dùng vừa nhập đã khớp; không
 *   bao giờ nói một giá trị đọc từ phả.**
 *
 * Vì thế `score`, `signals` và `hint` hiện **ngay từ thân lỗi 409** — chúng chỉ
 * làm vế thứ nhất, trả lời "vì sao nghi" mà không nói "người ấy là ai". Cắt nốt
 * chúng thì người nhập mất khả năng đối chiếu và sẽ bấm ghi đè theo phản xạ,
 * tức là bịt lỗ rò bằng cách phá chính cơ chế chống trùng.
 *
 * Phần danh tính thì ngược lại: nạp theo `personId` qua `GET /persons/{id}`
 * (xem {@link ConflictParty}). Bộ dò quét **toàn dòng họ** và không biết người
 * gọi là ai, nên ứng viên hoàn toàn có thể là một người **còn sống ở một chi
 * khác** mà người đang thêm nhân khẩu không được xem — kể cả việc họ tồn tại.
 *
 * <h2>`personId === null` là ca BÌNH THƯỜNG</h2>
 * Ứng viên khi ấy là một dòng **chưa được ghi** trong cùng lô nhập liệu; định
 * danh duy nhất là `ref`, và **không có gì để `GET`**. Đó là dữ liệu của chính
 * người nhập nên nó hiện thẳng, không cần đi qua bộ lọc nào.
 *
 * <h2>Vì sao không bắt gõ lý do như kỵ húy</h2>
 * Kỵ húy là một điều kiêng kỵ của dòng họ và việc bỏ qua nó phải để lại dấu
 * vết trong nhật ký (`note` + `confirmTabooOverride`). Nghi trùng chỉ là một
 * phỏng đoán thống kê của máy: hai người cùng tên cùng năm sinh trong một dòng
 * họ đông là chuyện thường. Bắt gõ lý do cho một cảnh báo hay báo nhầm sẽ dạy
 * người dùng gõ bừa cho xong — và thói quen ấy sẽ theo họ sang cả hộp thoại kỵ
 * húy.
 */
export function DuplicateConflictDialog({
  candidates,
  submitting,
  onCancel,
  onConfirm,
}: DuplicateConflictDialogProps) {
  const t = useTranslations("personForm");
  const open = Boolean(candidates && candidates.length > 0);

  return (
    <Modal
      open={open}
      onCancel={onCancel}
      title={
        <span className="flex items-center gap-2">
          <WarningFilled style={{ color: colorVars.accentText }} />
          {t("duplicate.title")}
        </span>
      }
      width={560}
      destroyOnClose
      footer={[
        <Button key="cancel" onClick={onCancel} disabled={submitting}>
          {t("duplicate.review")}
        </Button>,
        <Button key="confirm" type="primary" loading={submitting} onClick={onConfirm}>
          {t("duplicate.confirm")}
        </Button>,
      ]}
    >
      <Alert
        type="warning"
        showIcon={false}
        className="!mb-3"
        message={t("duplicate.lead", { count: candidates?.length ?? 0 })}
        description={t("duplicate.explain")}
      />

      <ul className="m-0 list-none space-y-2 p-0">
        {(candidates ?? []).map((candidate, index) => (
          <li
            key={candidate.personId ?? candidate.ref ?? `candidate-${index}`}
            className="rounded border px-3 py-2"
            style={{ borderColor: colorVars.borderDark, background: colorVars.warningBg }}
            data-testid={`duplicate-candidate-${candidate.personId ?? candidate.ref ?? index}`}
          >
            {/* Vế ĐƯỢC PHÉP: vì sao máy nghi. Tất cả đều nói về ô người dùng
                vừa nhập, nên chúng hiện được kể cả khi danh tính bên kia bị
                bộ lọc riêng tư giữ lại. */}
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="text-than font-semibold" style={{ color: colorVars.textMain }}>
                {t("duplicate.score", { score: candidate.score })}
              </span>
              {(candidate.signals ?? []).map((signal) => (
                <Tag key={signal} bordered={false} color={colorVars.textMuted} className="!m-0">
                  {t(`duplicate.signal.${signal}`)}
                </Tag>
              ))}
            </div>
            {/* Chỉ để hiển thị — không phân tích chuỗi này (contract). */}
            {isPresent(candidate.hint) && (
              <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
                {candidate.hint}
              </p>
            )}

            <div className="mt-1">
              {candidate.personId === null ? (
                <div data-testid={`duplicate-in-batch-${candidate.ref ?? index}`}>
                  <p className="m-0 text-than font-semibold" style={{ color: colorVars.textMain }}>
                    {t("duplicate.inBatch.title")}
                  </p>
                  <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
                    {isPresent(candidate.ref)
                      ? t("duplicate.inBatch.ref", { ref: candidate.ref })
                      : t("duplicate.inBatch.noRef")}
                  </p>
                </div>
              ) : (
                <ConflictParty
                  personId={candidate.personId}
                  notVisible={{
                    title: t("duplicate.candidateNotVisible.title"),
                    body: t("duplicate.candidateNotVisible.body"),
                    ask: t("duplicate.candidateNotVisible.ask"),
                  }}
                  loadFailedLabel={t("duplicate.candidateLoadFailed")}
                  testId={`duplicate-party-${candidate.personId}`}
                />
              )}
            </div>
          </li>
        ))}
      </ul>
    </Modal>
  );
}

"use client";

import { useId, useState } from "react";
import { Alert, App, Button, Input, Modal } from "antd";
import { CheckOutlined, CloseOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import { claimReviewFailureOf, type ClaimReviewView } from "@/lib/api/membership-admin";
import { useReviewClaim } from "./queries";

export interface RequestDecisionProps {
  request: ClaimReviewView;
  /** Lá đơn này đang tranh một nhân khẩu với lá khác. Đổi câu xác nhận lúc duyệt. */
  contested?: boolean;
  /** Tên hiển thị dùng trong câu xác nhận — nhân khẩu được nhận, hoặc tên tự khai. */
  subject: string;
}

/**
 * Hai nút quyết định của một lá đơn, cộng hộp thoại xác nhận cho từng nhánh.
 *
 * <h2>Từ chối BẮT BUỘC nêu lý do, và lý do ấy đến tay người gửi</h2>
 * Nút "Từ chối" không gửi gì ngay — nó mở một hộp thoại có đúng một ô bắt buộc.
 * Luật này không phải quy ước giao diện: {@code PersonClaim#reject} ở miền
 * backend ném lỗi khi thiếu lý do, nên ô ấy là hình chiếu của một bất biến.
 *
 * Vì sao bất biến ấy tồn tại: người khai đã bỏ công tìm mình giữa 1.500 người,
 * và họ chỉ còn một số lần gửi lại **có hạn** (`CLAIM_LIMIT_REACHED`). Trả về
 * một chữ "không" là cách chắc chắn để lần gửi tiếp theo cũng sai y như lần
 * này — và có thể là lần cuối họ được gửi. Câu chữ của hộp thoại nói thẳng rằng
 * người gửi sẽ đọc được lý do, để người duyệt viết cho một con người chứ không
 * cho một ô ghi chú.
 *
 * <h2>Duyệt một đơn `NEW_PERSON` là GHI VÀO PHẢ</h2>
 * Hộp thoại của nhánh ấy liệt kê hệ quả thay vì hỏi "Bạn chắc chứ?": tạo một
 * nhân khẩu mới, nối vào cây qua người thân được chỉ ra, và — vì xoá mềm là
 * luật tuyệt đối — **không gỡ lại được**. Một câu hỏi chung chung ở chỗ này sẽ
 * được bấm qua đúng như mọi câu hỏi chung chung khác.
 *
 * <h2>Duyệt một đơn đang tranh chấp thì đóng các đơn kia</h2>
 * Một nhân khẩu chỉ gắn được một tài khoản (`ux_app_user_person`). Người duyệt
 * phải biết điều đó **trước** khi bấm, chứ không phát hiện ra khi lá đơn thứ
 * hai biến mất khỏi màn hình.
 */
export function RequestDecision({ request, contested, subject }: RequestDecisionProps) {
  const t = useTranslations("membership");
  const { message } = App.useApp();
  const review = useReviewClaim();
  const [decision, setDecision] = useState<"approve" | "reject" | null>(null);
  const [note, setNote] = useState("");
  const [noteError, setNoteError] = useState<string | null>(null);
  // Hàng đợi vẽ nhiều thẻ cùng lúc; một `id` cố định làm `htmlFor` của mọi nhãn
  // trỏ vào ô nhập của thẻ ĐẦU TIÊN.
  const noteId = useId();

  if (request.status !== "PENDING") return null;

  const laNguoiMoi = request.kind === "NEW_PERSON";

  const close = () => {
    setDecision(null);
    setNote("");
    setNoteError(null);
    review.reset();
  };

  const confirm = async () => {
    const trimmed = note.trim();
    if (decision === "reject" && trimmed.length === 0) {
      setNoteError(t("review.noteRequired"));
      return;
    }
    try {
      await review.mutateAsync({
        id: request.id,
        approve: decision === "approve",
        note: trimmed.length > 0 ? trimmed : null,
      });
      message.success(t(decision === "approve" ? "review.approved" : "review.rejected"));
      close();
    } catch {
      // Lỗi hiện trong hộp thoại; giữ nguyên lý do người duyệt vừa gõ.
    }
  };

  const approveLabel = laNguoiMoi ? t("review.approveNewPerson") : t("review.approveClaim");

  return (
    <>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button
          type="primary"
          size="large"
          icon={<CheckOutlined aria-hidden />}
          onClick={() => setDecision("approve")}
        >
          {approveLabel}
        </Button>
        <Button
          danger
          size="large"
          icon={<CloseOutlined aria-hidden />}
          onClick={() => setDecision("reject")}
        >
          {t("review.reject")}
        </Button>
      </div>

      <Modal
        open={decision !== null}
        onCancel={close}
        title={
          decision === "reject"
            ? t("review.rejectTitle")
            : laNguoiMoi
              ? t("review.approveNewPersonTitle")
              : t("review.approveClaimTitle")
        }
        okText={decision === "reject" ? t("review.reject") : approveLabel}
        cancelText={t("common.cancel")}
        okButtonProps={{ danger: decision === "reject", size: "large" }}
        cancelButtonProps={{ size: "large" }}
        confirmLoading={review.isPending}
        onOk={() => void confirm()}
        destroyOnClose
      >
        {review.error && (
          <Alert
            className="!mb-3"
            type="error"
            showIcon
            message={t(`review.errors.${claimReviewFailureOf(review.error)}`)}
            description={review.error.problem?.detail}
          />
        )}

        {decision === "approve" && laNguoiMoi && (
          <Alert
            className="!mb-3"
            type="warning"
            showIcon
            message={t("review.approveNewPersonLead", { name: subject })}
            description={
              <ul className="m-0 list-disc pl-5 text-than text-text-main">
                <li>{t("review.newPersonConsequence1")}</li>
                <li>{t("review.newPersonConsequence2")}</li>
                <li>{t("review.newPersonConsequence3")}</li>
              </ul>
            }
          />
        )}

        {decision === "approve" && !laNguoiMoi && (
          <p className="m-0 mb-2 text-than text-text-main">
            {t("review.approveClaimLead", { name: subject })}
          </p>
        )}

        {decision === "approve" && contested && (
          <Alert
            className="!mb-3"
            type="info"
            showIcon
            message={t("review.contestedApproveWarning")}
          />
        )}

        {decision === "reject" && (
          <p className="m-0 mb-2 text-than text-text-main">{t("review.rejectLead")}</p>
        )}

        <label className="block text-than font-medium text-text-main" htmlFor={noteId}>
          {decision === "reject" ? t("review.noteLabelRequired") : t("review.noteLabel")}
        </label>
        <Input.TextArea
          id={noteId}
          rows={3}
          value={note}
          status={noteError ? "error" : undefined}
          onChange={(e) => {
            setNote(e.target.value);
            if (noteError) setNoteError(null);
          }}
          placeholder={
            decision === "reject"
              ? t("review.rejectNotePlaceholder")
              : t("review.approveNotePlaceholder")
          }
        />
        {noteError && (
          <p role="alert" className="m-0 mt-1 text-than" style={{ color: colorVars.danger }}>
            {noteError}
          </p>
        )}
        {decision === "reject" && (
          <p className="m-0 mt-2 text-than text-text-muted">{t("review.noteReachesSender")}</p>
        )}
      </Modal>
    </>
  );
}

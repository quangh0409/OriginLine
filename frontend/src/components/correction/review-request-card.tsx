"use client";

import { useId, useState } from "react";
import { Alert, App, Button, Input, Modal, Skeleton } from "antd";
import { CheckOutlined, CloseOutlined } from "@ant-design/icons";
import { useTranslations, useFormatter } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useReviewChangeRequest } from "@/hooks/use-change-requests";
import { usePerson } from "@/hooks/use-person";
import { ApiError } from "@/lib/api/http";
import { colorVars } from "@/styles/tokens";
import { headlineName } from "@/lib/format/name-layers";
import type { ChangeRequestView } from "@/lib/api/change-requests";
import { ChangeRequestStatusTag } from "./change-request-status-tag";
import { CorrectionDiff } from "./correction-diff";

export interface ReviewRequestCardProps {
  request: ChangeRequestView;
  /** `appUserId` của người đang đăng nhập — căn cứ chặn tự duyệt. */
  currentAppUserId: string | null;
}

/**
 * Một yêu cầu trong hàng đợi duyệt: **trước / sau cạnh nhau**, rồi Duyệt hoặc
 * Từ chối kèm lý do.
 *
 * <h2>Không tự duyệt đề nghị của chính mình</h2>
 * Backend chặn bằng `403 SELF_REVIEW_FORBIDDEN`, và ở đây nút bị ẩn kèm một
 * câu giải thích. Ẩn nút **không phải** là biện pháp bảo mật — nó là phép lịch
 * sự với người dùng; chốt thật vẫn nằm ở máy chủ, và nếu vì lý do gì đó lời gọi
 * vẫn đi tới nơi thì mã lỗi trả về được dịch thành đúng câu ấy chứ không phải
 * một thông báo lỗi chung chung.
 *
 * Ý nghĩa của luật này: một Trưởng chi vừa gửi vừa duyệt thì luồng đính chính
 * chỉ còn là một cách viết thẳng vào cây có thêm hai cú bấm. Nó cần **hai
 * người**, và với đề nghị do chính Trưởng chi gửi thì người thứ hai là Hội
 * đồng Tộc biểu.
 *
 * <h2>Từ chối bắt buộc nêu lý do</h2>
 * Người gửi bỏ công tra gia phả cũ; trả lại một chữ "không" là cách chắc chắn
 * để họ không gửi lần thứ hai.
 */
export function ReviewRequestCard({ request, currentAppUserId }: ReviewRequestCardProps) {
  const t = useTranslations("correction");
  const format = useFormatter();
  const { message } = App.useApp();
  const review = useReviewChangeRequest();
  const [decision, setDecision] = useState<"approve" | "reject" | null>(null);
  const [note, setNote] = useState("");
  const [noteError, setNoteError] = useState<string | null>(null);
  // Hàng đợi vẽ nhiều thẻ cùng lúc; một `id` cố định sẽ lặp lại trên cả trang và
  // `htmlFor` của nhãn trỏ vào ô nhập của thẻ ĐẦU TIÊN với mọi thẻ.
  const noteId = useId();

  // Hồ sơ hiện tại dựng cột "đang ghi". Lỗi ở đây KHÔNG chặn việc duyệt: một
  // yêu cầu vẫn đọc được qua `reason` + cột "đề nghị".
  const personQuery = usePerson(request.personId ?? undefined);
  const person = personQuery.data?.person;

  const isOwnRequest = currentAppUserId !== null && request.requestedBy === currentAppUserId;
  const isOpen = request.status === "PENDING";

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
      // Lỗi hiện trong hộp thoại; giữ nguyên ghi chú người dùng vừa gõ.
    }
  };

  return (
    <article className="rounded-lg border border-border bg-bg-card px-4 py-3">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="m-0 font-serif text-base font-semibold text-text-main">
            {request.personId ? (
              <Link href={`/persons/${request.personId}`} className="text-primary">
                {personQuery.isPending ? (
                  <Skeleton.Input active size="small" style={{ width: 160 }} />
                ) : (
                  (person && headlineName(person)) ?? t("card.unknownPerson")
                )}
              </Link>
            ) : (
              t("card.noPerson")
            )}
          </h3>
          <p className="m-0 mt-0.5 text-than text-text-muted">
            {t(`type.${request.type}`)}
            {request.createdAt && (
              <>
                {" · "}
                {format.dateTime(new Date(request.createdAt), {
                  dateStyle: "medium",
                  timeStyle: "short",
                })}
              </>
            )}
          </p>
        </div>
        <ChangeRequestStatusTag status={request.status} />
      </header>

      {request.reason && (
        <div className="mt-2">
          <p className="m-0 text-than font-medium uppercase tracking-wide text-text-muted">
            {t("card.reason")}
          </p>
          <p className="m-0 whitespace-pre-line text-than leading-snug text-text-main">
            {request.reason}
          </p>
        </div>
      )}

      <div className="mt-3">
        <CorrectionDiff
          payload={request.payload}
          payloadFields={request.payloadFields}
          person={person}
        />
      </div>

      {request.reviewNote && (
        <p className="m-0 mt-2 text-than text-text-muted">
          {t("card.reviewNote", { note: request.reviewNote })}
        </p>
      )}

      {isOpen && isOwnRequest && (
        <Alert
          className="!mt-3"
          type="info"
          showIcon
          message={t("review.selfReviewTitle")}
          description={t("review.selfReviewHint")}
        />
      )}

      {isOpen && !isOwnRequest && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button
            type="primary"
            icon={<CheckOutlined aria-hidden />}
            onClick={() => setDecision("approve")}
          >
            {t("review.approve")}
          </Button>
          <Button danger icon={<CloseOutlined aria-hidden />} onClick={() => setDecision("reject")}>
            {t("review.reject")}
          </Button>
        </div>
      )}

      <Modal
        open={decision !== null}
        onCancel={close}
        title={t(decision === "reject" ? "review.rejectTitle" : "review.approveTitle")}
        okText={t(decision === "reject" ? "review.reject" : "review.approve")}
        cancelText={t("review.cancel")}
        okButtonProps={{ danger: decision === "reject" }}
        confirmLoading={review.isPending}
        onOk={() => void confirm()}
        destroyOnClose
      >
        {review.error && <ReviewError error={review.error} />}
        <p className="m-0 mb-2 text-than text-text-muted">
          {t(decision === "reject" ? "review.rejectLead" : "review.approveLead")}
        </p>
        <label className="block text-than font-medium text-text-main" htmlFor={noteId}>
          {t(decision === "reject" ? "review.noteLabelRequired" : "review.noteLabel")}
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
          placeholder={t("review.notePlaceholder")}
        />
        {noteError && (
          <p role="alert" className="m-0 mt-1 text-than" style={{ color: colorVars.danger }}>
            {noteError}
          </p>
        )}
      </Modal>
    </article>
  );
}

/** Phân nhánh theo `code` — năm mã của W6 mỗi mã một câu khác nhau. */
function ReviewError({ error }: { error: ApiError }) {
  const t = useTranslations("correction");
  const key =
    error.code === "SELF_REVIEW_FORBIDDEN"
      ? "errors.selfReview"
      : error.code === "BRANCH_SCOPE_VIOLATION"
        ? "errors.branchScope"
        : error.code === "CHANGE_REQUEST_CLOSED"
          ? "errors.closed"
          : error.code === "ACCOUNT_NOT_PROVISIONED"
            ? "errors.notProvisioned"
            : error.code === "ACCOUNT_NOT_ACTIVE"
              ? "errors.notActive"
              : error.code === "FORBIDDEN"
                ? "errors.forbidden"
                : "errors.reviewFailed";
  return (
    <Alert
      className="!mb-3"
      type="error"
      showIcon
      message={t(key)}
      description={error.problem?.detail}
    />
  );
}

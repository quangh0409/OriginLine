"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Alert, Button, Input, Select } from "antd";
import { FlagOutlined } from "@ant-design/icons";
import { ApiError } from "@/lib/api/http";
import type { MediaReportReason } from "@/lib/api/media";
import { useReportMedia } from "../queries";

export interface MediaReportButtonProps {
  mediaId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const REASONS: MediaReportReason[] = ["RIENG_TU", "SAI_NGUOI", "KHONG_PHU_HOP", "BAN_QUYEN", "KHAC"];

/**
 * "Báo gỡ" một tệp đính kèm — Việc 2, điều kiện để "ảnh đi theo quyền của
 * bài" là một quyết định an toàn được: không có nút này thì không ai gỡ được
 * một tấm ảnh đăng nhầm, vì việc gỡ ảnh dùng lại đúng cổng quyền sửa bài (chỉ
 * tác giả/người duyệt trong `DRAFT`), và người xem bình thường không có cổng
 * đó.
 *
 * <h2>Đây là BÁO, không phải XOÁ</h2>
 * Bấm xong không có gì biến mất khỏi bài ngay lập tức — báo cáo đi vào hàng
 * chờ duyệt theo phạm vi chi (`MediaReportQueueScreen`), nơi người duyệt mới
 * là người quyết gỡ hay bỏ qua. Khác với nút xoá trong `MediaPicker`
 * (`mediaApi.remove`, chỉ tác giả dùng được, chỉ lúc soạn).
 *
 * <h2>Gắn với TỆP, không còn cần `postId`</h2>
 * Backend chốt endpoint là `/api/v1/media/{mediaId}/reports` — không lồng
 * dưới bài, vì người báo cáo đang báo một TỆP, và một tệp có đúng một khoá.
 *
 * <h2>Chọn "Khác" thì bắt buộc phải nói vì sao</h2>
 * Bốn lý do còn lại đã tự giải thích đủ cho người duyệt hình dung được vấn đề;
 * "Khác" thì không — máy chủ từ chối khi thiếu `note` ở đúng lựa chọn này.
 */
export function MediaReportButton({ mediaId, open, onOpenChange }: MediaReportButtonProps) {
  const t = useTranslations("posts.media");
  const [reason, setReason] = useState<MediaReportReason | undefined>(undefined);
  const [note, setNote] = useState("");
  const report = useReportMedia(mediaId);

  if (report.isSuccess) {
    return (
      <p className="m-0 text-than" role="status">
        {t("report.thanks")}
      </p>
    );
  }

  if (!open) {
    return (
      <Button
        type="text"
        size="small"
        icon={<FlagOutlined aria-hidden />}
        className="min-h-[44px]"
        onClick={() => onOpenChange(true)}
      >
        {t("report.action")}
      </Button>
    );
  }

  const noteRequired = reason === "KHAC";
  const canSubmit = Boolean(reason) && (!noteRequired || note.trim().length > 0);

  return (
    <div className="w-full space-y-2 sm:max-w-sm">
      <label htmlFor={`report-reason-${mediaId}`} className="block text-than font-medium">
        {t("report.reasonLabel")}
      </label>
      <Select<MediaReportReason>
        id={`report-reason-${mediaId}`}
        className="w-full"
        placeholder={t("report.reasonPlaceholder")}
        value={reason}
        onChange={setReason}
        options={REASONS.map((r) => ({ value: r, label: t(`report.reasons.${r}`) }))}
      />

      <label htmlFor={`report-note-${mediaId}`} className="block text-than font-medium">
        {noteRequired ? t("report.noteLabelRequired") : t("report.noteLabelOptional")}
      </label>
      <Input.TextArea
        id={`report-note-${mediaId}`}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder={t("report.notePlaceholder")}
        autoSize={{ minRows: 2, maxRows: 4 }}
      />
      {report.isError && (
        <Alert type="error" showIcon message={describeReportError(report.error, t)} />
      )}
      <div className="flex gap-2">
        <Button
          type="primary"
          size="small"
          className="min-h-[44px]"
          disabled={!canSubmit || report.isPending}
          loading={report.isPending}
          onClick={() => {
            if (!reason) return;
            report.mutate({ reason, note: note.trim() ? note.trim() : undefined });
          }}
        >
          {t("report.submit")}
        </Button>
        <Button size="small" className="min-h-[44px]" onClick={() => onOpenChange(false)}>
          {t("report.cancel")}
        </Button>
      </div>
    </div>
  );
}

/**
 * Một người chỉ mở được MỘT báo cáo đang treo cho mỗi tệp — máy chủ từ chối
 * lượt thứ hai bằng một mã lỗi chung (không có `ProblemCode` riêng cho ràng
 * buộc dữ liệu này), nên câu chữ ở đây nhánh theo `status`/`code` đã có sẵn
 * và rơi về một câu chung đủ dùng khi không khớp gì cụ thể.
 */
function describeReportError(error: unknown, t: ReturnType<typeof useTranslations>): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return t("report.alreadyReported");
    if (error.code === "VALIDATION_FAILED") return error.problem?.detail ?? t("report.failed");
  }
  return t("report.failed");
}

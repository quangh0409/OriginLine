"use client";

import { useId, useState, type ChangeEvent, type DragEvent } from "react";
import { useTranslations } from "next-intl";
import { Alert, Button, Input, Progress } from "antd";
import {
  CloseOutlined,
  LeftOutlined,
  PictureOutlined,
  ReloadOutlined,
  RightOutlined,
  VideoCameraOutlined,
} from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import type { MediaPolicy } from "@/lib/api/media";
import type { PostMediaItem } from "@/lib/api/posts";
import { useMediaPolicy, useRemoveMedia, useReorderMedia, useUpdateMediaAlt } from "../queries";
import { useMediaUploadQueue, type PendingMediaItem } from "./use-media-upload-queue";

export interface MediaPickerProps {
  postId: string | null;
  media: PostMediaItem[];
  /** `false` khi bài không còn sửa được (đã gửi duyệt/đã đăng) — hiện dạng chỉ đọc. */
  editable: boolean;
}

/** Nhị phân (1024), khớp cách {@link MediaPolicy} công bố trần — "MiB", không phải "MB" thập phân. */
function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KiB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
}

/**
 * Chọn và quản lý ảnh/video của bài viết — Việc 1 (design 07 §2: "vài ảnh,
 * hết" — nhưng người viết là bác 60 tuổi, nên mỗi bước phải tự nói nó đang
 * làm gì, không im lặng).
 *
 * <h2>Cần một bản nháp trước</h2>
 * Tải tệp cần `postId` (khoá gắn tệp vào đúng bài) — với bài chưa từng lưu,
 * `postId` là `null` và bộ chọn tệp bị khoá kèm lời giải thích, thay vì để
 * người dùng chọn ảnh rồi phát hiện chúng biến mất.
 *
 * <h2>Sắp lại thứ tự bằng BÀN PHÍM — lối chính, không phải lối phụ</h2>
 * Hai nút "sang trái"/"sang phải" trên mỗi ảnh đã gắn là cách sắp thứ tự
 * DUY NHẤT ở đây, và cố ý như vậy: chúng dùng bàn phím/trình đọc màn hình
 * được ngay từ đầu, không cần một lối kéo-thả riêng rồi thêm bàn phím sau.
 * Kéo-thả bằng chuột mà không kèm một lối bàn phím tương đương là loại người
 * dùng bàn phím/trình đọc màn hình ra khỏi chức năng này — đây là cái bẫy
 * chính đề bài nêu, nên hai nút này không phải "phương án dự phòng".
 */
export function MediaPicker({ postId, media, editable }: MediaPickerProps) {
  const t = useTranslations("posts.media");
  const inputId = useId();
  const [dragOver, setDragOver] = useState(false);

  const policyQuery = useMediaPolicy();
  const policy = policyQuery.data;

  const attachedCount = media.length;
  const queue = useMediaUploadQueue(postId, attachedCount, policy);
  const reorder = useReorderMedia(postId ?? "");
  const removeMedia = useRemoveMedia(postId ?? "");
  const updateAlt = useUpdateMediaAlt(postId ?? "");

  const totalCount = attachedCount + queue.items.filter((it) => it.status !== "rejected").length;
  const atCap = Boolean(policy) && totalCount >= (policy?.maxAttachmentsPerPost ?? Infinity);

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    queue.addFiles(files);
  };

  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragOver(false);
    if (!editable || queue.disabled) return;
    handleFiles(event.dataTransfer.files);
  };

  const moveBy = (mediaId: string, delta: 1 | -1) => {
    const index = media.findIndex((m) => m.id === mediaId);
    if (index === -1) return;
    const target = index + delta;
    if (target < 0 || target >= media.length) return;
    const nextOrder = media.map((m) => m.id);
    const [moved] = nextOrder.splice(index, 1);
    if (moved === undefined) return; // không thể xảy ra — `index` vừa được kiểm hợp lệ ở trên
    nextOrder.splice(target, 0, moved);
    reorder.mutate(nextOrder);
  };

  if (!editable) {
    if (media.length === 0) return null;
    return <MediaGridReadOnlyHint count={media.length} />;
  }

  return (
    <div className="space-y-3">
      <div>
        <span id={`${inputId}-label`} className="mb-1 block text-than font-medium text-text-main">
          {t("label")}
        </span>
        <p className="m-0 mb-2 text-than text-text-muted">{t("hint")}</p>

        <div
          onDragOver={(e) => {
            e.preventDefault();
            if (!queue.disabled) setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={onDrop}
          className="rounded-lg border-2 border-dashed px-4 py-4"
          style={{
            borderColor: dragOver ? colorVars.primary : colorVars.borderInput,
            backgroundColor: dragOver ? colorVars.primaryLight : colorVars.bgPage,
          }}
        >
          <input
            id={inputId}
            type="file"
            multiple
            accept={policy ? [...policy.image.mimeTypes, ...policy.video.mimeTypes].join(",") : undefined}
            disabled={queue.disabled || atCap}
            aria-labelledby={`${inputId}-label`}
            className="sr-only"
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              handleFiles(e.target.files);
              e.target.value = ""; // cho chọn lại đúng tệp vừa gỡ
            }}
          />
          <label
            htmlFor={inputId}
            aria-disabled={queue.disabled || atCap}
            className="inline-flex min-h-[44px] min-w-[44px] cursor-pointer items-center justify-center gap-2 rounded-md border px-4 text-than font-medium"
            style={{
              borderColor: colorVars.borderInput,
              backgroundColor: colorVars.bgCard,
              color: colorVars.textMain,
              opacity: queue.disabled || atCap ? 0.55 : 1,
              pointerEvents: queue.disabled || atCap ? "none" : undefined,
            }}
          >
            <PictureOutlined aria-hidden />
            <span>{t("pick")}</span>
          </label>
          <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
            {t("dropHint")}
          </p>
          {policy && (
            <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
              {t("limits", {
                imageMb: Math.round(policy.image.maxBytes / (1024 * 1024)),
                videoMb: Math.round(policy.video.maxBytes / (1024 * 1024)),
                videoSeconds: policy.video.maxDurationSeconds,
                max: policy.maxAttachmentsPerPost,
              })}
            </p>
          )}
        </div>

        {policyQuery.isPending && (
          <Alert
            className="mt-2"
            type="info"
            showIcon
            message={<span className="text-than">{t("loadingPolicy")}</span>}
          />
        )}
        {policyQuery.isError && (
          <Alert
            className="mt-2"
            type="error"
            showIcon
            message={<span className="text-than">{t("policyLoadFailed")}</span>}
          />
        )}
        {!policyQuery.isPending && !postId && (
          <Alert
            className="mt-2"
            type="info"
            showIcon
            message={<span className="text-than">{t("needsDraftFirst")}</span>}
          />
        )}
        {policy && postId && atCap && (
          <Alert
            className="mt-2"
            type="warning"
            showIcon
            message={<span className="text-than">{t("atCap", { max: policy.maxAttachmentsPerPost })}</span>}
          />
        )}
      </div>

      {(media.length > 0 || queue.items.length > 0) && (
        <ul className="m-0 grid list-none grid-cols-2 gap-3 p-0 sm:grid-cols-3" data-testid="media-grid">
          {media.map((item, index) => (
            <AttachedMediaCard
              key={item.id}
              item={item}
              isFirst={index === 0}
              isLast={index === media.length - 1}
              onMoveLeft={() => moveBy(item.id, -1)}
              onMoveRight={() => moveBy(item.id, 1)}
              onRemove={() => removeMedia.mutate(item.id)}
              removing={removeMedia.isPending && removeMedia.variables === item.id}
              onAltChange={(alt) => updateAlt.mutate({ mediaId: item.id, alt })}
            />
          ))}
          {queue.items.map((item) => (
            <PendingMediaCard
              key={item.localId}
              item={item}
              policy={policy}
              onRemove={() => queue.removeItem(item.localId)}
              onRetry={() => queue.retry(item.localId)}
              onAltChange={(alt) => queue.setAlt(item.localId, alt)}
              onConfirmAlt={() => queue.confirmWithAlt(item.localId)}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

function MediaGridReadOnlyHint({ count }: { count: number }) {
  const t = useTranslations("posts.media");
  return (
    <p className="m-0 text-than text-text-muted">{t("readOnlyCount", { n: count })}</p>
  );
}

function CardShell({
  children,
  kind,
}: {
  children: React.ReactNode;
  kind: "IMAGE" | "VIDEO" | null;
}) {
  return (
    <li
      className="relative flex flex-col overflow-hidden rounded-lg border"
      style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
    >
      <div
        className="relative flex aspect-square items-center justify-center overflow-hidden"
        style={{ backgroundColor: colorVars.bgPage }}
      >
        {kind === "VIDEO" && (
          <span
            className="absolute left-1.5 top-1.5 z-10 inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-than font-medium"
            style={{ backgroundColor: colorVars.bgCard, color: colorVars.textMain }}
          >
            <VideoCameraOutlined aria-hidden />
          </span>
        )}
        {children}
      </div>
    </li>
  );
}

function AttachedMediaCard({
  item,
  isFirst,
  isLast,
  onMoveLeft,
  onMoveRight,
  onRemove,
  removing,
  onAltChange,
}: {
  item: PostMediaItem;
  isFirst: boolean;
  isLast: boolean;
  onMoveLeft: () => void;
  onMoveRight: () => void;
  onRemove: () => void;
  removing: boolean;
  onAltChange: (alt: string) => void;
}) {
  const t = useTranslations("posts.media");
  const [alt, setAltLocal] = useState(item.alt ?? "");
  const altId = useId();

  return (
    <CardShell kind={item.kind}>
      {item.kind === "IMAGE" ? (
        // eslint-disable-next-line @next/next/no-img-element -- xem trước tệp cục bộ/đã ký, không phải ảnh tối ưu hoá qua Next Image
        <img
          src={item.url}
          alt={item.alt ?? ""}
          width={item.width ?? undefined}
          height={item.height ?? undefined}
          className="h-full w-full object-cover"
        />
      ) : (
        <video
          src={item.url}
          controls
          preload="metadata"
          className="h-full w-full object-cover"
          aria-label={t("videoAttachmentLabel")}
        />
      )}
      <div
        className="absolute inset-x-1 top-1 flex items-center justify-between gap-1"
        aria-hidden={false}
      >
        <div className="flex gap-1">
          <IconButton
            label={t("moveLeft")}
            onClick={onMoveLeft}
            disabled={isFirst}
            icon={<LeftOutlined aria-hidden />}
          />
          <IconButton
            label={t("moveRight")}
            onClick={onMoveRight}
            disabled={isLast}
            icon={<RightOutlined aria-hidden />}
          />
        </div>
        <IconButton
          label={t("remove")}
          onClick={onRemove}
          loading={removing}
          icon={<CloseOutlined aria-hidden />}
          tone="danger"
        />
      </div>
      {item.kind === "IMAGE" && (
        <div className="border-t px-2 py-2" style={{ borderColor: colorVars.border }}>
          <label htmlFor={altId} className="mb-1 block text-than font-medium" style={{ color: colorVars.textMain }}>
            {t("altLabel")}
          </label>
          <Input
            id={altId}
            size="small"
            value={alt}
            placeholder={t("altPlaceholder")}
            onChange={(e) => setAltLocal(e.target.value)}
            onBlur={() => {
              if (alt.trim() && alt !== item.alt) onAltChange(alt.trim());
            }}
          />
        </div>
      )}
    </CardShell>
  );
}

function PendingMediaCard({
  item,
  policy,
  onRemove,
  onRetry,
  onAltChange,
  onConfirmAlt,
}: {
  item: PendingMediaItem;
  policy: MediaPolicy | undefined;
  onRemove: () => void;
  onRetry: () => void;
  onAltChange: (alt: string) => void;
  onConfirmAlt: () => void;
}) {
  const t = useTranslations("posts.media");
  const altId = useId();

  return (
    <CardShell kind={item.kind}>
      {item.previewUrl && item.kind === "IMAGE" && (
        // eslint-disable-next-line @next/next/no-img-element -- xem trước cục bộ (blob:), chưa phải URL đã ký
        <img src={item.previewUrl} alt="" className="h-full w-full object-cover opacity-80" />
      )}
      {item.previewUrl && item.kind === "VIDEO" && (
        <video src={item.previewUrl} muted className="h-full w-full object-cover opacity-80" aria-hidden />
      )}
      {!item.previewUrl && (
        <div className="flex flex-col items-center justify-center gap-1 px-2 text-center">
          <PictureOutlined aria-hidden style={{ color: colorVars.textMuted }} />
        </div>
      )}

      <div className="absolute inset-x-1 top-1 flex justify-end">
        <IconButton label={t("remove")} onClick={onRemove} icon={<CloseOutlined aria-hidden />} tone="danger" />
      </div>

      <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-3 text-center">
        {item.status === "uploading" && (
          <div className="w-full" data-testid="media-upload-progress" aria-live="polite">
            <Progress
              percent={item.progress}
              size="small"
              status="active"
              aria-label={t("uploadingProgress", { file: item.file.name, percent: item.progress })}
            />
            <span className="sr-only">{t("uploadingProgress", { file: item.file.name, percent: item.progress })}</span>
          </div>
        )}
        {item.status === "confirming" && (
          <p className="m-0 rounded bg-white/85 px-1.5 py-0.5 text-than" style={{ color: colorVars.textMain }}>
            {t("confirming")}
          </p>
        )}
      </div>

      {(item.status === "rejected" || item.status === "error") && (
        <div
          className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-2 text-center"
          style={{ backgroundColor: "rgba(255,255,255,0.92)" }}
          role="alert"
        >
          <p className="m-0 text-than font-semibold" style={{ color: colorVars.danger }}>
            {item.file.name}
          </p>
          <p className="m-0 text-than" style={{ color: colorVars.danger }}>
            {item.status === "rejected" ? rejectReasonText(t, item, policy) : item.errorMessage}
          </p>
          {item.status === "error" && (
            <Button size="small" icon={<ReloadOutlined aria-hidden />} onClick={onRetry}>
              {t("retry")}
            </Button>
          )}
        </div>
      )}

      {item.status === "needsAlt" && item.kind === "IMAGE" && (
        <div
          className="border-t px-2 py-2"
          style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
        >
          <label htmlFor={altId} className="mb-1 block text-than font-medium" style={{ color: colorVars.textMain }}>
            {t("altLabel")}
          </label>
          <p className="m-0 mb-1 text-than" style={{ color: colorVars.textMuted }}>
            {t("altWhy")}
          </p>
          <Input
            id={altId}
            size="small"
            value={item.alt}
            placeholder={t("altPlaceholder")}
            onChange={(e) => onAltChange(e.target.value)}
            onPressEnter={onConfirmAlt}
          />
          <Button
            type="primary"
            className="mt-1 min-h-[44px] w-full"
            disabled={!item.alt.trim()}
            onClick={onConfirmAlt}
          >
            {t("altConfirm")}
          </Button>
        </div>
      )}
    </CardShell>
  );
}

/**
 * Câu báo lỗi cho một tệp bị từ chối — HEIC/GIF/MOV/MKV được ưu tiên hiện
 * đúng khoá dịch cụ thể (`rejected.named.*`, mỗi cái kèm cách sửa thật, xem
 * `namedRejectionKey` ở `src/lib/api/media.ts`), không rơi về một câu chung.
 */
function rejectReasonText(
  t: ReturnType<typeof useTranslations>,
  item: PendingMediaItem,
  policy: MediaPolicy | undefined
): string {
  if (item.rejectReason === "NAMED_FORMAT" && item.namedFormatKey) {
    return t(`rejected.named.${item.namedFormatKey}`);
  }
  if (item.rejectReason === "TYPE") return t("rejected.type");
  if (item.rejectReason === "COUNT") return t("rejected.count", { max: policy?.maxAttachmentsPerPost ?? 0 });
  if (item.rejectReason === "DURATION" && policy) {
    return t("rejected.duration", { maxSeconds: policy.video.maxDurationSeconds });
  }
  if (item.rejectReason === "SIZE" && item.kind && policy) {
    const maxMb = Math.round(policy[item.kind === "IMAGE" ? "image" : "video"].maxBytes / (1024 * 1024));
    return t("rejected.size", { maxMb, actual: formatBytes(item.file.size) });
  }
  return t("rejected.unknown");
}

function IconButton({
  label,
  onClick,
  icon,
  disabled,
  loading,
  tone,
}: {
  label: string;
  onClick: () => void;
  icon: React.ReactNode;
  disabled?: boolean;
  loading?: boolean;
  tone?: "danger";
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled || loading}
      onClick={onClick}
      className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full border text-than shadow-sm disabled:cursor-not-allowed disabled:opacity-40"
      style={{
        backgroundColor: colorVars.bgCard,
        borderColor: colorVars.border,
        color: tone === "danger" ? colorVars.danger : colorVars.textMain,
      }}
    >
      {icon}
    </button>
  );
}

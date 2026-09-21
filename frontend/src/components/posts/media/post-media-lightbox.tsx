"use client";

import { useEffect, useState, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { Modal } from "antd";
import { LeftOutlined, RightOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import type { PostMediaItem } from "@/lib/api/posts";
import { MediaReportButton } from "./media-report-button";

export interface PostMediaLightboxProps {
  media: PostMediaItem[];
  index: number;
  postTitle: string;
  onClose: () => void;
  onIndexChange: (index: number) => void;
}

/**
 * Xem phóng to một ảnh/video — Việc 2.
 *
 * <h2>Vì sao dựng trên `antd.Modal` thay vì tự viết hộp thoại</h2>
 * `Modal` (nền `rc-dialog`) cho sẵn **bẫy tiêu điểm** trong hộp (Tab/Shift+Tab
 * không thoát ra ngoài) — thứ khó nhất trong ba yêu cầu của đề bài, và đáng để
 * không tự viết lại.
 *
 * <h2>Esc và trả tiêu điểm — KHÔNG phó mặc hoàn toàn cho antd</h2>
 * Hai effect riêng bên dưới tự làm lại hai việc còn lại, dù `rc-dialog` cũng
 * làm được: cơ chế mặc định của nó gắn với sự kiện kết thúc hiệu ứng CSS
 * (`transitionend`), và sự kiện đó có thể không bao giờ tới — máy chậm, người
 * dùng bật "giảm chuyển động", hoặc một môi trường không chạy transition thật
 * (đúng ca gặp phải khi viết bộ kiểm cho tệp này). Ghi lại phần tử đang giữ
 * tiêu điểm lúc mở rồi tự gọi `.focus()` lúc đóng, và tự nghe `keydown` cho
 * `Escape` trên `window`, là cách duy nhất chắc chắn đúng trong mọi trường
 * hợp — không chỉ trong trường hợp hiệu ứng chạy trơn tru.
 *
 * <h2>Điều hướng bằng phím mũi tên</h2>
 * `onKeyDown` bắt `ArrowLeft`/`ArrowRight` trên chính nội dung Modal — không
 * đăng ký listener toàn cục trên `window`, để không nhận phím mũi tên từ một
 * ô nhập chữ khác đang mở song song ở đâu đó trong cây (ví dụ ô `alt`).
 *
 * <h2>Video: không tự phát, không tự mở tiếng</h2>
 * `controls` là thuộc tính DUY NHẤT được đặt cứng ngoài `src`/`preload`.
 * Không `autoPlay`, không `muted` ép buộc — mặc định trình duyệt là có tiếng,
 * và vì không tự phát nên "tự mở tiếng" không bao giờ xảy ra.
 */
export function PostMediaLightbox({
  media,
  index,
  postTitle,
  onClose,
  onIndexChange,
}: PostMediaLightboxProps) {
  const t = useTranslations("posts.media");
  const [reportOpenFor, setReportOpenFor] = useState<string | null>(null);
  const item = media[index];

  useEffect(() => {
    setReportOpenFor(null);
  }, [index]);

  // `Modal` của antd tự đóng bằng Esc khi tiêu điểm còn nằm trong hộp thoại
  // (hành vi mặc định của `rc-dialog`) — nhưng KHÔNG dựa hẳn vào đó: nếu tiêu
  // điểm vì lý do gì đó thoát khỏi bẫy (một thư viện khác cưỡng focus, hoặc
  // môi trường kiểm thử không chạy đúng hiệu ứng focus lúc mở), người dùng
  // bấm Esc vẫn phải đóng được. Lắng nghe trên `window` bắt được Esc bất kể
  // tiêu điểm đang ở đâu trong trang.
  useEffect(() => {
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  // Trả tiêu điểm về đúng chỗ đã mở — TỰ LÀM, không phó mặc hoàn toàn cho
  // `rc-dialog`. Cơ chế trả tiêu điểm mặc định của nó gắn với sự kiện kết
  // thúc hiệu ứng chuyển động (CSS transition); ở một máy chậm, một người
  // dùng "giảm chuyển động", hoặc một môi trường không chạy transition thật
  // (như bộ kiểm), sự kiện đó có thể không bao giờ tới. Ghi lại phần tử đang
  // giữ tiêu điểm ngay khi hộp thoại xuất hiện — đúng là nút/thẻ vừa được
  // bấm để mở nó — và gọi lại `.focus()` khi tháo hộp thoại khỏi cây,
  // không phụ thuộc bất kỳ hiệu ứng CSS nào.
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    return () => {
      previouslyFocused?.focus?.();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- cố ý chỉ chạy một lần lúc mount/unmount
  }, []);

  if (!item) return null;

  const goTo = (next: number) => {
    if (next < 0 || next >= media.length) return;
    onIndexChange(next);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      goTo(index - 1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      goTo(index + 1);
    }
  };

  return (
    <Modal
      open
      onCancel={onClose}
      footer={null}
      width="min(92vw, 900px)"
      centered
      destroyOnClose
      title={t("lightboxTitle", { current: index + 1, total: media.length })}
      styles={{ body: { padding: 0 } }}
    >
      <div onKeyDown={handleKeyDown} className="flex flex-col gap-3">
        <div
          className="relative flex max-h-[70vh] items-center justify-center"
          style={{ backgroundColor: colorVars.bgPage }}
        >
          {media.length > 1 && (
            <NavButton
              side="left"
              label={t("prevImage")}
              disabled={index === 0}
              onClick={() => goTo(index - 1)}
            />
          )}

          {item.kind === "IMAGE" ? (
            // eslint-disable-next-line @next/next/no-img-element -- URL đã ký, xem phóng to
            <img
              src={item.url}
              alt={item.alt ?? t("openImage", { title: postTitle })}
              className="max-h-[70vh] w-auto max-w-full object-contain"
            />
          ) : (
            <video
              src={item.url}
              controls
              preload="metadata"
              aria-label={t("openVideo", { title: postTitle })}
              className="max-h-[70vh] w-auto max-w-full"
            >
              {t("videoUnsupported")}
            </video>
          )}

          {media.length > 1 && (
            <NavButton
              side="right"
              label={t("nextImage")}
              disabled={index === media.length - 1}
              onClick={() => goTo(index + 1)}
            />
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 px-4 pb-4">
          <p className="m-0 max-w-prose text-than" style={{ color: colorVars.textMain }}>
            {item.kind === "IMAGE" && item.alt ? item.alt : t("noCaption")}
          </p>
          <MediaReportButton
            mediaId={item.id}
            open={reportOpenFor === item.id}
            onOpenChange={(open) => setReportOpenFor(open ? item.id : null)}
          />
        </div>
      </div>
    </Modal>
  );
}

function NavButton({
  side,
  label,
  onClick,
  disabled,
}: {
  side: "left" | "right";
  label: string;
  onClick: () => void;
  disabled: boolean;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={`absolute top-1/2 z-10 inline-flex min-h-[44px] min-w-[44px] -translate-y-1/2 items-center justify-center rounded-full border shadow disabled:cursor-not-allowed disabled:opacity-30 ${
        side === "left" ? "left-2" : "right-2"
      }`}
      style={{ backgroundColor: colorVars.bgCard, borderColor: colorVars.border, color: colorVars.textMain }}
    >
      {side === "left" ? <LeftOutlined aria-hidden /> : <RightOutlined aria-hidden />}
    </button>
  );
}

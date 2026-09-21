"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { PlayCircleOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import type { PostMediaItem } from "@/lib/api/posts";
import { PostMediaLightbox } from "./post-media-lightbox";

export interface MediaGridProps {
  media: PostMediaItem[];
  postTitle: string;
  /** Thẻ danh sách/trang chủ — tối đa 4 ô kiểu album, ô cuối mang nhãn "+N". Trang chi tiết dùng lưới đầy đủ. */
  compact?: boolean;
}

/**
 * Lưới ảnh/video kiểu album — Việc 2. Dùng chung cho thẻ bài (`compact`) và
 * trang chi tiết (đầy đủ). Bấm một ô mở {@link PostMediaLightbox}.
 *
 * <h2>Không trang nhảy khi tệp tải xong</h2>
 * Mỗi ô là một khung `aspect-square` — kích thước khung **không phụ thuộc**
 * kích thước thật của tệp, nên trình duyệt dành đúng chỗ đó trước khi byte đầu
 * tiên của ảnh về tới. `width`/`height` trên `<img>` là lớp phòng thủ thứ hai
 * (khi trình duyệt hỗ trợ suy tỷ lệ từ chúng), không phải cơ chế chính.
 */
export function MediaGrid({ media, postTitle, compact = false }: MediaGridProps) {
  const t = useTranslations("posts.media");
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  if (media.length === 0) return null;

  const visible = compact ? media.slice(0, 4) : media;
  const hiddenCount = compact ? media.length - visible.length : 0;

  return (
    <>
      <ul
        className={`m-0 grid list-none gap-1.5 p-0 ${compact ? "grid-cols-4" : "grid-cols-2 sm:grid-cols-3"}`}
        data-testid="post-media-grid"
      >
        {visible.map((item, index) => {
          const isLastVisible = compact && index === visible.length - 1 && hiddenCount > 0;
          return (
            <li key={item.id} className="relative">
              <button
                type="button"
                className="relative block aspect-square w-full overflow-hidden rounded-md border-0 p-0"
                style={{ backgroundColor: colorVars.bgPage }}
                aria-label={
                  item.kind === "IMAGE"
                    ? (item.alt ?? t("openImage", { title: postTitle }))
                    : t("openVideo", { title: postTitle })
                }
                onClick={() => setOpenIndex(index)}
              >
                {item.kind === "IMAGE" ? (
                  // eslint-disable-next-line @next/next/no-img-element -- URL đã ký, ngắn hạn — không hợp với bộ tối ưu hoá của next/image
                  <img
                    src={item.url}
                    alt=""
                    width={item.width ?? undefined}
                    height={item.height ?? undefined}
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <>
                    <video
                      src={item.url}
                      preload="metadata"
                      muted
                      tabIndex={-1}
                      aria-hidden
                      className="h-full w-full object-cover"
                    />
                    <span className="absolute inset-0 flex items-center justify-center bg-black/20 text-white">
                      <PlayCircleOutlined aria-hidden style={{ fontSize: 28 }} />
                    </span>
                  </>
                )}
                {isLastVisible && (
                  <span className="absolute inset-0 flex items-center justify-center bg-black/50 text-lg font-semibold text-white">
                    +{hiddenCount}
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>

      {openIndex !== null && (
        <PostMediaLightbox
          media={media}
          index={openIndex}
          postTitle={postTitle}
          onClose={() => setOpenIndex(null)}
          onIndexChange={setOpenIndex}
        />
      )}
    </>
  );
}

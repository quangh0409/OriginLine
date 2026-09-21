import { useState } from "react";
import { describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import type { PostMediaItem } from "@/lib/api/media";

const { PostMediaLightbox } = await import("@/components/posts/media/post-media-lightbox");

/**
 * Việc 2 — xem phóng to (lightbox). Ba yêu cầu bắt buộc của nhiệm vụ: đóng
 * bằng `Esc`, trả tiêu điểm về đúng chỗ đã mở, và video không tự phát/tự mở
 * tiếng.
 */

const IMAGE_MEDIA: PostMediaItem[] = [
  {
    id: "m1",
    kind: "IMAGE",
    url: "data:image/png;base64,AAAA",
    alt: "Ảnh một",
    order: 0,
    createdAt: "2026-01-01T00:00:00.000Z",
  },
  {
    id: "m2",
    kind: "IMAGE",
    url: "data:image/png;base64,BBBB",
    alt: "Ảnh hai",
    order: 1,
    createdAt: "2026-01-01T00:00:00.000Z",
  },
];

const VIDEO_MEDIA: PostMediaItem[] = [
  {
    id: "v1",
    kind: "VIDEO",
    url: "data:video/mp4;base64,AAAA",
    alt: null,
    order: 0,
    createdAt: "2026-01-01T00:00:00.000Z",
  },
];

/** Bọc lightbox trong một nút mở thật — để có chỗ mà kiểm "trả tiêu điểm về đúng ảnh vừa bấm". */
function Harness({ media }: { media: PostMediaItem[] }) {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        Mở ảnh thứ nhất
      </button>
      {open && (
        <PostMediaLightbox
          media={media}
          index={0}
          postTitle="Đã hoàn thành trùng tu nhà thờ họ"
          onClose={() => setOpen(false)}
          onIndexChange={() => undefined}
        />
      )}
    </div>
  );
}

describe("PostMediaLightbox", () => {
  it("đóng bằng Esc và trả tiêu điểm về đúng nút đã mở nó", async () => {
    const { user } = renderWithProviders(<Harness media={IMAGE_MEDIA} />, { role: "member" });

    const trigger = screen.getByRole("button", { name: "Mở ảnh thứ nhất" });
    await user.click(trigger);

    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    await user.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });

  it("video KHÔNG tự phát và KHÔNG bị ép tắt/mở tiếng", async () => {
    const { user } = renderWithProviders(<Harness media={VIDEO_MEDIA} />, { role: "member" });

    await user.click(screen.getByRole("button", { name: "Mở ảnh thứ nhất" }));

    const dialog = await screen.findByRole("dialog");
    const video = dialog.querySelector("video");
    expect(video).not.toBeNull();
    expect(video).not.toHaveAttribute("autoplay");
    expect((video as HTMLVideoElement).autoplay).toBe(false);
    expect(video).toHaveAttribute("controls");
  });
});

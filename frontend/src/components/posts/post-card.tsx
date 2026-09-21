"use client";

import { Tag } from "antd";
import { useFormatter, useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { PostDto } from "@/lib/api/posts";
import { PostReviewMeta } from "./post-review-meta";
import { MediaGrid } from "./media/media-grid";

/** Rút gọn thân bài cho thẻ danh sách — không cắt giữa một từ có dấu. */
function excerpt(body: string, max = 160): string {
  const trimmed = body.trim();
  if (trimmed.length <= max) return trimmed;
  const cut = trimmed.slice(0, max);
  const lastSpace = cut.lastIndexOf(" ");
  return `${cut.slice(0, lastSpace > 40 ? lastSpace : max)}…`;
}

/** Một bài viết trong khối "Bài mới" ở trang chủ, hoặc trong danh sách đầy đủ. */
export function PostCard({ post }: { post: PostDto }) {
  const t = useTranslations("posts");
  const format = useFormatter();

  return (
    <article className="rounded-lg border border-border bg-bg-card px-4 py-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="m-0 font-serif text-[17px] font-semibold leading-snug text-text-main">
          {post.title}
        </h3>
        {post.status !== "PUBLISHED" && <PostStatusTag status={post.status} />}
      </div>

      <p className="m-0 mt-1 text-than text-text-muted">
        {t("card.byline", { author: post.authorDisplayName })}
        {isPresent(post.branchName) ? ` · ${post.branchName}` : ""}
        {isPresent(post.publishedAt)
          ? ` · ${format.dateTime(new Date(post.publishedAt), { dateStyle: "medium" })}`
          : ""}
      </p>

      <p className="m-0 mt-2 max-w-prose text-dan leading-relaxed text-text-main">
        {excerpt(post.body)}
      </p>

      {post.media.length > 0 && (
        <div className="mt-2 max-w-xs">
          <MediaGrid media={post.media} postTitle={post.title} compact />
        </div>
      )}

      <div className="mt-2">
        <PostReviewMeta post={post} />
      </div>
    </article>
  );
}

const STATUS_COLOR: Record<PostDto["status"], string> = {
  DRAFT: "default",
  PENDING: "gold",
  PUBLISHED: "green",
  WITHDRAWN: "default",
};

export function PostStatusTag({ status }: { status: PostDto["status"] }) {
  const t = useTranslations("posts");
  return (
    <Tag color={STATUS_COLOR[status]} className="!m-0 !text-than" style={{ color: colorVars.textMain }}>
      {t(`status.${status}`)}
    </Tag>
  );
}

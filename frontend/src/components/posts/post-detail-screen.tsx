"use client";

import { Alert, Empty, Skeleton } from "antd";
import { useFormatter, useTranslations } from "next-intl";
import { ApiError } from "@/lib/api/http";
import { isPresent } from "@/lib/privacy/present";
import { usePost } from "./queries";
import { PostStatusTag } from "./post-card";
import { PostReviewMeta } from "./post-review-meta";
import { MediaGrid } from "./media/media-grid";

/** Đọc một bài — `/bai-viet/{id}`. Máy chủ đã quyết bài này có lọt tới người gọi hay không (404 nếu không). */
export function PostDetailScreen({ postId }: { postId: string }) {
  const t = useTranslations("posts");
  const format = useFormatter();
  const { data, isPending, error } = usePost(postId);

  if (isPending) return <Skeleton active paragraph={{ rows: 8 }} />;

  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return notFound ? (
      <Empty description={t("detail.notFound")} />
    ) : (
      <Alert type="error" showIcon message={t("detail.loadFailed")} />
    );
  }

  const { post } = data;

  return (
    <article className="space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h1 className="m-0 font-serif text-2xl font-bold text-text-main">{post.title}</h1>
        {post.status !== "PUBLISHED" && <PostStatusTag status={post.status} />}
      </div>
      <p className="m-0 text-than text-text-muted">
        {t("card.byline", { author: post.authorDisplayName })}
        {isPresent(post.branchName) ? ` · ${post.branchName}` : ""}
        {isPresent(post.publishedAt)
          ? ` · ${format.dateTime(new Date(post.publishedAt), { dateStyle: "medium" })}`
          : ""}
      </p>
      <p className="m-0 max-w-prose whitespace-pre-wrap text-dan leading-relaxed text-text-main">
        {post.body}
      </p>
      {post.media.length > 0 && <MediaGrid media={post.media} postTitle={post.title} />}
      <PostReviewMeta post={post} />
    </article>
  );
}

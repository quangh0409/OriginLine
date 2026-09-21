"use client";

import { useState } from "react";
import { Alert, Button, Empty, Input, Skeleton } from "antd";
import { useFormatter, useTranslations } from "next-intl";
import { CheckOutlined, CloseOutlined, EditOutlined } from "@ant-design/icons";
import { useMe } from "@/hooks/use-me";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import { postsApi, type PostDto } from "@/lib/api/posts";
import { postErrorKey, postKeys, usePostsList, useReviewPost } from "./queries";
import { PostStatusTag } from "./post-card";
import { useQueryClient } from "@tanstack/react-query";

/**
 * **Hàng chờ duyệt bài** — `/quan-ly/bai-viet`. Trưởng chi/ngành và Hội đồng
 * Tộc biểu.
 *
 * <h2>Phạm vi do MÁY CHỦ cắt, không phải màn hình này</h2>
 * Cùng luật với {@code membership-queue-screen.tsx} (đọc javadoc ở đó trước
 * khi sửa tệp này): `GET /posts?status=PENDING` trả về ĐÚNG những bài mà
 * người gọi được quyền duyệt. Màn hình không lọc lại theo chi — lọc lại nghĩa
 * là dữ liệu ngoài phạm vi đã đi qua dây mạng rồi, và cửa thật thì không ai
 * kiểm nữa vì "phía client đã lọc".
 *
 * <h2>Ba việc, một hàng đợi</h2>
 * Duyệt · trả lại kèm lý do · sửa nhẹ rồi đăng — cả ba đọc từ đúng một bản ghi
 * đang hiện trên thẻ, không mở màn riêng.
 */
export function PostReviewQueueScreen() {
  const t = useTranslations("posts");
  const me = useMe();

  const roleCanReview = me.data?.role === "ADMIN" || me.data?.role === "COUNCIL" || me.data?.role === "BRANCH_HEAD";
  const queue = usePostsList({ status: "PENDING" });

  if (me.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (!me.data?.appUserId) return <Alert type="info" showIcon message={t("queue.errors.notProvisioned")} />;
  if (!roleCanReview) return <Alert type="info" showIcon message={t("queue.errors.noReviewRight")} />;

  if (queue.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (queue.isError) return <Alert type="error" showIcon message={t("queue.errors.loadFailed")} />;

  const items = queue.data?.items ?? [];
  if (items.length === 0) {
    const noScope = me.data.role === "BRANCH_HEAD" && (me.data.managedBranches.length ?? 0) === 0;
    return (
      <Empty
        description={
          <span style={{ color: colorVars.textMuted }}>
            {noScope ? t("queue.emptyNoScope") : t("queue.empty")}
          </span>
        }
      />
    );
  }

  const myPersonId = me.data.personId ?? null;

  return (
    <div className="flex flex-col gap-4">
      <p className="m-0 text-than text-text-muted">{t("queue.scopeNote", { n: items.length })}</p>
      {items.map((post) => (
        <PostQueueCard
          key={post.id}
          post={post}
          isSelf={Boolean(myPersonId) && myPersonId === post.authorPersonId}
        />
      ))}
    </div>
  );
}

function PostQueueCard({ post, isSelf }: { post: PostDto; isSelf: boolean }) {
  const t = useTranslations("posts");
  const format = useFormatter();
  const review = useReviewPost();
  const queryClient = useQueryClient();

  const [rejecting, setRejecting] = useState(false);
  const [note, setNote] = useState("");
  const [editing, setEditing] = useState(false);
  const [draftTitle, setDraftTitle] = useState(post.title);
  const [draftBody, setDraftBody] = useState(post.body);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const startEditing = () => {
    setDraftTitle(post.title);
    setDraftBody(post.body);
    setSaveError(null);
    setEditing(true);
  };

  const saveThenApprove = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      // Thẻ hàng đợi không mang `ETag` (chỉ `GET /posts/{id}` mới có) — tra lại
      // đúng trước lượt sửa để `If-Match` không bao giờ là đoán mò.
      const { etag } = await postsApi.getById(post.id);
      if (!etag) {
        setSaveError(t("queue.errors.missingEtag"));
        return;
      }
      const { data } = await postsApi.update(post.id, { title: draftTitle, body: draftBody }, etag);
      queryClient.setQueryData(postKeys.detail(post.id), { post: data, etag });
      await review.mutateAsync({ id: post.id, body: { approve: true } });
      setEditing(false);
    } catch (caught) {
      setSaveError(t(`queue.errors.${postErrorKey(caught)}`));
    } finally {
      setSaving(false);
    }
  };

  return (
    <article
      className="rounded-lg border px-4 py-4"
      style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="m-0 font-serif text-[17px] font-semibold text-text-main">{post.title}</h3>
        <PostStatusTag status={post.status} />
      </div>
      <p className="m-0 mt-1 text-than text-text-muted">
        {t("card.byline", { author: post.authorDisplayName })}
        {isPresent(post.branchName) ? ` · ${post.branchName}` : ""}
        {" · "}
        {t("queue.submittedAt", { date: format.dateTime(new Date(post.updatedAt), { dateStyle: "medium" }) })}
      </p>

      {!editing && (
        <p className="m-0 mt-2 max-w-prose whitespace-pre-wrap text-dan leading-relaxed text-text-main">
          {post.body}
        </p>
      )}

      {editing && (
        <div className="mt-2 space-y-2">
          <Input value={draftTitle} onChange={(e) => setDraftTitle(e.target.value)} maxLength={200} />
          <Input.TextArea
            value={draftBody}
            onChange={(e) => setDraftBody(e.target.value)}
            autoSize={{ minRows: 6, maxRows: 20 }}
          />
          {saveError && <Alert type="error" showIcon message={saveError} />}
        </div>
      )}

      {rejecting && (
        <div className="mt-3 space-y-2">
          <label className="block" htmlFor={`reject-note-${post.id}`}>
            <span className="mb-1 block text-than font-medium text-text-main">{t("queue.rejectReasonLabel")}</span>
            <Input.TextArea
              id={`reject-note-${post.id}`}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              autoSize={{ minRows: 2, maxRows: 6 }}
              placeholder={t("queue.rejectReasonPlaceholder")}
            />
          </label>
        </div>
      )}

      {review.isError && !rejecting && (
        <Alert className="mt-2" type="error" showIcon message={t(`queue.errors.${postErrorKey(review.error)}`)} />
      )}

      {isSelf ? (
        <p className="m-0 mt-3 text-than text-text-muted">{t("queue.cannotReviewSelf")}</p>
      ) : (
      <div className="mt-3 flex flex-wrap gap-2">
        {editing ? (
          <>
            <Button type="primary" icon={<CheckOutlined aria-hidden />} loading={saving} onClick={() => void saveThenApprove()}>
              {t("queue.saveAndApprove")}
            </Button>
            <Button onClick={() => setEditing(false)}>{t("queue.cancelEdit")}</Button>
          </>
        ) : rejecting ? (
          <>
            <Button
              danger
              icon={<CloseOutlined aria-hidden />}
              disabled={!note.trim() || review.isPending}
              loading={review.isPending}
              onClick={() => review.mutate({ id: post.id, body: { approve: false, note } })}
            >
              {t("queue.confirmReject")}
            </Button>
            <Button onClick={() => setRejecting(false)}>{t("queue.cancelEdit")}</Button>
          </>
        ) : (
          <>
            <Button
              type="primary"
              icon={<CheckOutlined aria-hidden />}
              loading={review.isPending}
              onClick={() => review.mutate({ id: post.id, body: { approve: true } })}
            >
              {t("queue.approve")}
            </Button>
            <Button icon={<EditOutlined aria-hidden />} onClick={startEditing}>
              {t("queue.editThenApprove")}
            </Button>
            <Button danger icon={<CloseOutlined aria-hidden />} onClick={() => setRejecting(true)}>
              {t("queue.reject")}
            </Button>
          </>
        )}
      </div>
      )}
    </article>
  );
}

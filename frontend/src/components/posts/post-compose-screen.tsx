"use client";

import { useEffect, useState } from "react";
import { Alert, Button, Input, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useFormatter } from "next-intl";
import { CheckCircleOutlined, SendOutlined } from "@ant-design/icons";
import { useMe } from "@/hooks/use-me";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { isPresent } from "@/lib/privacy/present";
import { postErrorKey, usePost, useSubmitPost, useWithdrawPost } from "./queries";
import { usePostDraft } from "./use-post-draft";
import { PostWriteGate } from "./post-write-gate";
import { MediaPicker } from "./media/media-picker";

export interface PostComposeScreenProps {
  /** Bỏ trống ⇒ soạn bài mới. Có giá trị ⇒ tiếp tục một nháp đã lưu, hoặc "sửa nhẹ". */
  postId?: string;
}

/**
 * Màn soạn bài — tiêu đề, thân bài, vài ảnh/video, hết. Không trình soạn thảo
 * giàu định dạng: người viết là bác 60 tuổi, không phải nhà báo (design 07 §2).
 *
 * <h2>Ảnh/video cần một bản nháp đã tồn tại</h2>
 * `MediaPicker` khoá bộ chọn tệp cho tới khi `draft.postId` có giá trị — gắn
 * một tệp cần biết gắn vào bài nào, và bài mới chỉ có id SAU lượt lưu nháp
 * đầu tiên (thường xảy ra ngay khi người dùng rời khỏi ô tiêu đề). Xem
 * `src/lib/api/media.ts` cho luồng ba bước và lý do hình dạng này vẫn là đề
 * xuất của FE (backend MinIO đang được một agent khác dựng song song).
 *
 * <h2>Vì sao không đổi URL sau lượt lưu nháp đầu tiên</h2>
 * `/bai-viet/moi` giữ nguyên URL trong suốt phiên soạn — không tự chuyển sang
 * `/bai-viet/{id}/sua` sau khi `usePostDraft` âm thầm tạo bản ghi. Điều hướng
 * ngay lúc đó đồng nghĩa dựng lại cả cây React ở dưới một route khác, và bất
 * kỳ ký tự nào gõ trong đúng khoảng round-trip của lượt lưu ấy sẽ nằm trong
 * state hiển thị của một component sắp bị huỷ. Khi đã có bản ghi, khối
 * "Đã lưu nháp" hiện một đường dẫn riêng để MỞ SANG đó — một hành động rõ ràng
 * của người dùng, không phải điều hướng ngầm giữa lúc họ còn đang gõ.
 */
export function PostComposeScreen({ postId }: PostComposeScreenProps) {
  return (
    <PostWriteGate>
      <PostComposeInner postId={postId} />
    </PostWriteGate>
  );
}

function PostComposeInner({ postId }: PostComposeScreenProps) {
  const t = useTranslations("posts");
  const format = useFormatter();
  const me = useMe();
  const existing = usePost(postId);

  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [hydrated, setHydrated] = useState(!postId);

  // Nạp nội dung ban đầu đúng MỘT lần khi bài đã tồn tại — sau đó state hiển
  // thị là của người dùng, không bị ghi đè bởi một lượt refetch nền.
  useEffect(() => {
    if (!postId || hydrated || !existing.data) return;
    setTitle(existing.data.post.title);
    setBody(existing.data.post.body);
    setHydrated(true);
  }, [postId, hydrated, existing.data]);

  const post = existing.data?.post;
  const isAuthor = !postId || Boolean(post && me.data?.personId && post.authorPersonId === me.data.personId);
  const editableStatus = !post || post.status === "DRAFT";

  const draft = usePostDraft({
    postId: postId ?? null,
    etag: existing.data?.etag ?? null,
    enabled: hydrated && Boolean(isAuthor) && editableStatus,
  });

  const submit = useSubmitPost();
  const withdraw = useWithdrawPost();

  if (postId && existing.isPending) return <Skeleton active paragraph={{ rows: 8 }} />;

  if (postId && existing.isError) {
    return <Alert type="error" showIcon message={t("compose.errors.loadFailed")} />;
  }

  if (postId && post && !isAuthor) {
    return <Alert type="warning" showIcon message={t("compose.errors.notAuthor")} />;
  }

  const canSubmit =
    editableStatus && title.trim().length > 0 && body.trim().length > 0 && draft.status !== "saving";

  const handleChange = (nextTitle: string, nextBody: string) => {
    setTitle(nextTitle);
    setBody(nextBody);
    draft.notifyChange({ title: nextTitle, body: nextBody });
  };

  const handleSubmitForReview = async () => {
    const saved = await draft.saveNow({ title, body });
    if (!saved) return; // trạng thái lưu đã tự nói rõ vì sao (conflict/hết phiên/lỗi)
    const id = draft.postId ?? postId;
    if (!id) return; // không nên xảy ra: lưu thành công luôn để lại một id
    await submit.mutateAsync(id);
  };

  return (
    <div className="space-y-4">
      <h1 className="m-0 font-serif text-2xl font-bold text-text-main">
        {postId ? t("compose.editTitle") : t("compose.newTitle")}
      </h1>

      {post?.status === "DRAFT" && isPresent(post.rejectReason) && (
        <Alert
          type="warning"
          showIcon
          message={t("compose.returnedTitle")}
          description={
            <div>
              <p className="m-0">{post.rejectReason}</p>
              {isPresent(post.reviewedAt) && (
                <p className="m-0 mt-1 text-than text-text-muted">
                  {t("compose.returnedAt", {
                    date: format.dateTime(new Date(post.reviewedAt), { dateStyle: "medium" }),
                  })}
                </p>
              )}
            </div>
          }
        />
      )}

      {!editableStatus && post && (
        <PostStatusNotice
          post={post}
          onWithdraw={() => withdraw.mutate(post.id)}
          withdrawing={withdraw.isPending}
        />
      )}

      {editableStatus && (
        <>
          <label className="block" htmlFor="post-title">
            <span className="mb-1 block text-than font-medium text-text-main">{t("compose.titleLabel")}</span>
            <Input
              id="post-title"
              size="large"
              maxLength={200}
              value={title}
              onChange={(e) => handleChange(e.target.value, body)}
              onBlur={() => void draft.saveNow({ title, body })}
              placeholder={t("compose.titlePlaceholder")}
            />
          </label>

          <label className="block" htmlFor="post-body">
            <span className="mb-1 block text-than font-medium text-text-main">{t("compose.bodyLabel")}</span>
            <Input.TextArea
              id="post-body"
              value={body}
              onChange={(e) => handleChange(title, e.target.value)}
              onBlur={() => void draft.saveNow({ title, body })}
              autoSize={{ minRows: 8, maxRows: 24 }}
              placeholder={t("compose.bodyPlaceholder")}
            />
          </label>

          <MediaPicker postId={draft.postId ?? postId ?? null} media={post?.media ?? []} editable />

          <DraftStatusLine draft={draft} />

          <div className="flex flex-wrap items-center gap-3">
            <Button
              type="primary"
              size="large"
              icon={<SendOutlined aria-hidden />}
              disabled={!canSubmit || submit.isPending}
              loading={submit.isPending}
              onClick={() => void handleSubmitForReview()}
            >
              {t("compose.submitForReview")}
            </Button>
            {draft.postId && (
              <Link href={`/bai-viet/${draft.postId}/sua`} className="text-than">
                {t("compose.openStableLink")}
              </Link>
            )}
          </div>
          {submit.isError && (
            <Alert type="error" showIcon message={t(`compose.errors.${postErrorKey(submit.error)}`)} />
          )}
        </>
      )}
    </div>
  );
}

function DraftStatusLine({ draft }: { draft: ReturnType<typeof usePostDraft> }) {
  const t = useTranslations("posts");
  const format = useFormatter();

  if (draft.status === "saving") {
    return <p className="m-0 text-than text-text-muted">{t("compose.draftSaving")}</p>;
  }
  if (draft.status === "saved" && draft.lastSavedAt) {
    return (
      <p className="m-0 flex items-center gap-1.5 text-than text-text-muted">
        <CheckCircleOutlined aria-hidden style={{ color: colorVars.successText }} />
        {t("compose.draftSaved", {
          time: format.dateTime(new Date(draft.lastSavedAt), { timeStyle: "short" }),
        })}
      </p>
    );
  }
  if (draft.status === "conflict") {
    return (
      <Alert
        type="warning"
        showIcon
        message={t("compose.conflictTitle")}
        description={
          <div className="space-y-2">
            <p className="m-0">{t("compose.conflictBody")}</p>
            <Button danger onClick={() => void draft.reloadFromServer()}>
              {t("compose.conflictReload")}
            </Button>
          </div>
        }
      />
    );
  }
  if (draft.status === "sessionExpired") {
    return (
      <Alert
        type="warning"
        showIcon
        message={t("compose.sessionExpiredTitle")}
        description={
          <div className="space-y-2">
            <p className="m-0">{t("compose.sessionExpiredBody")}</p>
            <Button onClick={draft.retryAfterSessionRestored}>{t("compose.sessionRetry")}</Button>
          </div>
        }
      />
    );
  }
  if (draft.status === "forbidden") {
    return <Alert type="error" showIcon message={t("compose.errors.forbidden")} />;
  }
  if (draft.status === "error") {
    return <Alert type="warning" showIcon message={t("compose.draftSaveFailed")} />;
  }
  return null;
}

function PostStatusNotice({
  post,
  onWithdraw,
  withdrawing,
}: {
  post: NonNullable<ReturnType<typeof usePost>["data"]>["post"];
  onWithdraw: () => void;
  withdrawing: boolean;
}) {
  const t = useTranslations("posts");
  const format = useFormatter();

  if (post.status === "PENDING") {
    return <Alert type="info" showIcon message={t("compose.pendingNotice")} />;
  }
  if (post.status === "PUBLISHED") {
    return (
      <Alert
        type="success"
        showIcon
        message={t("compose.publishedNotice", {
          date: isPresent(post.publishedAt)
            ? format.dateTime(new Date(post.publishedAt), { dateStyle: "medium" })
            : "",
        })}
        description={
          <Button danger size="small" loading={withdrawing} onClick={onWithdraw} className="mt-2">
            {t("compose.withdraw")}
          </Button>
        }
      />
    );
  }
  return <Alert type="info" showIcon message={t("compose.withdrawnNotice")} />;
}

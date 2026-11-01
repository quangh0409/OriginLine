"use client";

import { useEffect, useState } from "react";
import { Alert, Button, Input, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useFormatter } from "next-intl";
import { CheckCircleOutlined, SendOutlined } from "@ant-design/icons";
import { useMe } from "@/hooks/use-me";
import { Link, useRouter } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { isPresent } from "@/lib/privacy/present";
import type { PostDto } from "@/lib/api/posts";
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
 * <h2>Sau lượt lưu nháp đầu: ĐỔI URL, nhưng không ĐIỀU HƯỚNG</h2>
 * Bản trước giữ nguyên `/bai-viet/moi` suốt phiên soạn, với lý do vẫn đúng: một
 * lượt điều hướng (`router.replace`) sang `/bai-viet/{id}/sua` dựng lại cả cây
 * React dưới route khác, và ký tự nào gõ trong đúng khoảng round-trip ấy nằm
 * trong state của một component sắp bị huỷ. Nhưng nghiệm thu bằng trình duyệt
 * thật (24/09/2026) cho thấy cái giá của việc đứng yên: bấm F5 ở `/bai-viet/moi`
 * ra một form TRẮNG — nháp vẫn nằm trên máy chủ, nhưng bác 60 tuổi nhìn màn hình
 * thì thấy bài đã mất; gõ lại thì thành bản nháp thứ hai. Nay dùng
 * `window.history.replaceState` (cùng tiền lệ `set-password-screen.tsx`): URL đổi
 * sang `/bai-viet/{id}/sua` để F5/đánh dấu trang mở lại đúng bài này, còn cây
 * React KHÔNG dựng lại — không có lượt điều hướng nào, nên không có ký tự nào bị
 * nuốt. Đường dẫn "Mở bài này ở đường dẫn riêng" vẫn ở lại cho ai muốn chép.
 *
 * <h2>Gửi duyệt xong thì dẫn sang "Bài của tôi", kèm lời xác nhận</h2>
 * Bản trước ở yên trên form sau khi `POST /posts/{id}/submit` trả 200: với bài
 * mới không có `existing` nào để hiện `PostStatusNotice`, nên màn hình không đổi
 * một pixel — người dùng không biết đã gửi và bấm lại (đo bằng trình duyệt thật).
 * Nay mọi lượt gửi thành công dẫn sang `/bai-viet/cua-toi?daGui={id}`, nơi bài
 * hiện ở mục "Đã gửi đi" với trạng thái "Đang chờ duyệt" và một khối xác nhận
 * nói rõ ai sẽ xem tiếp.
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
  const router = useRouter();
  const existing = usePost(postId);

  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [hydrated, setHydrated] = useState(!postId);
  /** Từ lúc bấm "Gửi duyệt" tới khi xong cả lưu lẫn gửi — một trạng thái bận duy nhất cho nút. */
  const [sending, setSending] = useState(false);

  // Nạp nội dung ban đầu đúng MỘT lần khi bài đã tồn tại — sau đó state hiển
  // thị là của người dùng, không bị ghi đè bởi một lượt refetch nền.
  useEffect(() => {
    if (!postId || hydrated || !existing.data) return;
    setTitle(existing.data.post.title);
    setBody(existing.data.post.body);
    setHydrated(true);
  }, [postId, hydrated, existing.data]);

  const existingPost = existing.data?.post;
  const isAuthor =
    !postId || Boolean(existingPost && me.data?.personId && existingPost.authorPersonId === me.data.personId);
  const editableStatus = !existingPost || existingPost.status === "DRAFT";

  const draft = usePostDraft({
    postId: postId ?? null,
    etag: existing.data?.etag ?? null,
    enabled: hydrated && Boolean(isAuthor) && editableStatus,
    onCreated: replaceUrlWithStableLink,
  });

  /*
   * Bản ghi SỐNG của bài đang soạn. Với bài mới, `existing` là `usePost(undefined)` — không bao giờ
   * có dữ liệu — trong khi mọi kết quả gắn/sắp/gỡ ảnh được ghi vào cache của `draft.postId`. Đọc
   * `post.media` từ `existing` là lý do (đo bằng trình duyệt thật 24/09/2026) ảnh vừa tải lên, xác
   * nhận và gắn thành công (200 cả ba bước) BIẾN MẤT khỏi màn soạn `/bai-viet/moi`. Với nháp mở
   * lại thì hai khoá trùng nhau và đây chính là `existing`.
   */
  const live = usePost(draft.postId ?? postId);
  const post = live.data?.post ?? existingPost;

  const submit = useSubmitPost();
  const withdraw = useWithdrawPost();

  if (postId && existing.isPending) return <Skeleton active paragraph={{ rows: 8 }} />;

  if (postId && existing.isError) {
    return <Alert type="error" showIcon message={t("compose.errors.loadFailed")} />;
  }

  if (postId && post && !isAuthor) {
    return <Alert type="warning" showIcon message={t("compose.errors.notAuthor")} />;
  }

  // KHÔNG còn điều kiện `draft.status !== "saving"`: nhấn chuột vào nút làm ô nội dung mất tiêu
  // điểm ⇒ `onBlur` khởi một lượt lưu ⇒ nút bị khoá NGAY trước khi `click` tới ⇒ cú bấm đầu mất
  // trắng, không một chữ nào báo. `saveNow` nay tự chờ lượt lưu đang bay, nên bấm lúc đang lưu là
  // an toàn.
  const canSubmit = editableStatus && title.trim().length > 0 && body.trim().length > 0;

  const handleChange = (nextTitle: string, nextBody: string) => {
    setTitle(nextTitle);
    setBody(nextBody);
    draft.notifyChange({ title: nextTitle, body: nextBody });
  };

  const handleSubmitForReview = async () => {
    setSending(true);
    try {
      await saveThenSubmit();
    } finally {
      setSending(false);
    }
  };

  const saveThenSubmit = async () => {
    const saved = await draft.saveNow({ title, body });
    if (!saved) return; // trạng thái lưu đã tự nói rõ vì sao (conflict/hết phiên/lỗi)
    // Đọc từ ref, KHÔNG từ `draft.postId` của closure: với bài mới, lượt lưu vừa rồi có thể chính
    // là lượt TẠO, và closure này vẫn còn thấy `null` — bản trước dừng lặng lẽ ở đúng chỗ ấy.
    const id = draft.currentPostId() ?? postId;
    if (!id) return; // không nên xảy ra: lưu thành công luôn để lại một id
    const ok = await submit.mutateAsync(id).then(
      () => true,
      () => false // lỗi hiện ở khối `submit.isError` bên dưới
    );
    if (ok) router.push(`/bai-viet/cua-toi?daGui=${encodeURIComponent(id)}`);
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

      {!editableStatus && existingPost && post && (
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
              disabled={!canSubmit || sending}
              loading={sending}
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

/**
 * Đổi URL `/bai-viet/moi` → `/bai-viet/{id}/sua` ngay sau lượt tạo nháp, KHÔNG điều hướng — xem
 * khối "Sau lượt lưu nháp đầu" ở đầu tệp. Giữ nguyên tiền tố ngôn ngữ (`/en/...`) nếu có, và không
 * làm gì nếu URL hiện tại không phải màn bài mới (vd. đang ở `/sua` thì đã đúng rồi).
 */
function replaceUrlWithStableLink(created: PostDto) {
  if (typeof window === "undefined") return;
  const current = window.location.pathname;
  const next = current.replace(/\/bai-viet\/moi\/?$/, `/bai-viet/${encodeURIComponent(created.id)}/sua`);
  if (next !== current) window.history.replaceState(window.history.state, "", next);
}

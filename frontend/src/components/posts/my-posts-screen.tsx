"use client";

import { Alert, Button, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { EditOutlined } from "@ant-design/icons";
import { useMe } from "@/hooks/use-me";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import type { PostDto } from "@/lib/api/posts";
import { useMyPosts } from "./use-my-posts";
import { PostCard } from "./post-card";

/**
 * `/bai-viet/cua-toi` — "bài của tôi / nháp của tôi" (checklist §2, khoảng
 * trống thứ hai). Không gác bằng `PostWriteGate`: một người **từng** viết
 * bài rồi mất quyền (hiếm, nhưng khả dĩ) vẫn cần đọc lại thứ mình đã viết;
 * gác cứng ở đây sẽ khoá luôn lối vào nháp cũ của họ.
 */
export function MyPostsScreen() {
  const t = useTranslations("posts.mine");
  const me = useMe();
  const { isPending, isError, drafts, others, page, setPage, hasNext, totalPages } = useMyPosts();

  if (me.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (!me.data?.linkedToTree) {
    return (
      <Alert
        type="info"
        showIcon
        message={<span className="text-than">{t("notLinked")}</span>}
      />
    );
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="m-0 font-serif text-2xl font-bold text-text-main">{t("title")}</h1>
          <p className="m-0 mt-1 text-than text-text-muted">{t("lead")}</p>
        </div>
        <Link
          href="/bai-viet/moi"
          className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
          style={{ background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }}
        >
          <EditOutlined aria-hidden />
          <span>{t("writeAction")}</span>
        </Link>
      </header>

      {isPending && <Skeleton active paragraph={{ rows: 6 }} />}
      {isError && <Alert type="error" showIcon message={t("loadFailed")} />}

      {!isPending && !isError && (
        <>
          <PostGroup title={t("draftsTitle", { n: drafts.length })} posts={drafts} emptyText={t("draftsEmpty")} editable />
          <PostGroup title={t("othersTitle", { n: others.length })} posts={others} emptyText={t("othersEmpty")} />

          {(page > 0 || hasNext) && (
            <div className="flex justify-center gap-2">
              <Button disabled={page === 0} onClick={() => setPage(Math.max(0, page - 1))}>
                {t("prevPage")}
              </Button>
              <span className="flex items-center text-than text-text-muted">
                {t("pageOf", { page: page + 1, total: totalPages })}
              </span>
              <Button disabled={!hasNext} onClick={() => setPage(page + 1)}>
                {t("nextPage")}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function PostGroup({
  title,
  posts,
  emptyText,
  editable = false,
}: {
  title: string;
  posts: PostDto[];
  emptyText: string;
  editable?: boolean;
}) {
  return (
    <section>
      <h2 className="m-0 mb-2 font-serif text-de font-bold text-text-main">{title}</h2>
      {posts.length === 0 ? (
        <Empty description={<span style={{ color: colorVars.textMuted }}>{emptyText}</span>} />
      ) : (
        <div className="space-y-3">
          {posts.map((post) => (
            <Link
              key={post.id}
              href={editable ? `/bai-viet/${post.id}/sua` : `/bai-viet/${post.id}`}
              className="block no-underline"
            >
              <PostCard post={post} />
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

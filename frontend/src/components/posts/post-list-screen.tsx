"use client";

import { useState } from "react";
import { Alert, Button, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { EditOutlined, FileTextOutlined } from "@ant-design/icons";
import { useMe } from "@/hooks/use-me";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { usePostsList } from "./queries";
import { PostCard } from "./post-card";

const PAGE_SIZE = 10;

/**
 * `/bai-viet` — danh sách bài đã đăng. Việc 3 của checklist: trước màn này,
 * lối DUY NHẤT đọc một bài là dòng tin trên trang chủ (`home-hero.tsx` đã
 * trỏ `viewAllHref="/bai-viet"` từ trước — một đường dẫn treo, không có gì ở
 * đầu kia cho tới tệp này).
 *
 * <h2>Chỉ bài đã đăng</h2>
 * Gọi `status: "PUBLISHED"` tường minh, không bỏ trống tham số. Bỏ trống có
 * nghĩa khác hẳn ở tầng dưới (`PostJpaRepository.VISIBLE_TO_CALLER`): nó còn
 * trả về nháp/bài chờ duyệt của chính người gọi, đúng cho hàng chờ soạn
 * nhưng sai cho một trang công khai như trang này.
 */
export function PostListScreen() {
  const t = useTranslations("posts.list");
  const me = useMe();
  const [page, setPage] = useState(0);

  const query = usePostsList({ status: "PUBLISHED", page, size: PAGE_SIZE });
  const items = query.data?.items ?? [];
  const pageMeta = query.data?.page;

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="m-0 font-serif text-2xl font-bold text-text-main">{t("title")}</h1>
          <p className="m-0 mt-1 text-than text-text-muted">{t("lead")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {Boolean(me.data?.linkedToTree) && (
            <Link
              href="/bai-viet/cua-toi"
              className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
              style={{ borderColor: colorVars.primary, color: colorVars.primary, background: colorVars.bgCard }}
            >
              <FileTextOutlined aria-hidden />
              <span>{t("myPosts")}</span>
            </Link>
          )}
          <Link
            href="/bai-viet/moi"
            className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
            style={{ background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }}
          >
            <EditOutlined aria-hidden />
            <span>{t("writeAction")}</span>
          </Link>
        </div>
      </header>

      {query.isPending && <Skeleton active paragraph={{ rows: 6 }} />}
      {query.isError && <Alert type="error" showIcon message={t("loadFailed")} />}

      {!query.isPending && !query.isError && items.length === 0 && (
        <Empty description={<span style={{ color: colorVars.textMuted }}>{t("empty")}</span>} />
      )}

      <div className="space-y-3">
        {items.map((post) => (
          <Link key={post.id} href={`/bai-viet/${post.id}`} className="block no-underline">
            <PostCard post={post} />
          </Link>
        ))}
      </div>

      {pageMeta && (pageMeta.page > 0 || pageMeta.hasNext) && (
        <div className="flex justify-center gap-2">
          <Button disabled={pageMeta.page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            {t("prevPage")}
          </Button>
          <span className="flex items-center text-than text-text-muted">
            {t("pageOf", { page: pageMeta.page + 1, total: pageMeta.totalPages })}
          </span>
          <Button disabled={!pageMeta.hasNext} onClick={() => setPage((p) => p + 1)}>
            {t("nextPage")}
          </Button>
        </div>
      )}
    </div>
  );
}

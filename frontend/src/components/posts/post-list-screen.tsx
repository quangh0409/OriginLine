"use client";

import { useState } from "react";
import { Alert, Button, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { EditOutlined, FileTextOutlined } from "@ant-design/icons";
import { useMe } from "@/hooks/use-me";
import { useSignedIn } from "@/lib/auth/use-signed-in";
import { MembersOnlyNotice } from "@/components/auth/members-only-notice";
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
 * nhưng sai cho một trang chung như trang này.
 *
 * <h2>Khách: KHÔNG gọi `/posts` (26/09/2026)</h2>
 * `/posts` đòi đăng nhập — cố ý, và quyết định 26/09 giữ nguyên: mỗi bài mang
 * tên tác giả là một người đang sống. Bản trước vẫn gọi rồi nhận `401`, và màn
 * in "Chưa tải được danh sách bài viết" — sai nguyên nhân (không có gì hỏng) và
 * sai việc cần làm (thử lại bao nhiêu lần cũng vậy). Nay phiên được hỏi ở TRÌNH
 * DUYỆT trước (`useSignedIn`); khách thấy {@link MembersOnlyNotice} và không
 * một lượt gọi nào được phát. Phần có truy vấn nằm ở thành phần CON, nên hook
 * của nó chỉ chạy khi thật sự được dựng.
 */
export function PostListScreen() {
  const signedIn = useSignedIn();
  return (
    <div className="space-y-4">
      <PostListHeader />
      {signedIn === null && <Skeleton active paragraph={{ rows: 6 }} />}
      {signedIn === false && <MembersOnlyNotice />}
      {signedIn === true && <PostListBody />}
    </div>
  );
}

function PostListHeader() {
  const t = useTranslations("posts.list");
  // `useMe` tự trả hồ sơ khách mà không gọi mạng khi chưa đăng nhập (xem `probeSignedIn`).
  const me = useMe();
  return (
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
        {/* Hiện cả với khách, cùng lý do với trang chủ: cửa thật nằm ở `<PostWriteGate>`
            trên màn soạn bài, nơi giải thích vì sao. */}
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
  );
}

function PostListBody() {
  const t = useTranslations("posts.list");
  const [page, setPage] = useState(0);

  const query = usePostsList({ status: "PUBLISHED", page, size: PAGE_SIZE });
  const items = query.data?.items ?? [];
  const pageMeta = query.data?.page;

  return (
    <>
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
    </>
  );
}

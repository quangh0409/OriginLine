"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Skeleton } from "antd";
import { EditOutlined } from "@ant-design/icons";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { useSignedIn } from "@/lib/auth/use-signed-in";
import { MembersOnlyNotice } from "@/components/auth/members-only-notice";
import { useEvents } from "@/hooks/use-events";
import { EventCard } from "@/components/events/event-card";
import { PostCard, usePostsFeed } from "@/components/posts";
import { useHonoursList } from "@/components/honours";
import type { HonourDto } from "@/lib/api/honours";

/**
 * Ba khối nội dung của trang chủ: bài mới · vinh danh · việc họ sắp tới.
 *
 * <h2>Khách KHÔNG gọi ba API này — và vì sao</h2>
 * Chú thích cũ ở `home-hero.tsx` viết "bài đã đăng ai cũng xem được". Sai với
 * máy chủ thật: `/posts/feed`, `/honours`, `/events` đều nằm dưới
 * `anyRequest().authenticated()` và trả `401` cho khách (đo 24/09 trên cổng
 * 8088; hợp đồng `/posts` còn ghi thẳng "Khách vãng lai không dùng được
 * endpoint này"). Khối cũ đọc `401` thành mảng rỗng và in "Chưa có bài viết nào
 * được đăng." trong khi dòng họ đã đăng bài — một câu SAI về dữ liệu, nói với
 * đúng người không có cách nào kiểm lại.
 *
 * Nay khách thấy {@link MembersOnlyNotice}: nói thật rằng phần này cần đăng
 * nhập, kèm nút đăng nhập, và không phát lượt gọi nào.
 *
 * Bài viết đóng với khách là ĐÚNG, không chỉ là hiện trạng: mỗi bài mang tên
 * tác giả — một người đang sống — mà BA v2 §10 cấm lộ cho khách.
 *
 * <h2>Vinh danh và Việc họ đã MỞ cho khách (quyết định 26/09/2026)</h2>
 * Vinh danh và giỗ của các cụ đã khuất là công khai theo BA v2 §10; máy chủ nay
 * mở `GET /honours` và `GET /events` cho khách và tự lọc chỉ còn phần về người
 * đã khuất (`HonourService.searchForGuest`, `GuestEventPolicy`). Hai khối ấy vì
 * thế không còn bọc `MembersSection`: khách gọi thẳng và nhận tập đã lọc. Việc
 * lọc là của MÁY CHỦ — giao diện không lọc lại, vì một bản luật riêng tư thứ hai
 * ở client là thứ sớm muộn sẽ lệch.
 *
 * <h2>Lỗi thật cũng không được đọc thành "chưa có gì"</h2>
 * Mỗi danh sách có nhánh `isError` riêng: một `5xx` hay mất mạng mà in câu rỗng
 * là cùng một lời nói dối như trên, chỉ khác nguyên nhân.
 */

/**
 * Khung xương khi phiên chưa ngã ngũ, lời báo khi là khách, nội dung khi đã
 * đăng nhập. Nội dung là thành phần CON, nên hook truy vấn của nó chỉ chạy khi
 * được dựng — tức không bao giờ chạy cho khách.
 */
function MembersSection({ rows, children }: { rows: number; children: ReactNode }) {
  const signedIn = useSignedIn();
  if (signedIn === null) return <Skeleton active paragraph={{ rows }} />;
  if (!signedIn) return <MembersOnlyNotice testId="home-members-only" />;
  return <>{children}</>;
}

function Muted({ children }: { children: ReactNode }) {
  return <p className="m-0 text-than text-text-muted">{children}</p>;
}

function SectionHeader({
  title,
  viewAllHref,
  viewAllLabel,
}: {
  title: string;
  viewAllHref: string;
  viewAllLabel: string;
}) {
  return (
    <div className="mb-3 flex items-center justify-between gap-2">
      <h2 className="m-0 font-serif text-de font-bold text-text-main">{title}</h2>
      {/* `flex min-h-11 items-center` — không phải trang trí. Là <a> trần bên trong một hàng
          flex, trình duyệt BLOCKIFY nó (display tính toán thành "block", không còn "inline"),
          nên nó KHÔNG rơi vào ngoại lệ "liên kết giữa câu văn" của C-3.1/WCAG 2.5.8, và vùng
          chạm thật của nó chỉ cao bằng một dòng chữ — đo được 86–87×24px trên cả hai khung
          nhìn, dưới sàn 44px. Cùng mẫu `min-h-11 items-center` đang dùng ở NAV_LINK
          (layout/header.tsx) và MoreMenu, không phải một lớp mới. */}
      <Link href={viewAllHref} className="flex min-h-11 items-center text-than font-medium">
        {viewAllLabel}
      </Link>
    </div>
  );
}

export function PostsSection() {
  const t = useTranslations("home");

  return (
    <section>
      <SectionHeader
        title={t("posts.title")}
        viewAllHref="/bai-viet"
        viewAllLabel={t("posts.viewAll")}
      />

      {/* Nút "Viết bài mới" hiện cả với khách và người chưa duyệt: cửa thật nằm ở
          `<PostWriteGate>` trên màn soạn bài, nơi giải thích vì sao — quyết định đã chốt
          "đừng giấu nút rồi để người dùng tự đoán". */}
      <Link
        href="/bai-viet/moi"
        className="mb-3 inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
        style={{
          background: colorVars.primary,
          color: colorVars.bgCard,
          borderColor: colorVars.primary,
        }}
      >
        <EditOutlined aria-hidden />
        <span>{t("posts.writeAction")}</span>
      </Link>

      <MembersSection rows={4}>
        <PostsFeedList />
      </MembersSection>
    </section>
  );
}

function PostsFeedList() {
  const t = useTranslations("home");
  const feed = usePostsFeed({ limit: 4 });
  const posts = feed.data ?? [];

  if (feed.isPending) return <Skeleton active paragraph={{ rows: 4 }} />;
  if (feed.isError) return <Muted>{t("loadError")}</Muted>;
  if (posts.length === 0) return <Muted>{t("posts.empty")}</Muted>;
  return (
    <div className="space-y-3">
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  );
}

export function HonoursSection() {
  const t = useTranslations("home");
  return (
    <section>
      <SectionHeader
        title={t("honours.title")}
        viewAllHref="/vinh-danh"
        viewAllLabel={t("honours.viewAll")}
      />
      <HonoursList />
    </section>
  );
}

function HonoursList() {
  const t = useTranslations("home");
  // Lọc `status` ngay trong query — máy chủ đã hỗ trợ, không tải rồi lọc lại ở client.
  const query = useHonoursList({ size: 4, status: "PUBLISHED" });
  const items: HonourDto[] = query.data?.items ?? [];

  if (query.isPending) return <Skeleton active paragraph={{ rows: 4 }} />;
  if (query.isError) return <Muted>{t("loadError")}</Muted>;
  if (items.length === 0) return <Muted>{t("honours.empty")}</Muted>;
  return (
    <div className="space-y-3">
      {items.map((h) => (
        <MiniHonourLine key={h.id} honour={h} />
      ))}
    </div>
  );
}

function MiniHonourLine({ honour }: { honour: HonourDto }) {
  const t = useTranslations("honours");
  return (
    <article className="rounded-lg border border-border bg-bg-card px-4 py-3">
      <p className="m-0 text-than font-medium text-text-main">{honour.title}</p>
      <p className="m-0 mt-0.5 text-than text-text-muted">
        {t(`kind.${honour.kind}`)}
        {honour.personDisplayName ? ` · ${honour.personDisplayName}` : ""}
        {honour.year ? ` · ${honour.year}` : ""}
      </p>
    </article>
  );
}

const UPCOMING_DAYS = 60;

export function UpcomingEventsSection() {
  const t = useTranslations("home");
  return (
    <section>
      <SectionHeader
        title={t("upcomingEvents.title")}
        viewAllHref="/events"
        viewAllLabel={t("upcomingEvents.viewAll")}
      />
      <UpcomingEventsList />
    </section>
  );
}

function UpcomingEventsList() {
  const t = useTranslations("home");
  const events = useEvents({ upcomingDays: UPCOMING_DAYS, size: 3 });
  const items = events.data?.items ?? [];

  if (events.isPending) return <Skeleton active paragraph={{ rows: 3 }} />;
  if (events.isError) return <Muted>{t("loadError")}</Muted>;
  if (items.length === 0) return <Muted>{t("upcomingEvents.empty")}</Muted>;
  return (
    <div className="space-y-2">
      {items.map((event) => (
        <EventCard key={event.id} event={event} />
      ))}
    </div>
  );
}

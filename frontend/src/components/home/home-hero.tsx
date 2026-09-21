"use client";

import { useTranslations } from "next-intl";
import { Card, Skeleton, Typography } from "antd";
import {
  ApartmentOutlined,
  CalendarOutlined,
  EditOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { claimRoutes } from "@/components/claim";
import { useMe } from "@/hooks/use-me";
import { useEvents } from "@/hooks/use-events";
import { EventCard } from "@/components/events/event-card";
import { PostCard, usePostsFeed } from "@/components/posts";
import { useHonoursList } from "@/components/honours";
import type { HonourDto } from "@/lib/api/honours";

/**
 * **Trang chủ thật** — thay cho khối "sắp ra mắt" mà chính tài liệu mã của
 * phiên bản trước ghi rõ là "deliberately minimal for F0/F1: this sprint only
 * builds chrome". Bốn khối: bài mới · vinh danh · việc họ sắp tới · lối vào
 * phả đồ và tra danh xưng (checklist §2, "Trang chủ thật").
 *
 * <h2>Đây cũng là thứ người vừa đăng ký nhìn thấy trong lúc chờ duyệt</h2>
 * Quyết định chất lượng sản phẩm nói trong checklist: trang chủ phải tử tế với
 * người CHƯA được gắn vào phả — không phải một bức tường "bạn không có
 * quyền". Vì vậy:
 * <ul>
 *   <li>Không khối nào ở đây tự lọc theo `linkedToTree` — mỗi API con
 *       (`/posts/feed`, `/honours`, `/events`) đã lọc đúng phần việc của nó ở
 *       máy chủ (bài đã đăng ai cũng xem được; sự kiện/vinh danh của người còn
 *       sống thì ẩn với khách theo đúng luật đã có). Trang chủ chỉ render những
 *       gì mỗi API trả về, không đoán thêm.</li>
 *   <li>Riêng banner "đang chờ duyệt" bên dưới là thứ DUY NHẤT trang chủ tự
 *       quyết theo `MeView.linkedToTree` — vì đó là thông tin về CHÍNH người
 *       đang xem, không phải một phép lọc dữ liệu người khác.</li>
 * </ul>
 *
 * Nút "Viết bài mới" luôn hiện, kể cả cho người chưa được duyệt — cửa thật nằm
 * ở `<PostWriteGate>` trên chính màn soạn bài, nơi giải thích rõ vì sao, đúng
 * quyết định đã chốt: "đừng giấu nút rồi để người dùng tự đoán".
 */
export function HomeHero() {
  const t = useTranslations("home");
  const me = useMe();

  const pendingApproval = Boolean(me.data?.appUserId) && me.data?.linkedToTree === false;

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:py-12">
      <div className="rounded border border-border bg-bg-card px-6 py-8 text-center shadow-sm sm:px-12 sm:py-10">
        <span className="mb-4 inline-block h-1 w-16 rounded bg-accent" aria-hidden />
        <Typography.Title level={1} className="!mb-3 !text-primary">
          {t("heading")}
        </Typography.Title>
        <Typography.Paragraph className="mx-auto !mb-0 max-w-xl text-base text-text-muted">
          {t("subheading")}
        </Typography.Paragraph>
      </div>

      {pendingApproval && (
        <div
          className="mt-6 rounded-lg border px-4 py-4 sm:px-6"
          style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
        >
          <p className="m-0 font-serif text-de font-semibold text-text-main">{t("pendingNotice.title")}</p>
          <p className="m-0 mt-1 max-w-prose text-than leading-relaxed text-text-main">
            {t("pendingNotice.body")}
          </p>
          <Link href={claimRoutes.pending} className="mt-2 inline-block text-than font-medium">
            {t("pendingNotice.link")}
          </Link>
        </div>
      )}

      <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <QuickLinkCard href="/tree" icon={<ApartmentOutlined />} titleKey="tree" />
        <QuickLinkCard href="/kinship" icon={<TeamOutlined />} titleKey="kinship" />
        <QuickLinkCard href="/events" icon={<CalendarOutlined />} titleKey="events" />
      </div>

      <div className="mt-10 grid grid-cols-1 gap-8 lg:grid-cols-2">
        <PostsSection />
        <HonoursSection />
      </div>

      <div className="mt-10">
        <UpcomingEventsSection />
      </div>
    </div>
  );
}

function QuickLinkCard({
  href,
  icon,
  titleKey,
}: {
  href: string;
  icon: React.ReactNode;
  titleKey: "tree" | "kinship" | "events";
}) {
  const t = useTranslations("home");
  return (
    <Link href={href} className="block no-underline">
      <Card className="h-full border-border" variant="outlined" hoverable>
        <div className="mb-3 text-2xl text-primary">{icon}</div>
        <Typography.Text strong className="block text-text-main">
          {t(`quickLinks.${titleKey}.title`)}
        </Typography.Text>
        <Typography.Text className="mt-1 block text-than text-text-muted">
          {t(`quickLinks.${titleKey}.desc`)}
        </Typography.Text>
      </Card>
    </Link>
  );
}

function SectionHeader({ title, viewAllHref, viewAllLabel }: { title: string; viewAllHref: string; viewAllLabel: string }) {
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

function PostsSection() {
  const t = useTranslations("home");
  const feed = usePostsFeed({ limit: 4 });
  const posts = feed.data ?? [];

  return (
    <section>
      <SectionHeader title={t("posts.title")} viewAllHref="/bai-viet" viewAllLabel={t("posts.viewAll")} />

      <Link
        href="/bai-viet/moi"
        className="mb-3 inline-flex min-h-[44px] items-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
        style={{ background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }}
      >
        <EditOutlined aria-hidden />
        <span>{t("posts.writeAction")}</span>
      </Link>

      {feed.isPending && <Skeleton active paragraph={{ rows: 4 }} />}
      {!feed.isPending && posts.length === 0 && (
        <p className="m-0 text-than text-text-muted">{t("posts.empty")}</p>
      )}
      <div className="space-y-3">
        {posts.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </div>
    </section>
  );
}

function HonoursSection() {
  const t = useTranslations("home");
  // Lọc `status` ngay trong query — máy chủ đã hỗ trợ, không tải rồi lọc lại ở client.
  const query = useHonoursList({ size: 4, status: "PUBLISHED" });
  const items: HonourDto[] = query.data?.items ?? [];

  return (
    <section>
      <SectionHeader title={t("honours.title")} viewAllHref="/vinh-danh" viewAllLabel={t("honours.viewAll")} />

      {query.isPending && <Skeleton active paragraph={{ rows: 4 }} />}
      {!query.isPending && items.length === 0 && (
        <p className="m-0 text-than text-text-muted">{t("honours.empty")}</p>
      )}
      <div className="space-y-3">
        {items.map((h) => (
          <MiniHonourLine key={h.id} honour={h} />
        ))}
      </div>
    </section>
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

function UpcomingEventsSection() {
  const t = useTranslations("home");
  const events = useEvents({ upcomingDays: UPCOMING_DAYS, size: 3 });
  const items = events.data?.items ?? [];

  return (
    <section>
      <SectionHeader
        title={t("upcomingEvents.title")}
        viewAllHref="/events"
        viewAllLabel={t("upcomingEvents.viewAll")}
      />

      {events.isPending && <Skeleton active paragraph={{ rows: 3 }} />}
      {!events.isPending && items.length === 0 && (
        <p className="m-0 text-than text-text-muted">{t("upcomingEvents.empty")}</p>
      )}
      <div className="space-y-2">
        {items.map((event) => (
          <EventCard key={event.id} event={event} />
        ))}
      </div>
    </section>
  );
}

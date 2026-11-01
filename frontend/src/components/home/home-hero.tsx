"use client";

import { useTranslations } from "next-intl";
import { Card, Typography } from "antd";
import { ApartmentOutlined, CalendarOutlined, TeamOutlined } from "@ant-design/icons";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { claimRoutes } from "@/components/claim";
import { useMe } from "@/hooks/use-me";
import { clanDisplayName, useClanProfile } from "@/hooks/use-clan-profile";
import { HonoursSection, PostsSection, UpcomingEventsSection } from "./home-sections";

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
 *       máy chủ. Trang chủ chỉ render những gì mỗi API trả về, không đoán thêm.
 *       Riêng KHÁCH thì ba API ấy trả `401`, nên ba khối không gọi chúng mà nói
 *       thẳng là cần đăng nhập — xem `home-sections.tsx`. (Câu cũ ở đây, "bài đã
 *       đăng ai cũng xem được", sai với máy chủ thật và đã khiến trang chủ in
 *       "chưa có bài viết nào" cho khách.)</li>
 *   <li>Riêng banner "đang chờ duyệt" bên dưới là thứ DUY NHẤT trang chủ tự
 *       quyết theo `MeView.linkedToTree` — vì đó là thông tin về CHÍNH người
 *       đang xem, không phải một phép lọc dữ liệu người khác.</li>
 * </ul>
 *
 * Nút "Viết bài mới" luôn hiện — xem `PostsSection`.
 */
export function HomeHero() {
  const t = useTranslations("home");
  const me = useMe();
  const hasAccount = Boolean(me.data?.appUserId);

  const pendingApproval = Boolean(me.data?.appUserId) && me.data?.linkedToTree === false;

  // Tên dòng họ đọc từ hồ sơ công khai — xem javadoc `useClanProfile`. Chưa
  // tải xong hoặc chưa khởi tạo thì giữ nguyên chuỗi chung `t("heading")` cũ,
  // không có khung xương chặn tiêu đề (NFR-1: điều hướng → thẻ người đầu tiên).
  const clanProfile = useClanProfile();
  const heading = clanDisplayName(clanProfile.data, t("heading"));

  // Banner "chưa khởi tạo" — CHỈ cho ADMIN, đúng quyết định 26/09/2026: mọi
  // vai khác không thấy gì, kể cả Hội đồng/Trưởng chi (họ không làm được gì ở
  // `/quan-tri` — cổng đó khoá theo vai kỹ thuật, không theo danh xưng dòng họ).
  // Chờ CẢ HAI truy vấn tải xong trước khi quyết định hiện: hiện rồi ẩn ngay
  // là một nhấp nháy khó chịu hơn hẳn một nhịp chờ ngắn.
  const showUninitializedBanner =
    me.data?.role === "ADMIN" &&
    !me.isPending &&
    !clanProfile.isPending &&
    clanProfile.data?.initialized === false;

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:py-12">
      <div className="rounded border border-border bg-bg-card px-6 py-8 text-center shadow-sm sm:px-12 sm:py-10">
        <span className="mb-4 inline-block h-1 w-16 rounded bg-accent" aria-hidden />
        <Typography.Title level={1} className="!mb-3 !text-primary">
          {heading}
        </Typography.Title>
        <Typography.Paragraph className="mx-auto !mb-0 max-w-xl text-base text-text-muted">
          {t("subheading")}
        </Typography.Paragraph>
      </div>

      {pendingApproval && (
        <div
          className="mt-6 rounded-lg border px-4 py-4 sm:px-6"
          style={{
            background: colorVars.warningBg,
            borderColor: colorVars.borderDark,
          }}
        >
          <p className="m-0 font-serif text-de font-semibold text-text-main">
            {t("pendingNotice.title")}
          </p>
          <p className="m-0 mt-1 max-w-prose text-than leading-relaxed text-text-main">
            {t("pendingNotice.body")}
          </p>
          <Link href={claimRoutes.pending} className="mt-2 inline-block text-than font-medium">
            {t("pendingNotice.link")}
          </Link>
        </div>
      )}

      {/* CHỈ `ADMIN`, và CHỈ khi `GET /public/clan-profile` đã trả `initialized:
          false` — xem `showUninitializedBanner` ở trên. Mọi vai khác (kể cả Hội
          đồng/Trưởng chi) không thấy gì, đúng quyết định 26/09/2026: khởi tạo
          dòng họ là việc kỹ thuật của vai `ADMIN`, không phải danh xưng dòng họ. */}
      {showUninitializedBanner && (
        <div
          className="mt-6 rounded-lg border px-4 py-4 sm:px-6"
          style={{
            background: colorVars.warningBg,
            borderColor: colorVars.borderDark,
          }}
          data-testid="clan-uninitialized-banner"
        >
          <p className="m-0 font-serif text-de font-semibold text-text-main">
            {t("uninitializedNotice.title")}
          </p>
          <p className="m-0 mt-1 max-w-prose text-than leading-relaxed text-text-main">
            {t("uninitializedNotice.body")}
          </p>
          <Link href="/quan-tri" className="mt-2 inline-block text-than font-medium">
            {t("uninitializedNotice.link")}
          </Link>
        </div>
      )}

      {/*
        "Tra danh xưng" CHỈ hiện với người đã có tài khoản — cùng luật mà
        `header.tsx` áp cho mục cùng tên (`membersOnly: true`).

        Vì sao phải nhắc lại ở đây: hai chỗ cùng mời vào một màn thì phải cùng
        một luật, nếu không sẽ có một chỗ quên. Đúng chuyện đã xảy ra — thanh
        đầu trang ẩn mục ấy với khách, còn lối tắt này thì không, nên khách bấm
        vào là rơi thẳng vào một bảng đòi đăng nhập. `GET /api/v1/kinship`
        không có lối công khai nào, nên đây không phải chuyện chỉnh giao diện
        cho gọn: đó là một lời mời mà hệ thống không giữ được.

        Lưới đổi theo số ô còn lại để khách không nhìn thấy một chỗ trống.
      */}
      <div
        className={`mt-8 grid grid-cols-1 gap-4 ${
          hasAccount ? "sm:grid-cols-3" : "sm:grid-cols-2"
        }`}
      >
        <QuickLinkCard href="/tree" icon={<ApartmentOutlined />} titleKey="tree" />
        {hasAccount && <QuickLinkCard href="/kinship" icon={<TeamOutlined />} titleKey="kinship" />}
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

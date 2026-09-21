"use client";

import type { ReactNode } from "react";
import { Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { EditOutlined, LoginOutlined, UserOutlined } from "@ant-design/icons";
import { claimRoutes } from "@/components/claim";
import { useAuth } from "@/lib/auth/auth-context";
import { useMe } from "@/hooks/use-me";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";

/**
 * Cổng duy nhất trước màn soạn bài: **"viết bài là tiếng nói của người trong
 * họ"** (quyết định đã chốt của chủ dự án — design 07 §2).
 *
 * <h2>Hai lý do không viết được, và chúng cần hai câu khác nhau</h2>
 * <ul>
 *   <li><b>Chưa đăng nhập</b> ({@code me.appUserId == null}) — chưa có gì để
 *       nói tới phả cả, câu đúng là mời đăng nhập/đăng ký.</li>
 *   <li><b>Đã đăng nhập nhưng chưa được duyệt vào phả</b>
 *       ({@code me.linkedToTree === false}) — đây mới đúng là ca quyết định
 *       nói tới. Màn hình PHẢI nói ra lý do thật, không giấu nút "Viết bài" rồi
 *       để người dùng tự đoán vì sao nó biến mất.</li>
 * </ul>
 * Cổng đọc {@code MeView.linkedToTree}, không đọc vai — một System Admin kỹ
 * thuật (personId rỗng, xem CLAUDE.md "clan titles are separate from the
 * technical admin role") cũng không viết được bài chừng nào chưa gắn với một
 * nhân khẩu, đúng tinh thần "bài viết là tiếng nói của NGƯỜI trong họ" chứ
 * không phải của một vai kỹ thuật.
 *
 * Cửa thật vẫn ở máy chủ (`POST /posts` trả `403` nếu không đủ điều kiện) —
 * đây chỉ là để không hiện một biểu mẫu chắc chắn hỏng.
 */
export function PostWriteGate({ children }: { children: ReactNode }) {
  const t = useTranslations("posts");
  const me = useMe();
  const auth = useAuth();

  if (me.isPending) return <Skeleton active paragraph={{ rows: 4 }} />;

  if (!me.data?.appUserId) {
    return (
      <Notice
        icon={<LoginOutlined aria-hidden />}
        title={t("gate.needLoginTitle")}
        body={t("gate.needLoginBody")}
      >
        {/*
          HAI nút, không một — và đó là bản sửa lỗi, không phải trang trí.
          Nút cũ gộp "Đăng nhập / Đăng ký" làm MỘT, và cả hai chữ đều bấm vào
          `auth.login()` — thẳng tới trang đăng nhập của Keycloak. Realm đặt
          `registrationAllowed: false` (`infra/keycloak/realm-giapha.json`),
          nên trang ấy không có lối đăng ký nào: một người chưa từng có tài
          khoản bấm đúng theo chữ trên nút và rơi vào một trang không làm được
          việc nút vừa hứa. Đăng ký thật của sản phẩm đi qua `/dang-ky` (mã mời
          dòng họ, xem `RegisterScreen`) — một tuyến app tự dựng, không phải
          trang đăng ký chung của Keycloak, nên không gộp lại được thành một
          nút gọi cùng một hàm.
        */}
        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => auth.login()}
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
            style={{ background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }}
          >
            <LoginOutlined aria-hidden />
            <span>{t("gate.loginAction")}</span>
          </button>
          <Link
            href="/dang-ky"
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
            style={{ background: colorVars.bgCard, color: colorVars.primary, borderColor: colorVars.primary }}
          >
            <UserOutlined aria-hidden />
            <span>{t("gate.registerAction")}</span>
          </Link>
        </div>
      </Notice>
    );
  }

  if (!me.data.linkedToTree) {
    return (
      <Notice
        icon={<UserOutlined aria-hidden />}
        title={t("gate.notLinkedTitle")}
        body={t("gate.notLinkedBody")}
      >
        <div className="flex flex-wrap gap-3">
          <Link
            href={claimRoutes.start}
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
            style={{ background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }}
          >
            <UserOutlined aria-hidden />
            <span>{t("gate.claimStart")}</span>
          </Link>
          <Link
            href={claimRoutes.pending}
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-lg border px-4 text-than font-semibold no-underline"
            style={{ background: colorVars.bgCard, color: colorVars.primary, borderColor: colorVars.primary }}
          >
            <span>{t("gate.claimPending")}</span>
          </Link>
        </div>
      </Notice>
    );
  }

  return <>{children}</>;
}

function Notice({
  icon,
  title,
  body,
  children,
}: {
  icon: ReactNode;
  title: string;
  body: string;
  children: ReactNode;
}) {
  return (
    <section
      className="rounded-lg border px-4 py-5 sm:px-6"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <h1 className="m-0 flex items-center gap-2 font-serif text-de font-bold text-text-main">
        <span aria-hidden style={{ color: colorVars.accentText }}>
          {icon}
        </span>
        <span>{title}</span>
      </h1>
      <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">{body}</p>
      <div className="mt-4">{children}</div>
    </section>
  );
}

/** Icon dùng ở nút mở màn soạn bài từ nơi khác (vd. trang chủ). */
export const POST_COMPOSE_ICON = <EditOutlined aria-hidden />;

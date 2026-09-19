"use client";

import { Avatar, Button, Tag } from "antd";
import { EditOutlined, UserOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { headlineHanNom, headlineName } from "@/lib/format/name-layers";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import { PersonBadgeList } from "./person-badge-list";
import type { PersonBadge, PersonDto } from "@/types/api";

export interface PersonProfileHeaderProps {
  person: PersonDto;
  badges: PersonBadge[] | undefined;
  /** Hidden inside the tree drawer, where an edit route change would lose the canvas. */
  showEditAction?: boolean;
}

/**
 * Identity band of a profile. Carries the living/deceased distinction, which
 * is a first-class visual requirement rather than decoration:
 *  - deceased  -> aged-paper ground, deep-red rule, "đã khuất" tag;
 *  - living    -> plain card ground, quiet green dot.
 * The two must be tellable apart at a glance and WITHOUT relying on colour
 * alone, hence the tag/dot carry text for screen readers too.
 */
export function PersonProfileHeader({
  person,
  badges,
  showEditAction = true,
}: PersonProfileHeaderProps) {
  const t = useTranslations("person");
  const name = headlineName(person);
  const hanNom = headlineHanNom(person);
  const deceased = !person.isAlive;

  return (
    <header
      className="rounded-lg border px-4 py-4 sm:px-5"
      style={{
        background: deceased ? colorVars.bgDeceased : colorVars.bgCard,
        borderColor: deceased ? colorVars.borderDark : colorVars.border,
        // `accentText`, không phải `accent`: vạch 3px này PHÂN BIỆT người còn sống với người đã
        // khuất, tức là đồ hoạ mang nghĩa — WCAG 1.4.11 đòi 3:1 so với nền. Hổ phách rực chỉ đạt
        // 2,95:1. Cùng bản vá đã áp cho thanh hôn phối trên phả đồ.
        borderTop: `3px solid ${deceased ? colorVars.primary : colorVars.accentText}`,
      }}
    >
      <div className="flex items-start gap-3 sm:gap-4">
        <Avatar
          size={{ xs: 56, sm: 64, md: 72, lg: 72, xl: 72, xxl: 72 }}
          src={isPresent(person.avatarUrl) ? person.avatarUrl : undefined}
          icon={<UserOutlined />}
          className="shrink-0"
          style={{
            backgroundColor: deceased ? colorVars.borderDark : colorVars.primaryLight,
            color: deceased ? colorVars.bgCard : colorVars.primary,
          }}
          alt=""
        />

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            {/* headlineName can legitimately be absent (nothing survived the
                tier); rendering the wrapper anyway would leave a bare heading. */}
            {isPresent(name) && (
              <h1 className="m-0 break-words font-serif text-xl font-bold leading-snug text-text-main sm:text-2xl">
                {name}
              </h1>
            )}
            {deceased ? (
              // `style.color` là bắt buộc, không phải trang trí: `<Tag color={…}>` của
              // Ant Design ép chữ về TRẮNG, và trắng trên đỏ trầm ở CHẾ ĐỘ TỐI (#e08a72)
              // chỉ đạt 2,07:1 — đo được trên hồ sơ nhân khẩu. `bgPage` đúng ở cả hai
              // chiều: nền kem trên mảng trầm khi sáng, mực tối trên mảng sáng khi tối.
              <Tag
                bordered={false}
                color={colorVars.primary}
                style={{ color: colorVars.bgPage }}
                className="!m-0"
              >
                {t("deceased")}
              </Tag>
            ) : (
              <span className="inline-flex items-center gap-1.5 text-than text-text-muted">
                <span
                  aria-hidden
                  className="h-2 w-2 rounded-full"
                  style={{ background: colorVars.success }}
                />
                {t("alive")}
              </span>
            )}
          </div>

          {isPresent(hanNom) && (
            <p
              className="m-0 mt-0.5 font-serif text-lg text-text-muted"
              lang="zh-Hant"
              title={t("hanNom")}
            >
              {hanNom}
            </p>
          )}

          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-than text-text-muted">
            {isPresent(person.generation) && (
              <span>{t("generationValue", { n: person.generation })}</span>
            )}
            {isPresent(person.primaryBranch?.name) && (
              <span>{person.primaryBranch?.name}</span>
            )}
          </div>

          <div className="mt-2">
            <PersonBadgeList badges={badges} />
          </div>
        </div>

        {/* RBAC affordance comes from the server (`meta.canEdit`), never from a
            role guess on the client — a member sees no edit button at all
            rather than one that fails with 403 on submit. */}
        {showEditAction && person.meta.canEdit && (
          // `inline-flex` — xem chú thích ở person-profile.tsx: <a> bọc <Button>
          // mặc định là `display: inline`, nên vùng chạm tụt về 20px dù nút đã 44px.
          <Link href={`/persons/${person.id}/edit`} className="inline-flex">
            <Button icon={<EditOutlined />} size="small">
              <span className="hidden sm:inline">{t("edit")}</span>
            </Button>
          </Link>
        )}
      </div>
    </header>
  );
}

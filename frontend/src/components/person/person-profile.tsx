"use client";

import { Alert, Button, Empty, Skeleton, Space } from "antd";
import { ApartmentOutlined, TeamOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { usePerson } from "@/hooks/use-person";
import { usePersonBadges } from "@/hooks/use-person-badges";
import { ApiError } from "@/lib/api/http";
import { PersonBiography } from "./person-biography";
import { PersonContact } from "./person-contact";
import { PersonLifeDates } from "./person-life-dates";
import { PersonNameLayers } from "./person-name-layers";
import { PersonOriginFacts } from "./person-origin-facts";
import { PersonProfileHeader } from "./person-profile-header";
import { PersonRelations } from "./person-relations";
import { PrivacyTierNotice } from "@/components/privacy/privacy-tier-notice";
import { PrivacySharingCard } from "./privacy-sharing-card";

export interface PersonProfileProps {
  personId: string;
  /** Drawer mode drops navigation actions that would unmount the tree canvas. */
  compact?: boolean;
}

/**
 * F3 — hồ sơ nhân khẩu.
 *
 * Renders exactly what the API returned, nothing more. Every section is
 * self-hiding (see optional-field.tsx / person-section.tsx), so a guest
 * looking at a deceased ancestor and a member looking at a living cousin get
 * genuinely different page shapes rather than the same page with holes
 * punched in it.
 *
 * A 404 is rendered as plain "not found" on purpose: for a hidden living
 * person the backend answers 404 rather than 403 precisely so that the two
 * cases are indistinguishable, and adding a "you may not have permission"
 * hint here would give back exactly what that 404 was protecting.
 *
 * Cái duy nhất được phép nói về phân tầng là <PrivacyTierNotice>, và nó chỉ
 * đọc `meta.visibleTier` + `isAlive` — hai thứ nói về NGƯỜI GỌI, không phải
 * về dữ liệu của hồ sơ. Xem doc trong components/privacy/privacy-tier-notice.tsx
 * để hiểu ranh giới giữa "giải thích được" và "tiết lộ".
 */
export function PersonProfile({ personId, compact = false }: PersonProfileProps) {
  const t = useTranslations("person");
  const { data, isPending, error } = usePerson(personId);
  const { data: badges } = usePersonBadges(personId);

  if (isPending) {
    return (
      <div className="space-y-3">
        <Skeleton avatar active paragraph={{ rows: 2 }} />
        <Skeleton active paragraph={{ rows: 4 }} />
      </div>
    );
  }

  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    if (notFound) {
      return (
        <div className="py-10">
          <Empty description={t("notFound")} />
        </div>
      );
    }
    return <Alert type="error" showIcon message={t("loadError")} />;
  }

  const person = data.person;
  const etag = data.etag;

  return (
    <article className="space-y-3">
      <PersonProfileHeader person={person} badges={badges} showEditAction={!compact} />

      <PrivacyTierNotice isAlive={person.isAlive} meta={person.meta} />

      <PersonNameLayers person={person} />
      <PersonLifeDates person={person} />
      <PersonOriginFacts person={person} />
      {/* Quan hệ đứng trước liên hệ/tiểu sử: với hồ sơ dâu/rể hoặc hồ sơ ở
          tầng thấp, các mục trên tự ẩn hết và quan hệ trôi lên đầu — đúng chỗ
          nó cần đứng trong một sản phẩm gia phả. */}
      <PersonRelations person={person} />
      <PersonContact person={person} />
      <PersonBiography person={person} />

      {/* Chỉ hiện với CHÍNH CHỦ của một hồ sơ người còn sống; tự trả null trong
          mọi trường hợp khác. Đặt sau phần nội dung vì nó không phải thứ người
          khác đến đây để đọc — nhưng vẫn nằm trong trang hồ sơ chứ không phải
          trong phần cài đặt, bởi chủ thể chỉ nghĩ tới mức chia sẻ đúng lúc họ
          đang nhìn thấy chính dữ liệu ấy. */}
      <PrivacySharingCard person={person} etag={etag} />

      <Space wrap size="small">
        {/* `inline-flex`: <a> mặc định là `display: inline` nên hộp của nó chỉ cao
            bằng một dòng chữ (đo được 217×20px) dù cái <Button> bên trong đã 44px.
            Vùng chạm mà trình duyệt tính là hộp của <a>, không phải của nút. */}
        <Link href={`/tree?rootId=${person.id}`} className="inline-flex">
          <Button icon={<ApartmentOutlined />} size={compact ? "small" : "middle"}>
            {t("viewOnTree")}
          </Button>
        </Link>
        {/* `to=`, KHÔNG phải `from=`. Mở từ hồ sơ bác Đức thì câu người dùng muốn hỏi là
            "TÔI gọi bác Đức là gì?" — bác Đức là ĐÍCH. Dùng `from=` làm sản phẩm hỏi ngược
            180°: "bác Đức gọi ai là gì?", một câu chẳng ai cần. */}
        <Link href={`/kinship?to=${person.id}`} className="inline-flex">
          <Button icon={<TeamOutlined />} size={compact ? "small" : "middle"}>
            {t("lookupKinship")}
          </Button>
        </Link>
      </Space>
    </article>
  );
}

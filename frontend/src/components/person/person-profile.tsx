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

  return (
    <article className="space-y-3">
      <PersonProfileHeader person={person} badges={badges} showEditAction={!compact} />

      <PersonNameLayers person={person} />
      <PersonLifeDates person={person} />
      <PersonOriginFacts person={person} />
      <PersonContact person={person} />
      <PersonBiography person={person} />

      <Space wrap size="small">
        <Link href={`/tree?rootId=${person.id}`}>
          <Button icon={<ApartmentOutlined />} size={compact ? "small" : "middle"}>
            {t("viewOnTree")}
          </Button>
        </Link>
        <Link href={`/kinship?from=${person.id}`}>
          <Button icon={<TeamOutlined />} size={compact ? "small" : "middle"}>
            {t("lookupKinship")}
          </Button>
        </Link>
      </Space>
    </article>
  );
}

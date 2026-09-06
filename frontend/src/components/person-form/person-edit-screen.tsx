"use client";

import { Alert, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { usePerson } from "@/hooks/use-person";
import { ApiError } from "@/lib/api/http";
import { headlineName } from "@/lib/format/name-layers";
import { PersonForm } from "./person-form";

export interface PersonEditScreenProps {
  personId: string;
}

/**
 * Loads the record, then hands the form a snapshot plus the `ETag` it must
 * echo back as `If-Match` — the backend rejects a PATCH without one (412) and
 * rejects a stale one (409), which is what stops two branch heads from
 * silently overwriting each other.
 *
 * The edit affordance is gated on `meta.canEdit`, the server's own answer
 * about this caller and this record. Guessing from a role would ignore the
 * ltree branch scope: a Trưởng Chi may edit their own chi and nobody else's,
 * and only the backend knows where that boundary runs.
 */
export function PersonEditScreen({ personId }: PersonEditScreenProps) {
  const t = useTranslations("personForm");
  const { data, isPending, error } = usePerson(personId);

  if (isPending) return <Skeleton active paragraph={{ rows: 8 }} />;

  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return notFound ? (
      <Empty description={t("errors.notFound")} />
    ) : (
      <Alert type="error" showIcon message={t("errors.loadFailed")} />
    );
  }

  const { person, etag } = data;

  if (!person.meta.canEdit) {
    return <Alert type="info" showIcon message={t("errors.notEditable")} />;
  }

  if (!etag) {
    // Without an ETag the PATCH would be rejected with 412; better to say so
    // than to let the user fill in a long form that cannot be saved.
    return <Alert type="warning" showIcon message={t("errors.missingEtag")} />;
  }

  return (
    <>
      <h1 className="mb-4 font-serif text-2xl font-bold text-text-main">
        {t("editTitle", { name: headlineName(person) ?? "" })}
      </h1>
      <PersonForm mode="edit" person={person} etag={etag} />
    </>
  );
}

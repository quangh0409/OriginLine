"use client";

import { useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { PersonSection } from "./person-section";
import type { PersonDto } from "@/types/api";

export interface PersonBiographyProps {
  person: PersonDto;
}

/** Free-text notes from the clan book. Tier 3 — absent for most callers. */
export function PersonBiography({ person }: PersonBiographyProps) {
  const t = useTranslations("person");

  return (
    <PersonSection title={t("biography")} hasContent={isPresent(person.biography)}>
      <p className="m-0 whitespace-pre-line text-than leading-relaxed text-text-main">
        {person.biography}
      </p>
    </PersonSection>
  );
}

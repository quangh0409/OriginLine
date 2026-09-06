"use client";

import { useTranslations } from "next-intl";
import { anyPresent } from "@/lib/privacy/present";
import { OptionalField } from "./optional-field";
import { PersonSection } from "./person-section";
import type { PersonDto } from "@/types/api";

export interface PersonOriginFactsProps {
  person: PersonDto;
}

/**
 * Quê quán, đời thứ, chi/nhánh, nghề nghiệp, nơi ở.
 *
 * Every row is tier-gated, and the mix shifts by role rather than by person:
 * `occupation` and `currentPlaceProvince` are Tier 2, `currentPlaceFull` is
 * Tier 3. A member and a branch head looking at the same living relative will
 * see different numbers of rows here, with no marker where the others would
 * have been.
 *
 * `primaryBranch.name` is the accented display name; `primaryBranch.path` is
 * an ltree slug for RBAC scoping and is deliberately never shown.
 */
export function PersonOriginFacts({ person }: PersonOriginFactsProps) {
  const t = useTranslations("person");

  const hasContent = anyPresent(
    person.nativePlace,
    person.generation,
    person.primaryBranch?.name,
    person.occupation,
    person.currentPlaceProvince,
    person.currentPlaceFull
  );

  return (
    <PersonSection title={t("origin")} hasContent={hasContent}>
      <dl className="m-0 divide-y divide-border">
        <OptionalField label={t("nativePlace")} value={person.nativePlace} />
        <OptionalField label={t("generation")} value={person.generation}>
          {t("generationValue", { n: person.generation ?? 0 })}
        </OptionalField>
        <OptionalField label={t("branch")} value={person.primaryBranch?.name} />
        <OptionalField label={t("occupation")} value={person.occupation} />
        {/* Full address supersedes the province when both came back — showing
            both would just repeat the province inside the full address. */}
        <OptionalField
          label={t("currentPlace")}
          value={person.currentPlaceFull ?? person.currentPlaceProvince}
        />
      </dl>
    </PersonSection>
  );
}

"use client";

import { useTranslations } from "next-intl";
import { hasDisplayableDate, lunarParts } from "@/lib/format/date-dual";
import { DualDate } from "./dual-date";
import { OptionalField } from "./optional-field";
import { PersonSection } from "./person-section";
import type { PersonDto } from "@/types/api";

export interface PersonLifeDatesProps {
  person: PersonDto;
}

/**
 * Birth and death, each in both calendars.
 *
 * `death.lunar` is singled out with a giỗ note because it is the source of
 * truth for the death anniversary (CLAUDE.md) — the solar date beside it
 * drifts year to year and is not what the clan gathers on.
 */
export function PersonLifeDates({ person }: PersonLifeDatesProps) {
  const t = useTranslations("person");
  const showBirth = hasDisplayableDate(person.birth);
  const showDeath = hasDisplayableDate(person.death);
  // The giỗ note is only true when a LUNAR death date came back; a solar-only
  // record has nothing to anchor the anniversary to.
  const hasDeathLunar = lunarParts(person.death) !== undefined;

  return (
    <PersonSection title={t("lifeDates")} hasContent={showBirth || showDeath}>
      <dl className="m-0 divide-y divide-border">
        <OptionalField label={t("birth")} value={showBirth || undefined}>
          <DualDate date={person.birth} />
        </OptionalField>
        <OptionalField label={t("death")} value={showDeath || undefined}>
          <span className="flex flex-col gap-0.5">
            <DualDate date={person.death} />
            {hasDeathLunar && (
              <span className="text-than text-text-muted">{t("gioNote")}</span>
            )}
          </span>
        </OptionalField>
      </dl>
    </PersonSection>
  );
}

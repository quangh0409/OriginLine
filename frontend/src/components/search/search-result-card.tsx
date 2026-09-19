"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { PersonSummaryDto } from "@/types/api";

export interface SearchResultCardProps {
  person: PersonSummaryDto;
}

/**
 * One search hit.
 *
 * Same privacy contract as F3's profile: a field the backend withheld is
 * simply not rendered — no dash, no "•••", no lock. A list is actually the
 * *easier* place to leak, because a row of aligned empty cells advertises
 * exactly where the hidden data is, so every optional field here goes through
 * `isPresent` and the meta line is assembled from the parts that survived
 * rather than from a fixed template.
 */
export function SearchResultCard({ person }: SearchResultCardProps) {
  const t = useTranslations("search");
  const tPerson = useTranslations("person");

  // Only years that actually came back. `birthYear` alone renders as "1902–",
  // which is legitimate information (born, death year unknown or withheld);
  // a fabricated "?" on the other side would not be.
  const years = [person.birthYear, person.deathYear].filter(isPresent);
  const yearLabel =
    years.length === 2
      ? `${years[0]}–${years[1]}`
      : years.length === 1
        ? isPresent(person.birthYear)
          ? `${person.birthYear}–`
          : `–${person.deathYear}`
        : undefined;

  const metaParts = [
    isPresent(person.generation)
      ? tPerson("generationValue", { n: person.generation })
      : undefined,
    isPresent(person.primaryBranch?.name) ? person.primaryBranch?.name : undefined,
    isPresent(person.nativePlace) ? person.nativePlace : undefined,
    yearLabel,
  ].filter(isPresent);

  return (
    <li className="list-none">
      <Link
        href={`/persons/${person.id}`}
        className="flex items-start gap-3 rounded-lg border border-border bg-bg-card px-3 py-3 no-underline transition-colors hover:border-primary/40 hover:bg-primary-light sm:px-4"
      >
        <span
          aria-hidden
          className="mt-2 h-2 w-2 shrink-0 rounded-full"
          style={{
            background: person.isAlive ? colorVars.success : colorVars.borderDark,
          }}
        />
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
            <span className="font-serif text-[16px] font-semibold text-text-main">
              {person.displayName}
            </span>
            {isPresent(person.nameHanNom) && (
              <span className="font-serif text-than text-text-muted">
                {person.nameHanNom}
              </span>
            )}
            <span className="text-than text-text-muted">
              {person.isAlive ? tPerson("alive") : tPerson("deceased")}
            </span>
          </span>

          {metaParts.length > 0 && (
            <span className="mt-0.5 block text-than leading-relaxed text-text-muted">
              {metaParts.join(" · ")}
            </span>
          )}

          {isPresent(person.matchedNameType) && (
            <Tag
              bordered={false}
              className="!mt-1.5 !text-than"
              style={{ background: colorVars.warningBg, color: colorVars.textMuted }}
            >
              {t("matchedOn", { layer: tPerson(`nameType.${person.matchedNameType}`) })}
            </Tag>
          )}
        </span>
      </Link>
    </li>
  );
}

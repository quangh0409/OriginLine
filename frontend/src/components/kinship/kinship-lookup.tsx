"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Alert, Button, Empty, Skeleton } from "antd";
import { SwapOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/i18n/navigation";
import { useKinship } from "@/hooks/use-kinship";
import { usePerson } from "@/hooks/use-person";
import { ApiError } from "@/lib/api/http";
import { PersonPicker } from "@/components/person/person-picker";
import { KinshipResultCard } from "./kinship-result-card";
import type { PersonDto, PersonSummaryDto } from "@/types/api";

/**
 * F5 — tra cứu danh xưng.
 *
 * Pick two people, ask the backend, show the answer plus the LCA path that
 * justifies it. The resolution itself is 100% server-side (FR-1.3a: danh xưng
 * is configurable DATA per clan and region, not code) — this component never
 * infers a title, an elder/younger split, or a side; it only picks ids and
 * renders `KinshipResult`.
 *
 * The pair lives in the URL (`?from=&to=`) so an answer can be shared or
 * bookmarked — useful when someone asks the clan's group chat "tôi phải gọi
 * bác ấy là gì" — and so a profile can deep-link in with `from` prefilled.
 */
export function KinshipLookup() {
  const t = useTranslations("kinship");
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [fromId, setFromId] = useState<string | undefined>(
    searchParams.get("from") ?? undefined
  );
  const [toId, setToId] = useState<string | undefined>(searchParams.get("to") ?? undefined);

  // Names for the picked ids. Kept as state so a fresh pick shows instantly,
  // and backfilled from the profile query when the ids arrived via the URL.
  const [fromPerson, setFromPerson] = useState<PersonSummaryDto | undefined>();
  const [toPerson, setToPerson] = useState<PersonSummaryDto | undefined>();

  const fromSeed = useSeedSummary(fromId, fromPerson);
  const toSeed = useSeedSummary(toId, toPerson);

  useEffect(() => {
    const params = new URLSearchParams();
    if (fromId) params.set("from", fromId);
    if (toId) params.set("to", toId);
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }, [fromId, toId, pathname, router]);

  const swap = useCallback(() => {
    setFromId(toId);
    setToId(fromId);
    setFromPerson(toPerson);
    setToPerson(fromPerson);
  }, [fromId, toId, fromPerson, toPerson]);

  const { data: result, isFetching, error } = useKinship(fromId, toId);

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-border bg-bg-card p-4 sm:p-5">
        <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-end">
          <PersonPicker
            id="kinship-from"
            label={t("fromLabel")}
            value={fromId}
            seed={fromSeed}
            onChange={(id, person) => {
              setFromId(id);
              setFromPerson(person);
            }}
          />

          <Button
            icon={<SwapOutlined rotate={90} className="sm:!rotate-0" />}
            onClick={swap}
            disabled={!fromId && !toId}
            aria-label={t("swap")}
            size="large"
            className="justify-self-center"
          />

          <PersonPicker
            id="kinship-to"
            label={t("toLabel")}
            value={toId}
            seed={toSeed}
            onChange={(id, person) => {
              setToId(id);
              setToPerson(person);
            }}
          />
        </div>
        <p className="m-0 mt-3 text-than leading-relaxed text-text-muted">{t("hint")}</p>
      </div>

      {!fromId || !toId ? (
        <div className="rounded-lg border border-dashed border-border bg-bg-card py-10">
          <Empty description={t("emptyPrompt")} />
        </div>
      ) : isFetching && !result ? (
        <div className="rounded-lg border border-border bg-bg-card p-5">
          <Skeleton active paragraph={{ rows: 3 }} />
        </div>
      ) : error ? (
        <Alert
          type={error instanceof ApiError && error.status === 404 ? "info" : "error"}
          showIcon
          message={
            error instanceof ApiError && error.status === 404
              ? t("notFound")
              : t("loadError")
          }
        />
      ) : result ? (
        <KinshipResultCard result={result} fromPerson={fromSeed} toPerson={toSeed} />
      ) : null}
    </div>
  );
}

/**
 * Falls back to `/persons/{id}` when an id arrived via the URL and we have no
 * summary for it yet, so a shared link shows names instead of raw ids. Cheap:
 * React Query dedupes this against the profile page's own fetch.
 */
function useSeedSummary(
  id: string | undefined,
  picked: PersonSummaryDto | undefined
): PersonSummaryDto | undefined {
  const { data } = usePerson(picked || !id ? undefined : id);
  if (picked) return picked;
  if (!data?.person) return undefined;
  return toSummary(data.person);
}

function toSummary(person: PersonDto): PersonSummaryDto {
  return {
    id: person.id,
    displayName:
      person.displayName ?? person.names.find((n) => n.isPrimary)?.fullName ?? person.id,
    nameHanNom: person.names.find((n) => n.isPrimary)?.nameHanNom ?? undefined,
    gender: person.gender,
    generation: person.generation,
    isAlive: person.isAlive,
    birthYear: person.birth?.solar ? Number(person.birth.solar.slice(0, 4)) : undefined,
    deathYear: person.death?.solar ? Number(person.death.solar.slice(0, 4)) : undefined,
    primaryBranch: person.primaryBranch ?? undefined,
    nativePlace: person.nativePlace ?? undefined,
  };
}

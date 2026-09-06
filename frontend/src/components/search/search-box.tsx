"use client";

import { Input } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";

export interface SearchBoxProps {
  value: string;
  onChange: (next: string) => void;
  /** Live suggestion state — drives the aria-busy hint for screen readers. */
  busy?: boolean;
  id?: string;
}

/**
 * The accent-insensitive query box (F6 / FR-4.4).
 *
 * The client sends the raw string EXACTLY as typed. Folding diacritics here
 * would create a second, weaker matcher than the Postgres
 * `unaccent` + `pg_trgm` index the backend searches with, and the two would
 * disagree on words like "Đức" vs "Duc" in ways nobody could debug from the
 * UI. So: no normalisation, no client-side filtering, ever.
 *
 * Debouncing lives in the parent screen (one timer for query + filters
 * together) so a filter change and a keystroke don't fire two requests.
 */
export function SearchBox({ value, onChange, busy, id }: SearchBoxProps) {
  const t = useTranslations("search");

  return (
    <div>
      <label htmlFor={id ?? "person-search-q"} className="sr-only">
        {t("inputLabel")}
      </label>
      <Input
        id={id ?? "person-search-q"}
        size="large"
        allowClear
        // `search` gives mobile keyboards a "Tìm" action key instead of a
        // newline; autoCorrect off so Vietnamese names are not "fixed".
        type="search"
        autoCorrect="off"
        autoCapitalize="off"
        spellCheck={false}
        aria-busy={busy}
        prefix={<SearchOutlined className="text-text-muted" />}
        placeholder={t("inputPlaceholder")}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      <p className="mb-0 mt-1.5 text-[12.5px] text-text-muted">{t("unaccentedHint")}</p>
    </div>
  );
}

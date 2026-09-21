"use client";

import { useEffect, useMemo, useState } from "react";
import { Select, Spin, Tag } from "antd";
import { useTranslations } from "next-intl";
import { usePersonSearch } from "@/hooks/use-person-search";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { PersonSummaryDto } from "@/types/api";

export interface PersonPickerProps {
  value: string | undefined;
  onChange: (personId: string | undefined, person?: PersonSummaryDto) => void;
  label?: string;
  placeholder?: string;
  id?: string;
  disabled?: boolean;
  /** Pre-seeds the option list so an id passed in via the URL shows a name, not a raw id. */
  seed?: PersonSummaryDto | undefined;
  status?: "error" | "warning";
}

const DEBOUNCE_MS = 250;

/**
 * Search-and-select one person. Backed by `/persons/search`, which does
 * unaccented Vietnamese full-text search across every name layer server-side
 * — the client sends the raw query and never folds diacritics itself, so the
 * two can't disagree about what matches.
 *
 * Results are already privacy-filtered: a guest simply gets no living person
 * back. There is no "some results hidden" notice, by design.
 */
export function PersonPicker({
  value,
  onChange,
  label,
  placeholder,
  id,
  disabled,
  seed,
  status,
}: PersonPickerProps) {
  const t = useTranslations("person");
  const [rawQuery, setRawQuery] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setQuery(rawQuery), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [rawQuery]);

  // Đây luôn là một biểu mẫu của thành viên đã đăng nhập (thêm/sửa nhân khẩu,
  // link quan hệ) — Khách không bao giờ tới được màn này, nên audience cố
  // định `"member"` thay vì hỏi `useTreeAudience()` cho một câu đã biết trước
  // câu trả lời.
  const { data, isFetching } = usePersonSearch("member", { q: query, size: 20 });

  const options = useMemo(() => {
    const items = [...(data?.items ?? [])];
    // Keep the currently selected person selectable even when it is not in
    // the latest result page, otherwise the field blanks itself while typing.
    if (seed && !items.some((p) => p.id === seed.id)) items.unshift(seed);
    return items.map((person) => ({
      value: person.id,
      label: person.displayName,
      person,
    }));
  }, [data?.items, seed]);

  return (
    <label className="block" htmlFor={id}>
      {label && <span className="mb-1 block text-than text-text-muted">{label}</span>}
      <Select
        id={id}
        showSearch
        allowClear
        size="large"
        className="w-full"
        disabled={disabled}
        status={status}
        value={value}
        placeholder={placeholder ?? t("pickerPlaceholder")}
        // Filtering happens on the server (unaccented FTS); doing it again
        // here would drop legitimate matches the client can't recognise.
        filterOption={false}
        onSearch={setRawQuery}
        onChange={(next) => {
          const picked = options.find((o) => o.value === next)?.person;
          onChange(next ?? undefined, picked);
        }}
        notFoundContent={isFetching ? <Spin size="small" /> : t("pickerEmpty")}
        options={options}
        optionRender={(option) => {
          const person = option.data.person as PersonSummaryDto;
          return <PersonOptionRow person={person} />;
        }}
      />
    </label>
  );
}

function PersonOptionRow({ person }: { person: PersonSummaryDto }) {
  const t = useTranslations("person");
  // Only the years that actually came back, joined when both did. Deliberately
  // no "?" filler for a missing one: a placeholder in a list is the same hint
  // about withheld data that <OptionalField> exists to avoid.
  const years = [person.birthYear, person.deathYear].filter(isPresent).join("–");

  return (
    <div className="flex items-center justify-between gap-2 py-0.5">
      <div className="min-w-0">
        <div className="flex items-center gap-1.5">
          <span
            aria-hidden
            className="h-1.5 w-1.5 shrink-0 rounded-full"
            style={{ background: person.isAlive ? colorVars.success : colorVars.textMuted }}
          />
          <span className="truncate text-text-main">{person.displayName}</span>
        </div>
        <div className="truncate pl-3 text-than text-text-muted">
          {isPresent(person.generation) && t("generationValue", { n: person.generation })}
          {isPresent(person.primaryBranch?.name) && ` · ${person.primaryBranch?.name}`}
          {years.length > 0 && ` · ${years}`}
        </div>
      </div>
      {isPresent(person.nameHanNom) && (
        <Tag bordered={false} className="!m-0 !font-serif !text-than">
          {person.nameHanNom}
        </Tag>
      )}
    </div>
  );
}

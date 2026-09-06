"use client";

import type { ReactNode } from "react";
import { isPresent } from "@/lib/privacy/present";

export interface OptionalFieldProps {
  label: string;
  /** Presence gate. Absent (undefined / null / "" / [] / all-empty object) => nothing renders. */
  value: unknown;
  /** Custom rendering; defaults to `String(value)`. */
  children?: ReactNode;
}

/**
 * A labelled row that DISAPPEARS ENTIRELY when its value did not survive the
 * backend's privacy filtering.
 *
 * This is the component form of the rule in src/lib/privacy/present.ts, and
 * the single most important rendering decision in F3. An empty label, a dash,
 * a "•••", a lock icon — each of those silently announces "there is data here
 * you are not allowed to see", which is exactly what the tiering (BA v2 §10 /
 * Nghị định 13/2023) is there to hide, and would let a guest map out which
 * living people exist by reading the gaps. "Hidden" and "not recorded" are
 * required to be indistinguishable, so both render as nothing at all.
 *
 * Consequence to design around, not fight: sections shrink and grow between
 * roles. Callers must therefore also hide their own section headings when
 * every field inside them is absent (see `anyPresent`).
 */
export function OptionalField({ label, value, children }: OptionalFieldProps) {
  if (!isPresent(value)) return null;

  return (
    <div className="flex flex-col gap-0.5 py-2 sm:flex-row sm:gap-4 sm:py-1.5">
      <dt className="shrink-0 text-[13px] text-text-muted sm:w-40 sm:pt-px">{label}</dt>
      <dd className="m-0 min-w-0 text-[15px] leading-relaxed text-text-main">
        {children ?? String(value)}
      </dd>
    </div>
  );
}

"use client";

import type { ReactNode } from "react";

export interface PersonSectionProps {
  title: string;
  /**
   * Whether anything inside survived privacy filtering. `false` removes the
   * heading too — a section header with no rows under it is the same leak an
   * empty field would be (see optional-field.tsx).
   */
  hasContent: boolean;
  children: ReactNode;
  /** Optional right-aligned control (e.g. an edit button). */
  extra?: ReactNode;
}

/** Card-shaped block of a person profile. Mobile-first: full width, stacked. */
export function PersonSection({ title, hasContent, children, extra }: PersonSectionProps) {
  if (!hasContent) return null;

  return (
    <section className="rounded-lg border border-border bg-bg-card px-4 py-3 sm:px-5 sm:py-4">
      <div className="mb-1 flex items-baseline justify-between gap-3">
        <h2 className="m-0 font-serif text-base font-semibold text-primary sm:text-lg">
          {title}
        </h2>
        {extra}
      </div>
      {children}
    </section>
  );
}

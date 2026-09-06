import type { FieldErrors, FieldValues, Resolver } from "react-hook-form";
import type { SafeParseReturnType, ZodIssue } from "zod";

/**
 * Minimal `zod` -> react-hook-form resolver.
 *
 * Deliberately hand-written instead of pulling in `@hookform/resolvers`: both
 * `react-hook-form` and `zod` are already dependencies, the adapter between
 * them is ~40 lines, and the brief forbids adding new packages. It implements
 * the same contract the official resolver does — nested `FieldErrors` keyed by
 * field path, first issue per path wins (RHF only renders one message per
 * field anyway).
 */

/** Walks/creates the nested container for `path` and assigns the leaf error. */
function setNestedError(
  target: Record<string, unknown>,
  path: ReadonlyArray<string | number>,
  error: { type: string; message: string }
): void {
  let cursor: Record<string, unknown> = target;

  for (let i = 0; i < path.length - 1; i += 1) {
    const key = String(path[i]);
    const nextIsIndex = typeof path[i + 1] === "number";
    const existing = cursor[key];
    if (existing === undefined || typeof existing !== "object" || existing === null) {
      cursor[key] = nextIsIndex ? [] : {};
    }
    cursor = cursor[key] as Record<string, unknown>;
  }

  const leaf = String(path[path.length - 1]);
  // First issue wins: a field that fails several refinements should show one
  // message, not flicker between them as the user types.
  if (cursor[leaf] === undefined) cursor[leaf] = error;
}

function issueToPath(issue: ZodIssue): ReadonlyArray<string | number> {
  return issue.path as ReadonlyArray<string | number>;
}

/**
 * Structural minimum we need from a schema. Typed this way rather than as
 * `ZodType<...>` because `.superRefine()` produces a `ZodEffects`, whose
 * variance does not line up with `ZodType`'s three type parameters — the
 * official resolver papers over the same thing with overloads.
 */
interface ParsableSchema {
  safeParse: (data: unknown) => SafeParseReturnType<unknown, unknown>;
}

export function zodResolver<TFieldValues extends FieldValues>(
  schema: ParsableSchema
): Resolver<TFieldValues> {
  return async (values) => {
    const result = schema.safeParse(values);

    if (result.success) {
      return { values: result.data as TFieldValues, errors: {} };
    }

    const errors: Record<string, unknown> = {};
    for (const issue of result.error.issues) {
      const path = issueToPath(issue);
      if (path.length === 0) {
        // Schema-level refinement (e.g. "death must be after birth" attached
        // to the object). Surface it on a stable synthetic key so the form can
        // render it in a summary alert.
        setNestedError(errors, ["root"], { type: issue.code, message: issue.message });
        continue;
      }
      setNestedError(errors, path, { type: issue.code, message: issue.message });
    }

    return { values: {}, errors: errors as FieldErrors<TFieldValues> };
  };
}

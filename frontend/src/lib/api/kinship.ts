import { apiFetch } from "./http";
import type {
  EffectiveKinshipRuleSet,
  KinshipResult,
  KinshipRuleSetDto,
  KinshipRuleSetPage,
  KinshipRuleSetUpdateRequest,
  Region,
  RuleScope,
} from "@/types/api";

export const kinshipApi = {
  /**
   * Resolve the Vietnamese kinship title between two persons. ALWAYS goes to
   * the backend rule engine (FR-1.3a) — never recompute danh xưng
   * client-side. `status !== "RESOLVED"` is not an error; render per-status
   * messaging (see KinshipStatus doc in src/types/api.ts).
   */
  resolve: (fromId: string, toId: string, includePath = true) =>
    apiFetch<KinshipResult>("/api/v1/kinship", {
      query: { from: fromId, to: toId, includePath },
    }),

  /** Raw (per-scope) rule sets — admin screen mode. */
  getRuleSets: (params?: { scope?: RuleScope; region?: Region; page?: number; size?: number }) =>
    apiFetch<KinshipRuleSetPage>("/api/v1/kinship-rules", {
      query: { effective: false, ...params },
    }),

  /** Merged DEFAULT -> REGION -> CLAN -> BRANCH chain actually used by /kinship for a branch. */
  getEffectiveRuleSet: (branchId: string) =>
    apiFetch<EffectiveKinshipRuleSet>("/api/v1/kinship-rules", {
      query: { effective: true, branchId },
    }),

  /** COUNCIL/ADMIN only. Replaces the WHOLE rule list for one set — not a per-rule patch. */
  replaceRuleSet: (input: KinshipRuleSetUpdateRequest) =>
    apiFetch<KinshipRuleSetDto>("/api/v1/kinship-rules", {
      method: "PUT",
      body: input,
    }),
};

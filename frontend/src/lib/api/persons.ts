import { apiFetch, apiFetchWithMeta, type ApiResult } from "./http";
import type {
  CreatePersonRequest,
  PersonDto,
  PersonSummaryPage,
  UpdatePersonRequest,
} from "@/types/api";

export interface SearchPersonsParams {
  q: string;
  generation?: number;
  branchId?: string;
  nativePlace?: string;
  isAlive?: boolean;
  page?: number; // zero-based
  size?: number;
  sort?: string;
}

export const personsApi = {
  /**
   * Two-call kỵ húy flow (FR-1.6): first call WITHOUT confirmTabooOverride.
   * On `409 KY_HUY_CONFLICT`, the thrown ApiError's `.problem` carries
   * `conflicts[]` (cast to ConflictProblem) — show them, then resend the
   * identical body with `confirmTabooOverride: true`. Never default it.
   */
  create: (input: CreatePersonRequest) =>
    apiFetch<PersonDto>("/api/v1/persons", { method: "POST", body: input }),

  /** Returns the ETag alongside the body — keep it for the next PATCH's If-Match. */
  getById: (id: string): Promise<ApiResult<PersonDto>> =>
    apiFetchWithMeta<PersonDto>(`/api/v1/persons/${id}`),

  /** `ifMatch` is required by the backend (contracts/openapi.yaml IfMatch parameter). */
  update: (id: string, input: UpdatePersonRequest, ifMatch: string) =>
    apiFetchWithMeta<PersonDto>(`/api/v1/persons/${id}`, {
      method: "PATCH",
      body: input,
      ifMatch,
    }),

  /** Soft delete only (FR-1.5) — there is no hard-delete endpoint, ever. */
  remove: (id: string, reason?: string) =>
    apiFetch<void>(`/api/v1/persons/${id}`, {
      method: "DELETE",
      query: { reason },
    }),

  search: ({ q, page = 0, size = 20, ...filters }: SearchPersonsParams) =>
    apiFetch<PersonSummaryPage>("/api/v1/persons/search", {
      query: { q, page, size, ...filters },
    }),
};

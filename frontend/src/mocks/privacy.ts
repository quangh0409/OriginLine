import type { PersonDto, Role, VisibleTier } from "@/types/api";
import type { MockRole } from "./handlers/role";

/**
 * Illustrative stand-in for the backend's `PrivacyTierFilter` (W6), simplified
 * for mock purposes. The REAL rule (branch scope, self, opt-in level) is a
 * backend concern — do not port this logic anywhere near production code.
 * This exists only so F3+ screens have every tier to develop against without
 * a live backend.
 *
 * Mapping used here: guest -> caller must 404 before this runs (see
 * handlers/persons.ts); member -> T1; branch-head -> T2; admin -> T3.
 * Deceased persons are always PUBLIC (full fidelity, no filtering).
 */
function mockRoleToRole(role: MockRole): Role {
  switch (role) {
    case "admin":
      return "ADMIN";
    case "branch-head":
      return "BRANCH_HEAD";
    case "member":
      return "MEMBER";
    default:
      return "GUEST";
  }
}

function tierForRole(isAlive: boolean, role: MockRole): VisibleTier {
  if (!isAlive) return "PUBLIC";
  if (role === "admin") return "T3";
  if (role === "branch-head") return "T2";
  return "T1"; // member (guest must never reach here for a living person)
}

/**
 * Project a "full fidelity" mock PersonDto down to what a given mock role
 * would actually receive. Fields dropped from the tier are set to
 * `undefined`, which `JSON.stringify` (used by MSW's `HttpResponse.json`)
 * omits from the wire payload — the same "absent, not null" semantics as
 * the real REST contract (contracts/README §3).
 */
export function projectPersonForRole(full: PersonDto, role: MockRole): PersonDto {
  const tier = tierForRole(full.isAlive, role);

  if (tier === "PUBLIC" || tier === "T3") {
    return {
      ...full,
      meta: {
        visibleTier: tier,
        canEdit: role === "admin" || role === "branch-head",
        canDelete: role === "admin",
        canRequestCorrection: false,
        callerRole: mockRoleToRole(role),
      },
    };
  }

  const base: PersonDto = {
    id: full.id,
    isAlive: full.isAlive,
    generation: full.generation,
    gender: full.gender,
    displayName: full.displayName,
    primaryBranch: full.primaryBranch,
    // T1: only the primary name layer — húy/tự/hiệu are ritual-sensitive data.
    names: full.names.filter((n) => n.isPrimary),
    meta: {
      // A Trưởng chi can edit people in their own chi even when the tier hides
      // some of their fields — which is exactly the case the edit form has to
      // survive without wiping what it cannot see (see toUpdateRequest).
      canEdit: role === "admin" || role === "branch-head",
      canDelete: role === "admin",
      visibleTier: tier,
      canRequestCorrection: false,
      callerRole: mockRoleToRole(role),
    },
  };

  if (tier === "T1") return base;

  // T2: occupation, province-level place, native place, and birth truncated to
  // the YEAR — the exact day of birth of a living person is Tier 3 (BA v2
  // §10). Truncation happens here, on the server side of the mock, precisely
  // so the client never receives a value it is then trusted not to render.
  return {
    ...base,
    birth: truncateToYear(full.birth),
    occupation: full.occupation,
    currentPlaceProvince: full.currentPlaceProvince,
    nativePlace: full.nativePlace,
  };
}

/**
 * Year-only view of a dual date. The solar string keeps its `YYYY-MM-DD`
 * shape (the contract's declared format) but is zeroed to Jan 1 and marked
 * `precision: "YEAR"`; the lunar half is dropped entirely, because a lunar
 * day/month IS the precise birth date the tier is withholding.
 */
function truncateToYear(date: PersonDto["birth"]): PersonDto["birth"] {
  if (!date?.solar) return undefined;
  return {
    solar: `${date.solar.slice(0, 4)}-01-01`,
    lunar: undefined,
    precision: "YEAR",
  };
}

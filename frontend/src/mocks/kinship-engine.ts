import type {
  Gender,
  KinshipPathStep,
  KinshipResult,
  KinshipRuleDto,
  LcaInfo,
  RelationFacts,
  RelationSide,
} from "@/types/api";
import type { MockGraph } from "./tree-graph/build-graph";
import type { RawPerson } from "./tree-graph/generate-large-tree";

/**
 * Mock stand-in for the backend's `KinshipResolver` (TDD §8.2): LCA over the
 * graph, normalised `RelationFacts`, then a data-driven rule match. It lives
 * in src/mocks/ because it IS the fake backend — the real one runs an AGE
 * Cypher LCA plus the DEFAULT -> REGION -> CLAN -> BRANCH rule chain inside
 * Postgres. Either way the client never computes danh xưng; it calls
 * `/kinship`.
 *
 * Before this, `/kinship` returned one hard-coded answer for every pair, so F5
 * could not be built against realistic input: no genuine LCA, no multi-step
 * path, and none of the statuses (`NO_COMMON_ANCESTOR`, `NO_MATCHING_RULE`)
 * the UI is explicitly required to render differently from an error.
 */

export interface KinshipEngineParams {
  fromId: string;
  toId: string;
  rules: readonly KinshipRuleDto[];
  /** Mock PrivacyTierFilter — a hidden living person keeps its path step but loses its name. */
  isVisible: (person: RawPerson) => boolean;
  ruleSetId?: string | null;
}

/** Adoptive parent -> child edges, keyed `parentId>childId`. */
function adoptiveEdgeKeys(graph: MockGraph): Set<string> {
  const keys = new Set<string>();
  for (const edge of graph.edges) {
    if (edge.relType === "PARENT_ADOPT") keys.add(`${edge.source}>${edge.target}`);
  }
  return keys;
}

interface AncestorHop {
  distance: number;
  /** Chain walking UP from the person, excluding the person itself. */
  chain: string[];
}

function ancestorMap(graph: MockGraph, startId: string, maxDepth = 24): Map<string, AncestorHop> {
  const result = new Map<string, AncestorHop>([[startId, { distance: 0, chain: [] }]]);
  const queue: string[] = [startId];

  while (queue.length > 0) {
    const current = queue.shift()!;
    const hop = result.get(current)!;
    if (hop.distance >= maxDepth) continue;
    for (const parentId of graph.parentsOf.get(current) ?? []) {
      if (result.has(parentId)) continue;
      result.set(parentId, { distance: hop.distance + 1, chain: [...hop.chain, parentId] });
      queue.push(parentId);
    }
  }
  return result;
}

interface LcaHit {
  lcaId: string;
  distanceFrom: number;
  distanceTo: number;
  /** `from` -> ... -> lca, excluding `from`, including the lca. */
  chainFrom: string[];
  /** `to` -> ... -> lca, excluding `to`, including the lca. */
  chainTo: string[];
}

function findLca(graph: MockGraph, fromId: string, toId: string): LcaHit | null {
  const fromAncestors = ancestorMap(graph, fromId);
  const toAncestors = ancestorMap(graph, toId);

  let best: LcaHit | null = null;
  for (const [candidateId, fromHop] of fromAncestors) {
    const toHop = toAncestors.get(candidateId);
    if (!toHop) continue;
    const total = fromHop.distance + toHop.distance;
    const bestTotal = best ? best.distanceFrom + best.distanceTo : Number.POSITIVE_INFINITY;
    if (total < bestTotal) {
      best = {
        lcaId: candidateId,
        distanceFrom: fromHop.distance,
        distanceTo: toHop.distance,
        chainFrom: fromHop.chain,
        chainTo: toHop.chain,
      };
    }
  }
  return best;
}

/** Edge keys traversed by one upward chain, for adoption detection. */
function chainEdgeKeys(startId: string, chain: readonly string[]): string[] {
  return chain.map((ancestorId, index) => {
    const childId = index === 0 ? startId : chain[index - 1];
    return `${ancestorId}>${childId}`;
  });
}

/**
 * bác vs. chú: was the `to` side of the fork born before the `from` side?
 * Compares the two children of the LCA that each path descends through.
 *
 * Birth order comes from sibling order in `childrenOf` (the generator emits
 * children eldest-first, and a real backend would have `birth_order` /
 * `birth.solar` for this). `birthYear` is only a fallback — in the generated
 * graph every sibling shares a synthetic birth year, so it alone can never
 * separate bác from chú. Returns `null` when order is genuinely unknown: the
 * contract is explicit that an unknown is `null`, never a guess.
 */
function resolveIsElder(
  graph: MockGraph,
  hit: LcaHit,
  fromId: string,
  anchorId: string
): boolean | null {
  // Trực hệ (one is the other's ancestor): there is no sibling fork to compare.
  if (hit.distanceFrom === 0 || hit.distanceTo === 0) return null;

  const fromSideId =
    (hit.distanceFrom >= 2 ? hit.chainFrom[hit.chainFrom.length - 2] : fromId) ?? fromId;
  const toSideId =
    (hit.distanceTo >= 2 ? hit.chainTo[hit.chainTo.length - 2] : anchorId) ?? anchorId;

  const siblings = graph.childrenOf.get(hit.lcaId) ?? [];
  const fromIndex = siblings.indexOf(fromSideId);
  const toIndex = siblings.indexOf(toSideId);
  if (fromIndex >= 0 && toIndex >= 0 && fromIndex !== toIndex) {
    return toIndex < fromIndex;
  }

  const fromSide = graph.personsById.get(fromSideId);
  const toSide = graph.personsById.get(toSideId);
  if (!fromSide?.birthYear || !toSide?.birthYear) return null;
  if (fromSide.birthYear === toSide.birthYear) return null;
  return toSide.birthYear < fromSide.birthYear;
}

/**
 * `from` -> [marriage hop] -> fromAnchor -> UP to the LCA -> DOWN to toAnchor
 * -> [marriage hop] -> `to`. Either marriage hop is skipped when its anchor
 * is the person themselves (the ordinary blood-relative case).
 */
function buildPath(
  graph: MockGraph,
  hit: LcaHit,
  ends: { fromId: string; fromAnchorId: string; toAnchorId: string; toId: string },
  nameOf: (id: string) => string | null
): KinshipPathStep[] {
  const step = (
    id: string,
    direction: KinshipPathStep["direction"]
  ): KinshipPathStep => ({
    personId: id,
    displayName: nameOf(id),
    generation: graph.personsById.get(id)?.generation ?? null,
    direction,
    viaRelType:
      direction === "SELF" ? null : direction === "ACROSS" ? "SPOUSE" : "PARENT_BIO",
  });

  const steps: KinshipPathStep[] = [step(ends.fromId, "SELF")];

  if (ends.fromAnchorId !== ends.fromId) steps.push(step(ends.fromAnchorId, "ACROSS"));

  for (const id of hit.chainFrom) steps.push(step(id, "UP"));

  // chainTo runs toAnchor -> ... -> lca. Walking DOWN reverses it and drops
  // the lca, which the UP leg already emitted (or which is the from anchor).
  for (const id of [...hit.chainTo].reverse().slice(1)) steps.push(step(id, "DOWN"));
  if (hit.distanceTo > 0) steps.push(step(ends.toAnchorId, "DOWN"));

  if (ends.toAnchorId !== ends.toId) steps.push(step(ends.toId, "ACROSS"));

  return steps;
}

/** `null` on a rule means "any". More constrained rules win, then `priority`. */
function matchRule(
  rules: readonly KinshipRuleDto[],
  facts: RelationFacts
): KinshipRuleDto | undefined {
  const candidates = rules.filter((rule) => {
    if (rule.genDelta !== facts.genDelta) return false;
    if (rule.side !== facts.side) return false;
    if (rule.gender != null && rule.gender !== facts.targetGender) return false;
    if (rule.isElder != null && rule.isElder !== facts.isElder) return false;
    if (rule.collateralDegree != null && rule.collateralDegree !== facts.collateralDegree) {
      return false;
    }
    if (
      rule.throughMarriage != null &&
      rule.throughMarriage !== (facts.throughMarriage ?? false)
    ) {
      return false;
    }
    return true;
  });

  const specificity = (rule: KinshipRuleDto) =>
    Number(rule.gender != null) +
    Number(rule.isElder != null) +
    Number(rule.collateralDegree != null) +
    Number(rule.throughMarriage != null);

  return [...candidates].sort(
    (a, b) => specificity(b) - specificity(a) || (a.priority ?? 100) - (b.priority ?? 100)
  )[0];
}

function reciprocalOf(
  rules: readonly KinshipRuleDto[],
  facts: RelationFacts,
  matched: KinshipRuleDto | undefined,
  fromGender: Gender | undefined
): string | null {
  if (matched?.reciprocalTitle) return matched.reciprocalTitle;
  // Fall back to resolving the mirrored direction as its own lookup.
  const mirrored: RelationFacts = {
    ...facts,
    genDelta: -facts.genDelta,
    targetGender: fromGender,
    isElder: facts.isElder == null ? null : !facts.isElder,
  };
  return matchRule(rules, mirrored)?.title ?? null;
}

export function resolveKinshipMock(graph: MockGraph, params: KinshipEngineParams): KinshipResult {
  const { fromId, toId, rules, isVisible } = params;
  const base = {
    status: "NO_COMMON_ANCESTOR" as const,
    fromPersonId: fromId,
    toPersonId: toId,
  };

  const fromPerson = graph.personsById.get(fromId);
  const toPerson = graph.personsById.get(toId);
  if (!fromPerson || !toPerson) return base;
  if (fromId === toId) return { ...base, status: "SELF" };

  // Contract: keep the STEP, replace the NAME. Dropping a hidden person's step
  // would make the relation path visibly shorter than it really is.
  const nameOf = (id: string): string | null => {
    const person = graph.personsById.get(id);
    if (!person) return null;
    return isVisible(person) ? person.displayName : "—";
  };

  const adoptive = adoptiveEdgeKeys(graph);

  // --- 1. Married to each other --------------------------------------------
  if ((graph.spousesOf.get(fromId) ?? []).includes(toId)) {
    const facts: RelationFacts = {
      genDelta: 0,
      side: "IN_LAW",
      targetGender: toPerson.gender,
      isElder: null,
      throughMarriage: true,
      throughAdoption: false,
      collateralDegree: null,
    };
    const matched = matchRule(rules, facts);
    return {
      ...base,
      status: matched ? "RESOLVED" : "NO_MATCHING_RULE",
      title: matched?.title ?? null,
      reciprocalTitle: reciprocalOf(rules, facts, matched, fromPerson.gender),
      facts,
      lca: null,
      path: [
        {
          personId: fromId,
          displayName: nameOf(fromId),
          generation: fromPerson.generation,
          direction: "SELF",
          viaRelType: null,
        },
        {
          personId: toId,
          displayName: nameOf(toId),
          generation: toPerson.generation,
          direction: "ACROSS",
          viaRelType: "SPOUSE",
        },
      ],
      ruleSetId: params.ruleSetId ?? null,
      ruleSetScope: "DEFAULT",
      ruleId: matched?.id ?? null,
      ruleSetChain: ["DEFAULT"],
      cached: false,
    };
  }

  // --- 2. Blood line, else bridge exactly one marriage hop -------------------
  //
  // The bridge is tried on BOTH ends, not just `to`. A rể asking what to call
  // his father-in-law is as common a question as the reverse, and handling
  // only one direction made the feature silently asymmetric.
  let hit = findLca(graph, fromId, toId);
  let fromAnchorId = fromId;
  let toAnchorId = toId;
  let throughMarriage = false;

  if (!hit) {
    // `to` married into the clan (dâu/rể): relate `from` to `to`'s spouse.
    for (const spouseOfTo of graph.spousesOf.get(toId) ?? []) {
      const viaSpouse = findLca(graph, fromId, spouseOfTo);
      if (viaSpouse) {
        hit = viaSpouse;
        toAnchorId = spouseOfTo;
        throughMarriage = true;
        break;
      }
    }
  }

  if (!hit) {
    // `from` married into the clan: relate `from`'s spouse to `to`.
    for (const spouseOfFrom of graph.spousesOf.get(fromId) ?? []) {
      const viaSpouse = findLca(graph, spouseOfFrom, toId);
      if (viaSpouse) {
        hit = viaSpouse;
        fromAnchorId = spouseOfFrom;
        throughMarriage = true;
        break;
      }
    }
  }

  if (!hit) return base; // genuinely unrelated within the loaded graph

  const genDelta = hit.distanceTo - hit.distanceFrom;
  const collateralDegree = Math.min(hit.distanceFrom, hit.distanceTo);
  const throughAdoption = [
    ...chainEdgeKeys(fromAnchorId, hit.chainFrom),
    ...chainEdgeKeys(toAnchorId, hit.chainTo),
  ].some((key) => adoptive.has(key));

  const side: RelationSide = throughMarriage ? "IN_LAW" : "PATERNAL";

  const facts: RelationFacts = {
    genDelta,
    side,
    targetGender: toPerson.gender,
    isElder: resolveIsElder(graph, hit, fromAnchorId, toAnchorId),
    throughMarriage,
    throughAdoption,
    collateralDegree,
  };

  const matched = matchRule(rules, facts);
  const lcaPerson = graph.personsById.get(hit.lcaId);

  const lca: LcaInfo = {
    personId: hit.lcaId,
    displayName: nameOf(hit.lcaId),
    generation: lcaPerson?.generation ?? null,
    distanceFrom: hit.distanceFrom,
    distanceTo: hit.distanceTo,
  };

  const path = buildPath(graph, hit, { fromId, fromAnchorId, toAnchorId, toId }, nameOf);

  return {
    ...base,
    status: matched ? "RESOLVED" : "NO_MATCHING_RULE",
    title: matched?.title ?? null,
    reciprocalTitle: reciprocalOf(rules, facts, matched, fromPerson.gender),
    titleEn: matched?.note ?? null,
    facts,
    lca,
    path,
    ruleSetId: params.ruleSetId ?? null,
    ruleSetScope: "DEFAULT",
    ruleId: matched?.id ?? null,
    ruleSetChain: ["DEFAULT"],
    cached: false,
  };
}

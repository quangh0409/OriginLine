import { generateLargeTree, type GeneratedGraph, type RawEdge, type RawPerson } from "./generate-large-tree";

/**
 * Builds (once, lazily, module-singleton) the graph MSW's `/tree` and
 * GraphQL `tree` resolver query against. Default size ~4,000 persons —
 * deliberately "a few thousand" per the F2 brief, so lazy-loading and
 * virtualization have something real to prove themselves against instead of
 * the ~5-node hand-authored fixture that shipped in Sprint 1.
 *
 * The curated "story" individuals from src/mocks/data.ts (THUY_TO / p-001,
 * chi1Ancestor / p-010, chi2Ancestor / p-011, chi1Daughter / p-020,
 * chi1SonInLaw / p-021, LIVING_MEMBER_FULL / p-100, LIVING_MINOR_FULL /
 * p-101) are grafted into this generated graph by id, NOT duplicated —
 * `generateLargeTree` is told to use those exact ids for the generation-2
 * chi heads, and the two "living" fixtures are spliced in by renaming
 * generated placeholders after the fact (see `graftStoryFixtures` below).
 * This keeps `/persons/{id}` (data.ts) and `/tree` (this module) answering
 * about the SAME person for every id the two module's docs cross-reference.
 */

export interface MockGraph {
  personsById: Map<string, RawPerson>;
  edges: RawEdge[];
  /** Adjacency, precomputed once for O(1) BFS steps instead of O(n) scans. */
  childrenOf: Map<string, string[]>; // via PARENT_BIO/PARENT_ADOPT, source -> [target,...]
  parentsOf: Map<string, string[]>; // reverse of the above
  spousesOf: Map<string, string[]>; // SPOUSE, either direction
  rootId: string;
}

function renamePersonId(graph: GeneratedGraph, oldId: string, newId: string) {
  const person = graph.persons.find((p) => p.id === oldId);
  if (person) person.id = newId;
  for (const e of graph.edges) {
    if (e.source === oldId) e.source = newId;
    if (e.target === oldId) e.target = newId;
  }
}

/**
 * Grafts the two "living person" fixtures (p-100 member, p-101 restricted
 * minor) and the chi-1 daughter/son-in-law pair (p-020/p-021) into whatever
 * the generator produced, by renaming an already-connected generated
 * placeholder rather than inserting a disconnected node — every id in the
 * result stays reachable from the root via real PARENT_BIO/SPOUSE edges,
 * which the guest-visibility BFS test (contracts/README §7.3, "cây đứt
 * đoạn") depends on.
 */
function graftStoryFixtures(graph: GeneratedGraph): void {
  // --- chi1Daughter (p-020) + chi1SonInLaw (p-021) ---------------------
  // Prefer a daughter of p-010 who already has a husband (SPOUSE edge), so
  // both curated fixtures graft in together; only ~60% of generated
  // daughters get one (see generate-large-tree.ts), so we search all of
  // p-010's daughters rather than assuming the first one qualifies.
  const p010Daughters = graph.edges.filter(
    (e) =>
      e.relType === "PARENT_BIO" &&
      e.source === "p-010" &&
      graph.persons.find((p) => p.id === e.target)?.gender === "FEMALE"
  );
  const daughterEdge =
    p010Daughters.find((d) => graph.edges.some((e) => e.relType === "SPOUSE" && e.target === d.target)) ??
    p010Daughters[0];
  if (daughterEdge) {
    const oldDaughterId = daughterEdge.target;
    const husbandEdge = graph.edges.find(
      (e) => e.relType === "SPOUSE" && e.target === oldDaughterId
    );
    renamePersonId(graph, oldDaughterId, "p-020");
    const daughter = graph.persons.find((p) => p.id === "p-020");
    if (daughter) {
      daughter.displayName = "Nguyễn Thị Ngọc";
      daughter.badges = ["DECEASED"];
    }
    if (husbandEdge) {
      renamePersonId(graph, husbandEdge.source, "p-021");
      const husband = graph.persons.find((p) => p.id === "p-021");
      if (husband) {
        husband.displayName = "Trần Văn Khoa";
        husband.badges = ["RE", "DECEASED"];
      }
    }
  }

  // --- LIVING_MEMBER_FULL (p-100) ---------------------------------------
  const chi1Branch = "b-chi1";
  const candidate = graph.persons.find(
    (p) =>
      p.generation === 5 &&
      p.gender === "MALE" &&
      p.isAlive &&
      p.primaryBranch?.id === chi1Branch
  );
  const fallbackParent = graph.persons.find(
    (p) => p.generation === 4 && p.primaryBranch?.id === chi1Branch
  );

  let livingMemberId: string | null = null;
  if (candidate) {
    renamePersonId(graph, candidate.id, "p-100");
    candidate.id = "p-100";
    candidate.displayName = "Nguyễn Văn An";
    candidate.badges = [];
    livingMemberId = "p-100";
  } else if (fallbackParent) {
    // Deterministic seed didn't happen to produce a gen-5 living chi-1 male
    // this run (generation params changed) — attach a fresh one instead of
    // silently having no living-person fixture at all.
    const injected: RawPerson = {
      id: "p-100",
      displayName: "Nguyễn Văn An",
      gender: "MALE",
      generation: fallbackParent.generation + 1,
      isAlive: true,
      primaryBranch: fallbackParent.primaryBranch,
      badges: [],
    };
    graph.persons.push(injected);
    graph.edges.push({
      id: "e-p100-parent",
      source: fallbackParent.id,
      target: "p-100",
      relType: "PARENT_BIO",
    });
    livingMemberId = "p-100";
  }

  // --- LIVING_MINOR_FULL (p-101), child of p-100 ------------------------
  if (livingMemberId) {
    const parent = graph.persons.find((p) => p.id === livingMemberId)!;
    graph.persons.push({
      id: "p-101",
      displayName: "Nguyễn Thị Bé",
      gender: "FEMALE",
      generation: parent.generation + 1,
      isAlive: true,
      primaryBranch: parent.primaryBranch,
      badges: [],
    });
    graph.edges.push({
      id: "e-p101-parent",
      source: livingMemberId,
      target: "p-101",
      relType: "PARENT_BIO",
    });
  }
}

let cached: MockGraph | null = null;

/**
 * Total size tuned for a real Sprint-2 perf test. The generator's natural
 * ceiling for 7 generations / 4 chi is ~3,300-3,400 persons regardless of a
 * higher target (see generate-large-tree.ts) — "vài nghìn" (a few thousand)
 * as the brief asks for, without inventing an eighth generation the domain
 * doesn't call for just to hit a rounder number.
 */
const DEFAULT_TARGET_SIZE = 3500;

export function getMockGraph(): MockGraph {
  if (cached) return cached;

  const generated = generateLargeTree({
    targetSize: DEFAULT_TARGET_SIZE,
    rootId: "p-001",
    rootDisplayName: "Nguyễn Văn Thủy Tổ",
    chiHeadOverrides: [
      { id: "p-010", displayName: "Nguyễn Văn Hiển" },
      { id: "p-011", displayName: "Nguyễn Văn Hoà" },
    ],
  });
  graftStoryFixtures(generated);

  const personsById = new Map(generated.persons.map((p) => [p.id, p]));
  const childrenOf = new Map<string, string[]>();
  const parentsOf = new Map<string, string[]>();
  const spousesOf = new Map<string, string[]>();

  for (const e of generated.edges) {
    if (e.relType === "PARENT_BIO" || e.relType === "PARENT_ADOPT") {
      if (!childrenOf.has(e.source)) childrenOf.set(e.source, []);
      childrenOf.get(e.source)!.push(e.target);
      if (!parentsOf.has(e.target)) parentsOf.set(e.target, []);
      parentsOf.get(e.target)!.push(e.source);
    } else if (e.relType === "SPOUSE") {
      if (!spousesOf.has(e.source)) spousesOf.set(e.source, []);
      spousesOf.get(e.source)!.push(e.target);
      if (!spousesOf.has(e.target)) spousesOf.set(e.target, []);
      spousesOf.get(e.target)!.push(e.source);
    }
  }

  cached = {
    personsById,
    edges: generated.edges,
    childrenOf,
    parentsOf,
    spousesOf,
    rootId: generated.rootId,
  };
  return cached;
}

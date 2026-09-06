import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { canSeeLivingPersons, resolveMockRole } from "./role";
import type { RawPerson } from "@/mocks/tree-graph/generate-large-tree";
import type { Problem, TreeDirection } from "@/types/api";

function notFound(instance: string) {
  const problem: Problem = {
    type: "about:blank",
    title: "Không tìm thấy nhân khẩu gốc của cây",
    status: 404,
    code: "NOT_FOUND",
    instance,
  };
  return HttpResponse.json(problem, { status: 404 });
}

export const treeHandlers = [
  http.get(`${API_BASE_URL}/api/v1/tree`, ({ request }) => {
    const url = new URL(request.url);
    const graph = getMockGraph();
    const rootId = url.searchParams.get("rootId") ?? graph.rootId;
    const depth = Number(url.searchParams.get("depth") ?? "3");
    const direction = (url.searchParams.get("direction") as TreeDirection | null) ?? "DESCENDANTS";
    const includeSpousesParam = url.searchParams.get("includeSpouses");
    const includeSpouses = includeSpousesParam === null ? true : includeSpousesParam !== "false";
    const maxNodes = Number(url.searchParams.get("maxNodes") ?? "500");
    const role = resolveMockRole(request);

    const isVisible = (person: RawPerson) => person.isAlive === false || canSeeLivingPersons(role);

    // A guest asking for a hidden living person's tree gets the same 404 a
    // guest gets from GET /persons/{id} — existence itself is not disclosed
    // (contracts/README §3).
    const rootPerson = graph.personsById.get(rootId);
    if (rootPerson && !isVisible(rootPerson)) {
      return notFound(`/api/v1/tree?rootId=${rootId}`);
    }

    const projection = queryTreeProjection(graph, {
      rootId,
      depth,
      direction,
      includeSpouses,
      maxNodes,
      isVisible,
    });

    if (!projection) return notFound(`/api/v1/tree?rootId=${rootId}`);

    return HttpResponse.json(projection, {
      headers: {
        ETag: `"tree-${rootId}-${depth}-${direction}-${maxNodes}-${role}"`,
      },
    });
  }),
];

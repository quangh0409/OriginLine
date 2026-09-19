import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { canSeeBirthYearMock } from "@/mocks/privacy";
import { canSeeLivingPersons, resolveMockRole } from "./role";
import { identityOf, pathIsWithin, type MockIdentity } from "@/mocks/identity";
import type { MockGraph } from "@/mocks/tree-graph/build-graph";
import type { RawPerson } from "@/mocks/tree-graph/generate-large-tree";
import type { Problem, TreeDirection } from "@/types/api";

/**
 * Khách gọi bản THÀNH VIÊN của phả đồ.
 *
 * Trước đây handler này trả về một cây đã lọc cho khách — tiện, nhưng **sai so
 * với máy chủ thật**: `/api/v1/tree` nằm sau `authenticated()` nên khách nhận
 * `401` trước khi Spring kịp đọc một tham số nào. Chính chỗ lệch ấy đã giấu đi
 * việc giao diện chưa bao giờ nối vào `/api/v1/public/tree`: dưới MSW mọi thứ
 * xanh, trên hệ thống thật khách không xem được phả đồ.
 *
 * Bản công khai của khách nay nằm ở `./public-portal.ts`.
 */
function unauthenticated(instance: string) {
  const problem: Problem = {
    type: "about:blank",
    title: "Cần đăng nhập để xem phả đồ đầy đủ",
    status: 401,
    code: "UNAUTHENTICATED",
    instance,
  };
  return HttpResponse.json(problem, { status: 401 });
}

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

/**
 * "Ông tổ" của một tập người: đời nhỏ nhất, và trong cùng một đời thì **ưu tiên
 * người CÓ CON**.
 *
 * Vế thứ hai không phải làm đẹp. Ở đời 1 của bộ dữ liệu giả lập có hai người —
 * thuỷ tổ và bà tổ — và mọi cạnh cha/mẹ đều xuất phát từ thuỷ tổ. Phá hoà bằng
 * id sẽ chọn bà tổ (`g-0` đứng trước `p-001`), và phả đồ mở ra đúng hai node:
 * cụ bà và cụ ông, một cái cây không ai nhận ra là dòng họ mình. Phá hoà bằng
 * id vẫn giữ ở vế cuối, để hai lượt gọi giống nhau không bao giờ ra hai gốc
 * khác nhau.
 */
function ancestorOf(persons: RawPerson[], graph: MockGraph): RawPerson | null {
  let best: RawPerson | null = null;
  let bestHasChildren = false;
  for (const person of persons) {
    const gen = person.generation ?? Number.MAX_SAFE_INTEGER;
    const hasChildren = (graph.childrenOf.get(person.id)?.length ?? 0) > 0;
    if (best === null) {
      best = person;
      bestHasChildren = hasChildren;
      continue;
    }
    const bestGen = best.generation ?? Number.MAX_SAFE_INTEGER;
    const better =
      gen < bestGen ||
      (gen === bestGen &&
        (hasChildren !== bestHasChildren ? hasChildren : person.id < best.id));
    if (better) {
      best = person;
      bestHasChildren = hasChildren;
    }
  }
  return best;
}

/**
 * **Gốc mặc định theo vai** — bản giả lập của phép chọn mà máy chủ thật làm khi
 * `rootId` vắng mặt (openapi `GET /tree`, "`rootId` tuỳ chọn — gốc mặc định
 * theo vai"):
 *
 * | Vai | Gốc |
 * |---|---|
 * | `ADMIN` · `COUNCIL` | thuỷ tổ của cả dòng họ |
 * | `BRANCH_HEAD` | ông tổ của chi được giao (chi **nông nhất** theo `ltree`) |
 * | `MEMBER` | ông tổ chi nhà mình |
 *
 * Cố ý **không** lấy hồ sơ của chính người dùng (`identity.personId`) làm gốc:
 * chiều mặc định là `DESCENDANTS`, nên một thành viên chưa có con sẽ mở màn
 * hình chủ lực ra và thấy đúng một node — chính mình. Hồ sơ cá nhân được dùng
 * để **xác định chi**, không để làm gốc.
 *
 * Chi rỗng thì nới dần lên theo đường `ltree` rồi cuối cùng về cả dòng họ; chỉ
 * khi phả không có ai mới trả `null` (→ `404`), chứ không bao giờ là một cây
 * rỗng `200` — cây rỗng nói dối rằng dòng họ không có ai.
 */
function defaultRootFor(graph: MockGraph, identity: MockIdentity): string | null {
  const everyone = [...graph.personsById.values()];

  // Vai toàn dòng họ → thuỷ tổ. Bộ dữ liệu giả lập tự ghi lại người ấy là ai,
  // nên không phải suy ra — và không thể suy trượt.
  if (identity.clanWide) return graph.personsById.has(graph.rootId) ? graph.rootId : null;

  const scopes = [...identity.managedBranches, identity.homeBranch]
    .filter((path): path is string => typeof path === "string" && path.length > 0)
    // Chi nông nhất trước: một trưởng chi được giao cả `root.chi_nhat` lẫn
    // `root.chi_nhat.nganh_truong` phải mở ra cái bao trùm.
    .sort((a, b) => a.split(".").length - b.split(".").length || a.localeCompare(b));

  for (const scope of scopes) {
    // Nới dần: `root.chi_nhat.nganh_truong` → `root.chi_nhat` → `root`.
    let path: string | null = scope;
    while (path) {
      const inScope = everyone.filter((p) => pathIsWithin(path!, p.primaryBranch?.path));
      const found = ancestorOf(inScope, graph);
      if (found) return found.id;
      const cut = path.lastIndexOf(".");
      path = cut === -1 ? null : path.slice(0, cut);
    }
  }

  return ancestorOf(everyone, graph)?.id ?? null;
}

export const treeHandlers = [
  http.get(`${API_BASE_URL}/api/v1/tree`, ({ request }) => {
    const url = new URL(request.url);
    const graph = getMockGraph();
    const rootIdParam = url.searchParams.get("rootId")?.trim() || null;
    const depth = Number(url.searchParams.get("depth") ?? "3");
    const direction = (url.searchParams.get("direction") as TreeDirection | null) ?? "DESCENDANTS";
    const includeSpousesParam = url.searchParams.get("includeSpouses");
    const includeSpouses = includeSpousesParam === null ? true : includeSpousesParam !== "false";
    const maxNodes = Number(url.searchParams.get("maxNodes") ?? "500");
    const role = resolveMockRole(request);
    const identity = identityOf(role);

    // Kiểm phiên TRƯỚC khi đọc bất kỳ tham số nào — `/api/v1/tree` nằm sau
    // `authenticated()` nên khách nhận `401` trước khi Spring kịp nhìn `rootId`.
    if (identity.appUserId === null) {
      return unauthenticated(`/api/v1/tree${url.search}`);
    }

    // `rootId` TUỲ CHỌN. Thiếu nó, máy chủ chọn gốc theo vai + phạm vi chi.
    const rootId = rootIdParam ?? defaultRootFor(graph, identity);
    if (rootId === null) return notFound(`/api/v1/tree${url.search}`);

    const isVisible = (person: RawPerson) => person.isAlive === false || canSeeLivingPersons(role);

    // Hiện được người KHÔNG có nghĩa là hiện được năm sinh của họ: sau mô hình
    // đồng thuận V8, năm sinh người còn sống nằm trong nhóm `birthDetailAndPhoto`
    // và nhóm ấy mặc định đóng — kể cả với người cùng chi. Thẻ trên phả đồ vì thế
    // trống dòng ngày rất thường xuyên, và đó là trạng thái BÌNH THƯỜNG mà giao
    // diện phải dựng được (xem src/lib/tree/life-dates.ts).
    const canSeeBirthYear = (person: RawPerson) =>
      canSeeBirthYearMock(
        {
          id: person.id,
          isAlive: person.isAlive,
          birthYear: person.birthYear ?? null,
          primaryBranch: person.primaryBranch ?? null,
        },
        role
      );

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
      canSeeBirthYear,
    });

    if (!projection) return notFound(`/api/v1/tree?rootId=${rootId}`);

    return HttpResponse.json(projection, {
      headers: {
        ETag: `"tree-${rootId}-${depth}-${direction}-${maxNodes}-${role}"`,
      },
    });
  }),
];

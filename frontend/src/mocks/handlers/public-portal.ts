import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph, type MockGraph } from "@/mocks/tree-graph/build-graph";
import { queryTreeProjection } from "@/mocks/tree-graph/query-tree";
import { unaccent } from "@/mocks/unaccent";
import type {
  PublicPersonDto,
  PublicPersonSummaryDto,
  PublicPersonSummaryPage,
  PublicTreeProjection,
} from "@/lib/api/public-portal";
import type { RawPerson } from "@/mocks/tree-graph/generate-large-tree";
import type { Problem, TreeDirection } from "@/types/api";

/**
 * MSW cho **cổng thông tin công khai** — `/api/v1/public/**`.
 *
 * <h2>Điểm khác biệt quan trọng nhất với mọi handler khác trong thư mục này</h2>
 * Nó **không đọc `x-mock-role`**, và đó không phải sơ suất: máy chủ thật ép ngữ
 * cảnh về Khách bằng `PublicGuestScope.asGuest` trước khi chạm vào dữ liệu, nên
 * phản hồi giống hệt nhau với mọi người gọi — chính điều đó mới cho phép nó gắn
 * `Cache-Control: public`. Một handler giả lập biết tới vai sẽ làm bộ test không
 * còn kiểm được bất biến ấy.
 *
 * Hệ quả: `isAlive === false` là điều kiện lọc **duy nhất**, ghim cứng ở đây
 * đúng như nó được ghim trong câu SQL của `PersonSearchPort`.
 */

const PUBLIC_TREE_MAX_DEPTH = 4;
const PUBLIC_TREE_MAX_NODES = 200;
const PUBLIC_SEARCH_SIZE = 20;
const PUBLIC_MIN_QUERY_LENGTH = 2;
const PUBLIC_MAX_PAGE = 4;

function problem(status: number, code: Problem["code"], title: string, instance: string) {
  const body: Problem = { type: "about:blank", title, status, code, instance };
  return HttpResponse.json(body, { status });
}

/**
 * **Thuỷ tổ** — gốc mặc định của cổng công khai khi `rootId` vắng mặt.
 *
 * Chỉ xét người **đã khuất**, đúng bộ lọc duy nhất của bề mặt này, nên phép
 * chọn gốc không thể tự nó tiết lộ rằng có một người còn sống ở đời trên. Phá
 * hoà bằng id để hai lượt gọi giống nhau không ra hai gốc khác nhau — nếu không
 * thì `Cache-Control: public` đang nói dối.
 *
 * Không đọc `x-mock-role`: gốc mặc định ở đây **không phụ thuộc token**, y như
 * mọi thứ khác trong tệp này.
 */
function publicDefaultRootId(graph: MockGraph): string | null {
  const thuyTo = graph.personsById.get(graph.rootId);
  if (thuyTo && !thuyTo.isAlive) return thuyTo.id;

  // Đường lùi cho một bộ dữ liệu mà thuỷ tổ được ghi là còn sống (không xảy ra
  // trong thực tế, nhưng bộ sinh dữ liệu thì cấu hình được). Ưu tiên người CÓ
  // CON trong cùng một đời: ở đời 1 có cả cụ ông lẫn cụ bà, mà mọi cạnh cha/mẹ
  // chỉ xuất phát từ một người — chọn nhầm là mở ra một cái cây hai node.
  let best: RawPerson | null = null;
  let bestHasChildren = false;
  for (const person of graph.personsById.values()) {
    if (person.isAlive) continue;
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
  return best?.id ?? null;
}

/** Người đã khuất, dạng gọn công khai. Không `avatarKey` — bộ giả lập không có MinIO. */
function toPublicSummary(person: RawPerson): PublicPersonSummaryDto {
  return {
    id: person.id,
    displayName: person.displayName,
    gender: person.gender,
    generation: person.generation,
    isAlive: false,
    birthYear: person.birthYear ?? undefined,
    deathYear: person.deathYear ?? undefined,
    primaryBranch: person.primaryBranch ?? undefined,
    nativePlace: person.nativePlace ?? undefined,
  };
}

export const publicPortalHandlers = [
  http.get(`${API_BASE_URL}/api/v1/public/tree`, ({ request }) => {
    const url = new URL(request.url);
    const instance = `/api/v1/public/tree${url.search}`;
    const rootIdParam = url.searchParams.get("rootId")?.trim() || null;

    const depth = Number(url.searchParams.get("depth") ?? "3");
    if (Number.isNaN(depth) || depth < 0 || depth > PUBLIC_TREE_MAX_DEPTH) {
      return problem(
        400,
        "VALIDATION_FAILED",
        `Phả đồ công khai giới hạn độ sâu tối đa ${PUBLIC_TREE_MAX_DEPTH} đời`,
        instance
      );
    }

    const direction = (url.searchParams.get("direction") as TreeDirection | null) ?? "DESCENDANTS";
    const includeSpousesParam = url.searchParams.get("includeSpouses");
    const includeSpouses = includeSpousesParam === null ? true : includeSpousesParam !== "false";

    const graph = getMockGraph();

    // `rootId` TUỲ CHỌN — thiếu nó, cổng công khai mở ra thuỷ tổ. Trước đây chỗ
    // này trả `400`, và nó làm trang phả đồ công khai — cửa vào của cả nửa *cổng
    // thông tin dòng họ* — hỏng với mọi Khách.
    const rootId = rootIdParam ?? publicDefaultRootId(graph);
    if (rootId === null) {
      return problem(404, "NOT_FOUND", "Không tìm thấy nhân khẩu gốc của cây", instance);
    }

    const rootPerson = graph.personsById.get(rootId);

    // Gốc không tồn tại, đã xoá mềm, HOẶC là người còn sống → cùng một `404`.
    // Phân biệt ba ca đó là tự xác nhận người kia có thật.
    if (!rootPerson || rootPerson.isAlive) {
      return problem(404, "NOT_FOUND", "Không tìm thấy nhân khẩu gốc của cây", instance);
    }

    const projection = queryTreeProjection(graph, {
      rootId,
      depth: Math.max(1, depth),
      direction,
      includeSpouses,
      maxNodes: PUBLIC_TREE_MAX_NODES,
      isVisible: (person) => !person.isAlive,
    });

    if (!projection) {
      return problem(404, "NOT_FOUND", "Không tìm thấy nhân khẩu gốc của cây", instance);
    }

    const body: PublicTreeProjection = {
      rootId: projection.rootId,
      nodes: projection.nodes.map((node) => ({
        id: node.id,
        person: toPublicSummary(graph.personsById.get(node.id) as RawPerson),
        depth: node.depth,
        parentIds: node.parentIds,
        spouseIds: node.spouseIds,
        // `expandable` suy ra từ THAM SỐ YÊU CẦU, không từ số con thật: số con
        // thật đếm cả người còn sống, để lộ ra là gián tiếp nói "cụ này còn mấy
        // người con mà bạn không được thấy".
        expandable: node.hasMoreDescendants,
      })),
      edges: projection.edges.map((edge) => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        relType: edge.relType,
        heirKind: edge.heirKind ?? undefined,
        spouseOrder: edge.spouseOrder ?? undefined,
      })),
      meta: {
        depth: projection.meta.depth,
        direction: projection.meta.direction,
        nodeCount: projection.nodes.length,
        edgeCount: projection.edges.length,
        truncated: projection.meta.truncated,
        // HẰNG SỐ, không phải phép đếm.
        guestFiltered: true,
      },
    };

    return HttpResponse.json(body, {
      headers: { "Cache-Control": "max-age=300, public" },
    });
  }),

  http.get(`${API_BASE_URL}/api/v1/public/persons/search`, ({ request }) => {
    const url = new URL(request.url);
    const instance = `/api/v1/public/persons/search${url.search}`;
    const term = (url.searchParams.get("q") ?? "").trim();
    const page = Number(url.searchParams.get("page") ?? "0");

    if (term.length < PUBLIC_MIN_QUERY_LENGTH) {
      return problem(
        400,
        "VALIDATION_FAILED",
        `Từ khoá tìm kiếm công khai phải có ít nhất ${PUBLIC_MIN_QUERY_LENGTH} ký tự`,
        instance
      );
    }
    if (page > PUBLIC_MAX_PAGE) {
      return problem(400, "VALIDATION_FAILED", "Vượt quá trang cuối của cổng công khai", instance);
    }

    const generationParam = url.searchParams.get("generation");
    const generation = generationParam === null ? undefined : Number(generationParam);
    const needle = unaccent(term);

    const matches = [...getMockGraph().personsById.values()]
      .filter((person) => !person.isAlive)
      .filter((person) => generation === undefined || person.generation === generation)
      .filter((person) => unaccent(person.displayName).includes(needle))
      .sort(
        (a, b) =>
          (a.generation ?? 0) - (b.generation ?? 0) ||
          a.displayName.localeCompare(b.displayName, "vi")
      );

    const start = page * PUBLIC_SEARCH_SIZE;
    const items = matches.slice(start, start + PUBLIC_SEARCH_SIZE).map(toPublicSummary);

    // KHÔNG có `totalElements`: đó là một phép đếm dân số dòng họ.
    const body: PublicPersonSummaryPage = {
      items,
      page: {
        page,
        size: PUBLIC_SEARCH_SIZE,
        hasNext: start + PUBLIC_SEARCH_SIZE < matches.length && page < PUBLIC_MAX_PAGE,
      },
    };

    return HttpResponse.json(body, {
      headers: { "Cache-Control": "max-age=300, public" },
    });
  }),

  http.get(`${API_BASE_URL}/api/v1/public/persons/:id`, ({ params }) => {
    const id = params.id as string;
    const instance = `/api/v1/public/persons/${id}`;
    const person = getMockGraph().personsById.get(id);

    // Người còn sống và người không tồn tại đọc ra y hệt nhau.
    if (!person || person.isAlive) {
      return problem(404, "NOT_FOUND", "Không tìm thấy nhân khẩu", instance);
    }

    // `fullName`, KHÔNG phải `value`: `PersonName` của hợp đồng dùng `fullName`,
    // và một khoá sai ở đây đọc ra như "người này không có tên nào" — mục Tên
    // nhiều lớp tự ẩn đi và không ai thấy có gì hỏng.
    const body: PublicPersonDto = {
      id: person.id,
      names: [{ nameType: "HUY", fullName: person.displayName, isPrimary: true }],
      displayName: person.displayName,
      gender: person.gender,
      generation: person.generation,
      isAlive: false,
      nativePlace: person.nativePlace ?? undefined,
      primaryBranch: person.primaryBranch ?? undefined,
      relations: [],
      // `meta` CỐ Ý vắng: một agent backend đang thêm khối ấy vào bản công khai,
      // và cho tới lúc nó lên, bộ giả lập phải giữ giao diện ở đúng trạng thái
      // mà hệ thống thật đang trả về — nếu không thì "chịu được khi trường ấy
      // chưa có" không bao giờ được chạy qua.
    };

    return HttpResponse.json(body, {
      headers: { "Cache-Control": "max-age=300, public" },
    });
  }),
];

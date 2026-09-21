import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { DEFAULT_KINSHIP_RULE_SET } from "@/mocks/data";
import { resolveKinshipMock } from "@/mocks/kinship-engine";
import { isAliveMock } from "@/mocks/person-detail";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { canSeeLivingPersons, resolveMockRole } from "./role";
import type { RawPerson } from "@/mocks/tree-graph/generate-large-tree";
import type {
  EffectiveKinshipRuleSet,
  KinshipResult,
  KinshipRuleSetDto,
  KinshipRuleSetPage,
  KinshipRuleSetUpdateRequest,
  Problem,
} from "@/types/api";

function problem(status: number, title: string, code: Problem["code"], instance: string) {
  const body: Problem = { type: "about:blank", title, status, code, instance };
  return HttpResponse.json(body, { status });
}

export const kinshipHandlers = [
  http.get(`${API_BASE_URL}/api/v1/kinship`, ({ request }) => {
    const url = new URL(request.url);
    const fromId = url.searchParams.get("from");
    const toId = url.searchParams.get("to");
    const includePath = url.searchParams.get("includePath") !== "false";
    const role = resolveMockRole(request);

    if (!fromId || !toId) {
      return problem(400, "Thiếu tham số from/to", "VALIDATION_FAILED", "/api/v1/kinship");
    }

    // Khách: 401, không phải một câu trả lời riêng tư nào.
    //
    // Bản trước của handler này coi "khách" chỉ như MỘT nhánh của bộ lọc
    // riêng tư (`endpointHidden` bên dưới) — với một cặp toàn người ĐÃ KHUẤT,
    // khách vẫn nhận `200` kèm kết quả đầy đủ. Đó là bộ giả lập đang mô phỏng
    // một API DỄ HƠN bản thật: `SecurityConfig.apiSecurityFilterChain` không
    // có lối `permitAll` nào cho `/api/v1/kinship` — nó rơi thẳng vào
    // `anyRequest().authenticated()`, nên MỌI yêu cầu không kèm JWT hợp lệ đều
    // nhận `401` trước khi bất kỳ câu hỏi riêng tư nào được xét tới, bất kể cả
    // hai người có đã khuất hay không. `<KinshipGuestNotice>` chặn khách lại
    // từ trước khi màn hình gọi tới endpoint này, nhưng handler vẫn phải nói
    // đúng sự thật cho một yêu cầu gọi thẳng (DevTools, ca kiểm, một client
    // khác trong tương lai).
    if (role === "guest") {
      return problem(401, "Cần đăng nhập để tra danh xưng", "UNAUTHENTICATED", "/api/v1/kinship");
    }

    // Ghi chú phạm vi: mô hình giả lập này chỉ phân hai hạng "khách"/"đã đăng
    // nhập" cho MỌI vai đã đăng nhập (`canSeeLivingPersons` trả `true` như
    // nhau cho member/branch-head/admin) — nó KHÔNG mô phỏng việc một thành
    // viên vẫn có thể bị giấu một người còn sống ngoài phạm vi của mình. Nhánh
    // dưới đây vì vậy không còn ca nào chạy tới trong bộ giả lập hôm nay; giữ
    // lại vì đúng nghiệp vụ thật (404 chứ không phải 403, để không xác nhận
    // sự tồn tại) và vì việc mô hình hoá phạm vi chi/ngành cho endpoint này là
    // một việc khác, chưa làm ở đây.
    const endpointHidden = [fromId, toId].some(
      (id) => isAliveMock(id) === true && !canSeeLivingPersons(role)
    );
    if (endpointHidden) {
      return problem(404, "Không tìm thấy một trong hai nhân khẩu", "NOT_FOUND", "/api/v1/kinship");
    }

    const graph = getMockGraph();
    if (!graph.personsById.has(fromId) || !graph.personsById.has(toId)) {
      return problem(404, "Không tìm thấy một trong hai nhân khẩu", "NOT_FOUND", "/api/v1/kinship");
    }

    const isVisible = (person: RawPerson) =>
      person.isAlive === false || canSeeLivingPersons(role);

    const result: KinshipResult = resolveKinshipMock(graph, {
      fromId,
      toId,
      rules: DEFAULT_KINSHIP_RULE_SET.rules,
      ruleSetId: DEFAULT_KINSHIP_RULE_SET.id,
      isVisible,
    });

    return HttpResponse.json(includePath ? result : { ...result, path: undefined });
  }),

  http.get(`${API_BASE_URL}/api/v1/kinship-rules`, ({ request }) => {
    const url = new URL(request.url);
    const effective = url.searchParams.get("effective") === "true";
    const branchId = url.searchParams.get("branchId");

    if (effective) {
      const result: EffectiveKinshipRuleSet = {
        branchId: branchId ?? "b-chi1",
        chain: [
          {
            ruleSetId: DEFAULT_KINSHIP_RULE_SET.id,
            scope: "DEFAULT",
            name: DEFAULT_KINSHIP_RULE_SET.name,
          },
        ],
        rules: DEFAULT_KINSHIP_RULE_SET.rules.map((r) => ({
          ...r,
          inheritedFromScope: "DEFAULT" as const,
          overridden: false,
        })),
      };
      return HttpResponse.json(result);
    }

    const page: KinshipRuleSetPage = {
      items: [DEFAULT_KINSHIP_RULE_SET],
      page: { page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false },
    };
    return HttpResponse.json(page);
  }),

  http.put(`${API_BASE_URL}/api/v1/kinship-rules`, async ({ request }) => {
    const role = resolveMockRole(request);
    if (role !== "admin") {
      return problem(
        403,
        "Chỉ Hội đồng Tộc biểu được sửa bộ luật danh xưng",
        "FORBIDDEN",
        "/api/v1/kinship-rules"
      );
    }
    const body = (await request.json()) as KinshipRuleSetUpdateRequest;
    if (!body.ruleSetId && body.scope === "DEFAULT") {
      return problem(
        403,
        "Không được sửa bộ luật DEFAULT của hệ thống",
        "SYSTEM_RULE_SET_IMMUTABLE",
        "/api/v1/kinship-rules"
      );
    }
    const updated: KinshipRuleSetDto = {
      id: body.ruleSetId ?? `krs-mock-${Date.now()}`,
      scope: body.scope ?? "BRANCH",
      name: body.name ?? null,
      region: body.region ?? null,
      clanId: body.clanId ?? null,
      branchId: body.branchId ?? null,
      rules: body.rules ?? [],
      version: 1,
    };
    return HttpResponse.json(updated, { status: body.ruleSetId ? 200 : 201 });
  }),
];

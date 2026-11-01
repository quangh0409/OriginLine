import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { resolveMockRole } from "./role";
import { activeClanInviteCountMock } from "./membership-admin";
import type { Problem, ProblemCode } from "@/types/api";
import type {
  AncestralHallDto,
  ClanProfileDto,
  ClanProfilePublicDto,
  ClanProfileWriteBody,
  ClanReadinessDto,
  ClanRegion,
  CouncilAccountCreatedDto,
  CreateCouncilAccountBody,
  GioToDto,
} from "@/lib/api/clan-profile";

/**
 * MSW cho **khởi tạo dòng họ** — `/api/v1/public/clan-profile` và
 * `/api/v1/admin/clan-profile/**`, quyết định 26/09/2026 của chủ dự án.
 *
 * <h2>Trạng thái sống qua nhiều lượt gọi, KHÔNG đọc lại `mocks/data.ts`</h2>
 * Không giống `persons`/`tree` (dựng từ đồ thị cố định), hồ sơ dòng họ ở đây
 * bắt đầu RỖNG (`initialized: false`) mỗi khi bộ giả lập khởi động lại — đúng
 * hiện trạng thật mà `/quan-tri` được sinh ra để sửa. `resetClanMockState()`
 * đưa nó về rỗng giữa các ca kiểm.
 *
 * <h2>`persons.count`/`branches.count` đọc từ ĐỒ THỊ THẬT, không phải một số bịa</h2>
 * Cùng đồ thị mà `GraphQL BranchesQuery` dùng (`handlers/graphql.ts`) — một
 * dòng họ "mới khởi tạo" trong bộ giả lập vẫn đang đứng cạnh đồ thị demo gần
 * 4.000 người, nên các số này không phải `0`. Đó ĐÚNG LÀ ĐIỀU PHẢI THẤY: màn
 * `/quan-tri` không tự xoá đồ thị demo, nó chỉ thêm một hồ sơ dòng họ lên
 * trên. Ca kiểm "gốc phả khoá khi đã có nhân khẩu" dựa vào đúng sự thật này.
 */

interface ClanProfileMockState {
  name: string;
  shortName?: string;
  rootBranchPath: string;
  region: ClanRegion;
  ancestralHall?: AncestralHallDto;
  ancestorName?: string;
  foundedNote?: string;
  gioTo?: GioToDto;
  version: number;
  createdAt: string;
  updatedAt: string;
}

let clanProfile: ClanProfileMockState | null = null;
let councilAccountsCreated = 0;
/** Định danh đã "đăng ký" qua bước này — cho ca `422 IDENTITY_ALREADY_REGISTERED`. */
const usedLoginIds = new Set<string>();

/** Đưa cả ba kho về trạng thái đầu. Dùng giữa các ca kiểm. */
export function resetClanMockState(): void {
  clanProfile = null;
  councilAccountsCreated = 0;
  usedLoginIds.clear();
}

function toPublicDto(state: ClanProfileMockState): ClanProfilePublicDto {
  return {
    initialized: true,
    name: state.name,
    shortName: state.shortName,
    rootBranchPath: state.rootBranchPath,
    region: state.region,
    ancestralHall: state.ancestralHall,
    ancestorName: state.ancestorName,
    foundedNote: state.foundedNote,
    gioTo: state.gioTo,
  };
}

function toAdminDto(state: ClanProfileMockState): ClanProfileDto {
  return {
    ...toPublicDto(state),
    createdAt: state.createdAt,
    updatedAt: state.updatedAt,
    version: state.version,
  };
}

/**
 * Có dấu ngoặc kép — cùng khuôn `persons.ts#etagFor` và khớp `AdminClanProfileController.etagOf`
 * thật (`"\"" + version + "\""`). Client chỉ ECHO chuỗi này lại nguyên vẹn ở `If-Match`, không bao
 * giờ tự phân tích nó, nên khuôn không ảnh hưởng gì tới máy khách — nhưng để mock trả bản KHÔNG
 * ngoặc trong khi máy chủ thật luôn có ngoặc là một chỗ dễ đánh lừa người đọc log HTTP.
 */
function etagFor(state: ClanProfileMockState): string {
  return `"${state.version}"`;
}

function problem(
  status: number,
  code: ProblemCode,
  title: string,
  instance: string,
  detail?: string
): HttpResponse<Problem> {
  const body: Problem = {
    type: "about:blank",
    title,
    status,
    code,
    instance,
    ...(detail !== undefined ? { detail } : {}),
  };
  return HttpResponse.json(body, { status });
}

const INSTANCE_ADMIN = "/api/v1/admin/clan-profile";
const INSTANCE_READINESS = "/api/v1/admin/clan-profile/readiness";
const INSTANCE_COUNCIL = "/api/v1/admin/clan-profile/council-accounts";

/** Chỉ `ADMIN` được vào mọi lối `/admin/clan-profile/**` — bốn lối đều chung một cổng. */
function requireAdmin(request: Request, instance: string) {
  const role = resolveMockRole(request);
  if (role !== "admin") {
    return problem(
      403,
      "FORBIDDEN",
      "Không đủ quyền",
      instance,
      "Chỉ Quản trị hệ thống mới khởi tạo/sửa hồ sơ dòng họ."
    );
  }
  return null;
}

function countPersonsAndBranches(): { persons: number; branches: number } {
  const graph = getMockGraph();
  const branchIds = new Set<string>();
  for (const person of graph.personsById.values()) {
    if (person.primaryBranch) branchIds.add(person.primaryBranch.id);
  }
  return { persons: graph.personsById.size, branches: branchIds.size };
}

const REGIONS: readonly ClanRegion[] = ["BAC", "TRUNG", "NAM"];

/** `422`/`400`-worthy hình dạng sai — kiểm TỐI THIỂU, đủ để ca kiểm 400 có gì để bắn vào. */
function validateWrite(body: ClanProfileWriteBody): string | null {
  if (!body.name || body.name.trim().length === 0) return "Thiếu tên dòng họ.";
  if (body.name.length > 120) return "Tên dòng họ tối đa 120 ký tự.";
  if (!body.rootBranchPath || body.rootBranchPath.trim().length === 0) return "Thiếu gốc phả.";
  if (!/^[a-z0-9_]+(\.[a-z0-9_]+)*$/.test(body.rootBranchPath)) {
    return "Gốc phả chỉ gồm chữ thường, số, gạch dưới, phân đoạn bằng dấu chấm.";
  }
  if (!REGIONS.includes(body.region)) return "Vùng miền không hợp lệ.";
  if (body.foundedNote && body.foundedNote.length > 2000) return "Ghi chú lập họ tối đa 2000 ký tự.";
  return null;
}

export const clanHandlers = [
  http.get(`${API_BASE_URL}/api/v1/public/clan-profile`, () => {
    if (!clanProfile) return HttpResponse.json({ initialized: false } satisfies ClanProfilePublicDto);
    return HttpResponse.json(toPublicDto(clanProfile));
  }),

  http.get(`${API_BASE_URL}/api/v1/admin/clan-profile`, ({ request }) => {
    const denied = requireAdmin(request, INSTANCE_ADMIN);
    if (denied) return denied;
    if (!clanProfile) {
      return problem(
        404,
        "CLAN_NOT_INITIALIZED",
        "Dòng họ chưa được khởi tạo",
        INSTANCE_ADMIN,
        "Chưa có hồ sơ dòng họ nào — dùng PUT để tạo mới."
      );
    }
    return HttpResponse.json(toAdminDto(clanProfile), { headers: { ETag: etagFor(clanProfile) } });
  }),

  http.put(`${API_BASE_URL}/api/v1/admin/clan-profile`, async ({ request }) => {
    const denied = requireAdmin(request, INSTANCE_ADMIN);
    if (denied) return denied;

    const body = (await request.json()) as ClanProfileWriteBody;
    const validationError = validateWrite(body);
    if (validationError) {
      return problem(400, "VALIDATION_FAILED", "Dữ liệu gửi lên không hợp lệ", INSTANCE_ADMIN, validationError);
    }

    const nowIso = new Date().toISOString();

    if (!clanProfile) {
      // TẠO MỚI — không đòi `If-Match`, có gửi kèm cũng bỏ qua: chưa có phiên
      // bản nào để khoá lạc quan với nó.
      clanProfile = {
        name: body.name.trim(),
        shortName: body.shortName?.trim() || undefined,
        rootBranchPath: body.rootBranchPath.trim(),
        region: body.region,
        ancestralHall: body.ancestralHall,
        ancestorName: body.ancestorName,
        foundedNote: body.foundedNote,
        gioTo: body.gioTo,
        version: 1,
        createdAt: nowIso,
        updatedAt: nowIso,
      };
      return HttpResponse.json(toAdminDto(clanProfile), {
        status: 201,
        headers: { ETag: etagFor(clanProfile) },
      });
    }

    // CẬP NHẬT — cùng luật `If-Match` với `PATCH /persons/{id}`.
    const ifMatch = request.headers.get("If-Match");
    if (!ifMatch) {
      return problem(
        412,
        "PRECONDITION_REQUIRED",
        "Thiếu header If-Match",
        INSTANCE_ADMIN,
        "Đọc lại hồ sơ để lấy ETag hiện tại rồi gửi lại."
      );
    }
    if (ifMatch !== etagFor(clanProfile)) {
      return problem(
        409,
        "OPTIMISTIC_LOCK_CONFLICT",
        "Bản ghi đã bị người khác sửa",
        INSTANCE_ADMIN,
        "Tải lại hồ sơ dòng họ rồi sửa lại."
      );
    }

    const { persons } = countPersonsAndBranches();
    // Gốc phả khoá vĩnh viễn kể từ nhân khẩu đầu tiên — xem javadoc
    // `ProblemCode.CLAN_ROOT_LOCKED`. So sánh giá trị GỬI LÊN với giá trị
    // HIỆN CÓ: gửi lại đúng gốc cũ (form khoá ô, không đổi gì) vẫn đi qua.
    if (persons > 0 && body.rootBranchPath.trim() !== clanProfile.rootBranchPath) {
      return problem(
        409,
        "CLAN_ROOT_LOCKED",
        "Gốc phả đã khoá",
        INSTANCE_ADMIN,
        `Dòng họ đã có ${persons} nhân khẩu — không đổi gốc phả được nữa.`
      );
    }

    clanProfile = {
      ...clanProfile,
      name: body.name.trim(),
      shortName: body.shortName?.trim() || undefined,
      region: body.region,
      ancestralHall: body.ancestralHall,
      ancestorName: body.ancestorName,
      foundedNote: body.foundedNote,
      gioTo: body.gioTo,
      version: clanProfile.version + 1,
      updatedAt: nowIso,
    };
    return HttpResponse.json(toAdminDto(clanProfile), { headers: { ETag: etagFor(clanProfile) } });
  }),

  http.get(`${API_BASE_URL}/api/v1/admin/clan-profile/readiness`, ({ request }) => {
    const denied = requireAdmin(request, INSTANCE_READINESS);
    if (denied) return denied;

    const { persons, branches } = countPersonsAndBranches();
    const profileDone = clanProfile !== null;
    const rootBranchDone = clanProfile !== null;
    const councilDone = councilAccountsCreated > 0;
    const activeInvites = activeClanInviteCountMock();

    const body: ClanReadinessDto = {
      profile: { done: profileDone, name: clanProfile?.name },
      rootBranch: { done: rootBranchDone, path: clanProfile?.rootBranchPath },
      // Bộ giả lập không có một `kinship_rule_set` "đã tuỳ biến" riêng cho
      // dòng họ giả lập — `customized: false` cố định là đúng sự thật của nó:
      // chưa ai sửa qua `/kinship` cho tới nay.
      kinshipRuleSet: { done: profileDone, region: clanProfile?.region, customized: false },
      council: { done: councilDone, count: councilAccountsCreated },
      persons: { count: persons },
      branches: { count: branches },
      inviteCodes: { active: activeInvites },
      steps: [
        { key: "profile", done: profileDone, hint: profileDone ? undefined : "Chưa lưu hồ sơ dòng họ." },
        {
          key: "council",
          done: councilDone,
          hint: councilDone ? undefined : "Chưa có tài khoản Hội đồng nào.",
        },
        {
          key: "import",
          done: persons > 0,
          hint: persons > 0 ? undefined : "Chưa nhập nhân khẩu nào từ Excel.",
        },
        {
          key: "invite",
          done: activeInvites > 0,
          hint: activeInvites > 0 ? undefined : "Chưa phát mã mời nào.",
        },
        {
          key: "done",
          done: profileDone && councilDone && persons > 0,
          hint: undefined,
        },
      ],
    };
    return HttpResponse.json(body);
  }),

  http.post(`${API_BASE_URL}/api/v1/admin/clan-profile/council-accounts`, async ({ request }) => {
    const denied = requireAdmin(request, INSTANCE_COUNCIL);
    if (denied) return denied;

    const body = (await request.json()) as CreateCouncilAccountBody;
    if (!body.displayName || body.displayName.trim().length === 0) {
      return problem(400, "VALIDATION_FAILED", "Dữ liệu gửi lên không hợp lệ", INSTANCE_COUNCIL, "Thiếu họ và tên.");
    }
    if (!body.loginId || body.loginId.trim().length === 0) {
      return problem(
        400,
        "VALIDATION_FAILED",
        "Dữ liệu gửi lên không hợp lệ",
        INSTANCE_COUNCIL,
        "Thiếu số điện thoại hoặc email."
      );
    }
    const loginId = body.loginId.trim().toLowerCase();
    if (usedLoginIds.has(loginId)) {
      return problem(
        422,
        "IDENTITY_ALREADY_REGISTERED",
        "Định danh đã có tài khoản",
        INSTANCE_COUNCIL,
        "Số điện thoại/email này đã có tài khoản trong hệ thống."
      );
    }

    usedLoginIds.add(loginId);
    councilAccountsCreated += 1;
    const seq = councilAccountsCreated;
    const nowIso = new Date();
    const expires = new Date(nowIso.getTime() + 30 * 60_000);

    const created: CouncilAccountCreatedDto = {
      appUserId: `u-council-${seq}`,
      keycloakSub: `kc-council-${seq}`,
      role: "COUNCIL",
      setPasswordUrl: `https://id.giapha.dev/dat-mat-khau/gia-lap-council-${seq}`,
      setPasswordExpiresAt: expires.toISOString(),
    };
    return HttpResponse.json(created, { status: 201 });
  }),
];

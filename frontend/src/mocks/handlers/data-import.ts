import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import type {
  ImportBranch,
  ImportBranchProgress,
  ImportCommitProgress,
  ImportDuplicatePair,
  ImportIssue,
  ImportIssueSeverity,
  ImportPlannedAction,
  ImportProblemCode,
  ImportRollbackPreflight,
  ImportRow,
} from "@/lib/api/data-import";
import {
  CLOSED_STATUSES,
  MOCK_DUPLICATE_POLICY,
  MOCK_IMPORT_BRANCHES,
  batchDto,
  findMockBranch,
  mutateImportMockDb,
  readImportMockDb,
  seedMockBatch,
  undecidedOf,
  warningsDigestOf,
  type StoredBatch,
} from "@/mocks/data-import";
import { canWriteInBranch, identityOf, type MockIdentity } from "@/mocks/identity";
import { resolveMockRole } from "./role";
import type { Problem } from "@/types/api";

/**
 * MSW cho `/api/v1/import/**`, bám **`contracts/openapi.yaml`** — 15 endpoint.
 *
 * <h2>Năm bất biến của bản thật được giữ nguyên ở đây</h2>
 * <ol>
 *   <li><b>Không có đường nào ghi vào phả trước `POST .../commit`.</b> Số nhân
 *       khẩu của chi (`branchPersonCounts`) chỉ đổi ở đúng hai chỗ: lúc ghi và
 *       lúc gỡ lô.</li>
 *   <li><b>Cổng duyệt nằm ở máy chủ</b>, bốn điều kiện theo đúng thứ tự của
 *       `ImportCommitGate`, kể cả khi giao diện đã ẩn nút.</li>
 *   <li><b>Không ngưỡng nào tự gộp người.</b> `POST .../decision` ghi được cả
 *       ba lựa chọn, nhưng **không** có lối gọi hàng loạt và không có ngưỡng
 *       điểm nào tự quyết hộ.</li>
 *   <li><b>`DEFERRED` vẫn chặn cổng duyệt.</b> Bộ giả lập đếm nó vào
 *       `undecidedDuplicateCount` y như bản thật — xoa dịu chỗ này là biến
 *       "hoãn" thành nút "cho tôi qua".</li>
 *   <li><b>Bên `TREE` của một cặp nghi trùng chỉ có `personId`.</b> Muốn biết
 *       người ấy là ai thì gọi `GET /persons/{id}` và nhận lại `404` khi không
 *       được phép biết bản ghi có tồn tại hay không.</li>
 * </ol>
 *
 * Mã trạng thái bám `GlobalExceptionHandler` của backend: `ForbiddenException`
 * → 403, `DomainException` → 422, `NotFoundException` → 404.
 */

const BASE = `${API_BASE_URL}/api/v1/import`;

function problem(
  status: number,
  code: ImportProblemCode | "NOT_FOUND",
  title: string,
  detail: string,
  instance: string,
  extra: Record<string, unknown> = {}
) {
  const body: Problem & Record<string, unknown> = {
    type: "about:blank",
    title,
    status,
    code: code as Problem["code"],
    detail,
    instance,
    ...extra,
  };
  return HttpResponse.json(body, { status });
}

function requireAccount(identity: MockIdentity, instance: string) {
  if (identity.appUserId === null) {
    return problem(
      403,
      "ACCOUNT_NOT_PROVISIONED",
      "Không đủ thẩm quyền",
      "Tài khoản chưa được khởi tạo trong hệ thống, xin đăng nhập lại.",
      instance
    );
  }
  return null;
}

function scopeViolation(instance: string, branchName: string) {
  return problem(
    403,
    "BRANCH_SCOPE_VIOLATION",
    "Ngoài phạm vi được giao",
    `Bạn không được giao quyền nhập liệu cho ${branchName}.`,
    instance
  );
}

function notFound(instance: string) {
  return problem(
    404,
    "NOT_FOUND",
    "Không tìm thấy",
    "Không tìm thấy lô nhập liệu này.",
    instance
  );
}

function storedBatch(batchId: string): StoredBatch | undefined {
  return readImportMockDb().batches.find((b) => b.id === batchId);
}

/**
 * Một tệp `.xlsx` giả.
 *
 * Bốn byte đầu `PK\x03\x04` là chữ ký ZIP thật — đúng thứ backend dùng để nhận
 * diện định dạng ("không tin đuôi tệp, không tin `Content-Type` của client").
 * Phần còn lại là rác có chủ ý: bộ giả lập không sinh workbook thật, và giả vờ
 * sinh được sẽ che mất việc ấy.
 */
function fakeXlsx(note: string): ArrayBuffer {
  const header = [0x50, 0x4b, 0x03, 0x04];
  const body = new TextEncoder().encode(note);
  const out = new Uint8Array(header.length + body.length);
  out.set(header, 0);
  out.set(body, header.length);
  return out.buffer;
}

function xlsxResponse(bytes: ArrayBuffer, fileName: string) {
  return new HttpResponse(bytes, {
    status: 200,
    headers: {
      "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      // Dạng RFC 5987 để tên chi tiếng Việt không rụng dấu trên đường về.
      "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(fileName)}`,
    },
  });
}

/** Bỏ dấu tiếng Việt — dựng vế `filename=` cho client cũ. */
function asciiFold(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .replace(/[^ -~]/g, "");
}

/**
 * Như {@link xlsxResponse}, nhưng mang **cả hai** dạng tên tệp — đúng như bản
 * thật của `GET .../issues.xlsx`.
 *
 * Đây là cái bẫy mà bộ giả lập phải dựng lại cho đủ: một client đọc `filename=`
 * trước sẽ chạy "thành công" và trao cho người dùng một tệp tên đã rụng hết
 * dấu. Chỉ có bản mang cả hai dạng mới lộ ra lỗi ấy.
 */
function issuesXlsxResponse(bytes: ArrayBuffer, fileName: string) {
  return new HttpResponse(bytes, {
    status: 200,
    headers: {
      "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "Content-Disposition":
        `attachment; filename="${asciiFold(fileName)}"; ` +
        `filename*=UTF-8''${encodeURIComponent(fileName)}`,
    },
  });
}

/**
 * Người phụ trách từng chi.
 *
 * Chi Tứ **không có mặt ở đây** một cách có chủ ý: chi chưa ai nhận là rủi ro
 * lớn nhất của cả đợt, và nó thuộc về Hội đồng chứ không thuộc về phần mềm. Màn
 * tiến độ phải hiện được đúng trạng thái ấy thay vì một ô trống trông như lỗi.
 */
const MOCK_COORDINATORS: Record<string, string | undefined> = {
  "b-chi1": "Nguyễn Văn Đức",
  "b-chi2": "Nguyễn Văn Hoà",
  "b-chi3": "Nguyễn Thị Mai",
};

/** Một lượt ghi nền diễn trong 1,8 giây — đủ để giao diện có gì mà vẽ. */
const COMMIT_DURATION_MS = 1_800;

/**
 * Đưa các lô đang `COMMITTING` về đích nếu đã quá thời lượng.
 *
 * Bản thật làm việc này ở một luồng nền (`@Async`). Bộ giả lập không có luồng
 * nào, nên nó "đuổi kịp" mỗi khi có ai hỏi tới — hành vi nhìn từ phía giao diện
 * là như nhau, và giao diện chính là thứ đang được kiểm.
 */
function advanceCommits(now: number): void {
  /**
   * Đọc trước, chỉ GHI khi thật sự có việc.
   *
   * `mutateImportMockDb` đọc cả kho, sửa, rồi ghi lại **toàn bộ** kho. Nếu hàm
   * này ghi vô điều kiện thì mỗi lượt GET trở thành một lượt ghi đè — và một
   * lượt ghi đè xen vào giữa phần đọc và phần ghi của một thao tác thật sẽ nuốt
   * mất thao tác ấy, im lặng. Đó là kiểu hỏng chỉ lộ ra khi máy tải nặng.
   */
  const snapshot = readImportMockDb();
  const due = snapshot.batches.some(
    (b) =>
      b.status === "COMMITTING" &&
      now - (snapshot.committingSince[b.id] ?? Number.POSITIVE_INFINITY) >= COMMIT_DURATION_MS
  );
  if (!due) return;

  mutateImportMockDb((db) => {
    for (const batch of db.batches) {
      if (batch.status !== "COMMITTING") continue;
      const startedAt = db.committingSince[batch.id];
      if (startedAt === undefined || now - startedAt < COMMIT_DURATION_MS) continue;

      batch.status = "COMMITTED";
      batch.committedAt = new Date(now).toISOString();
      batch.version += 1;
      // ĐÂY là ranh giới ghi duy nhất của bộ giả lập. Không endpoint nào khác
      // được đụng vào `branchPersonCounts`.
      db.branchPersonCounts[batch.branchId] =
        (db.branchPersonCounts[batch.branchId] ?? 0) + batch.plannedCreateCount;
      delete db.committingSince[batch.id];
    }
  });
}

/**
 * Vướng mắc khi gỡ một lô — sao y `RollbackImportBatchService.vuongMac`.
 *
 * Điều kiện thật **không phải** một cửa sổ thời gian mà là "đã có người sửa hồ
 * sơ sau khi ghi": hai thứ dẫn tới hai hành động hoàn toàn khác nhau, nên
 * chúng phải là hai câu khác nhau trên màn xác nhận.
 */
function rollbackBlockers(batch: StoredBatch): string[] {
  const blockers: string[] = [];
  if (batch.status !== "COMMITTED" || !batch.committedAt) {
    blockers.push(
      `Lô này chưa từng được ghi vào phả (trạng thái ${batch.status}), nên không có gì để gỡ.`
    );
    return blockers;
  }
  const days = Math.floor((Date.now() - Date.parse(batch.committedAt)) / 86_400_000);
  if (days >= 30) {
    blockers.push(
      `Lô đã ghi được ${days} ngày, quá cửa sổ gỡ 30 ngày. Phần sai phải sửa tay từng người.`
    );
  }
  const edited = readImportMockDb().editedAfterCommit[batch.id] ?? 0;
  if (edited > 0) {
    blockers.push(
      `${edited} hồ sơ do lô này tạo đã được người khác sửa sau khi ghi. Gỡ lô sẽ làm mất công của họ — hãy hỏi lại những người ấy trước.`
    );
  }
  return blockers;
}

export const dataImportHandlers = [
  /* ── Chi và quyền nhập liệu ────────────────────────────────────────────── */
  http.get(`${BASE}/branches`, ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, "/api/v1/import/branches");
    if (denied) return denied;

    // CẢ CÂY, cho mọi tài khoản đã khởi tạo. Danh sách chi là cấu trúc tổ chức,
    // không phải dữ liệu cá nhân — giấu ba chi kia không bảo vệ thêm được gì,
    // chỉ làm màn đầu của quy trình mất khả năng trả lời "tôi đang ở đâu".
    advanceCommits(Date.now());
    const db = readImportMockDb();
    const rows: ImportBranch[] = MOCK_IMPORT_BRANCHES.map((b) => {
      const canImport = canWriteInBranch(identity, b.path);
      // Mã lô đang dở CHỈ phát cho chi người gọi được ghi: một mã lô là một lối
      // đi thẳng vào khu vực chờ của chi khác.
      const open = canImport
        ? db.batches.find((x) => x.branchId === b.id && !CLOSED_STATUSES.includes(x.status))
        : undefined;
      return {
        id: b.id,
        name: b.name,
        path: b.path,
        kind: b.kind,
        canImport,
        openBatchId: open?.id,
      };
    });
    return HttpResponse.json(rows);
  }),

  http.get(`${BASE}/duplicate-policy`, ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, "/api/v1/import/duplicate-policy");
    if (denied) return denied;
    return HttpResponse.json(MOCK_DUPLICATE_POLICY);
  }),

  /* ── Mẫu Excel riêng từng chi ──────────────────────────────────────────── */
  http.get(
    `${API_BASE_URL}/api/v1/admin/branches/:branchId/import-template.xlsx`,
    ({ request, params }) => {
      const branchId = String(params.branchId);
      const instance = `/api/v1/admin/branches/${branchId}/import-template.xlsx`;
      const identity = identityOf(resolveMockRole(request));
      const denied = requireAccount(identity, instance);
      if (denied) return denied;

      const branch = findMockBranch(branchId);
      if (!branch) return notFound(instance);
      // Dưới `/admin` nhưng phép kiểm là QUYỀN GHI TRÊN CHÍNH CHI ĐÓ, không
      // phải "chỉ System Admin": endpoint này phục vụ đúng Trưởng chi.
      if (!canWriteInBranch(identity, branch.path)) {
        return scopeViolation(instance, branch.name);
      }
      return xlsxResponse(
        fakeXlsx(`mau-nhap-lieu ${branch.name}`),
        `Mau-nhap-lieu-${branch.name.replace(/\s+/g, "-")}.xlsx`
      );
    }
  ),

  /* ── Tải tệp lên ───────────────────────────────────────────────────────── */
  http.post(`${BASE}/batches`, async ({ request }) => {
    const instance = "/api/v1/import/batches";
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    // Thân multipart hỏng phải ra IMP_BAD_FORMAT, không ra 500: tệp người dùng
    // gửi lên là đầu vào không tin cậy, và một thân yêu cầu méo không được phép
    // thành lỗi hệ thống.
    let form: FormData;
    try {
      form = await request.formData();
    } catch {
      return problem(
        400,
        "IMP_BAD_FORMAT",
        "Tệp không hợp lệ",
        "Không đọc được nội dung tệp tải lên.",
        instance
      );
    }
    const file = form.get("file");
    const branchId = String(form.get("branchId") ?? "");
    const force = form.get("force") === "true";

    const branch = findMockBranch(branchId);
    if (!branch) return notFound(instance);
    if (!canWriteInBranch(identity, branch.path)) {
      return scopeViolation(instance, branch.name);
    }
    if (!(file instanceof File)) {
      return problem(400, "IMP_BAD_FORMAT", "Tệp không hợp lệ", "Chưa chọn tệp nào.", instance);
    }

    const name = file.name.toLowerCase();
    if (!name.endsWith(".xlsx")) {
      // 400: tệp không đọc được. Việc cần làm là CHỌN TỆP KHÁC.
      return problem(
        400,
        "IMP_BAD_FORMAT",
        "Tệp không đúng định dạng",
        "Hệ thống chỉ nhận tệp .xlsx thật. Tệp .xls và .xlsm không nhận được.",
        instance
      );
    }
    // Hai kịch bản dưới đây khoá đúng chỗ mã HTTP hay bị đặt sai: quá cỡ là
    // 413, còn tệp đọc được mà cấu trúc sai là 422 — không phải 400, vì việc
    // cần làm là SỬA CẤU TRÚC TỆP hoặc TÁCH TỆP, không phải chọn tệp khác.
    if (name.includes("qua-lon") || name.includes("toobig")) {
      return problem(
        413,
        "IMP_FILE_TOO_LARGE",
        "Tệp quá lớn",
        "Tệp vượt trần 10 MB; thường là có ảnh nhúng.",
        instance
      );
    }
    if (name.includes("thieu-trang") || name.includes("nosheet")) {
      return problem(
        422,
        "IMP_MISSING_SHEET",
        "Tệp thiếu trang Nhân khẩu",
        "Không tìm thấy trang Nhân khẩu trong tệp. Hãy dùng đúng mẫu của chi.",
        instance
      );
    }

    advanceCommits(Date.now());

    // `IMP_ALREADY_COMMITTED` chặn ĐÚNG MỘT THỨ: bấm hai lần. Nó so mã băm với
    // các lô ĐÃ GHI VÀO PHẢ của cùng chi — tải lại một tệp giống hệt để *kiểm*
    // thì không bị chặn, và đó là bước đối soát bình thường.
    const sha = `sha256-${file.name.length}-${
      name.includes("sach") || name.includes("clean")
        ? "clean"
        : name.includes("da-sua") || name.includes("fixed")
          ? "fixed"
          : "dirty"
    }`;
    const alreadyCommitted = readImportMockDb().batches.find(
      (b) => b.branchId === branchId && b.status === "COMMITTED" && b.fileSha256 === sha
    );
    if (alreadyCommitted && !force) {
      return problem(
        409,
        "IMP_ALREADY_COMMITTED",
        "Tệp này đã được ghi vào phả",
        `Chi này đã có một lô đã ghi vào phả mang đúng mã băm ấy (lô ${alreadyCommitted.id}).`,
        instance,
        // Chốt vượt qua được có ý thức, không phải lệnh cấm.
        { overridable: true, overrideField: "force" }
      );
    }

    // Gọi ĐÚNG hàm mà test đơn vị dùng để gieo lô: một đường tạo lô duy nhất.
    const created = seedMockBatch({
      branchId,
      fileName: file.name,
      // `requireAccount` đã loại Khách ở trên, nên chỗ này luôn có `app_user`.
      uploadedBy: identity.appUserId ?? undefined,
    });

    // 201 + Location. Cả `VALIDATED` lẫn `FAILED` đều là 201: tệp đã được nhận
    // và đã có kết quả đối soát; "còn lỗi chặn" là một KẾT QUẢ, không phải một
    // lỗi HTTP.
    return HttpResponse.json(created, {
      status: 201,
      headers: { Location: `/api/v1/import/batches/${created.id}` },
    });
  }),

  /* ── Danh sách lô trong phạm vi người gọi ──────────────────────────────── */
  http.get(`${BASE}/batches`, ({ request }) => {
    const instance = "/api/v1/import/batches";
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    advanceCommits(Date.now());
    const url = new URL(request.url);
    const branchId = url.searchParams.get("branchId");
    const status = url.searchParams.get("status");
    const page = Number(url.searchParams.get("page") ?? 0);
    // Trần 200: trên ngưỡng đó thì đây không còn là màn đối soát mà là một bản
    // kết xuất.
    const size = Math.min(Number(url.searchParams.get("size") ?? 20), 200);

    if (branchId) {
      const branch = findMockBranch(branchId);
      if (!branch) return notFound(instance);
      // Chi ngoài phạm vi ra 403, KHÔNG ra một danh sách rỗng: "không có lô
      // nào" và "chi này không phải của bạn" là hai câu trả lời khác hẳn nhau.
      if (!canWriteInBranch(identity, branch.path)) return scopeViolation(instance, branch.name);
    }

    // PHẠM VI QUYẾT TRƯỚC, rồi mới phân trang. Lấy 20 lô rồi bỏ đi những lô
    // ngoài phạm vi sẽ trả về những trang thủng lỗ chỗ.
    const scope = new Set(
      MOCK_IMPORT_BRANCHES.filter((b) => canWriteInBranch(identity, b.path)).map((b) => b.id)
    );
    const rows = readImportMockDb()
      .batches.filter((b) => scope.has(b.branchId))
      .filter((b) => (branchId ? b.branchId === branchId : true))
      .filter((b) => (status ? b.status === status : true))
      .sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt))
      .slice(page * size, page * size + size)
      .map(batchDto);
    return HttpResponse.json(rows);
  }),

  /* ── Một lô ────────────────────────────────────────────────────────────── */
  http.get(`${BASE}/batches/:batchId`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    advanceCommits(Date.now());
    const batch = storedBatch(batchId);
    // Lô không có thật → 404; lô có thật ngoài phạm vi → 403. Hai mã khác nhau
    // vì "gõ nhầm mã lô" và "lô này không phải của tôi" dẫn tới hai việc khác.
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }
    return HttpResponse.json(batchDto(batch));
  }),

  /* ── Chạy lại bộ kiểm ──────────────────────────────────────────────────── */
  http.post(`${BASE}/batches/:batchId/validate`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/validate`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }
    if (CLOSED_STATUSES.includes(batch.status)) {
      return problem(
        422,
        "IMP_BATCH_CLOSED",
        "Lô đã chốt",
        "Lô này đã chốt, hãy tải lên lô mới.",
        instance
      );
    }

    // Không tích luỹ: một lượt kiểm sinh lại TOÀN BỘ danh sách. Kho giả sinh
    // danh sách từ tên tệp nên nó vốn đã tất định — giữ nguyên, và chỉ đóng
    // dấu thời điểm kiểm.
    const updated = mutateImportMockDb((db) => {
      const target = db.batches.find((b) => b.id === batchId)!;
      target.validatedAt = new Date().toISOString();
      // `version` tăng ở MỌI lần ghi, kể cả lần không đổi trạng thái.
      target.version += 1;
      // Tập cảnh báo y nguyên ⇒ vân tay y nguyên ⇒ xác nhận cũ CÒN hiệu lực.
      // Bắt tick lại một danh sách không đổi là cách chắc chắn để lần thứ ba
      // người ta tick mà không đọc.
      target.warningsDigest = warningsDigestOf(db.issues[batchId] ?? []);
      return batchDto(target);
    });
    return HttpResponse.json(updated);
  }),

  /* ── Lỗi và cảnh báo ───────────────────────────────────────────────────── */
  http.get(`${BASE}/batches/:batchId/issues`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/issues`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    const severity = new URL(request.url).searchParams.get(
      "severity"
    ) as ImportIssueSeverity | null;
    const all: ImportIssue[] = readImportMockDb().issues[batchId] ?? [];
    const filtered = severity ? all.filter((i) => i.severity === severity) : all;

    // Sắp TẤT ĐỊNH theo (sheet, rowNo, code, field, message): hai lượt kiểm
    // trên cùng một tệp phải cho hai danh sách so sánh được từng dòng.
    const order = (s: ImportIssue["sheet"]) => ["NHAN_KHAU", "HON_PHOI", "LO"].indexOf(s);
    return HttpResponse.json(
      [...filtered].sort(
        (a, b) =>
          order(a.sheet) - order(b.sheet) ||
          (a.rowNo ?? 0) - (b.rowNo ?? 0) ||
          a.code.localeCompare(b.code) ||
          (a.field ?? "").localeCompare(b.field ?? "") ||
          a.message.localeCompare(b.message)
      )
    );
  }),

  /**
   * Bản lỗi dạng Excel — **đường thoát** của người nhập liệu thật.
   *
   * Không có tham số `severity`: bộ sinh tự tách hai nhóm thành hai trang, nên
   * một nút là đủ và hai nút là sai. Phép kiểm là quyền **ghi** trên chi của
   * lô, như mọi endpoint đọc của lô.
   *
   * `Content-Disposition` mang **cả hai** dạng tên tệp, đúng như bản thật: dạng
   * RFC 5987 có dấu, và một dạng thường đã rụng dấu cho client cũ. Client phải
   * đọc dạng RFC 5987 **trước** — nếu không, Trưởng chi nhận về một tệp tên
   * "Danh sach can sua".
   */
  http.get(`${BASE}/batches/:batchId/issues.xlsx`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/issues.xlsx`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    const issues = readImportMockDb().issues[batchId] ?? [];
    const day = new Date().toISOString().slice(0, 10);
    return issuesXlsxResponse(
      fakeXlsx(
        `Tổng quan|Lỗi phải sửa: ${issues.filter((i) => i.severity === "BLOCKING").length}` +
          `|Nên xem lại: ${issues.filter((i) => i.severity === "WARNING").length}`
      ),
      `Danh sách cần sửa - ${batch.branchName ?? batch.branchId} - ${day}.xlsx`
    );
  }),

  /* ── Xem trước các dòng đang chờ ───────────────────────────────────────── */
  http.get(`${BASE}/batches/:batchId/rows`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/rows`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    const action = new URL(request.url).searchParams.get("action") as ImportPlannedAction | null;
    const rows: ImportRow[] = readImportMockDb().rows[batchId] ?? [];
    const filtered = action ? rows.filter((r) => r.plannedAction === action) : rows;
    return HttpResponse.json([...filtered].sort((a, b) => a.rowNo - b.rowNo));
  }),

  /* ── Nghi trùng ────────────────────────────────────────────────────────── */
  http.get(`${BASE}/batches/:batchId/duplicates`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/duplicates`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    const pairs: ImportDuplicatePair[] = readImportMockDb().duplicates[batchId] ?? [];
    // Điểm cao lên trước: máy chỉ được phép xếp THỨ TỰ XEM XÉT.
    return HttpResponse.json([...pairs].sort((a, b) => b.score - a.score));
  }),

  /**
   * Ghi quyết định cho **một** cặp. Trả cặp **và** lô đã tính lại.
   *
   * <h2>Bốn thứ đổi cùng lúc, nên cả bốn về trong một vòng gọi</h2>
   * Trạng thái cặp · `undecidedDuplicateCount` · `plannedCreateCount` /
   * `plannedUpdateCount` · `canApprove`. Trả mỗi cặp thì giao diện phải gọi
   * thêm một vòng để biết ba thứ còn lại, và trong lúc chưa gọi xong thì nút
   * duyệt hiện sai.
   *
   * <h2>"Hoãn" không đổi kế hoạch và không mở khoá gì</h2>
   * `DEFERRED` được ghi xuống thật — nó là một lời khai, không phải một khoảng
   * lặng — nhưng cặp ấy **vẫn** nằm trong số chưa quyết.
   *
   * <h2>Từ chối: `IMP_MERGE_NOT_APPLICABLE`</h2>
   * Gộp hai cặp của **cùng một dòng** trỏ sang **hai người khác nhau đã có
   * trong phả** là bảo hệ thống rằng một dòng vừa là người A vừa là người B.
   * Quyết định *vẫn được ghi* (người dùng đã nói ra ý của mình), nhưng lô dừng
   * lại bằng một lỗi **chặn** — hợp nhất hai hồ sơ đã nằm trong phả là việc của
   * màn quản lý nhân khẩu.
   */
  http.post(`${BASE}/batches/:batchId/duplicates/:pairId/decision`, async ({ request, params }) => {
    const batchId = String(params.batchId);
    const pairId = String(params.pairId);
    const instance = `/api/v1/import/batches/${batchId}/duplicates/${pairId}/decision`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }
    if (CLOSED_STATUSES.includes(batch.status)) {
      return problem(
        422,
        "IMP_BATCH_CLOSED",
        "Lô đã chốt",
        "Lô này đã chốt, hãy tải lên lô mới.",
        instance
      );
    }

    // Cặp không thuộc lô nêu trong đường dẫn được coi như KHÔNG TỒN TẠI: đường
    // dẫn là thứ duy nhất đã qua phép kiểm phạm vi chi.
    const pairs = readImportMockDb().duplicates[batchId] ?? [];
    if (!pairs.some((p) => p.id === pairId)) return notFound(instance);

    const body = (await request.json().catch(() => ({}))) as {
      decision?: unknown;
      note?: unknown;
    };
    const decision = body.decision;
    // `PENDING` bị từ chối cùng chỗ với một giá trị rác: rút lại một lời khai đã
    // ghi không phải là xoá nó đi.
    if (decision !== "MERGED" && decision !== "DISTINCT" && decision !== "DEFERRED") {
      return problem(
        400,
        "IMP_BAD_FORMAT",
        "Quyết định không hợp lệ",
        'Trường `decision` phải là một trong "MERGED", "DISTINCT", "DEFERRED".',
        instance
      );
    }
    const note = typeof body.note === "string" && body.note.trim() ? body.note.trim() : undefined;

    const result = mutateImportMockDb((db) => {
      const target = db.batches.find((b) => b.id === batchId)!;
      const list = db.duplicates[batchId]!;
      const pair = list.find((p) => p.id === pairId)!;
      const before = pair.status;

      pair.status = decision;
      pair.decidedBy = identity.appUserId ?? undefined;
      pair.decidedAt = new Date().toISOString();
      pair.note = note;

      // Kế hoạch chỉ đổi ở thao tác GỘP, và chỉ ở lần đầu cặp ấy thành `MERGED`.
      if (decision === "MERGED" && before !== "MERGED" && target.plannedCreateCount > 0) {
        target.plannedCreateCount -= 1;
        // Cặp `TREE`: dòng chuyển từ "tạo mới" sang "cập nhật" đúng hồ sơ ấy.
        // Cặp `FILE`: một trong hai dòng biến mất hẳn trước khi ghi.
        if (pair.existing.source === "TREE") target.plannedUpdateCount += 1;
      }

      // Nhóm gộp của CÙNG một dòng dính hai người khác nhau đã có trong phả.
      const treeTargets = new Set(
        list
          .filter(
            (p) => p.rowNo === pair.rowNo && p.status === "MERGED" && p.existing.source === "TREE"
          )
          .map((p) => p.existing.personId)
      );
      if (treeTargets.size >= 2) {
        const issues = db.issues[batchId] ?? [];
        const issueId = `${batchId}-merge-na-${pair.rowNo}`;
        if (!issues.some((i) => i.id === issueId)) {
          issues.push({
            id: issueId,
            severity: "BLOCKING",
            code: "IMP_MERGE_NOT_APPLICABLE",
            sheet: "NHAN_KHAU",
            rowNo: pair.rowNo,
            externalCode: pair.incoming.externalCode,
            message:
              "Dòng này được gộp vào hai người khác nhau đã có trong phả. Hệ thống không hợp nhất hai hồ sơ đã có — hãy chọn lại một trong hai, hoặc nhờ màn quản lý nhân khẩu hợp nhất hai hồ sơ ấy trước.",
          });
          db.issues[batchId] = issues;
        }
        target.blockingCount = issues.filter((i) => i.severity === "BLOCKING").length;
        // Lô DỪNG. Không có gì vào phả, và cổng duyệt đóng ở điều kiện thứ nhất.
        target.status = "FAILED";
        target.failureReason =
          "Một dòng được gộp vào hai người khác nhau đã có trong phả. Lô dừng ở đây.";
      }

      target.undecidedDuplicateCount = undecidedOf(list);
      target.version += 1;
      return { pair: { ...pair }, batch: batchDto(target) };
    });

    return HttpResponse.json(result);
  }),

  /* ── Xác nhận đã xem phần cần xem lại ──────────────────────────────────── */
  http.post(`${BASE}/batches/:batchId/acknowledge-warnings`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/acknowledge-warnings`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }
    if (CLOSED_STATUSES.includes(batch.status)) {
      return problem(
        422,
        "IMP_BATCH_CLOSED",
        "Lô đã chốt",
        "Lô này đã chốt nên không còn gì để xác nhận.",
        instance
      );
    }

    const updated = mutateImportMockDb((db) => {
      const target = db.batches.find((b) => b.id === batchId)!;
      // MỘT LỜI KHAI, không phải một cái cờ: ghi lại ai, lúc nào, và vân tay
      // của tập cảnh báo người ấy vừa đọc.
      target.warningsAcknowledgedAt = new Date().toISOString();
      target.warningsAcknowledgedBy = identity.appUserId ?? undefined;
      target.warningsAcknowledgedDigest = target.warningsDigest;
      target.version += 1;
      return batchDto(target);
    });
    // Lô không có cảnh báo nào vẫn trả 200: bấm nút trên một danh sách rỗng là
    // chuyện vô hại, và ném lỗi vào mặt người dùng vì điều đó chỉ làm màn đối
    // soát khó dùng hơn.
    return HttpResponse.json(updated);
  }),

  /* ── Duyệt và ghi vào phả ──────────────────────────────────────────────── */
  http.post(`${BASE}/batches/:batchId/commit`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/commit`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    advanceCommits(Date.now());
    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    // Bốn điều kiện, bốn mã, THEO ĐÚNG THỨ TỰ người nhập phải xử lý. Kiểm
    // ngược lại thì người nhập được bảo đi quyết mấy cặp nghi trùng của một lô
    // mà đằng nào cũng phải nộp lại.
    if (CLOSED_STATUSES.includes(batch.status) || batch.status === "COMMITTING") {
      return problem(
        422,
        "IMP_BATCH_CLOSED",
        "Lô đã chốt",
        "Lô này đã được ghi, đang ghi, hoặc đã có lô mới hơn thay thế.",
        instance
      );
    }
    if (batch.status !== "VALIDATED" || batch.blockingCount > 0) {
      return problem(
        422,
        "IMP_BLOCKING_ISSUES_PRESENT",
        "Còn lỗi phải sửa",
        `Còn ${batch.blockingCount} lỗi chặn phải sửa trong tệp. Sửa trong Excel rồi tải lại.`,
        instance
      );
    }
    // KHÔNG so `warningsAcknowledgedAt !== undefined`: xác nhận là một lời khai
    // gắn với VÂN TAY của tập cảnh báo đã đọc. Kiểm lại mà sinh cảnh báo mới
    // thì lời khai cũ hết hiệu lực, trong khi dấu thời gian vẫn còn nguyên.
    const ackInForce =
      batch.warningsAcknowledgedDigest !== null &&
      batch.warningsAcknowledgedDigest === batch.warningsDigest;
    if (batch.warningCount > 0 && !ackInForce) {
      return problem(
        422,
        "IMP_WARNINGS_NOT_ACKNOWLEDGED",
        "Chưa xem phần cần xem lại",
        batch.warningsAcknowledgedAt === undefined
          ? "Xác nhận đã xem xong phần cần xem lại trước khi duyệt."
          : "Bộ kiểm đã sinh thêm mục cần xem lại sau lần xác nhận gần nhất. Hãy đọc phần mới rồi xác nhận lại.",
        instance
      );
    }
    if (batch.undecidedDuplicateCount > 0) {
      return problem(
        422,
        "IMP_DUPLICATES_UNDECIDED",
        "Còn cặp nghi trùng chưa quyết",
        `Còn ${batch.undecidedDuplicateCount} cặp nghi trùng chưa ai quyết.`,
        instance
      );
    }

    const updated = mutateImportMockDb((db) => {
      const target = db.batches.find((b) => b.id === batchId)!;
      target.status = "COMMITTING";
      target.committedBy = identity.appUserId ?? undefined;
      target.version += 1;
      db.committingSince[batchId] = Date.now();
      return batchDto(target);
    });
    // 202: việc ghi chạy nền, luồng web không chờ. Trạng thái cuối CHỈ đến từ
    // `/commit-progress`.
    return HttpResponse.json(updated, {
      status: 202,
      headers: { Location: `/api/v1/import/batches/${batchId}/commit-progress` },
    });
  }),

  http.get(`${BASE}/batches/:batchId/commit-progress`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/commit-progress`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const now = Date.now();
    advanceCommits(now);
    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    const startedAt = readImportMockDb().committingSince[batchId];
    const elapsed = startedAt === undefined ? COMMIT_DURATION_MS : now - startedAt;
    const ratio = Math.min(1, elapsed / COMMIT_DURATION_MS);
    const phase: ImportCommitProgress["phase"] =
      batch.status === "COMMITTED"
        ? "DONE"
        : batch.status === "FAILED"
          ? "FAILED"
          : ratio < 0.25
            ? "PERSONS"
            : ratio < 0.6
              ? "PARENT_EDGES"
              : ratio < 0.9
                ? "SPOUSE_EDGES"
                : "CACHE";

    const view: ImportCommitProgress = {
      batchId,
      status: batch.status,
      phase,
      processedRows: Math.round(batch.personRowCount * ratio),
      totalRows: batch.personRowCount,
      // Luôn `true`: con số đếm ngoài giao dịch ghi nên nó là ước lượng theo
      // bản chất, không phải vì hiện thực còn dở.
      estimated: true,
      failureMessage: batch.status === "FAILED" ? batch.failureReason : undefined,
    };
    return HttpResponse.json(view);
  }),

  /* ── Gỡ cả lô ──────────────────────────────────────────────────────────── */
  http.get(`${BASE}/batches/:batchId/rollback-preflight`, ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/rollback-preflight`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    advanceCommits(Date.now());
    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    const blockers = rollbackBlockers(batch);
    const view: ImportRollbackPreflight = { canRollback: blockers.length === 0, blockers };
    return HttpResponse.json(view);
  }),

  http.post(`${BASE}/batches/:batchId/rollback`, async ({ request, params }) => {
    const batchId = String(params.batchId);
    const instance = `/api/v1/import/batches/${batchId}/rollback`;
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    advanceCommits(Date.now());
    const batch = storedBatch(batchId);
    if (!batch) return notFound(instance);
    if (!canWriteInBranch(identity, batch.branchPath ?? "")) {
      return scopeViolation(instance, batch.branchName ?? "chi này");
    }

    // `reason` là TUỲ CHỌN — thân rỗng là hợp lệ, và một thân rỗng không được
    // phép thành 500.
    let reason: string | null = null;
    try {
      const body = (await request.json()) as { reason?: string } | null;
      reason = body?.reason?.trim() || null;
    } catch {
      reason = null;
    }

    const blockers = rollbackBlockers(batch);
    if (blockers.length > 0) {
      return problem(
        422,
        "IMP_ROLLBACK_REFUSED",
        "Không gỡ được lô này",
        `Không gỡ được lô này: ${blockers.length} vướng mắc.`,
        instance,
        { blockers }
      );
    }

    const updated = mutateImportMockDb((db) => {
      const target = db.batches.find((b) => b.id === batchId)!;
      db.branchPersonCounts[target.branchId] =
        (db.branchPersonCounts[target.branchId] ?? 0) - target.plannedCreateCount;
      // TRẠNG THÁI KHÔNG ĐỔI: lô vẫn `COMMITTED` vì nó đã TỪNG được ghi, và
      // `committedAt` là mốc mà mọi phép kiểm "ai đã động vào sau khi ghi" dựa
      // vào. Việc đã gỡ là hai cột riêng — và hai cột ấy chưa ra tới DTO.
      target.rolledBackAt = new Date().toISOString();
      target.rollbackReason = reason;
      target.version += 1;
      return batchDto(target);
    });
    // 200, không phải 202: việc gỡ chạy đồng bộ trong đúng một transaction.
    return HttpResponse.json(updated);
  }),

  /* ── Tiến độ từng chi ──────────────────────────────────────────────────── */
  http.get(`${BASE}/progress`, ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, "/api/v1/import/progress");
    if (denied) return denied;

    advanceCommits(Date.now());
    const db = readImportMockDb();

    const rows: ImportBranchProgress[] = MOCK_IMPORT_BRANCHES.map((branch) => {
      const inScope = canWriteInBranch(identity, branch.path);
      const mine = db.batches.filter((b) => b.branchId === branch.id);
      const open = mine.find((b) => !CLOSED_STATUSES.includes(b.status));
      const stage: ImportBranchProgress["stage"] = mine.some((b) => b.status === "COMMITTED")
        ? "COMMITTED"
        : mine.some((b) => b.status === "VALIDATED" || b.status === "COMMITTING")
          ? "RECONCILING"
          : mine.length > 0
            ? "UPLOADED"
            : "NOT_STARTED";

      const row: ImportBranchProgress = {
        branchId: branch.id,
        branchName: branch.name,
        branchPath: branch.path,
        stage,
        personsInTree: db.branchPersonCounts[branch.id] ?? 0,
        // `missingGioCount` KHÔNG bị cắt: nó là một phép đếm về người đã khuất,
        // không phải một lời phán về ai đang làm chậm.
        missingGioCount: (db.issues[open?.id ?? ""] ?? []).filter(
          (i) => i.code === "IMP_MISSING_GIO"
        ).length,
      };
      // VẮNG MẶT, không phải 0: chưa ai đếm cuốn sổ giấy của chi ấy.
      if (branch.expectedPersons !== undefined) row.expectedPersons = branch.expectedPersons;

      if (inScope) {
        // Năm trường chỉ gắn vào khi chi NẰM TRONG phạm vi. `coordinatorName`
        // là tên một người còn sống; bốn trường kia mô tả việc đang làm dở của
        // người khác và biến màn "còn thiếu gì" thành màn "ai đang sai nhiều".
        const coordinator = MOCK_COORDINATORS[branch.id];
        if (coordinator) row.coordinatorName = coordinator;
        if (open) {
          row.openBatch = { id: open.id, status: open.status, uploadedAt: open.uploadedAt };
        }
        row.blockingCount = open?.blockingCount ?? 0;
        row.warningCount = open?.warningCount ?? 0;
        row.undecidedDuplicateCount = open?.undecidedDuplicateCount ?? 0;
      }
      return row;
    });

    return HttpResponse.json(rows);
  }),
];

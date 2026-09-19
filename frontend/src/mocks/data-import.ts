import type {
  ImportBatch,
  ImportBatchStatus,
  ImportBranchKind,
  ImportDuplicateEvidence,
  ImportDuplicatePair,
  ImportDuplicatePolicy,
  ImportIssue,
  ImportRow,
} from "@/lib/api/data-import";

/**
 * Kho dữ liệu giả của đường ống nhập liệu, **bám `contracts/openapi.yaml`**.
 *
 * <h2>Bộ giả lập phải nói dối ít nhất có thể, kể cả khi sự thật bất tiện</h2>
 * Hai lối gọi từng vắng mặt — xuất lỗi ra Excel và ghi quyết định một cặp nghi
 * trùng — **nay có thật**, nên ở đây chúng chạy thật: quyết định được ghi
 * xuống kho, và bốn con số của lô được **tính lại** đúng như máy chủ tính. Quan
 * trọng nhất là bộ giả lập giữ nguyên hai hệ quả khó chịu thay vì xoa dịu
 * chúng: `DEFERRED` **vẫn** chặn cổng duyệt, và một nhóm gộp dính hai người đã
 * có trong phả **bị từ chối** bằng một lỗi chặn.
 *
 * <h2>Vì sao kho này BỀN, khác mọi kho giả khác trong `src/mocks/`</h2>
 * Các bộ giả lập còn lại giữ trạng thái trong biến ở phạm vi module, nên tải
 * lại trang là mất sạch. Với luồng nhập liệu thì đó là một mô phỏng **sai bản
 * chất**: một lô nằm ở bảng `import_*` trên máy chủ, và cả tính năng được thiết
 * kế quanh việc Trưởng chi làm 6–10 buổi, đóng trình duyệt giữa chừng rồi hôm
 * sau mở lại.
 *
 * Vì vậy kho này ghi xuống `localStorage` — nó đóng vai **cơ sở dữ liệu của máy
 * chủ giả**, không phải bộ nhớ của ứng dụng. Ở Node (vitest) không có
 * `localStorage` thì tự rơi về bộ nhớ tiến trình, và `resetImportMockDb()` dọn
 * giữa các ca kiểm.
 *
 * Dữ liệu ở đây là **người đã khuất và dữ liệu bịa**, không có trường Tầng 3
 * của người còn sống.
 */

const STORAGE_KEY = "giapha.mock.data-import.v3";

/** Ngưỡng do máy chủ công bố — `DuplicateScorer.NGUONG_NGHI_TRUNG` là 70. */
export const MOCK_DUPLICATE_POLICY: ImportDuplicatePolicy = {
  suspectThreshold: 70,
  preselectMergeThreshold: 85,
  autoMerge: false,
};

/** Một chi trong kho giả. `canImport` **không** nằm ở đây: nó phụ thuộc người gọi. */
export interface MockBranchSeed {
  id: string;
  name: string;
  path: string;
  kind: ImportBranchKind;
  /**
   * Số người Hội đồng đã đếm trên bản phả **giấy** của chi ấy.
   *
   * **Vắng mặt với ba chi trong bốn, và đó là dữ liệu đúng**: nguồn thật là một
   * bảng riêng do Hội đồng điền tay, rỗng cho tới khi có người ngồi đếm. Ghi
   * cứng một con số cho mọi chi là dạy giao diện rằng mẫu số luôn có — rồi màn
   * tiến độ sẽ vẽ "còn thiếu 0 người" cho một chi chưa ai mở sổ ra xem.
   */
  expectedPersons?: number;
}

/**
 * Cây chi của dòng họ giả — `GET /import/branches` trả **cả cây** cho mọi tài
 * khoản đã khởi tạo, kèm cờ `canImport` tính theo người gọi.
 *
 * Không có `codePrefix`: bảng `branch` không có cột ấy. Mọi quy tắc tiền tố mã
 * cứng là quy tắc tự bịa và sẽ đá nhau với cách đánh số thật của từng chi.
 */
export const MOCK_IMPORT_BRANCHES: readonly MockBranchSeed[] = [
  { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", kind: "CHI", expectedPersons: 380 },
  { id: "b-chi2", name: "Chi Nhị", path: "root.chi_nhi", kind: "CHI" },
  { id: "b-chi3", name: "Chi Tam", path: "root.chi_tam", kind: "CHI" },
  // Chi chưa có ai nhận. Kế hoạch 00 §4: rủi ro lớn nhất của đợt này không nằm
  // ở mã — chi nào chưa có người phụ trách thì đứng im.
  { id: "b-chi4", name: "Chi Tứ", path: "root.chi_tu", kind: "CHI" },
];

/**
 * Một lô trong kho giả.
 *
 * Hai chỗ cố ý khác `ImportBatch` của hợp đồng, và cả hai đều là **bản sao
 * trung thực của backend**:
 *
 * <ul>
 *   <li>`canApprove` **không** lưu. Nó được tính lúc đọc, từ đúng bất biến mà
 *       `ImportCommitGate` áp dụng — lưu nó là dựng ra nguồn chân lý thứ hai
 *       rồi sớm muộn hai nguồn lệch nhau.</li>
 *   <li>`rolledBackAt` **có** trong kho nhưng **không** ra tới JSON: cột ấy có
 *       thật trong `import_batch` mà chưa có trên DTO. Giao diện vì thế không
 *       đọc được nó, và ca kiểm nào dựa vào nó là ca kiểm đang tự bịa.</li>
 * </ul>
 */
export type StoredBatch = Omit<ImportBatch, "canApprove"> & {
  /**
   * Vân tay của **tập cảnh báo** ở lần kiểm gần nhất, và vân tay của tập mà
   * người xác nhận đã đọc.
   *
   * Hợp đồng không lộ hai trường này ra, và đó là chủ ý: chúng là *cơ chế*, còn
   * *câu trả lời* là `canApprove`. Bộ giả lập giữ chúng vì không có chúng thì
   * không mô phỏng được bất biến quan trọng nhất của bước này — kiểm lại mà
   * sinh cảnh báo MỚI thì xác nhận cũ hết hiệu lực, trong khi dấu thời gian vẫn
   * còn nguyên.
   */
  warningsDigest: string;
  warningsAcknowledgedDigest: string | null;
  rollbackReason: string | null;
};

export interface ImportMockDb {
  batches: StoredBatch[];
  issues: Record<string, ImportIssue[]>;
  rows: Record<string, ImportRow[]>;
  duplicates: Record<string, ImportDuplicatePair[]>;
  /** Số nhân khẩu đã vào phả theo chi — đổi đúng một lần, lúc ghi và lúc gỡ lô. */
  branchPersonCounts: Record<string, number>;
  /** Thời điểm bấm duyệt, để `commit-progress` diễn được một lượt ghi chạy nền. */
  committingSince: Record<string, number>;
  /**
   * Số hồ sơ do một lô đã ghi sinh ra mà **người khác đã sửa sau đó** — đầu vào
   * duy nhất của `IMP_ROLLBACK_REFUSED`.
   *
   * Bản thật suy con số này từ `person.version` tại lúc ghi. Bộ giả lập không
   * có bảng `person` nên để ca kiểm tự gieo bằng {@link markEditedAfterCommit}:
   * bịa ra một quy tắc "cứ lô thứ hai thì bị từ chối" sẽ dạy sai về điều kiện
   * thật, mà điều kiện thật mới là thứ quyết định câu chữ trên màn xác nhận.
   */
  editedAfterCommit: Record<string, number>;
  seq: number;
}

const INITIAL_PERSON_COUNTS: Record<string, number> = {
  "b-chi1": 42,
  "b-chi2": 8,
  "b-chi3": 0,
  "b-chi4": 0,
};

function emptyDb(): ImportMockDb {
  return {
    batches: [],
    issues: {},
    rows: {},
    duplicates: {},
    branchPersonCounts: { ...INITIAL_PERSON_COUNTS },
    committingSince: {},
    editedAfterCommit: {},
    seq: 13,
  };
}

let memoryDb: ImportMockDb | null = null;

function storage(): Storage | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage;
  } catch {
    // Safari riêng tư ném ngay khi chạm vào. Bộ giả lập không được phép làm sập
    // màn hình vì một kho dữ liệu giả.
    return null;
  }
}

export function readImportMockDb(): ImportMockDb {
  const store = storage();
  if (!store) {
    memoryDb ??= emptyDb();
    return memoryDb;
  }
  try {
    const raw = store.getItem(STORAGE_KEY);
    if (!raw) return emptyDb();
    return JSON.parse(raw) as ImportMockDb;
  } catch {
    return emptyDb();
  }
}

export function writeImportMockDb(db: ImportMockDb): void {
  const store = storage();
  if (!store) {
    memoryDb = db;
    return;
  }
  try {
    store.setItem(STORAGE_KEY, JSON.stringify(db));
  } catch {
    memoryDb = db;
  }
}

export function resetImportMockDb(): void {
  memoryDb = null;
  storage()?.removeItem(STORAGE_KEY);
}

export function mutateImportMockDb<T>(fn: (db: ImportMockDb) => T): T {
  const db = readImportMockDb();
  const result = fn(db);
  writeImportMockDb(db);
  return result;
}

/**
 * Gieo ca "lượt kiểm lại sinh ra một cảnh báo MỚI".
 *
 * Đây là ca duy nhất chứng minh được rằng giao diện không suy "đã xác nhận" từ
 * `warningsAcknowledgedAt`: sau lời gọi này dấu thời gian **vẫn còn nguyên**,
 * `warningCount` tăng một, còn `canApprove` phải quay về `false`.
 */
export function markNewWarningAfterAck(batchId: string): void {
  mutateImportMockDb((db) => {
    const batch = db.batches.find((b) => b.id === batchId);
    if (!batch) return;
    const issues = db.issues[batchId] ?? [];
    issues.push({
      id: `${batchId}-w-moi`,
      severity: "WARNING",
      code: "IMP_ROW_DISAPPEARED",
      sheet: "NHAN_KHAU",
      rowNo: 99,
      externalCode: "AT-07-001",
      message: "Người này có ở lần nhập trước nhưng không còn trong tệp lần này. Không xoá gì cả.",
    });
    db.issues[batchId] = issues;
    batch.warningCount = issues.filter((i) => i.severity === "WARNING").length;
    batch.warningsDigest = warningsDigestOf(issues);
    batch.version += 1;
  });
}

/**
 * Gieo ca **từ chối hợp nhất**: thêm một cặp nghi trùng thứ hai cho **cùng dòng
 * 12**, trỏ sang một người **khác** đã có trong phả.
 *
 * Gộp cả hai cặp ấy nghĩa là bảo hệ thống rằng một dòng trong tệp vừa là người
 * A vừa là người B — hai hồ sơ đã nằm sẵn trong phả. Máy chủ từ chối bằng lỗi
 * chặn `IMP_MERGE_NOT_APPLICABLE`, và đó là câu trả lời đúng: hợp nhất hai hồ
 * sơ *đã có trong phả* là việc của màn quản lý nhân khẩu, không phải của màn
 * nhập liệu.
 *
 * Cặp này **không** nằm trong bộ mặc định: thêm nó vào mọi lô sẽ đổi con số "3
 * cặp nghi trùng" mà cả quy trình đang dạy người dùng đọc, chỉ để phục vụ một
 * ca hiếm.
 */
export function seedConflictingMergePair(batchId: string): string {
  mutateImportMockDb((db) => {
    const batch = db.batches.find((b) => b.id === batchId);
    const pairs = db.duplicates[batchId];
    if (!batch || !pairs || pairs.some((p) => p.id === DUP_PAIR_TREE_SECOND)) return;
    const first = pairs.find((p) => p.rowNo === 12);
    if (!first) return;
    pairs.push({
      id: DUP_PAIR_TREE_SECOND,
      rowNo: 12,
      score: 76,
      preselectMerge: false,
      status: "PENDING",
      signals: "Trùng tên huý và cùng đời, khác ngày giỗ.",
      incoming: { ...first.incoming },
      existing: { source: "TREE", personId: "p-002" },
      evidence: [],
    });
    batch.suspectDuplicateCount = pairs.length;
    batch.undecidedDuplicateCount = undecidedOf(pairs);
    batch.version += 1;
  });
  return DUP_PAIR_TREE_SECOND;
}

/** Gieo ca "đã có người sửa hồ sơ sau khi ghi" cho màn xác nhận gỡ lô. */
export function markEditedAfterCommit(batchId: string, soNguoi: number): void {
  mutateImportMockDb((db) => {
    db.editedAfterCommit[batchId] = soNguoi;
  });
}

export function findMockBranch(branchId: string): MockBranchSeed | undefined {
  return MOCK_IMPORT_BRANCHES.find((b) => b.id === branchId);
}

/** Trạng thái đã chốt — không nhận thêm thao tác nào. Không có `ROLLED_BACK`. */
export const CLOSED_STATUSES: readonly ImportBatchStatus[] = ["COMMITTED", "SUPERSEDED"];

/**
 * `canApprove` — **một bản, tính lúc đọc**, sao y `ImportCommitGate`.
 *
 * `undecidedDuplicateCount` đếm cặp `PENDING` **và** cặp `DEFERRED`: xem
 * {@link undecidedOf}. "Hoãn" không phải "đã quyết".
 */
export function canApproveOf(batch: StoredBatch): boolean {
  const acknowledgedInForce =
    batch.warningCount === 0 ||
    (batch.warningsAcknowledgedDigest !== null &&
      batch.warningsAcknowledgedDigest === batch.warningsDigest);
  return (
    batch.status === "VALIDATED" &&
    batch.blockingCount === 0 &&
    acknowledgedInForce &&
    batch.undecidedDuplicateCount === 0
  );
}

/**
 * Số cặp **chưa quyết** — `PENDING` cộng `DEFERRED`.
 *
 * `DEFERRED` nằm trong con số này là cả điểm mấu chốt của cơ chế: nó tồn tại để
 * người đối chiếu không phải chọn bừa khi chưa chắc, nhưng nếu nó mở khoá nút
 * duyệt thì nó lập tức thành nút "cho tôi qua".
 */
export function undecidedOf(pairs: readonly ImportDuplicatePair[]): number {
  return pairs.filter((p) => p.status === "PENDING" || p.status === "DEFERRED").length;
}

/** Vân tay của một tập cảnh báo — đổi khi và chỉ khi tập ấy đổi. */
export function warningsDigestOf(issues: readonly ImportIssue[]): string {
  return issues
    .filter((i) => i.severity === "WARNING")
    .map((i) => i.id)
    .sort()
    .join(",");
}

/** Thân JSON của một lô, đúng tập trường hợp đồng công bố — không hơn. */
export function batchDto(batch: StoredBatch): ImportBatch {
  // Vân tay và lý do gỡ ở lại kho: chúng là CƠ CHẾ, không phải câu trả lời. Hợp
  // đồng cố ý không lộ chúng ra để client không có cách nào tự suy điều kiện
  // duyệt từ các trường lẻ — `canApprove` là câu trả lời duy nhất.
  const rest = { ...batch } as Partial<StoredBatch>;
  delete rest.warningsDigest;
  delete rest.warningsAcknowledgedDigest;
  delete rest.rollbackReason;
  return {
    ...(rest as Omit<
      StoredBatch,
      "warningsDigest" | "warningsAcknowledgedDigest" | "rollbackReason"
    >),
    canApprove: canApproveOf(batch),
  };
}

/* ══════════════════════════════════════════════════════════════════════════
   Sinh một lô từ tên tệp
   ══════════════════════════════════════════════════════════════════════════ */

/**
 * Bộ giả lập chọn kịch bản theo **tên tệp**, và đó là một quyết định phải nói ra.
 *
 * Backend thật đọc nội dung `.xlsx` bằng Apache POI. Bộ giả lập không đọc
 * `.xlsx` — dựng một bộ đọc Excel trong trình duyệt chỉ để có dữ liệu giả là
 * đổi một tuần công lấy thứ sẽ bị vứt đi. Nên tên tệp đóng vai công tắc kịch
 * bản, và **đây là chỗ bộ giả lập khác bản thật rõ nhất**:
 *
 *   `*sach*` / `*clean*`      → 0 lỗi chặn, 0 cảnh báo, 0 cặp nghi trùng
 *   `*da-sua*` / `*fixed*`    → 0 lỗi chặn, còn 11 cảnh báo và 3 cặp nghi trùng
 *   còn lại                   → 6 lỗi chặn + 11 cảnh báo + 3 cặp nghi trùng
 *   không phải `.xlsx`        → 400 IMP_BAD_FORMAT
 *
 * <h2>Vì sao phải có kịch bản "sạch hoàn toàn"</h2>
 * Không phải để cho dễ. Hôm nay `acknowledge-warnings` chưa có và bảng quyết
 * định nghi trùng chưa có, nên **mọi** lô có cảnh báo hoặc có cặp nghi trùng
 * đều không duyệt được — đó là sự thật của hệ thống, và kịch bản `FIXED` giữ
 * nguyên nó. Một tệp sạch hoàn toàn là ca duy nhất còn đi hết được tới bước ghi
 * và bước gỡ, nên nó là ca duy nhất kiểm được hai bước ấy mà không phải giả vờ
 * một endpoint đã tồn tại.
 */
export type MockScenario = "DIRTY" | "FIXED" | "CLEAN";

export function scenarioOf(fileName: string): MockScenario {
  const lower = fileName.toLowerCase();
  if (lower.includes("sach") || lower.includes("clean")) return "CLEAN";
  return lower.includes("da-sua") || lower.includes("fixed") ? "FIXED" : "DIRTY";
}

/**
 * Sáu lỗi chặn của kịch bản bẩn.
 *
 * Cố ý phủ sáu hình dạng khác nhau chứ không sáu bản sao của một hình: có lỗi
 * mang gợi ý mã, có lỗi mang đường vòng, có lỗi mang danh sách dòng, có lỗi
 * mang hai con số đời, có cái bẫy "Excel nuốt ngày âm thành số sê-ri", và một
 * lỗi của **cả lô** (`sheet: "LO"`, không có `rowNo`) — hình dạng mà giao diện
 * hay quên và sẽ in ra "Dòng undefined".
 */
function blockingIssues(batchId: string): ImportIssue[] {
  return [
    {
      id: `${batchId}-b1`,
      severity: "BLOCKING",
      code: "IMP_PARENT_NOT_FOUND",
      sheet: "NHAN_KHAU",
      rowNo: 37,
      field: "Mã cha",
      externalCode: "AT-05-012",
      message: 'Mã cha "AT-04-O03" không có trong tệp, cũng không có trong phả của chi này.',
      // Gợi ý do MÁY CHỦ tính: nó thấy cả tệp lẫn `person_external_ref`. Cố ý
      // gồm cặp chữ O / số 0 — lỗi gõ phổ biến nhất khi chép từ sổ giấy.
      context: {
        maKhongTimThay: "AT-04-O03",
        goiY: ["AT-04-003", "AT-04-013", "AT-04-030"],
      },
    },
    {
      id: `${batchId}-b2`,
      severity: "BLOCKING",
      code: "IMP_CYCLE",
      sheet: "NHAN_KHAU",
      rowNo: 52,
      field: "Mã cha",
      externalCode: "AT-05-021",
      message: "Vòng lặp tổ tiên: một người đang là cha của chính tổ tiên mình.",
      context: { chuoi: ["AT-05-021", "AT-04-008", "AT-03-002", "AT-05-021"] },
    },
    {
      id: `${batchId}-b3`,
      severity: "BLOCKING",
      code: "IMP_DUP_CODE",
      sheet: "NHAN_KHAU",
      rowNo: 58,
      field: "Mã",
      externalCode: "AT-05-030",
      message: "Mã AT-05-030 xuất hiện ở nhiều dòng trong cùng một tệp.",
      context: { cacDong: [58, 59, 61] },
    },
    {
      id: `${batchId}-b4`,
      severity: "BLOCKING",
      code: "IMP_GENERATION_MISMATCH",
      sheet: "NHAN_KHAU",
      rowNo: 64,
      field: "Đời",
      externalCode: "AT-06-004",
      message: "Đời khai là 6 nhưng cha ở đời 6, nên người này phải ở đời 7.",
      context: { doiKhai: 6, doiSuyRa: 7 },
    },
    {
      id: `${batchId}-b5`,
      severity: "BLOCKING",
      code: "IMP_LUNAR_DATE_IS_SERIAL",
      sheet: "NHAN_KHAU",
      rowNo: 71,
      field: "Ngày mất âm",
      externalCode: "AT-06-011",
      message:
        "Ô Ngày mất âm đến nơi dưới dạng số sê-ri Excel (45890). Đặt định dạng cột thành Văn bản rồi gõ lại 15/8.",
    },
    {
      // Lỗi của CẢ LÔ: không có `rowNo`, và giao diện phải nói "Cả lô" thay vì
      // in ra một số dòng không tồn tại.
      id: `${batchId}-b6`,
      severity: "BLOCKING",
      code: "IMP_MASS_CREATE_GUARD",
      sheet: "LO",
      message:
        "Lô này sẽ tạo mới 276 người trong khi chi chỉ có 42 người trong phả. Hãy kiểm lại cột Mã trước khi duyệt.",
    },
  ];
}

/** Mười một mục "cần xem lại" — vẫn nhập được, nhưng phải có người đọc qua. */
function warningIssues(batchId: string): ImportIssue[] {
  const missingGio = [40, 41, 44, 47].map<ImportIssue>((rowNo, i) => ({
    id: `${batchId}-w-gio-${i}`,
    severity: "WARNING",
    code: "IMP_MISSING_GIO",
    sheet: "NHAN_KHAU",
    rowNo,
    field: "Ngày mất âm",
    externalCode: `AT-05-01${i + 4}`,
    message: "Đã mất nhưng trống Ngày mất âm — người này sẽ không bao giờ được nhắc giỗ.",
  }));

  return [
    {
      id: `${batchId}-w1`,
      severity: "WARNING",
      code: "IMP_SUSPECT_DUPLICATE",
      sheet: "NHAN_KHAU",
      rowNo: 12,
      field: "Họ tên",
      externalCode: "AT-03-004",
      message: "Dòng này nghi trùng với một nhân khẩu đã có trong phả (90/100 điểm).",
      // Đã CẮT sạch trường dữ liệu của người trong phả: chỉ còn khoá.
      context: { nghiNgo: [{ nguon: "TREE", diem: 90, personId: "p-001" }] },
    },
    {
      id: `${batchId}-w2`,
      severity: "WARNING",
      code: "IMP_SUSPECT_DUPLICATE",
      sheet: "NHAN_KHAU",
      rowNo: 19,
      field: "Họ tên",
      externalCode: "AT-04-002",
      message: "Dòng này nghi trùng với một nhân khẩu đã có trong phả (72/100 điểm).",
      context: { nghiNgo: [{ nguon: "TREE", diem: 72, personId: "p-778" }] },
    },
    {
      id: `${batchId}-w3`,
      severity: "WARNING",
      code: "IMP_SUSPECT_DUPLICATE",
      sheet: "NHAN_KHAU",
      rowNo: 26,
      field: "Họ tên",
      externalCode: "AT-04-009",
      message: "Dòng 26 và dòng 9 trong chính tệp này nghi là cùng một người (88/100 điểm).",
      context: { nghiNgo: [{ nguon: "FILE", diem: 88, rowNo: 9 }] },
    },
    {
      id: `${batchId}-w4`,
      severity: "WARNING",
      code: "IMP_TABOO_COLLISION",
      sheet: "NHAN_KHAU",
      rowNo: 31,
      field: "Tên huý",
      externalCode: "AT-05-003",
      message: "Tên huý “Hoằng” trùng tên huý của một bậc trên. Ghi được, nhưng cần người trong họ xác nhận.",
      // Tên và đời của bậc trên đã bị cắt — chỉ còn khoá.
      context: { tenHuy: "Hoằng", bacTrenId: "p-010" },
    },
    ...missingGio,
    {
      id: `${batchId}-w5`,
      severity: "WARNING",
      code: "IMP_GIO_DAY_30",
      sheet: "NHAN_KHAU",
      rowNo: 55,
      field: "Ngày mất âm",
      externalCode: "AT-06-001",
      message: "Ngày mất âm là 30 mà không rõ năm — năm nào tháng đó thiếu thì giỗ trôi về 29.",
    },
    {
      id: `${batchId}-w6`,
      severity: "WARNING",
      code: "IMP_MISSING_PLACE_CODE",
      sheet: "NHAN_KHAU",
      rowNo: 58,
      field: "Mã tỉnh",
      externalCode: "AT-06-005",
      message: "Có nguyên quán bằng chữ nhưng không có mã tỉnh — dòng này không vào được báo cáo dân số.",
    },
    {
      id: `${batchId}-w7`,
      severity: "WARNING",
      code: "IMP_HEIR_TARGET_NOT_FOUND",
      sheet: "NHAN_KHAU",
      rowNo: 62,
      field: "Kế tự cho mã",
      externalCode: "AT-06-009",
      message: 'Mã người được kế tự "AT-05-O21" không tìm thấy. Kế tự là quan hệ thêm, không chặn.',
      context: { goiY: ["AT-05-021"] },
    },
  ];
}

/**
 * Khoá của bốn cặp giả — **UUID cố định**, không sinh ngẫu nhiên.
 *
 * Test và spec E2E trỏ thẳng vào `data-testid` dựng từ khoá này, và một khoá
 * đổi mỗi lần chạy sẽ biến mọi ca kiểm thành ca kiểm dò theo thứ tự hiển thị —
 * đúng thứ vỡ ngay khi máy chủ đổi cách xếp.
 */
export const DUP_PAIR_TREE_VISIBLE = "7a1d4c60-0000-4000-8000-000000000012";
export const DUP_PAIR_TREE_HIDDEN = "7a1d4c60-0000-4000-8000-000000000019";
export const DUP_PAIR_IN_FILE = "7a1d4c60-0000-4000-8000-000000000026";
/** Cặp thứ hai của **cùng dòng 12**, trỏ sang một người khác đã có trong phả. */
export const DUP_PAIR_TREE_SECOND = "7a1d4c60-0000-4000-8000-00000000012b";

function evidence(
  field: ImportDuplicateEvidence["field"],
  match: ImportDuplicateEvidence["match"],
  existingValue?: string,
  incomingValue?: string
): ImportDuplicateEvidence {
  // KHÔNG có `points`: bộ dò chưa công bố bản tách điểm theo từng tín hiệu, và
  // một con số bịa ở đây sẽ được người đối chiếu đọc như điểm thật.
  return { field, match, existingValue, incomingValue };
}

/**
 * Ba cặp nghi trùng, cố ý dựng ba tình huống khác nhau **về bản chất riêng tư**.
 *
 * <ol>
 *   <li><b>TREE, tra được</b> (`p-001`, cụ Thuỷ Tổ đã khuất) — giao diện cầm
 *       khoá gọi `GET /persons/{id}` và dựng được cột bên phả.</li>
 *   <li><b>TREE, KHÔNG tra được</b> (`p-778`) — `GET /persons` trả `404`. Đây
 *       là ca <b>bình thường</b>, không phải lỗi: bộ dò quét toàn dòng họ nên
 *       người bị nghi có thể đang còn sống ở một chi khác mà người nhập chi này
 *       không được biết là có tồn tại hay không.</li>
 *   <li><b>FILE</b> — một dòng khác trong chính tệp vừa nộp. Không có gì để
 *       giấu với chính tác giả của nó, nên `evidence` đầy đủ. Ca này rất hay
 *       xảy ra: một người đàn ông xuất hiện ở cả trang đời cha lẫn trang đời
 *       con trong sổ.</li>
 * </ol>
 *
 * Hai cặp `TREE` mang `evidence: []` và **không** có `hint` — bất biến riêng
 * tư, không phải thiếu sót. Chúng **có** `signals`, và đó là ô "vì sao nghi"
 * duy nhất của chúng: nhãn ấy nói *vì sao nghi* mà không tiết lộ *người ấy là
 * ai*, nên nó an toàn ở cả hai loại cặp.
 *
 * Khoá là **UUID thật** — `import_duplicate_pair.id` — chứ không còn là khoá
 * suy ra từ `(rowNo, bên bị nghi)`. Nó ổn định qua các lần kiểm lại, và đó là
 * điều làm cho quyết định cũ vẫn dính đúng cặp cũ.
 */
function duplicatePairs(): ImportDuplicatePair[] {
  return [
    {
      id: DUP_PAIR_TREE_VISIBLE,
      rowNo: 12,
      score: 90,
      preselectMerge: true,
      status: "PENDING",
      signals: "Trùng ngày giỗ 15/8, cùng đời 6, cùng mã cha.",
      incoming: {
        source: "FILE",
        rowNo: 12,
        externalCode: "AT-03-004",
        displayName: "Nguyễn Văn Trực",
        generation: 6,
        birthYear: 1903,
        deathLunar: "15/8",
        fatherCode: "AT-02-001",
        nativePlace: "Đông Ngạc",
      },
      existing: { source: "TREE", personId: "p-001" },
      evidence: [],
    },
    {
      id: DUP_PAIR_TREE_HIDDEN,
      rowNo: 19,
      score: 72,
      preselectMerge: false,
      status: "PENDING",
      signals: "Trùng tên huý và cùng chi, lệch năm sinh 2 năm.",
      incoming: {
        source: "FILE",
        rowNo: 19,
        externalCode: "AT-04-002",
        displayName: "Nguyễn Văn Bân",
        tabooName: "Bân",
        generation: 7,
        birthYear: 1931,
        deathLunar: "21/3",
        fatherCode: "AT-03-004",
        nativePlace: "Đông Ngạc",
      },
      existing: { source: "TREE", personId: "p-778" },
      evidence: [],
    },
    {
      id: DUP_PAIR_IN_FILE,
      rowNo: 26,
      score: 88,
      preselectMerge: true,
      status: "PENDING",
      hint: "Hai dòng cùng tên huý và cùng mã cha, nhưng một dòng bỏ trống ngày giỗ.",
      signals: "Trùng tên huý, trùng mã cha.",
      incoming: {
        source: "FILE",
        rowNo: 26,
        externalCode: "AT-04-009",
        displayName: "Nguyen Thi Lanh",
        tabooName: "Lành",
        fatherCode: "AT-03-004",
      },
      existing: {
        source: "FILE",
        rowNo: 9,
        externalCode: "AT-04-009B",
        displayName: "Nguyễn Thị Lành",
        tabooName: "Lành",
        generation: 7,
        birthYear: 1934,
        deathLunar: "9/11",
        fatherCode: "AT-03-004",
        nativePlace: "Đông Ngạc",
      },
      evidence: [
        evidence("DEATH_LUNAR", "MISSING_IN_FILE", "9/11"),
        evidence("FATHER", "SAME", "AT-03-004", "AT-03-004"),
        evidence("TABOO_NAME", "SAME", "Lành", "Lành"),
        evidence("FULL_NAME", "DIFFERENT", "Nguyễn Thị Lành", "Nguyen Thi Lanh"),
        evidence("GENERATION", "MISSING_IN_FILE", "7"),
        evidence("BIRTH_YEAR", "MISSING_IN_FILE", "1934"),
        evidence("ORIGIN_PLACE", "MISSING_IN_FILE", "Đông Ngạc"),
      ],
    },
  ];
}

/** Vài dòng của khu vực chờ — đủ để màn xem trước trả lời "tải lại có sinh người trùng không". */
function stagedRows(priorPersonsInTree: number): ImportRow[] {
  const update = priorPersonsInTree > 0;
  return [
    {
      rowNo: 9,
      externalCode: "AT-04-009B",
      fullName: "Nguyễn Thị Lành",
      tabooName: "Lành",
      gender: "FEMALE",
      generation: 7,
      fatherCode: "AT-03-004",
      alive: false,
      birthYear: 1934,
      deathLunar: "9/11",
      nativePlace: "Đông Ngạc",
      nativePlaceCode: "VN-HN",
      plannedAction: update ? "UPDATE" : "CREATE",
      resolvedPersonId: update ? "p-020" : undefined,
    },
    {
      rowNo: 12,
      externalCode: "AT-03-004",
      fullName: "Nguyễn Văn Trực",
      gender: "MALE",
      generation: 6,
      fatherCode: "AT-02-001",
      alive: false,
      birthYear: 1903,
      deathLunar: "15/8",
      plannedAction: update ? "UPDATE" : "CREATE",
      resolvedPersonId: update ? "p-010" : undefined,
    },
    {
      rowNo: 19,
      externalCode: "AT-04-002",
      fullName: "Nguyễn Văn Bân",
      tabooName: "Bân",
      gender: "MALE",
      generation: 7,
      fatherCode: "AT-03-004",
      alive: false,
      birthYear: 1931,
      deathLunar: "21/3",
      plannedAction: "CREATE",
    },
    {
      rowNo: 26,
      externalCode: "AT-04-009",
      fullName: "Nguyen Thi Lanh",
      tabooName: "Lành",
      gender: "FEMALE",
      fatherCode: "AT-03-004",
      // `alive` VẮNG MẶT: tệp bỏ trống ô "Còn sống" và không có ngày giỗ, nên
      // bộ kiểm nói "chưa biết" thay vì tự quyết là còn sống.
      plannedAction: "CREATE",
    },
    {
      rowNo: 31,
      externalCode: "AT-05-003",
      fullName: "Nguyễn Văn Hoằng",
      tabooName: "Hoằng",
      gender: "MALE",
      generation: 8,
      fatherCode: "AT-04-002",
      alive: false,
      deathLunar: "2/5",
      heirOfCode: "AT-04-002",
      heirKind: "DICH_TON",
      plannedAction: "CREATE",
    },
  ];
}

export interface CreatedBatch {
  batch: StoredBatch;
  issues: ImportIssue[];
  rows: ImportRow[];
  duplicates: ImportDuplicatePair[];
}

/** Dựng một lô mới đã đọc và kiểm xong, đúng như `POST /batches` trả về. */
export function buildBatch(args: {
  id: string;
  branch: MockBranchSeed;
  fileName: string;
  uploadedBy: string;
  now: Date;
  priorPersonsInTree: number;
}): CreatedBatch {
  const { id, branch, fileName, uploadedBy, now, priorPersonsInTree } = args;
  const scenario = scenarioOf(fileName);
  const blocking = scenario === "DIRTY" ? blockingIssues(id) : [];
  const warnings = scenario === "CLEAN" ? [] : warningIssues(id);
  const pairs = scenario === "CLEAN" ? [] : duplicatePairs();

  const personRowCount = 318;
  // Tải lại vào một chi đã có người: phần lớn dòng là cập nhật, đúng ngữ nghĩa
  // `person_external_ref` — tải lại nguyên tệp cũ không sinh người nào.
  const plannedUpdateCount = Math.min(priorPersonsInTree, personRowCount);

  const batch: StoredBatch = {
    id,
    branchId: branch.id,
    branchName: branch.name,
    branchPath: branch.path,
    sourceKind: "EXCEL",
    status: blocking.length > 0 ? "FAILED" : "VALIDATED",
    fileName,
    fileSha256: `sha256-${fileName.length}-${scenario.toLowerCase()}`,
    fileSizeBytes: 184_320 + fileName.length,
    uploadedBy,
    uploadedAt: now.toISOString(),
    validatedAt: now.toISOString(),
    failureReason:
      blocking.length > 0 ? `Còn ${blocking.length} lỗi chặn phải sửa trong tệp.` : undefined,
    personRowCount,
    marriageRowCount: 146,
    blockingCount: blocking.length,
    warningCount: warnings.length,
    plannedCreateCount: personRowCount - plannedUpdateCount,
    plannedUpdateCount,
    suspectDuplicateCount: pairs.length,
    // Lô mới: mọi cặp đều `PENDING`, nên hai con số bằng nhau lúc này. Chúng
    // tách ra ngay khi có người quyết cặp đầu tiên — trừ khi người ấy bấm
    // "hoãn", vì `DEFERRED` vẫn nằm trong số chưa quyết.
    undecidedDuplicateCount: undecidedOf(pairs),
    version: 0,
    warningsDigest: warningsDigestOf(warnings),
    warningsAcknowledgedDigest: null,
    rollbackReason: null,
  };

  return {
    batch,
    issues: [...blocking, ...warnings],
    rows: stagedRows(priorPersonsInTree),
    duplicates: pairs,
  };
}

/**
 * Tạo một lô y hệt như `POST /api/v1/import/batches` tạo ra, nhưng không qua HTTP.
 *
 * <h2>Vì sao hàm này tồn tại</h2>
 * Handler của MSW gọi chính nó, nên trạng thái do test gieo và trạng thái do
 * một lượt tải lên thật sinh ra là **cùng một đoạn mã** — không có nguy cơ hai
 * đường đi lệch nhau rồi test xanh trong khi màn hình hỏng.
 *
 * <h2>Và vì sao test đơn vị phải dùng nó thay vì gọi `upload()`</h2>
 * Dưới `jsdom`, `FormData` là bản của jsdom còn `fetch` là bản của undici
 * (Node). Undici không nhận ra một `FormData` lạ, nên nó không đặt
 * `Content-Type: multipart/form-data` và `request.formData()` phía MSW ném
 * ngay. Đây là **lệch môi trường thử**, không phải lỗi sản phẩm: trong trình
 * duyệt thật cả hai đều là bản gốc. Vì vậy đường multipart được kiểm ở
 * `e2e/data-import.spec.ts` — nơi có trình duyệt thật.
 */
export function seedMockBatch(args: {
  branchId: string;
  fileName: string;
  uploadedBy?: string;
  now?: Date;
}): ImportBatch {
  const branch = findMockBranch(args.branchId);
  if (!branch) throw new Error(`Không có chi ${args.branchId} trong kho giả lập`);

  return mutateImportMockDb((db) => {
    db.seq += 1;
    const id = `2026-${String(db.seq).padStart(3, "0")}`;
    const built = buildBatch({
      id,
      branch,
      fileName: args.fileName,
      uploadedBy: args.uploadedBy ?? "11111111-1111-4111-8111-111111111111",
      now: args.now ?? new Date(),
      priorPersonsInTree: db.branchPersonCounts[args.branchId] ?? 0,
    });
    // Tải lại thay cho lô đang dở của CÙNG chi. Lô cũ chuyển SUPERSEDED chứ
    // không bị xoá — không bao giờ xoá.
    for (const old of db.batches) {
      const closed = CLOSED_STATUSES.includes(old.status);
      if (old.branchId === args.branchId && !closed) old.status = "SUPERSEDED";
    }
    db.batches.push(built.batch);
    db.issues[id] = built.issues;
    db.rows[id] = built.rows;
    db.duplicates[id] = built.duplicates;
    return batchDto(built.batch);
  });
}

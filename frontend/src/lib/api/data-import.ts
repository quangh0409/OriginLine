import { API_BASE_URL, ApiError, getApiLocale } from "./http";
import { MOCKING_ENABLED, getDevRole } from "./dev-role";
import { getAuthToken } from "@/lib/auth/token-bridge";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * Lớp API cho **đường ống nhập liệu ban đầu** (`vn.giapha.dataimport`).
 *
 * <h2>Nguồn của các kiểu dưới đây</h2>
 * `contracts/openapi.yaml` — 18 endpoint dưới thẻ `import`. Hợp đồng là **nguồn
 * sự thật duy nhất**; tệp này không suy diễn từ kế hoạch nào nữa. Hai lối gọi
 * từng vắng mặt — xuất danh sách lỗi ra Excel ({@link dataImportApi.issuesWorkbook})
 * và ghi quyết định của một cặp nghi trùng ({@link dataImportApi.decideDuplicate})
 * — **nay có thật**, nên màn hình không còn khối "chờ backend" nào.
 *
 * <h2>Năm bất biến mà giao diện KHÔNG được tự nới</h2>
 * <ol>
 *   <li><b>Chưa ghi gì vào phả cho tới khi người nhập bấm duyệt.</b> Mọi thứ
 *       trước `commit()` chỉ chạm bảng `import_*`.</li>
 *   <li><b>Lỗi chặn và cảnh báo là hai nhóm khác nhau về hệ quả</b>, không phải
 *       hai mức của một danh sách. Không bao giờ hiện một con số gộp.</li>
 *   <li><b>Backend đếm, client không đếm lại.</b> Nút duyệt khoá/mở theo
 *       {@link ImportBatch.canApprove} — đọc thẳng từ bất biến mà cổng duyệt
 *       phía máy chủ áp dụng. Suy ở hai nơi thì hai nơi sẽ lệch.</li>
 *   <li><b>Không có ngưỡng nào tự gộp người</b>, và ngưỡng tick sẵn "hợp nhất"
 *       do **máy chủ** công bố ({@link ImportDuplicatePolicy}) rồi tính sẵn
 *       thành {@link ImportDuplicatePair.preselectMerge}. Client không so điểm.</li>
 *   <li><b>"Hoãn" không mở khoá nút duyệt.</b> `DEFERRED` **vẫn** nằm trong
 *       {@link ImportBatch.undecidedDuplicateCount} và `canApprove` vẫn `false`.
 *       Bù trừ ở client biến nó thành nút "cho tôi qua" và cả cơ chế dò trùng
 *       thành trang trí.</li>
 * </ol>
 */

/* ══════════════════════════════════════════════════════════════════════════
   Máy trạng thái của một lô — ImportBatchStatus
   ══════════════════════════════════════════════════════════════════════════ */

/**
 * Khớp `import_batch.status`.
 *
 * ```
 * DRAFT → PARSED → VALIDATING → VALIDATED → COMMITTING → COMMITTED
 *                      ↓
 *                   FAILED        (còn lỗi chặn; sửa tệp rồi tải lại = lô MỚI)
 * bất kỳ ↓
 * SUPERSEDED                      (cùng chi đã có lô mới hơn)
 * ```
 *
 * `VALIDATED` **chính là** "chờ duyệt" — theo định nghĩa nó là lô 0 lỗi chặn.
 *
 * <b>Không có `ROLLED_BACK`.</b> Một lô đã gỡ vẫn mang `COMMITTED` vì nó *đã
 * từng* được ghi — đó là sự thật lịch sử, và `committedAt` là mốc mà mọi phép
 * kiểm "ai đã động vào sau khi ghi" dựa vào. Việc đã gỡ là một **cột riêng**
 * (`import_batch.rolled_back_at`), không phải một trạng thái.
 *
 * Cột ấy **chưa** ra tới hợp đồng (xem {@link ImportBatch}), nên hôm nay giao
 * diện phân biệt "đã gỡ" với "chưa bao giờ ghi" bằng
 * {@link dataImportApi.rollbackPreflight}, không bằng thân lô.
 */
export type ImportBatchStatus =
  | "DRAFT"
  | "PARSED"
  | "VALIDATING"
  | "VALIDATED"
  | "FAILED"
  | "COMMITTING"
  | "COMMITTED"
  | "SUPERSEDED";

/** `import_batch.source_kind`. `GEDCOM` có từ ngày đầu dù đợt này chỉ dùng `EXCEL`. */
export type ImportSourceKind = "EXCEL" | "GEDCOM";

/**
 * `import_issue.severity`.
 *
 * Khác nhau ở đúng một điểm, và điểm ấy là toàn bộ lý do tách hai nhóm:
 * `BLOCKING` **không cho** bấm duyệt; `WARNING` cho, sau khi người nhập xác
 * nhận đã xem.
 */
export type ImportIssueSeverity = "BLOCKING" | "WARNING";

/**
 * Trang trong mẫu Excel mà một vấn đề trỏ tới — **giữ nguyên tên trang thật**.
 *
 * Người nhập sửa trên chính tệp của họ, nên nhãn phải khớp cái tab họ đang
 * nhìn: `NHAN_KHAU` và `HON_PHOI` là tên hai trang trong mẫu. `LO` nghĩa là
 * vấn đề của **cả lô**, không gắn với dòng nào — và khi ấy `rowNo` vắng mặt.
 */
export type ImportSheet = "NHAN_KHAU" | "HON_PHOI" | "LO";

/**
 * Hành động dự kiến của một dòng khi lô được ghi.
 *
 * Suy từ `person_external_ref`, **không** suy từ tên: `UPDATE` nghĩa là mã này
 * đã có chủ trong phả. Đây là câu trả lời cho nỗi lo duy nhất khi nộp lần thứ
 * hai — *tải lại có sinh ra người trùng không*.
 */
export type ImportPlannedAction = "CREATE" | "UPDATE" | "SKIP";

/**
 * Mười sáu mã **chặn**: mâu thuẫn nội tại của dữ liệu — chắc chắn sai, và chỉ
 * ra được sai ở đâu.
 *
 * `IMP_MERGE_NOT_APPLICABLE` là mã duy nhất ở đây **không** sinh ra lúc đọc
 * tệp: nó sinh ra lúc người đối chiếu bấm "hợp nhất" trên một nhóm dính tới
 * **hai người khác nhau đã có trong phả**. Xem {@link ImportDuplicateDecision}.
 */
export type ImportBlockingCode =
  | "IMP_PARENT_NOT_FOUND"
  | "IMP_CYCLE"
  | "IMP_SELF_PARENT"
  | "IMP_CHILD_BEFORE_PARENT"
  | "IMP_LUNAR_DATE_NOT_EXIST"
  | "IMP_LUNAR_DATE_UNPARSEABLE"
  | "IMP_LUNAR_DATE_IS_SERIAL"
  | "IMP_DUP_CODE"
  | "IMP_MISSING_CODE"
  | "IMP_MISSING_NAME"
  | "IMP_BAD_CODE_FORMAT"
  | "IMP_ALIVE_WITH_DEATH"
  | "IMP_GENERATION_MISMATCH"
  | "IMP_SPOUSE_ORDER_CONFLICT"
  | "IMP_MASS_CREATE_GUARD"
  | "IMP_MERGE_NOT_APPLICABLE";

/**
 * Mười một mã **cảnh báo**: nghi ngờ — máy không đủ căn cứ, chỉ người mở sổ ra
 * mới trả lời được.
 */
export type ImportWarningCode =
  | "IMP_SUSPECT_DUPLICATE"
  | "IMP_TABOO_COLLISION"
  | "IMP_MISSING_GIO"
  | "IMP_GIO_DAY_30"
  | "IMP_LONE_NODE"
  | "IMP_UNKNOWN_GENDER"
  | "IMP_MISSING_MOTHER"
  | "IMP_ROW_DISAPPEARED"
  | "IMP_UNKNOWN_PLACE_CODE"
  | "IMP_MISSING_PLACE_CODE"
  | "IMP_HEIR_TARGET_NOT_FOUND";

export type ImportIssueCode = ImportBlockingCode | ImportWarningCode;

/**
 * `Problem.code` của nhóm `/api/v1/import/**`.
 *
 * **Không còn là một danh sách chép tay.** Hợp đồng đã gộp mười bốn mã `IMP_*`
 * vào `ProblemCode` dùng chung, nên kiểu này chỉ **thu hẹp** kiểu ấy — thêm một
 * mã ở `types/api.ts` là tự động có ở đây, và một mã gõ sai thì không biên dịch
 * được. Hai mã cuối không mang tiền tố `IMP_` nhưng vẫn đi ra từ nhóm này.
 */
export type ImportProblemCode =
  | Extract<ProblemCode, `IMP_${string}`>
  | "BRANCH_SCOPE_VIOLATION"
  | "ACCOUNT_NOT_PROVISIONED";

/* ══════════════════════════════════════════════════════════════════════════
   Khung nhìn
   ══════════════════════════════════════════════════════════════════════════ */

/** Bậc trong cây dòng tộc. Giữ nguyên thuật ngữ tiếng Việt. */
export type ImportBranchKind = "DONG_HO" | "CHI" | "NGANH" | "CANH" | "NHANH";

/**
 * Một chi trong màn nhập liệu — `GET /import/branches` trả **cả cây**, đã sắp
 * theo `ltree`.
 *
 * <h2>Vì sao có cả chi ngoài phạm vi</h2>
 * Bốn chi nhập song song, và màn đầu phải trả lời được "tôi đang ở đâu trong cả
 * dòng họ". Danh sách chi và `ltree` của chúng là *cấu trúc tổ chức*, không
 * phải dữ liệu cá nhân — nó vốn đã hiện trên phả đồ cho mọi thành viên.
 *
 * Phân quyền nằm ở {@link ImportBranch.canImport}, và ở phép kiểm thật tại
 * `POST /import/batches`.
 *
 * <b>Không có `codePrefix`.</b> Bảng `branch` không có cột ấy, và đó là chủ ý:
 * mọi quy tắc tiền tố cứng đều là quy tắc tự bịa và sẽ đá nhau với cách đánh số
 * thật của từng chi.
 */
export interface ImportBranch {
  id: string;
  name: string;
  /** `ltree` — **căn cứ phạm vi duy nhất**. Đừng suy phạm vi từ `id` hay từ tên. */
  path: string;
  kind: ImportBranchKind;
  /**
   * Người **đang gọi** có quyền nhập liệu vào chi này không. Cờ này để giao
   * diện khoá nút *trước* khi người dùng chọn tệp, thay vì để họ chọn xong rồi
   * nhận `403`.
   */
  canImport: boolean;
  /**
   * Lô đang dở của chi này, nếu có — **đủ để dựng một đường dẫn, không hơn**.
   *
   * <b>Chưa có trong `contracts/openapi.yaml`</b>, nhưng `ImportBranchDto` của
   * backend đã phát nó, nên trường này để tuỳ chọn: vắng thì giao diện xử như
   * không có lô nào đang dở.
   *
   * Nó đáng giá vì đúng một lý do: chừng nào `GET /import/batches` còn thiếu,
   * đây là **cách duy nhất** để màn dẫn nhập trả lời được "lần trước tôi làm
   * đến đâu rồi" mà không bắt người nhập nhớ mã lô trong thanh địa chỉ. Máy chủ
   * chỉ phát nó cho chi mà người gọi **được ghi** — một mã lô là một lối đi
   * thẳng vào khu vực chờ của chi khác.
   */
  openBatchId?: string;
}

/**
 * Hai ngưỡng của thang điểm 0–100, **do máy chủ công bố**.
 *
 * Giao diện không ghi cứng con số nào: thang điểm là dữ liệu hiệu chỉnh của bộ
 * dò trùng và sẽ còn được chỉnh. Một bản sao ghi cứng ở client chắc chắn sẽ
 * trôi, và triệu chứng rất khó truy — giao diện tick sẵn "hợp nhất" cho một cặp
 * mà máy chủ coi là chưa đáng nghi.
 */
export interface ImportDuplicatePolicy {
  /** Điểm tối thiểu để sinh cảnh báo `IMP_SUSPECT_DUPLICATE`. */
  suspectThreshold: number;
  /** Điểm từ đó giao diện tick sẵn "hợp nhất". Vẫn phải người xác nhận. */
  preselectMergeThreshold: number;
  /** **Luôn `false`.** Không ngưỡng nào tự gộp người, ở bất kỳ điểm số nào. */
  autoMerge: boolean;
}

/**
 * Một lô — `import_batch` cộng **mọi con số đã đếm sẵn**.
 *
 * <h2>Bốn trường mà bản giao diện tự nghĩ ra từng có và KHÔNG tồn tại</h2>
 * `uploadedByName` (chỉ có `uploadedBy` dạng UUID), `rollbackDeadline`,
 * `committedPersonCount`, `committedRelationshipCount`. Đừng dựng lại chúng ở
 * client bằng cách suy từ chỗ khác.
 */
export interface ImportBatch {
  id: string;
  branchId: string;
  /** Vắng mặt khi máy chủ không phân giải được chi — hiếm, nhưng hợp đồng cho phép. */
  branchName?: string;
  /** `ltree` của chi — căn cứ phạm vi. */
  branchPath?: string;
  sourceKind: ImportSourceKind;
  status: ImportBatchStatus;
  fileName: string;
  /**
   * **Chỉ làm đúng một việc: chặn bấm hai lần.** Tính đúng đắn *không* đến từ
   * mã băm — sửa một ô là đổi mã băm, mà sửa một ô rồi tải lại chính là bước
   * đối soát bình thường. Cơ chế chống sinh người trùng là `person_external_ref`.
   */
  fileSha256: string;
  fileSizeBytes: number;
  /** `app_user` đã tải lên, dạng UUID. Vắng mặt với lô do tiến trình nền tạo. */
  uploadedBy?: string;
  uploadedAt: string;
  validatedAt?: string;
  committedAt?: string;
  /**
   * Lúc người nhập tick "tôi đã xem hết phần cần xem lại".
   *
   * <b>KHÔNG phải câu trả lời cho "đã xác nhận chưa".</b> Xác nhận là một *lời
   * khai* gắn với vân tay của **tập cảnh báo** đã đọc: kiểm lại lô mà sinh cảnh
   * báo **mới** thì xác nhận cũ hết hiệu lực, nhưng dấu thời gian này **vẫn
   * còn** — đã có người thật bấm nút, và đó là sự thật lịch sử.
   *
   * Suy "đã xác nhận" từ `warningsAcknowledgedAt !== undefined` vì thế là sai,
   * và sai đúng vào ca nguy hiểm nhất: người duyệt tưởng mình đã đọc những mục
   * mà họ chưa từng nhìn thấy. Câu trả lời duy nhất là {@link ImportBatch.canApprove}.
   */
  warningsAcknowledgedAt?: string;
  /**
   * `app_user` đã tick. Cái tick ấy mở khoá nút ghi vài trăm người vào phả nên
   * nó **phải có chủ**: một dấu thời gian vô danh không trả lời được câu duy
   * nhất người ta sẽ hỏi về sau.
   */
  warningsAcknowledgedBy?: string;
  /** `app_user` đã bấm ghi vào phả. */
  committedBy?: string;
  /**
   * Lúc lô được gỡ khỏi phả. **Không phải một trạng thái** — lô vẫn mang
   * `COMMITTED` vì nó *đã từng* vào phả, và `committedAt` là mốc mà mọi phép
   * kiểm "ai đã động vào sau khi ghi" dựa vào.
   *
   * Đây là cách phân biệt "đã gỡ" với "chưa bao giờ ghi":
   * `committedAt != null && rolledBackAt != null`.
   */
  rolledBackAt?: string;
  /** Lý do khi `status === "FAILED"`. */
  failureReason?: string;
  personRowCount: number;
  marriageRowCount: number;
  /** Số lỗi **chặn**. Giao diện không bao giờ cộng nó với `warningCount`. */
  blockingCount: number;
  warningCount: number;
  /** Số dòng sẽ **tạo người mới**, suy từ `person_external_ref`. */
  plannedCreateCount: number;
  /** Số dòng sẽ **cập nhật người đã có** — bằng chứng tải lại không sinh người trùng. */
  plannedUpdateCount: number;
  /**
   * Số **cặp** nghi trùng, không phải số dòng cảnh báo: một dòng có thể bị nghi
   * trùng với nhiều hồ sơ, và người đối chiếu phải quyết từng cặp một.
   */
  suspectDuplicateCount: number;
  /** Trong đó bao nhiêu cặp người nhập **chưa quyết**. Đầu vào của cổng duyệt. */
  undecidedDuplicateCount: number;
  /**
   * Nút duyệt có mở không. Đọc thẳng từ cùng một bất biến mà cổng duyệt phía
   * máy chủ áp dụng, nên không có cách nào để nút hiện ra trong lúc máy chủ sẽ
   * từ chối, và ngược lại.
   *
   * Nó đã tính cả `warningsAcknowledgedAt` **lẫn việc xác nhận ấy còn hiệu lực
   * hay không**. Client **không** được tự suy điều kiện duyệt từ các trường lẻ.
   */
  canApprove: boolean;
  /**
   * Bộ đếm sửa đổi của dòng. Tăng ở **mọi** lần ghi, kể cả những lần không đổi
   * trạng thái (đếm lại sau khi kiểm, xác nhận cảnh báo). Giao diện dùng nó để
   * phát hiện một tab khác đã động vào lô giữa chừng.
   */
  version: number;
}

/**
 * `import_issue.context` — cùng nội dung của `message` ở dạng **máy đọc được**.
 *
 * <h2>Khoá giữ nguyên tên tiếng Việt của cơ sở dữ liệu</h2>
 * `import_issue.context` là JSONB do từng luật tự soạn. Dịch khoá ở tầng API
 * nghĩa là tầng API phải biết trước payload của cả mười ba luật — mỗi luật mới
 * là một chỗ phải nhớ sửa hai nơi, và bản xuất Excel sẽ thấy một bộ tên khác
 * hẳn giao diện.
 */
export interface ImportIssueContext {
  /**
   * Mã gần giống, đã xếp theo độ gần — `IMP_PARENT_NOT_FOUND`,
   * `IMP_HEIR_TARGET_NOT_FOUND`. **Do máy chủ tính**: client chỉ thấy phần dữ
   * liệu đã tải về nên sẽ gợi ý sai đúng lúc người ta tin nhất.
   */
  goiY?: string[];
  /** Đường đi thật của vòng lặp, **đã đóng kín** — `IMP_CYCLE`. */
  chuoi?: string[];
  /** Các số dòng cùng dính. Là **mảng**, không phải một số. */
  cacDong?: number[];
  /** Đời người nhập gõ / đời hệ thống suy ra — `IMP_GENERATION_MISMATCH`. */
  doiKhai?: number;
  doiSuyRa?: number;
  /** Mã không phân giải được — `IMP_PARENT_NOT_FOUND`. */
  maKhongTimThay?: string;
  /**
   * Các hồ sơ bị nghi — `IMP_SUSPECT_DUPLICATE`. Phần tử `nguon === "TREE"`
   * **chỉ** còn `{ personId, diem, nguon }`: mọi trường dữ liệu của người đã có
   * trong phả đã bị cắt.
   */
  nghiNgo?: Array<{
    nguon: "FILE" | "TREE";
    diem: number;
    personId?: string;
    [key: string]: unknown;
  }>;
  /** Tên huý va chạm — `IMP_TABOO_COLLISION`. */
  tenHuy?: string;
  /** **Khoá** của bậc trên; tên và đời của người ấy đã bị cắt. */
  bacTrenId?: string;
  [key: string]: unknown;
}

/**
 * Một lỗi hoặc cảnh báo — `import_issue`.
 *
 * <h2>Khoá danh sách là `id`, không phải một bộ ghép</h2>
 * `(sheet, rowNo, code, field)` **không duy nhất**: hai luật khác nhau vẫn có
 * thể cùng trỏ vào một ô. Dựng khoá React từ bộ ấy làm bảng lỗi nhảy chỗ mỗi
 * lần kiểm lại — đúng vào lúc người nhập đang dò theo nó để sửa tệp.
 */
export interface ImportIssue {
  /** Khoá dòng trong `import_issue`. Giao diện dùng nó làm khoá danh sách. */
  id: string;
  severity: ImportIssueSeverity;
  code: ImportIssueCode;
  sheet: ImportSheet;
  /** Số dòng **đúng như Excel đánh**. Vắng mặt khi `sheet === "LO"`. */
  rowNo?: number;
  /** Tên cột tiếng Việt, để tô đúng ô. Vắng mặt khi lỗi thuộc về cả dòng. */
  field?: string;
  /** Cột `Mã` của dòng, để tra ngược vào sổ giấy mà không phải đếm dòng. */
  externalCode?: string;
  /**
   * Một câu tiếng Việt hoàn chỉnh.
   *
   * <b>Chỉ tiếng Việt, và KHÔNG đổi theo `Accept-Language`</b>: câu chữ được
   * soạn *lúc kiểm* rồi lưu vào `import_issue.message`. Giao diện bản tiếng Anh
   * phải bọc nó trong `lang="vi"` thay vì giả vờ đã dịch.
   */
  message: string;
  context?: ImportIssueContext;
}

/**
 * Một dòng trong khu vực chờ — thứ người nhập đã gõ, **kể cả khi nó sai**.
 *
 * Đó là toàn bộ lý do khu vực chờ tồn tại. Không có dữ liệu nào của phả ở đây;
 * `resolvedPersonId` là ngoại lệ duy nhất và nó chỉ là một **khoá**.
 */
export interface ImportRow {
  rowNo: number;
  /** Cột `Mã`, đã nâng hoa và bỏ khoảng trắng — **khoá bất biến** của cả đường ống. */
  externalCode?: string;
  fullName?: string;
  /** Tên huý — lớp tên duy nhất kích hoạt cảnh báo kỵ húy (FR-1.6). */
  tabooName?: string;
  posthumousName?: string;
  hanNomName?: string;
  gender?: "MALE" | "FEMALE" | "UNKNOWN";
  generation?: number;
  fatherCode?: string;
  motherCode?: string;
  /** Quan hệ với cha/mẹ — ruột, nuôi, kế. */
  parentRel?: "BIO" | "ADOPTED" | "STEP";
  /**
   * Ô "Còn sống". Để trống **không** có nghĩa là còn sống — với một cuốn gia
   * phả, mặc định như vậy sinh ra phả đồ toàn người sống từ đời thứ ba. Vắng
   * mặt nghĩa là *chưa biết*, và bộ kiểm nói ra thay vì tự quyết.
   */
  alive?: boolean;
  birthYear?: number;
  /** Ngày giỗ như người nhập gõ, ví dụ `15/8` hoặc `15/8 nhuận/1945`. */
  deathLunar?: string;
  nativePlace?: string;
  /** Mã tỉnh/quốc gia — đầu vào duy nhất gộp nhóm được cho báo cáo dân số. */
  nativePlaceCode?: string;
  heirOfCode?: string;
  heirKind?: "DICH_TON" | "THUA_TU" | "KE_TU";
  plannedAction: ImportPlannedAction;
  /**
   * Nhân khẩu đã có mang mã này. **Chỉ là một khoá** — muốn biết người ấy là ai
   * thì gọi `GET /persons/{id}`, nơi bộ lọc phân tầng riêng tư quyết định
   * trường nào được trả.
   */
  resolvedPersonId?: string;
}

/**
 * Một bên của cặp nghi trùng. **Hai nguồn, và chúng khác nhau về mọi thứ.**
 *
 * `source === "FILE"` — một dòng trong chính tệp vừa nộp. Toàn bộ là thứ người
 * nhập vừa gõ, nên trả đủ: không có gì để giấu với chính tác giả của nó.
 *
 * `source === "TREE"` — một nhân khẩu **đã có trong phả**. Chỉ có `personId`,
 * **không một trường nào khác**. Bộ dò quét *toàn dòng họ*, nên hồ sơ bị nghi
 * có thể là một người **còn sống ở một chi khác** mà người nhập chi này không
 * có quyền biết gì về họ. Cầm khoá ấy gọi `GET /persons/{id}`: nơi đó mới biết
 * về đồng thuận của chủ thể, tuổi vị thành niên và vai của người gọi.
 */
export interface ImportDuplicateParty {
  source: "FILE" | "TREE";
  /** Chỉ có với `TREE`. */
  personId?: string;
  /** Chỉ có với `FILE`. */
  externalCode?: string;
  rowNo?: number;
  displayName?: string;
  tabooName?: string;
  generation?: number;
  birthYear?: number;
  deathLunar?: string;
  fatherCode?: string;
  nativePlace?: string;
}

export type ImportEvidenceField =
  | "FULL_NAME"
  | "TABOO_NAME"
  | "GENERATION"
  | "BIRTH_YEAR"
  | "DEATH_LUNAR"
  | "FATHER"
  | "ORIGIN_PLACE";

export type ImportEvidenceMatch =
  | "SAME"
  | "DIFFERENT"
  /** Bên `existing` có, bên `incoming` trống — hợp nhất ẩu là mất lớp tên. */
  | "MISSING_IN_FILE"
  | "MISSING_IN_TREE";

/**
 * Một dấu hiệu trong bảng đối chiếu.
 *
 * <b>`existingValue` vắng mặt có đúng một nghĩa: "nguồn bên kia không ghi mục
 * này".</b> Nó *không bao giờ* dùng để nói "đã bị cắt theo phân tầng riêng tư"
 * — dấu hiệu nào không được phép trưng ra thì bị **bỏ hẳn khỏi mảng**, vì một ô
 * rỗng vừa rò rỉ sự tồn tại của dữ liệu đang giấu, vừa dẫn người đối chiếu tới
 * một quyết định gộp sai.
 */
export interface ImportDuplicateEvidence {
  field: ImportEvidenceField;
  match: ImportEvidenceMatch;
  /**
   * Số điểm dấu hiệu này đóng góp. **Vắng mặt** khi bộ dò chưa công bố bản tách
   * điểm theo từng tín hiệu — và khi vắng thì giao diện **không** được tự cộng
   * bù. Hôm nay backend không gửi trường này.
   */
  points?: number;
  existingValue?: string;
  /** Giá trị trong tệp. Vắng mặt nghĩa là tệp bỏ trống — chỗ phải tô. */
  incomingValue?: string;
}

/**
 * Một cặp nghi trùng. **Máy nghi ngờ, người quyết định.**
 *
 * Khi `existing.source === "TREE"`, `evidence` **rỗng** và `existing` chỉ có
 * `personId`. Đây là bất biến riêng tư, không phải thiếu sót của hiện thực.
 */
export interface ImportDuplicatePair {
  /**
   * Khoá chính thật của cặp (`import_duplicate_pair.id`, UUID), và là thứ gửi
   * lên {@link dataImportApi.decideDuplicate}.
   *
   * **Ổn định qua các lần kiểm lại**: bấm "kiểm lại" không đổi khoá của những
   * cặp không đổi, nên quyết định cũ vẫn dính đúng cặp cũ. Giao diện được phép
   * lưu nó — đó chính là điều làm cho một buổi đối chiếu 40 cặp không bị xoá
   * sạch vì lần kiểm sau tìm thêm cặp thứ 41.
   */
  id: string;
  rowNo: number;
  /** Điểm 0–100. Ngưỡng do `GET /import/duplicate-policy` công bố. */
  score: number;
  /** Giao diện nên tick sẵn "hợp nhất". **Tính ở máy chủ** — đừng so điểm ở client. */
  preselectMerge: boolean;
  /**
   * Quyết định của **người** về cặp này. Cả bốn giá trị đều xảy ra thật.
   *
   * `DEFERRED` ("chưa chắc, để sau") là lựa chọn quan trọng nhất trong ba — ép
   * chọn nhị phân khi chưa chắc thì người ta bấm đại, và cái bấm đại đó thành
   * sự thật trong phả. Nhưng nó **vẫn nằm trong `undecidedDuplicateCount` và
   * vẫn chặn nút duyệt**; đừng bù trừ ở client.
   */
  status: "PENDING" | "MERGED" | "DISTINCT" | "DEFERRED";
  /** `app_user` đã quyết. Vắng mặt khi `status === "PENDING"`. */
  decidedBy?: string;
  /** ISO-8601. Vắng mặt khi `status === "PENDING"`. */
  decidedAt?: string;
  /** Ghi chú tuỳ chọn của người quyết, ví dụ "đối chiếu với sổ chi Giáp bản 1998". */
  note?: string;
  /**
   * Câu giải thích **tự do**, chỉ để hiển thị; đừng phân tích chuỗi này.
   *
   * **Vắng mặt với cặp `TREE`** — chốt chặn rò rỉ, không phải thiếu sót: câu
   * này có thể nhắc tới giá trị trường của bên kia ("trùng năm sinh 1975"), mà
   * bên kia có thể là một người **còn sống ở một chi khác**.
   */
  hint?: string;
  /**
   * **Nhãn tín hiệu** do bộ chấm điểm sinh ra ("trùng ngày giỗ, cùng chi"): trả
   * lời *vì sao nghi* mà không tiết lộ *người ấy là ai*.
   *
   * An toàn ở **cả hai** loại cặp, và là thứ duy nhất của vế trong phả được
   * phép đi kèm khoá — nên với cặp `TREE`, đây là ô "vì sao nghi" **duy nhất**
   * có nội dung. Đọc `hint` ở đó thì ô ấy luôn trống, đúng với loại cặp quan
   * trọng nhất. Cố ý khác tên với `hint`: hai khoá mang hai mức dữ liệu khác
   * nhau, nên đọc nhầm khoá nhận `undefined` chứ không nhận dữ liệu mức kia.
   */
  signals?: string;
  incoming: ImportDuplicateParty;
  existing: ImportDuplicateParty;
  evidence: ImportDuplicateEvidence[];
}

/**
 * Ba lựa chọn của người đối chiếu. **`PENDING` không gửi lên được** — rút lại
 * một lời khai đã ghi không phải là xoá nó đi, và máy chủ trả `400`.
 *
 * <h2>"Gộp" nghĩa là gì — hai ca, hai nghĩa hoàn toàn khác nhau</h2>
 * Với cặp `TREE`, dòng trong tệp **không tạo người mới** mà **cập nhật** hồ sơ
 * đã có: `plannedAction` đổi `CREATE` → `UPDATE`. Với cặp `FILE`, một trong hai
 * dòng **biến mất trước khi ghi** và mọi mã cha/mẹ/kế tự/hôn phối trỏ tới dòng
 * bị bỏ được trỏ lại. Cả hai đều làm `plannedCreateCount` / `plannedUpdateCount`
 * đổi — nên hai con số ấy phải được vẽ lại sau mỗi lần bấm.
 *
 * <h2>Ca từ chối: `IMP_MERGE_NOT_APPLICABLE`</h2>
 * Khi một nhóm gộp dính tới **hai người khác nhau đã có trong phả**, máy chủ
 * sinh một lỗi **chặn** mang mã ấy và lô dừng lại. Đó không phải việc của màn
 * nhập liệu: hợp nhất hai hồ sơ *đã nằm trong phả* là thao tác của màn quản lý
 * nhân khẩu, nơi có đủ lịch sử sửa đổi và đủ quyền để làm.
 */
export type ImportDuplicateDecision = "MERGED" | "DISTINCT" | "DEFERRED";

/**
 * Kết quả một lần ghi quyết định — **cặp và lô đã tính lại, trong một vòng gọi**.
 *
 * Một quyết định đổi bốn thứ trên màn hình cùng lúc: trạng thái cặp,
 * `undecidedDuplicateCount`, `plannedCreateCount`/`plannedUpdateCount`, và
 * `canApprove`. Vì cả bốn về cùng nhau nên **không phải gọi lại gì** sau mỗi
 * lần bấm — thay thẳng vào cache. Gọi thêm một vòng chỉ tạo ra một khoảng thời
 * gian ngắn mà nút duyệt hiện sai.
 */
export interface ImportDuplicateDecisionResult {
  pair: ImportDuplicatePair;
  batch: ImportBatch;
}

/**
 * Tiến độ ghi, chạy nền — **nguồn duy nhất của trạng thái cuối**.
 *
 * `estimated` luôn `true` và giao diện phải hiện điều đó thành chữ: con số đếm
 * ngoài giao dịch ghi nên nó là ước lượng *theo bản chất*. Vẽ nó như một thanh
 * chính xác là hứa một điều hệ thống không giữ được.
 */
export interface ImportCommitProgress {
  batchId: string;
  status: ImportBatchStatus;
  phase: "QUEUED" | "PERSONS" | "PARENT_EDGES" | "SPOUSE_EDGES" | "CACHE" | "DONE" | "FAILED";
  processedRows: number;
  totalRows: number;
  estimated: boolean;
  /** Câu lỗi khi `phase === "FAILED"`. Lô đã cuộn lại **toàn bộ**. */
  failureMessage?: string;
}

/**
 * "Gỡ được không, vướng ở đâu" — trả lời mà **không gỡ gì cả**.
 *
 * Phải có, vì một hộp thoại "Bạn chắc chứ?" không nói gì về việc đã có người
 * sửa hồ sơ do lô ấy sinh ra là một hộp thoại **nói dối**. `blockers` là danh
 * sách vướng mắc bằng tiếng Việt, đủ cụ thể để người bấm biết mình đang đánh
 * đổi cái gì: *"12 người đã được sửa sau khi ghi"* dẫn tới việc đi hỏi 12 người
 * ấy, khác hẳn *"đã quá hạn"*.
 */
export interface ImportRollbackPreflight {
  canRollback: boolean;
  /** Rỗng nghĩa là gỡ được. */
  blockers: string[];
}

/**
 * Vị trí của một chi trong quy trình nhập liệu.
 *
 * **Không có `TEMPLATE_DOWNLOADED`**, dù kế hoạch từng nhắc tới nó: hệ thống
 * chưa ghi lại lượt tải mẫu, và bịa ra một bậc không đo được thì cả thang bậc
 * mất giá trị.
 *
 * `COMMITTED` là bậc cao nhất và **không** bị một lô nháp mới kéo tụt xuống:
 * chi ấy *đã* có dữ liệu trong phả, và đó là sự thật bền hơn trạng thái của lô
 * đang mở.
 */
export type ImportStage = "NOT_STARTED" | "UPLOADED" | "RECONCILING" | "COMMITTED";

/**
 * Một dòng của màn tiến độ theo chi.
 *
 * <h2>Chi ngoài phạm vi bị CẮT TRƯỜNG, không phải null hoá</h2>
 * Năm trường `coordinatorName` · `openBatch` · `blockingCount` · `warningCount`
 * · `undecidedDuplicateCount` **vắng hẳn khỏi JSON** với chi ngoài phạm vi
 * người gọi. Một khoá `null` vẫn công bố rằng trường ấy tồn tại và ở đâu đó có
 * giá trị; với `coordinatorName`, vốn **là tên một người còn sống**, chừng đó
 * đã là một mẩu rò rỉ.
 *
 * Bốn trường còn lại bị cắt vì lý do khác: chúng mô tả *việc đang làm dở của
 * người khác*, và biến màn "còn thiếu gì" thành màn "ai đang sai nhiều".
 *
 * <h2>`expectedPersons` vắng nghĩa là CHƯA AI ĐẾM, không phải 0</h2>
 * Nó là số người Hội đồng đếm trên bản phả **giấy** — đếm tay, không suy ra
 * được từ dữ liệu đã nhập. Đọc "vắng" thành 0 là báo mọi chi đã xong 100% ngay
 * khi chưa ai bắt đầu.
 *
 * <b>Đây không phải bảng xếp hạng</b> — cố ý không có thứ hạng, phần trăm so
 * với chi khác, hay mốc "đáng lẽ phải xong".
 */
export interface ImportBranchProgress {
  branchId: string;
  branchName: string;
  /** `ltree` — căn cứ phạm vi duy nhất. */
  branchPath: string;
  stage: ImportStage;
  /** Số nhân khẩu chưa xoá mềm đang treo vào chi. Một phép đếm, không phải một danh sách. */
  personsInTree: number;
  /** **Vắng mặt = chưa ai đếm cuốn sổ giấy.** Khác hẳn 0. */
  expectedPersons?: number;
  /**
   * Số người đã mất mà trống ngày giỗ trong lô đang mở — con số đáng giá nhất
   * của cả màn. **Không** bị cắt với chi ngoài phạm vi: nó là một phép đếm về
   * người **đã khuất**, không phải một lời phán về ai đang làm chậm.
   */
  missingGioCount: number;
  /* ---- năm trường dưới đây VẮNG MẶT với chi ngoài phạm vi người gọi ------ */
  /** Cũng vắng mặt khi chi *trong* phạm vi mà chưa có ai nhận phụ trách. */
  coordinatorName?: string;
  openBatch?: { id: string; status: ImportBatchStatus; uploadedAt: string };
  blockingCount?: number;
  warningCount?: number;
  undecidedDuplicateCount?: number;
}

/* ══════════════════════════════════════════════════════════════════════════
   Vận chuyển
   ══════════════════════════════════════════════════════════════════════════ */

const BASE = "/api/v1/import";

/**
 * Header dùng chung cho các lời gọi **không** đi qua `apiFetch`.
 *
 * Hai loại lời gọi ấy là tải tệp lên (multipart) và tải tệp về (nhị phân).
 * `apiFetch` đặt cứng `Content-Type: application/json` rồi `JSON.stringify`
 * thân yêu cầu — đúng cho mọi endpoint khác, sai cho hai loại này: đặt
 * `Content-Type` bằng tay cho `FormData` làm mất `boundary`, và server nhận về
 * một thân rỗng mà không báo lỗi gì.
 */
async function transportHeaders(): Promise<Record<string, string>> {
  const token = await getAuthToken();
  return {
    "Accept-Language": getApiLocale(),
    ...(MOCKING_ENABLED ? { "x-mock-role": getDevRole() } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function throwProblem(response: Response): Promise<never> {
  const contentType = response.headers.get("content-type") ?? "";
  const problem = contentType.includes("json") ? ((await response.json()) as Problem) : undefined;
  throw new ApiError(response.status, problem);
}

/** Kết quả một lượt tải tệp về — tên tệp lấy từ `Content-Disposition` của server. */
export interface DownloadedFile {
  blob: Blob;
  fileName: string;
}

export function fileNameFrom(response: Response, fallback: string): string {
  const header = response.headers.get("Content-Disposition") ?? "";
  // Dạng RFC 5987 đứng TRƯỚC dạng thường: với tên chi tiếng Việt ("Chi Nhất")
  // đó là dạng duy nhất server ghi được, và bỏ qua nó thì tên tệp về tay người
  // dùng sẽ là bản đã rụng dấu.
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utf8?.[1]) return decodeURIComponent(utf8[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain?.[1] ?? fallback;
}

async function downloadFile(path: string, fallbackName: string): Promise<DownloadedFile> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      ...(await transportHeaders()),
      Accept:
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/problem+json",
    },
  });
  if (!response.ok) await throwProblem(response);
  return { blob: await response.blob(), fileName: fileNameFrom(response, fallbackName) };
}

async function jsonFetch<T>(
  path: string,
  init: { method?: string; body?: unknown; query?: Record<string, string | undefined> } = {}
): Promise<T> {
  const url = new URL(`${API_BASE_URL}${path}`);
  for (const [key, value] of Object.entries(init.query ?? {})) {
    if (value !== undefined) url.searchParams.set(key, value);
  }
  const response = await fetch(url.toString(), {
    method: init.method ?? "GET",
    headers: {
      ...(await transportHeaders()),
      "Content-Type": "application/json",
      Accept: "application/json, application/problem+json",
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  if (!response.ok) await throwProblem(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export interface UploadBatchInput {
  branchId: string;
  file: File;
  /**
   * Bỏ qua **đúng một chốt**: chốt bấm-hai-lần `IMP_ALREADY_COMMITTED`, tức
   * "tệp y hệt này đã *được ghi vào phả* cho chi này rồi". Nó **không** chặn
   * việc tải lại để kiểm. Chỉ đặt `true` sau khi người dùng đã đọc câu cảnh
   * báo — không bao giờ đặt sẵn.
   */
  force?: boolean;
}

export const dataImportApi = {
  /**
   * Cây chi/ngành **của cả dòng họ**, đã sắp theo `ltree`, kèm cờ `canImport`.
   *
   * Khách vãng lai và tài khoản chưa có `app_user` nhận
   * `403 ACCOUNT_NOT_PROVISIONED` — **không** phải một danh sách rỗng. Trả danh
   * sách rỗng là nói với họ rằng màn này có thật.
   */
  branches: () => jsonFetch<ImportBranch[]>(`${BASE}/branches`),

  /** Ngưỡng nghi trùng do máy chủ công bố. Giao diện không ghi cứng con số nào. */
  duplicatePolicy: () => jsonFetch<ImportDuplicatePolicy>(`${BASE}/duplicate-policy`),

  /**
   * Mẫu Excel **riêng cho từng chi** — năm trang, và từ lần nhập thứ hai trở đi
   * mang sẵn *những người đã có trong phả* của chi ấy. Đó là toàn bộ cơ chế giữ
   * cho lần nhập sau không sinh người trùng.
   *
   * Đường dẫn nằm dưới `/admin` nhưng **không** phải "chỉ System Admin": phép
   * kiểm là **quyền ghi trên chính chi đó**, giống hệt `POST /import/batches`.
   */
  template: (branchId: string, fallbackName: string) =>
    downloadFile(`/api/v1/admin/branches/${branchId}/import-template.xlsx`, fallbackName),

  /**
   * Tải tệp lên. Trả **201** kèm `Location`, và thân là lô đã **đọc và kiểm
   * xong** (`VALIDATED` hoặc `FAILED`) — cả hai đều là 201, vì "còn lỗi chặn"
   * là một *kết quả*, không phải một lỗi HTTP.
   *
   * Tải lại là hành vi bình thường: sửa một ô rồi nộp lại sinh ra một lô **mới**
   * và lô cũ của cùng chi chuyển `SUPERSEDED` ngay trong giao dịch ấy.
   */
  upload: async ({ branchId, file, force = false }: UploadBatchInput) => {
    const form = new FormData();
    form.append("file", file);
    form.append("branchId", branchId);
    if (force) form.append("force", "true");

    const response = await fetch(`${API_BASE_URL}${BASE}/batches`, {
      method: "POST",
      headers: {
        ...(await transportHeaders()),
        Accept: "application/json, application/problem+json",
      },
      body: form,
    });
    if (!response.ok) await throwProblem(response);
    return (await response.json()) as ImportBatch;
  },

  batch: (batchId: string) => jsonFetch<ImportBatch>(`${BASE}/batches/${batchId}`),

  /**
   * Chạy lại bộ kiểm trên đúng các dòng đang chờ.
   *
   * Chạy lại nhiều lần là hành vi bình thường của quy trình. Mỗi lượt sinh lại
   * *toàn bộ* danh sách lỗi và **không tích luỹ**: nếu lỗi cũ còn sót lại thì
   * người nhập sửa xong vẫn thấy y nguyên danh sách và sẽ kết luận bộ kiểm
   * hỏng — từ lúc đó mọi cảnh báo, kể cả cảnh báo đúng, đều vô giá trị.
   */
  revalidate: (batchId: string) =>
    jsonFetch<ImportBatch>(`${BASE}/batches/${batchId}/validate`, { method: "POST" }),

  /** Danh sách lỗi, lọc theo mức. Đã sắp tất định ở máy chủ. */
  issues: (batchId: string, severity?: ImportIssueSeverity) =>
    jsonFetch<ImportIssue[]>(`${BASE}/batches/${batchId}/issues`, { query: { severity } }),

  /** Từng dòng trong khu vực chờ, đúng như người nhập đã gõ. Đã sắp theo `rowNo`. */
  rows: (batchId: string, action?: ImportPlannedAction) =>
    jsonFetch<ImportRow[]>(`${BASE}/batches/${batchId}/rows`, { query: { action } }),

  /**
   * Một phần tử cho mỗi **cặp** (dòng × hồ sơ bị nghi), không phải cho mỗi dòng.
   *
   * Gọi lại sau `POST /validate` là **bắt buộc**, và an toàn: cặp đã quyết giữ
   * nguyên `status`, `decidedBy`, `decidedAt`; chỉ cặp **mới** vào với
   * `PENDING`. Đừng xoá state quyết định ở client khi kiểm lại.
   */
  duplicates: (batchId: string) =>
    jsonFetch<ImportDuplicatePair[]>(`${BASE}/batches/${batchId}/duplicates`),

  /**
   * Ghi quyết định cho **một** cặp. **Máy nghi ngờ, người quyết định.**
   *
   * <h2>Không có lối gọi hàng loạt, và đó là chủ ý</h2>
   * Đừng dựng một nút "để riêng tất cả" bằng cách gọi hàm này trong vòng lặp:
   * đó đúng là nút "cho tôi qua" mà cả cơ chế dò trùng sinh ra để tránh. Một cú
   * bấm nhầm ở đó là hàng chục người bị gộp sai trong phả.
   *
   * <h2>Phản hồi đã đủ — đừng gọi lại gì</h2>
   * {@link ImportDuplicateDecisionResult} mang cả cặp lẫn lô **đã tính lại**.
   *
   * Lỗi: `400` (thiếu/không hợp lệ, hoặc gửi `PENDING`) · `403` · `404` (cặp
   * không thuộc lô này — ba tình huống cùng một mã) · `422 IMP_BATCH_CLOSED`.
   */
  decideDuplicate: (
    batchId: string,
    pairId: string,
    input: { decision: ImportDuplicateDecision; note?: string }
  ) =>
    jsonFetch<ImportDuplicateDecisionResult>(
      `${BASE}/batches/${batchId}/duplicates/${pairId}/decision`,
      {
        method: "POST",
        body: {
          decision: input.decision,
          // Ghi chú TRỐNG không gửi đi: một chuỗi rỗng trong nhật ký kiểm toán
          // trông y hệt một ghi chú đã bị mất.
          ...(input.note && input.note.trim().length > 0 ? { note: input.note.trim() } : {}),
        },
      }
    ),

  /**
   * Danh sách lỗi ở dạng `.xlsx` — **đường thoát của người nhập liệu thật**.
   *
   * Khi bộ kiểm kêu, chỗ Trưởng chi 45–65 tuổi sửa được là chính tệp Excel, nơi
   * họ quen tay, chứ không phải một bảng trên web bắt sửa từng dòng. Vì vậy nút
   * gọi hàm này phải nằm **ngay cạnh bảng lỗi**; giấu nó trong một menu phụ là
   * để đường thoát tồn tại mà không ai tìm thấy.
   *
   * **Không có tham số `severity`, và đừng thêm.** Tệp có ba trang
   * (`Tổng quan` · `Lỗi phải sửa` · `Nên xem lại`) và bộ sinh tự tách hai nhóm
   * — một bản xuất chỉ có cảnh báo là một tệp mang tên "Danh sách cần sửa" mà
   * thiếu đúng phần phải sửa. Một lối gọi, một nút.
   */
  issuesWorkbook: (batchId: string, fallbackName: string) =>
    downloadFile(`${BASE}/batches/${batchId}/issues.xlsx`, fallbackName),

  /**
   * Ranh giới ghi duy nhất của cả đường ống. Trả **202** — việc ghi chạy ở
   * luồng nền và luồng web không bao giờ chờ nó. Trạng thái cuối **chỉ** đến từ
   * {@link dataImportApi.commitProgress}; đừng suy từ phản hồi này.
   */
  commit: (batchId: string) =>
    jsonFetch<ImportBatch>(`${BASE}/batches/${batchId}/commit`, { method: "POST" }),

  commitProgress: (batchId: string) =>
    jsonFetch<ImportCommitProgress>(`${BASE}/batches/${batchId}/commit-progress`),

  /** Lô này còn gỡ được không, và vướng ở đâu. **Không gỡ gì cả.** */
  rollbackPreflight: (batchId: string) =>
    jsonFetch<ImportRollbackPreflight>(`${BASE}/batches/${batchId}/rollback-preflight`),

  /**
   * Gỡ cả lô khỏi phả — xoá mềm, cạnh gỡ trước, người xoá mềm sau. Trả **200**.
   *
   * `reason` là **tuỳ chọn** và đi vào nhật ký kiểm toán. Từ chối với
   * `422 IMP_ROLLBACK_REFUSED` kèm `blockers[]` khi đã có người khác động vào
   * dữ liệu lô ấy sinh ra.
   */
  rollback: (batchId: string, reason?: string) =>
    jsonFetch<ImportBatch>(`${BASE}/batches/${batchId}/rollback`, {
      method: "POST",
      body: reason && reason.trim().length > 0 ? { reason: reason.trim() } : {},
    }),

  /**
   * Các lô của một chi, hoặc của **mọi chi người gọi được phép ghi** khi bỏ
   * trống `branchId`. Mới nhất trước.
   *
   * Đây là thứ làm cho "đóng trình duyệt rồi quay lại hôm sau" chạy được: một
   * lô 400 dòng mất vài buổi để đối soát, và trạng thái dở dang ấy sống ở **máy
   * chủ**. Không có danh sách này thì người nhập chỉ tìm lại được lô của mình
   * nếu còn nhớ mã lô trong thanh địa chỉ.
   *
   * `branchId` trỏ tới một chi ngoài phạm vi trả `403 BRANCH_SCOPE_VIOLATION`,
   * **không** phải một danh sách rỗng: "không có lô nào" và "chi này không phải
   * của bạn" là hai câu trả lời khác nhau hẳn. Người gọi không quản chi nào (và
   * không nêu `branchId`) nhận danh sách rỗng — đúng, vì họ thật sự không có lô.
   *
   * Máy chủ ghim `size` ở **200**: trên ngưỡng đó thì đây không còn là màn đối
   * soát mà là một bản kết xuất.
   */
  batches: (
    params: {
      branchId?: string;
      status?: ImportBatchStatus;
      page?: number;
      size?: number;
    } = {}
  ) =>
    jsonFetch<ImportBatch[]>(`${BASE}/batches`, {
      query: {
        branchId: params.branchId,
        status: params.status,
        page: params.page === undefined ? undefined : String(params.page),
        size: params.size === undefined ? undefined : String(Math.min(params.size, 200)),
      },
    }),

  /**
   * "Tôi đã xem hết phần cần xem lại." Không sửa gì cả — chỉ mở khoá nút duyệt.
   *
   * <h2>Đây là điều kiện để cả đường ống dùng được</h2>
   * Một cuốn gia phả thật **luôn** có cảnh báo: thuỷ tổ thì lần nào cũng sinh
   * `IMP_LONE_NODE`, và đó là dữ liệu đúng chứ không phải lỗi. Không có lối gọi
   * này thì mọi lô thật đều dừng ở `VALIDATED` với nút duyệt xám vĩnh viễn.
   *
   * <h2>Là một lời khai, không phải một cái cờ</h2>
   * Máy chủ ghi lại **ai** xác nhận, **lúc nào**, và vân tay của **tập cảnh
   * báo** người ấy đã đọc. Kiểm lại lô mà sinh cảnh báo **mới** thì xác nhận cũ
   * hết hiệu lực và `canApprove` quay về `false` — trong khi
   * `warningsAcknowledgedAt` vẫn còn giá trị. Sau lời gọi này, đọc `canApprove`
   * của phản hồi, đừng đọc dấu thời gian.
   *
   * Thân yêu cầu tuỳ chọn; gửi `{acknowledged: true}` cho tường minh. Máy chủ
   * **không** nhận `acknowledged: false` như một lệnh rút lại.
   */
  acknowledgeWarnings: (batchId: string) =>
    jsonFetch<ImportBatch>(`${BASE}/batches/${batchId}/acknowledge-warnings`, {
      method: "POST",
      body: { acknowledged: true },
    }),

  /**
   * Một dòng cho **mỗi chi trong cả dòng họ**, đã sắp theo `ltree`.
   *
   * Khách vãng lai và tài khoản chưa có `app_user` nhận `403`/`401`, **không**
   * phải một danh sách rỗng: danh sách rỗng nói dối hai lần — rằng dòng họ này
   * không có chi nào, và rằng màn này có thật với họ.
   *
   * Xem {@link ImportBranchProgress} về năm trường bị cắt với chi ngoài phạm vi.
   */
  progress: () => jsonFetch<ImportBranchProgress[]>(`${BASE}/progress`),
};

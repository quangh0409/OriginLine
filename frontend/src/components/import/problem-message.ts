"use client";

import { useTranslations } from "next-intl";
import { ApiError } from "@/lib/api/http";
import type { ImportProblemCode } from "@/lib/api/data-import";

/**
 * Mười sáu mã mà nhánh nhập liệu có câu chữ riêng.
 *
 * Danh sách này bám `ProblemCode` của hợp đồng — `satisfies` làm một mã gõ sai,
 * hoặc một mã đã bị hợp đồng bỏ, hỏng biên dịch thay vì im lặng rơi về câu
 * chung. Hai mã từng có trong bản giao diện tự nghĩ ra và không tồn tại ở đâu
 * cả: "tệp trùng" (mã thật nói *đã ghi vào phả*, và mang `overridable`) và "quá
 * hạn gỡ" (điều kiện thật là *đã có người sửa*, không phải một cái đồng hồ).
 */
const KNOWN = [
  "IMP_BAD_FORMAT",
  "IMP_CORRUPT_FILE",
  "IMP_UNSAFE_FILE",
  "IMP_MISSING_SHEET",
  "IMP_MISSING_COLUMN",
  "IMP_TOO_MANY_ROWS",
  "IMP_FILE_TOO_LARGE",
  "IMP_ALREADY_COMMITTED",
  "IMP_BLOCKING_ISSUES_PRESENT",
  "IMP_WARNINGS_NOT_ACKNOWLEDGED",
  "IMP_DUPLICATES_UNDECIDED",
  "IMP_BATCH_CLOSED",
  "IMP_COMMIT_BLOCKED",
  "IMP_ROLLBACK_REFUSED",
  "BRANCH_SCOPE_VIOLATION",
  "ACCOUNT_NOT_PROVISIONED",
] as const satisfies readonly ImportProblemCode[];

/**
 * Mã có câu chữ ở đây nhưng **chưa có trong `ProblemCode` của hợp đồng**.
 *
 * `IMP_MERGE_NOT_APPLICABLE` được hợp đồng mô tả như một **lỗi chặn** trong
 * `import_issue` — và đó là đường nó đi hôm nay, nên khối
 * {@link MergeNotApplicableNotice} mới là chỗ chính hiển thị nó. Nhưng hợp đồng
 * **không nói** chuyện gì xảy ra nếu máy chủ chọn trả nó thẳng thành một
 * `Problem` của lời gọi quyết định. Nếu điều đó xảy ra, rơi về câu chung
 * ("Không thực hiện được. Thử lại sau ít phút.") là câu **sai hẳn về nghiệp
 * vụ**: thử lại sau ít phút sẽ không bao giờ thành công.
 *
 * Danh sách này cố ý **không** đi qua `satisfies readonly ImportProblemCode[]`:
 * ràng buộc ấy tồn tại để bắt mã gõ sai, và một mã hợp đồng chưa công bố sẽ làm
 * hỏng biên dịch. Khi `ProblemCode` nhận mã này, chuyển nó lên {@link KNOWN} và
 * xoá danh sách này đi.
 */
const KNOWN_BEYOND_CONTRACT: readonly string[] = ["IMP_MERGE_NOT_APPLICABLE"];

/**
 * Câu lỗi cho người đọc, chọn theo `Problem.code`.
 *
 * <h2>Phân nhánh theo `code`, không theo `detail`</h2>
 * `contracts/README §3` chốt điều này cho cả sản phẩm, và ở đây nó có một lý do
 * thêm: `detail` do máy chủ soạn theo `Accept-Language`, nên so chuỗi sẽ hỏng
 * ngay khi ai đó đổi một dấu phẩy trong bản tiếng Việt.
 *
 * <h2>Mã lạ vẫn phải ra một câu tiếng người</h2>
 * Rơi về `detail` của máy chủ, rồi mới tới một câu chung — còn hơn để
 * `MISSING_MESSAGE` hiện lên giữa lúc người nhập vừa bấm duyệt 318 người, đúng
 * khoảnh khắc họ cần hiểu nhất.
 */
export function useImportProblemMessage() {
  const t = useTranslations("dataImport.problem");

  return (error: unknown): string => {
    if (error instanceof ApiError) {
      const code = error.code as ImportProblemCode;
      if ((KNOWN as readonly string[]).includes(code)) return t(code);
      if (KNOWN_BEYOND_CONTRACT.includes(code)) return t(code);
      // `detail` của máy chủ vẫn là câu tốt hơn một câu chung, khi có.
      if (error.problem?.detail) return error.problem.detail;
    }
    return t("UNKNOWN");
  };
}

/**
 * `blockers[]` của `422 IMP_ROLLBACK_REFUSED` — danh sách vướng mắc tiếng Việt.
 *
 * Đọc thẳng từ thân lỗi chứ không dựng lại từ `detail`: *"12 người đã được sửa
 * sau khi ghi"* dẫn tới việc đi hỏi 12 người ấy, khác hẳn một câu chung "không
 * gỡ được".
 */
export function rollbackBlockersOf(error: unknown): string[] {
  if (!(error instanceof ApiError)) return [];
  const raw = (error.problem as unknown as { blockers?: unknown } | undefined)?.blockers;
  return Array.isArray(raw) ? raw.filter((x): x is string => typeof x === "string") : [];
}

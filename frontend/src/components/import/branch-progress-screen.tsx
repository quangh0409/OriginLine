"use client";

import { useTranslations } from "next-intl";
import { Alert, Skeleton } from "antd";
import { InfoCircleOutlined } from "@ant-design/icons";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import type { ImportBranch, ImportBranchProgress } from "@/lib/api/data-import";
import { useImportBranches, useImportProgress } from "./hooks/use-data-import";
import { useImportProblemMessage } from "./problem-message";

/**
 * Màn 5 — **tiến độ theo chi**.
 *
 * <h2>Đây không phải bảng xếp hạng, và điều đó được ghi thẳng lên màn hình</h2>
 * Màn này hiển thị *còn thiếu gì*, không hiển thị *ai chậm* — cùng lý do bản
 * chốt phạm vi đã cắt xếp hạng đóng góp theo số tiền. Một dòng họ không phải
 * một bảng thi đua, và biến nó thành bảng thi đua là cách làm hỏng đúng thứ mà
 * sản phẩm này tồn tại để giữ.
 *
 * Ba hệ quả cụ thể trong mã dưới đây:
 * <ul>
 *   <li>Các chi giữ nguyên thứ tự `ltree` của máy chủ, <b>không sắp theo số
 *       người đã nhập</b>. Sắp theo số là dựng bảng xếp hạng bằng thứ tự dòng.</li>
 *   <li>Không có phần trăm hoàn thành, không có thanh tiến trình so sánh được
 *       giữa các chi. Mẫu số ("bản phả gốc có bao nhiêu người") nhiều chi còn
 *       chưa đếm, nên một phần trăm ở đây là con số bịa.</li>
 *   <li>Chi ngoài phạm vi hiện **ít thông tin hơn**, và phần thiếu <b>biến mất
 *       hẳn</b> thay vì thành một ô trống.</li>
 * </ul>
 *
 * <h2>Hai nguồn, và mỗi nguồn trả lời một câu</h2>
 * `GET /import/progress` cho các con số của từng chi. `GET /import/branches`
 * cho **`canImport`** — thứ `progress` không nói, và là thứ người dùng cần để
 * hiểu vì sao chi kia không bấm được gì. Hai lời gọi song song, và màn hình vẫn
 * dựng được khung khi một trong hai hỏng.
 *
 * <h2>Chi ngoài phạm vi bị CẮT TRƯỜNG, không phải null hoá</h2>
 * `coordinatorName`, `openBatch`, `blockingCount`, `warningCount`,
 * `undecidedDuplicateCount` **vắng hẳn khỏi JSON** với chi ngoài phạm vi người
 * gọi. Giao diện không được vẽ ô trống ở chỗ chúng từng có: một ô trống đọc ra
 * là "chi ấy không còn việc gì" — ngược hẳn sự thật, mà sự thật là "bạn không
 * được biết".
 */
export function BranchProgressScreen() {
  const t = useTranslations("dataImport.progress");
  const tc = useTranslations("dataImport.common");
  const describeProblem = useImportProblemMessage();

  const branchesQuery = useImportBranches();
  const progressQuery = useImportProgress();

  if (branchesQuery.isPending) return <Skeleton active paragraph={{ rows: 8 }} />;
  if (branchesQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={<span className="text-dan">{tc("error")}</span>}
        description={<span className="text-than">{describeProblem(branchesQuery.error)}</span>}
      />
    );
  }

  const branches = branchesQuery.data ?? [];
  const progressByBranch = new Map(
    (progressQuery.data ?? []).map((row) => [row.branchId, row] as const)
  );

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold" style={{ color: colorVars.textMain }}>
          {t("title")}
        </h1>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
          {t("lead")}
        </p>
      </header>

      <p
        className="m-0 flex items-start gap-2 rounded-lg border px-4 py-3 text-than"
        style={{
          borderColor: colorVars.border,
          backgroundColor: colorVars.bgPage,
          color: colorVars.textMain,
        }}
      >
        <InfoCircleOutlined aria-hidden style={{ color: colorVars.textMuted }} />
        {t("notARanking")}
      </p>

      {progressQuery.isError && (
        <Alert
          type="error"
          showIcon
          message={<span className="text-than">{describeProblem(progressQuery.error)}</span>}
        />
      )}

      <ul className="m-0 list-none space-y-3 p-0">
        {branches.map((branch) => (
          <li key={branch.id}>
            <BranchProgressCard branch={branch} progress={progressByBranch.get(branch.id)} />
          </li>
        ))}
      </ul>

      <p className="m-0 text-than" style={{ color: colorVars.textMuted }}>
        {t("missingGioWhy")}
      </p>
    </div>
  );
}

function BranchProgressCard({
  branch,
  progress,
}: {
  branch: ImportBranch;
  progress?: ImportBranchProgress;
}) {
  const t = useTranslations("dataImport.progress");
  const tStage = useTranslations("dataImport.stage");

  /**
   * Việc còn lại — chỉ dựng từ những trường **thật sự có mặt**.
   *
   * `?? 0` ở đây sẽ là một lời nói dối: một chi ngoài phạm vi không gửi
   * `blockingCount`, và "0 lỗi phải sửa" khác hẳn "bạn không được biết chi ấy
   * còn bao nhiêu lỗi".
   */
  const remaining: string[] = [];
  if (progress?.blockingCount) remaining.push(t("blocking", { count: progress.blockingCount }));
  if (progress?.warningCount) remaining.push(t("warnings", { count: progress.warningCount }));
  if (progress?.undecidedDuplicateCount) {
    remaining.push(t("undecided", { count: progress.undecidedDuplicateCount }));
  }
  if (progress?.missingGioCount) {
    remaining.push(t("missingGio", { count: progress.missingGioCount }));
  }
  /**
   * Chi này có nằm trong phạm vi người gọi không.
   *
   * Đọc từ `branch.canImport` — câu trả lời **trực tiếp** của máy chủ — chứ
   * không suy từ việc `blockingCount` có mặt hay không. Hai thứ trùng nhau hôm
   * nay, nhưng một cái là sự thật được công bố còn cái kia là một suy luận từ
   * hình dạng JSON, và suy luận ấy sẽ sai im lặng vào ngày máy chủ đổi cách cắt
   * trường.
   */
  const inScope = branch.canImport;

  return (
    <article
      className="rounded-lg border px-4 py-3"
      style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      data-testid={`branch-progress-${branch.id}`}
    >
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="m-0 font-serif text-de font-bold" style={{ color: colorVars.textMain }}>
          {branch.name}
        </h2>
        {progress && (
          <p className="m-0 text-than font-medium" style={{ color: colorVars.textMuted }}>
            {tStage(progress.stage)}
          </p>
        )}
      </header>

      <p className="m-0 mt-1 break-all font-mono text-than" style={{ color: colorVars.textMuted }}>
        {branch.path}
      </p>

      {/* `canImport` là thứ duy nhất về QUYỀN mà màn này biết chắc. Nói ra nó
          thay vì để người dùng đoán vì sao chi kia không bấm được gì. */}
      <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
        {branch.canImport ? t("yoursToImport") : t("notYoursToImport")}
      </p>

      {/* Chi chưa có người phụ trách là rủi ro lớn nhất của cả đợt, và nó thuộc
          về Hội đồng, không thuộc về phần mềm.

          `coordinatorName` vắng mặt vì HAI lý do khác nhau: chi ngoài phạm vi
          (tên một người còn sống, bị cắt), hoặc chi trong phạm vi mà chưa ai
          nhận. Chỉ dựng khối này cho chi TRONG phạm vi — ở đó "vắng" có đúng
          một nghĩa, và nghĩa ấy là một việc cần làm. */}
      {progress !== undefined && inScope && (
        <>
          {progress.coordinatorName ? (
            <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
              {t("coordinator")}: {progress.coordinatorName}
            </p>
          ) : (
            <div
              className="mt-2 rounded-md border px-3 py-2"
              style={{ borderColor: colorVars.accent, backgroundColor: colorVars.warningBg }}
            >
              <p className="m-0 text-than font-semibold" style={{ color: colorVars.accentText }}>
                {t("noCoordinator")}
              </p>
              <p className="m-0 text-than" style={{ color: colorVars.textMain }}>
                {t("noCoordinatorHint")}
              </p>
            </div>
          )}
        </>
      )}

      {progress && (
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMain }}>
          {t("inTree")}: {progress.personsInTree}
          {" · "}
          {/* VẮNG MẶT nghĩa là chưa ai đếm cuốn sổ giấy, KHÔNG phải 0. Đọc
              "vắng" thành 0 là báo chi ấy đã xong 100% trước khi ai bắt đầu —
              nên chỗ này nói thẳng "chưa đếm" thay vì im lặng bỏ qua. */}
          {progress.expectedPersons === undefined
            ? t("expectedUncounted")
            : t("expected", { count: progress.expectedPersons })}
        </p>
      )}

      {progress?.openBatch && (
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
          {t("openBatch", { batchId: progress.openBatch.id })}
        </p>
      )}

      {progress !== undefined && inScope && (
        <>
          <h3 className="m-0 mt-2 text-than font-semibold" style={{ color: colorVars.textMain }}>
            {t("remaining")}
          </h3>
          {remaining.length === 0 ? (
            <p className="m-0 text-than" style={{ color: colorVars.successText }}>
              {t("nothingLeft")}
            </p>
          ) : (
            <ul className="m-0 list-disc pl-5 text-than" style={{ color: colorVars.textMain }}>
              {remaining.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
        </>
      )}

      {progress?.openBatch && (
        <Link
          href={`/nhap-lieu/${progress.openBatch.id}`}
          className="mt-2 inline-flex min-h-[44px] min-w-[44px] items-center underline"
          style={{ color: colorVars.primary }}
        >
          {t("open")}
        </Link>
      )}
    </article>
  );
}

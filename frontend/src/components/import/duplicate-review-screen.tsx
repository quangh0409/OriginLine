"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Alert, Skeleton } from "antd";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import {
  useDecideDuplicate,
  useImportBatch,
  useImportDuplicatePolicy,
  useImportDuplicates,
  useImportIssues,
} from "./hooks/use-data-import";
import { useImportProblemMessage } from "./problem-message";
import { DuplicatePairCard } from "./duplicate-pair-card";
import { MergeNotApplicableNotice } from "./merge-not-applicable-notice";
import type { ImportDuplicateDecision } from "@/lib/api/data-import";

export interface DuplicateReviewScreenProps {
  batchId: string;
}

/**
 * Màn 4 — **đối chiếu người nghi trùng**.
 *
 * <h2>Máy xếp thứ tự, người quyết</h2>
 * Câu đầu tiên trên màn nói thẳng điều đó, và nó không phải lời lịch sự: bộ dò
 * trùng chấm điểm bằng một thang do người đặt ra và sẽ còn được hiệu chỉnh trên
 * tệp thật của chi đầu tiên. Một danh sách xếp theo điểm là *thứ tự xem xét*,
 * không phải *kết luận*. `autoMerge` của `GET /duplicate-policy` luôn `false`,
 * và trường ấy tồn tại để điều đó là một **lời hứa của hợp đồng** chứ không
 * phải một câu trong tài liệu.
 *
 * <h2>Ngưỡng hiện ra từ máy chủ, không từ một hằng số trong mã</h2>
 * Hai con số 70/85 được công bố ở `GET /import/duplicate-policy`. Chúng hiện
 * lên màn hình vì người đối chiếu cần biết "dưới bao nhiêu điểm thì máy không
 * buồn nghi" để đọc đúng ý nghĩa của một cặp 72 điểm.
 *
 * <h2>Tất cả các cặp trên một trang, không phải một trình hướng dẫn từng bước</h2>
 * Ba tới ba mươi cặp là khối lượng một buổi. Trình hướng dẫn từng-cặp-một buộc
 * người dùng quyết ngay mới đi tiếp — mà "chưa rõ" là một câu trả lời hợp lệ ở
 * đây, nên ép đi tới là ép bấm đại.
 *
 * <h2>Không có nút "quyết tất cả"</h2>
 * Nó sẽ được bấm. Và một lần bấm nhầm ở đó là hàng chục người bị gộp sai trong
 * phả, mỗi người một dấu vết xoá mềm không gỡ sạch được. Hợp đồng **cố ý** không
 * có lối gọi hàng loạt, nên dựng lại nó bằng một vòng lặp ở client là đi vòng
 * qua đúng chốt chặn mà cơ chế này sinh ra.
 *
 * <h2>Khối kế hoạch: hai con số ĐỔI sau mỗi lần bấm, và người dùng phải thấy</h2>
 * Một quyết định "hợp nhất" gộp một dòng từ "thêm mới" sang "cập nhật", hoặc
 * làm nó biến mất hẳn. `plannedCreateCount`/`plannedUpdateCount` vì thế **có**
 * đổi — và chúng về ngay trong phản hồi của lời gọi quyết định, đã tính lại ở
 * máy chủ. Không hiện chúng ở đây là để người đối chiếu bấm ba mươi lần mà
 * không bao giờ thấy hệ quả của việc mình vừa làm.
 *
 * <h2>Cửa duyệt đọc từ `canApprove`, và "hoãn" KHÔNG mở nó</h2>
 * Câu dưới khối kế hoạch lấy thẳng `undecidedDuplicateCount` của máy chủ, con
 * số đã tính cả cặp `DEFERRED`. Màn này không trừ đi, không làm tròn, không
 * "tạm coi như xong".
 */
export function DuplicateReviewScreen({ batchId }: DuplicateReviewScreenProps) {
  const t = useTranslations("dataImport.duplicates");
  const tc = useTranslations("dataImport.common");
  const describeProblem = useImportProblemMessage();

  const duplicatesQuery = useImportDuplicates(batchId);
  const policyQuery = useImportDuplicatePolicy();
  const batchQuery = useImportBatch(batchId);
  const decide = useDecideDuplicate(batchId);

  /**
   * Cặp nào đang ghi, và cặp nào vừa lỗi.
   *
   * Giữ theo `pair.id` chứ không phải một cờ chung: ba mươi cặp trên một trang,
   * và một cờ chung sẽ quay vòng tròn trên **cả ba mươi** nút khi người dùng
   * bấm đúng một cái. Đây là state của một lượt render, không phải dữ liệu —
   * sự thật vẫn nằm ở máy chủ.
   */
  const [pendingPairId, setPendingPairId] = useState<string | null>(null);
  const [errorByPair, setErrorByPair] = useState<Record<string, string>>({});

  const batch = batchQuery.data;

  /**
   * Danh sách lỗi chặn — chỉ hỏi khi lô **đang có** lỗi chặn.
   *
   * Một quyết định "hợp nhất" có thể bị từ chối bằng `IMP_MERGE_NOT_APPLICABLE`,
   * và lỗi ấy không về trong phản hồi: chỉ có `blockingCount` tăng. Không đọc
   * lại danh sách thì màn hình im lặng trong khi lô đã dừng.
   */
  const blockingQuery = useImportIssues(
    batch && batch.blockingCount > 0 ? batchId : undefined,
    "BLOCKING"
  );
  const mergeRefusals = (blockingQuery.data ?? []).filter(
    (issue) => issue.code === "IMP_MERGE_NOT_APPLICABLE"
  );

  if (duplicatesQuery.isPending) return <Skeleton active paragraph={{ rows: 8 }} />;
  if (duplicatesQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={<span className="text-dan">{tc("error")}</span>}
        description={<span className="text-than">{describeProblem(duplicatesQuery.error)}</span>}
      />
    );
  }

  const pairs = duplicatesQuery.data ?? [];
  const policy = policyQuery.data;

  const onDecide = async (
    pairId: string,
    decision: ImportDuplicateDecision,
    note: string | undefined
  ) => {
    setPendingPairId(pairId);
    setErrorByPair((prev) => {
      const next = { ...prev };
      delete next[pairId];
      return next;
    });
    try {
      // Phản hồi mang cả cặp lẫn lô đã tính lại — hook ghi thẳng vào cache.
      // KHÔNG gọi lại gì ở đây.
      await decide.mutateAsync({ pairId, decision, note });
    } catch (error) {
      setErrorByPair((prev) => ({ ...prev, [pairId]: describeProblem(error) }));
    } finally {
      setPendingPairId(null);
    }
  };

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold" style={{ color: colorVars.textMain }}>
          {t("title")}
        </h1>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
          {t("lead")}
        </p>
        {/* Ngưỡng do MÁY CHỦ công bố. Không có con số nào ghi cứng ở client. */}
        {policy && (
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
            {t("policy", {
              suspect: policy.suspectThreshold,
              preselect: policy.preselectMergeThreshold,
            })}
          </p>
        )}
      </header>

      <Link
        href={`/nhap-lieu/${batchId}`}
        className="inline-flex min-h-[44px] min-w-[44px] items-center underline"
        style={{ color: colorVars.primary }}
      >
        {t("back")}
      </Link>

      {/* Ca TỪ CHỐI đứng trên cùng: lô đã dừng, và mọi quyết định tiếp theo là
          vô nghĩa cho tới khi người dùng gỡ đúng chỗ này. */}
      {mergeRefusals.map((issue) => (
        <MergeNotApplicableNotice key={issue.id} issue={issue} />
      ))}

      {pairs.length === 0 ? (
        <Alert type="info" showIcon message={<span className="text-than">{t("none")}</span>} />
      ) : (
        <>
          {/* KẾ HOẠCH GHI — hai con số này đổi sau mỗi lần bấm, và chúng do máy
              chủ tính lại rồi gửi kèm trong phản hồi của lời gọi quyết định. */}
          {batch && (
            <section
              className="rounded-lg border px-4 py-3"
              data-testid="duplicate-plan"
              aria-live="polite"
              style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
            >
              <h2
                className="m-0 font-serif text-de font-bold"
                style={{ color: colorVars.textMain }}
              >
                {t("plan.title")}
              </h2>
              <ul
                className="m-0 mt-1 list-none space-y-1 p-0 text-than"
                style={{ color: colorVars.textMain }}
              >
                <li data-testid="plan-create">
                  {t("plan.create", { count: batch.plannedCreateCount })}
                </li>
                <li data-testid="plan-update">
                  {t("plan.update", { count: batch.plannedUpdateCount })}
                </li>
                <li data-testid="plan-undecided">
                  {batch.undecidedDuplicateCount === 0
                    ? t("plan.allDecided")
                    : t("plan.undecided", { count: batch.undecidedDuplicateCount })}
                </li>
              </ul>
              <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
                {t("plan.why")}
              </p>
              {/* Cửa duyệt: đọc `undecidedDuplicateCount` của MÁY CHỦ, con số đã
                  tính cả cặp `DEFERRED`. Không bù trừ ở client. */}
              <p
                className="m-0 mt-2 text-than font-medium"
                data-testid="duplicate-gate"
                style={{
                  color:
                    batch.undecidedDuplicateCount === 0
                      ? colorVars.successText
                      : colorVars.accentText,
                }}
              >
                {batch.undecidedDuplicateCount === 0
                  ? t("plan.gateOpen")
                  : t("plan.gateClosed", { count: batch.undecidedDuplicateCount })}
              </p>
            </section>
          )}

          <p className="m-0 text-than" style={{ color: colorVars.textMain }}>
            {t("privacyNote")}
          </p>

          <ul className="m-0 list-none space-y-4 p-0">
            {pairs.map((pair, index) => (
              <li key={pair.id}>
                <DuplicatePairCard
                  pair={pair}
                  index={index + 1}
                  total={pairs.length}
                  deciding={pendingPairId === pair.id}
                  error={errorByPair[pair.id]}
                  onDecide={(decision, note) => void onDecide(pair.id, decision, note)}
                />
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

"use client";

import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dataImportApi,
  type ImportBatch,
  type ImportBranch,
  type ImportBranchProgress,
  type ImportCommitProgress,
  type ImportDuplicateDecision,
  type ImportDuplicateDecisionResult,
  type ImportDuplicatePair,
  type ImportDuplicatePolicy,
  type ImportIssue,
  type ImportIssueSeverity,
  type ImportPlannedAction,
  type ImportRollbackPreflight,
  type ImportRow,
  type UploadBatchInput,
} from "@/lib/api/data-import";
import { ApiError } from "@/lib/api/http";

/**
 * Server-state của đường ống nhập liệu.
 *
 * <h2>Khoá query gom tại đây, không ở `lib/query/keys.ts`</h2>
 * Cùng lý do nhóm `change-requests` đã làm: đây là một mảnh mới, và gom khoá
 * ngay cạnh hook dùng nó giữ cho việc vô hiệu hoá cache đọc được trong một màn
 * hình. Khi nhóm này ổn định thì chuyển sang factory chung.
 *
 * <h2>Không có state cục bộ nào thay thế được máy chủ ở đây</h2>
 * Trưởng chi nhập 350–400 người trong 6–10 buổi và sẽ đóng trình duyệt giữa
 * chừng. Vì vậy **mọi** thứ đáng giữ — lô nào đang dở, đã tick xem cảnh báo
 * chưa, cặp nghi trùng nào đã quyết — đều là dữ liệu máy chủ. Giữ chúng trong
 * `useState` là cách để một cú F5 xoá mất nửa buổi làm việc mà không ai biết vì
 * sao.
 *
 * <h2>Mọi hook ở đây dùng `retry: false`</h2>
 * Nhóm này trả lỗi có nghĩa — `403 BRANCH_SCOPE_VIOLATION`, `422` của cổng
 * duyệt — và thử lại một `403` ba lần chỉ làm màn hình đứng im lâu hơn trước
 * khi nói ra câu người dùng cần đọc.
 */
export const importKeys = {
  all: ["data-import"] as const,
  branches: () => ["data-import", "branches"] as const,
  policy: () => ["data-import", "duplicate-policy"] as const,
  batches: (branchId?: string) => ["data-import", "batches", branchId ?? "all"] as const,
  batch: (batchId: string) => ["data-import", "batch", batchId] as const,
  issues: (batchId: string, severity?: ImportIssueSeverity) =>
    ["data-import", "batch", batchId, "issues", severity ?? "all"] as const,
  rows: (batchId: string, action?: ImportPlannedAction) =>
    ["data-import", "batch", batchId, "rows", action ?? "all"] as const,
  duplicates: (batchId: string) => ["data-import", "batch", batchId, "duplicates"] as const,
  commitProgress: (batchId: string) => ["data-import", "batch", batchId, "commit"] as const,
  rollbackPreflight: (batchId: string) => ["data-import", "batch", batchId, "preflight"] as const,
  progress: () => ["data-import", "progress"] as const,
};

/**
 * Cây chi của **cả dòng họ**, kèm `canImport` cho người đang gọi.
 *
 * Mảng rỗng không còn nghĩa là "chưa được giao chi nào" — máy chủ trả cả cây,
 * nên câu trả lời ấy nằm ở chỗ **không chi nào có `canImport`**.
 */
export function useImportBranches() {
  return useQuery<ImportBranch[], ApiError>({
    queryKey: importKeys.branches(),
    queryFn: () => dataImportApi.branches(),
    retry: false,
    staleTime: 5 * 60_000,
  });
}

/**
 * Ngưỡng nghi trùng **do máy chủ công bố**.
 *
 * `staleTime` dài vì đây là cấu hình, không phải dữ liệu nghiệp vụ. Nhưng nó
 * vẫn phải là một lời gọi thật: ghi cứng 70/85 vào giao diện là dựng một bản
 * sao chắc chắn sẽ trôi khỏi bộ dò trùng.
 */
export function useImportDuplicatePolicy() {
  return useQuery<ImportDuplicatePolicy, ApiError>({
    queryKey: importKeys.policy(),
    queryFn: () => dataImportApi.duplicatePolicy(),
    retry: false,
    staleTime: 30 * 60_000,
  });
}

export function useImportBatch(batchId: string | undefined) {
  return useQuery<ImportBatch, ApiError>({
    queryKey: importKeys.batch(batchId ?? ""),
    queryFn: () => dataImportApi.batch(batchId!),
    enabled: Boolean(batchId),
    retry: false,
    /**
     * Đọc lại mỗi lần mở màn, không tin bản trong bộ nhớ.
     *
     * Lô là thứ **gác cửa ghi vào phả**: `canApprove` quyết định nút duyệt mở
     * hay khoá. Người dùng đi sang màn đối chiếu rồi quay lại; một bản cũ vài
     * giây làm nút khoá trong khi lẽ ra đã mở (người ta tưởng hỏng) hoặc mở
     * trong khi lẽ ra còn khoá (tệ hơn nhiều).
     */
    refetchOnMount: "always",
  });
}

export function useImportIssues(batchId: string | undefined, severity?: ImportIssueSeverity) {
  return useQuery<ImportIssue[], ApiError>({
    queryKey: importKeys.issues(batchId ?? "", severity),
    queryFn: () => dataImportApi.issues(batchId!, severity),
    enabled: Boolean(batchId),
    retry: false,
  });
}

/** Các dòng đang chờ. Chỉ hỏi khi người dùng mở khối xem trước — 318 dòng không rẻ. */
export function useImportRows(
  batchId: string | undefined,
  options: { enabled?: boolean; action?: ImportPlannedAction } = {}
) {
  const { enabled = true, action } = options;
  return useQuery<ImportRow[], ApiError>({
    queryKey: importKeys.rows(batchId ?? "", action),
    queryFn: () => dataImportApi.rows(batchId!, action),
    enabled: Boolean(batchId) && enabled,
    retry: false,
  });
}

export function useImportDuplicates(batchId: string | undefined) {
  return useQuery<ImportDuplicatePair[], ApiError>({
    queryKey: importKeys.duplicates(batchId ?? ""),
    queryFn: () => dataImportApi.duplicates(batchId!),
    enabled: Boolean(batchId),
    retry: false,
    refetchOnMount: "always",
  });
}

/**
 * Ghi quyết định cho **một** cặp nghi trùng.
 *
 * <h2>Không gọi lại gì sau mỗi quyết định</h2>
 * Phản hồi mang cả cặp lẫn **lô đã tính lại** — `undecidedDuplicateCount`,
 * `plannedCreateCount`/`plannedUpdateCount` và `canApprove` đều đã đúng. Thay
 * thẳng vào cache. Một lượt `invalidateQueries` ở đây chỉ tạo ra khoảng thời
 * gian ngắn mà nút duyệt hiện sai, rồi tự sửa — đúng loại nhấp nháy làm người
 * dùng mất tin vào cổng duyệt.
 *
 * <h2>Và KHÔNG bù trừ gì ở client</h2>
 * Sau `DEFERRED`, `canApprove` vẫn `false` và số cặp chưa quyết vẫn ≥ 1. Đó là
 * câu trả lời của máy chủ và giao diện chép lại y nguyên: nếu "hoãn" mở khoá
 * nút duyệt thì nó thành nút "cho tôi qua" và cả cơ chế dò trùng thành trang
 * trí.
 *
 * <h2>Không có lối gọi hàng loạt</h2>
 * Hook này nhận **một** cặp mỗi lần, có chủ ý. Đừng bọc nó trong một vòng lặp
 * để dựng nút "quyết tất cả".
 */
export function useDecideDuplicate(batchId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    ImportDuplicateDecisionResult,
    ApiError,
    { pairId: string; decision: ImportDuplicateDecision; note?: string }
  >({
    mutationFn: ({ pairId, decision, note }) =>
      dataImportApi.decideDuplicate(batchId, pairId, { decision, note }),
    onSuccess: ({ pair, batch }) => {
      queryClient.setQueryData(importKeys.batch(batchId), batch);
      // Thay ĐÚNG cặp vừa quyết, giữ nguyên thứ tự máy chủ đã xếp: danh sách
      // nhảy chỗ dưới tay người đang đối chiếu là cách chắc chắn để cặp kế tiếp
      // bị bấm nhầm.
      queryClient.setQueryData<ImportDuplicatePair[]>(importKeys.duplicates(batchId), (old) =>
        old?.map((p) => (p.id === pair.id ? pair : p))
      );
      // Gộp một cặp `TREE` đổi `plannedAction` của chính dòng ấy, nên bản xem
      // trước các dòng đang chờ đã cũ. Nó không nằm trong phản hồi — đây là lời
      // gọi lại DUY NHẤT được phép ở đây, và nó chỉ chạy khi khối xem trước
      // đang mở.
      void queryClient.invalidateQueries({
        queryKey: ["data-import", "batch", batchId, "rows"],
      });
      // Một quyết định "hợp nhất" có thể bị TỪ CHỐI bằng một lỗi chặn mới
      // (`IMP_MERGE_NOT_APPLICABLE`). Lỗi ấy không về trong phản hồi, chỉ có
      // `blockingCount` tăng — nên danh sách lỗi phải được đọc lại.
      if (batch.blockingCount > 0) {
        void queryClient.invalidateQueries({
          queryKey: ["data-import", "batch", batchId, "issues"],
        });
      }
    },
  });
}

/**
 * Tiến độ ghi nền.
 *
 * Chỉ hỏi khi lô đang `COMMITTING`. Hỏi mãi sau khi xong là đốt pin điện thoại
 * cho một con số không còn đổi — và người dùng của sản phẩm này ngồi trên 3G ở
 * nhà thờ họ.
 */
export function useCommitProgress(batchId: string | undefined, active: boolean) {
  const queryClient = useQueryClient();
  const query = useQuery<ImportCommitProgress, ApiError>({
    queryKey: importKeys.commitProgress(batchId ?? ""),
    queryFn: () => dataImportApi.commitProgress(batchId!),
    enabled: Boolean(batchId) && active,
    refetchInterval: active ? 700 : false,
    retry: false,
  });

  /**
   * Khi việc ghi nền kết thúc, **đọc lại chính cái lô**.
   *
   * Đây là chỗ luồng này từng đứng im mãi mãi: `POST /commit` trả về lô ở trạng
   * thái `COMMITTING`, rồi endpoint tiến độ là nơi DUY NHẤT biết lúc nào nó
   * xong. Không có lời gọi đọc lại ở đây thì bản sao trong bộ nhớ vẫn là
   * `COMMITTING` vĩnh viễn — màn hình quay thanh tiến trình đến hết ngày trong
   * khi 318 người đã nằm yên trong phả từ lâu.
   */
  const status = query.data?.status;
  useEffect(() => {
    if (!batchId || !status || status === "COMMITTING") return;
    void queryClient.invalidateQueries({ queryKey: importKeys.batch(batchId) });
    void queryClient.invalidateQueries({ queryKey: importKeys.rollbackPreflight(batchId) });
  }, [batchId, status, queryClient]);

  return query;
}

/**
 * "Lô này còn gỡ được không, vướng ở đâu" — hỏi **trước** khi mở hộp xác nhận.
 *
 * Một hộp thoại "Bạn chắc chứ?" không nói gì về việc đã có người sửa hồ sơ do
 * lô ấy sinh ra là một hộp thoại nói dối. Vì vậy hộp xác nhận không được mở khi
 * chưa có câu trả lời này.
 */
export function useRollbackPreflight(batchId: string | undefined, enabled: boolean) {
  return useQuery<ImportRollbackPreflight, ApiError>({
    queryKey: importKeys.rollbackPreflight(batchId ?? ""),
    queryFn: () => dataImportApi.rollbackPreflight(batchId!),
    enabled: Boolean(batchId) && enabled,
    retry: false,
    // Ai đó có thể vừa sửa một hồ sơ trong lúc hộp thoại đang mở.
    refetchOnMount: "always",
    staleTime: 0,
  });
}

export function useUploadBatch() {
  const queryClient = useQueryClient();
  return useMutation<ImportBatch, ApiError, UploadBatchInput>({
    mutationFn: (input) => dataImportApi.upload(input),
    onSuccess: () => {
      // Tải lại làm lô cũ của cùng chi thành SUPERSEDED, nên phải làm mới cả
      // nhóm chứ không chỉ lô vừa tạo.
      void queryClient.invalidateQueries({ queryKey: importKeys.all });
    },
  });
}

/**
 * Chạy lại bộ kiểm. Sinh lại **toàn bộ** danh sách lỗi — không tích luỹ.
 *
 * <h2>Vô hiệu hoá cache KHÔNG phải là xoá quyết định</h2>
 * Lượt `invalidateQueries` dưới đây kéo cả `/duplicates` về đọc lại, và đó
 * chính là điều đúng: quyết định sống ở **máy chủ**, nên cặp đã quyết quay về
 * nguyên `status`, `decidedBy` và `decidedAt` của nó; chỉ cặp **mới** vào với
 * `PENDING`. Giao diện không giữ bản sao nào để mà xoá — bắt người ta quyết lại
 * 40 cặp vì lần kiểm sau tìm thêm cặp thứ 41 là cách huấn luyện họ bấm bừa.
 */
export function useRevalidateBatch(batchId: string) {
  const queryClient = useQueryClient();
  return useMutation<ImportBatch, ApiError, void>({
    mutationFn: () => dataImportApi.revalidate(batchId),
    onSuccess: (batch) => {
      queryClient.setQueryData(importKeys.batch(batchId), batch);
      void queryClient.invalidateQueries({ queryKey: ["data-import", "batch", batchId] });
    },
  });
}

export function useCommitBatch(batchId: string) {
  const queryClient = useQueryClient();
  return useMutation<ImportBatch, ApiError, void>({
    mutationFn: () => dataImportApi.commit(batchId),
    onSuccess: (batch) => {
      queryClient.setQueryData(importKeys.batch(batchId), batch);
      // Cây phả hệ vừa đổi thật — đây là lần duy nhất trong cả đường ống điều
      // đó đúng.
      void queryClient.invalidateQueries({ queryKey: ["tree"] });
      void queryClient.invalidateQueries({ queryKey: ["tree-branch"] });
    },
  });
}

export function useRollbackBatch(batchId: string) {
  const queryClient = useQueryClient();
  return useMutation<ImportBatch, ApiError, string | undefined>({
    mutationFn: (reason) => dataImportApi.rollback(batchId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: importKeys.all });
      void queryClient.invalidateQueries({ queryKey: ["tree"] });
      void queryClient.invalidateQueries({ queryKey: ["tree-branch"] });
    },
  });
}

/**
 * Danh sách lô trong phạm vi người gọi — thứ làm cho "đóng trình duyệt rồi quay
 * lại hôm sau" chạy được. Mảng rỗng nghĩa là người này thật sự không có lô nào.
 */
export function useImportBatches(branchId?: string) {
  return useQuery<ImportBatch[], ApiError>({
    queryKey: importKeys.batches(branchId),
    queryFn: () => dataImportApi.batches({ branchId }),
    retry: false,
  });
}

/**
 * Xác nhận đã xem phần cần xem lại.
 *
 * Ghi thẳng phản hồi vào cache: `canApprove` trong đó là câu trả lời cuối cùng
 * cho "nút duyệt có mở không", và nó đã tính cả việc xác nhận còn hiệu lực hay
 * không. Đọc lại `warningsAcknowledgedAt` để tự kết luận là đi chệch khỏi đúng
 * cái bất biến này tồn tại để giữ.
 */
export function useAcknowledgeWarnings(batchId: string) {
  const queryClient = useQueryClient();
  return useMutation<ImportBatch, ApiError, void>({
    mutationFn: () => dataImportApi.acknowledgeWarnings(batchId),
    onSuccess: (batch) => {
      queryClient.setQueryData(importKeys.batch(batchId), batch);
    },
  });
}

/**
 * Tiến độ từng chi.
 *
 * Không `retry`: khách và tài khoản chưa khởi tạo nhận `403`, và câu trả lời ấy
 * đúng ngay từ lần đầu.
 */
export function useImportProgress() {
  return useQuery<ImportBranchProgress[], ApiError>({
    queryKey: importKeys.progress(),
    queryFn: () => dataImportApi.progress(),
    retry: false,
  });
}

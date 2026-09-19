import { useCallback, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { personsApi } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { queryKeys } from "@/lib/query/keys";
import {
  isDuplicateCandidate,
  isTabooConflict,
  type ConflictProblem,
  type CreatePersonRequest,
  type DateDual,
  type DuplicateCandidate,
  type PersonDto,
  type TabooConflict,
  type UpdatePersonRequest,
} from "@/types/api";

/**
 * Thứ hộp thoại "Bạn đang nhập thông tin người mất" cần để nói cho ra người, ra việc.
 *
 * Chỉ là dữ liệu HIỂN THỊ. Ai chuyển từ sống sang mất, và có phải hỏi hay không, do form quyết
 * định (nó nắm bản ghi gốc); hook này chỉ giữ payload lại cho đến khi người dùng trả lời.
 */
export interface DeathConfirmationRequest {
  /** Tên người sắp bị đánh dấu là đã mất — không được hỏi trống không. */
  personName: string;
  /** Ngày mất vừa nhập, để hộp thoại chiếu lại cho người dùng soi xem có gõ nhầm không. */
  deathDate?: DateDual | null;
}

export type PersonSubmitTarget =
  | { mode: "create" }
  | { mode: "update"; personId: string; etag: string };

/**
 * Hai cờ ghi đè, hai cổng nối tiếp nhau.
 *
 * `confirmDuplicateOverride` **chỉ có ở `POST`** — hợp đồng không đặt phép
 * chống trùng lên `PATCH`, nên `update` cố tình không nhận nó.
 */
export interface SubmitOverrides {
  confirmTabooOverride?: boolean;
  confirmDuplicateOverride?: boolean;
  overrideReason?: string;
}

export interface PersonSubmitPayload {
  create: (options: SubmitOverrides) => CreatePersonRequest;
  update: (options: Omit<SubmitOverrides, "confirmDuplicateOverride">) => UpdatePersonRequest;
}

/**
 * The kỵ húy two-call flow (FR-1.6), which is the whole reason submitting a
 * person is not a plain mutation.
 *
 *   1. Send the payload WITHOUT `confirmTabooOverride`. Defaulting it to true
 *      "for convenience" would delete the feature (contracts/README §7.9).
 *   2. On `409 KY_HUY_CONFLICT`, surface `conflicts[]` — who the new name
 *      collides with, which generation, and how (exact vs. unaccented).
 *   3. If the user still wants it, resend the IDENTICAL payload plus
 *      `confirmTabooOverride: true` and the reason they typed, which the
 *      backend records in the audit trail.
 *
 * Any other 409 (`OPTIMISTIC_LOCK_CONFLICT`) is NOT overridable and is
 * reported as an error: someone else changed the record and the edit must be
 * reloaded, not forced through.
 */
export function usePersonSubmit(target: PersonSubmitTarget) {
  const queryClient = useQueryClient();
  const [conflicts, setConflicts] = useState<TabooConflict[] | null>(null);
  const [duplicates, setDuplicates] = useState<DuplicateCandidate[] | null>(null);
  /**
   * Cờ kỵ húy đã được chấp nhận ở vòng trước.
   *
   * Cần giữ lại vì hai cổng nối tiếp nhau: người dùng có thể vượt kỵ húy rồi
   * mới đụng cổng nghi trùng. Nếu vòng gửi thứ ba quên cờ thứ nhất thì máy chủ
   * lại đáp 409 kỵ húy và hộp thoại kia bật lên lần nữa — một vòng lặp mà
   * người dùng không có cách nào thoát.
   */
  const acceptedTaboo = useRef<{ reason: string } | null>(null);
  const [deathConfirmation, setDeathConfirmation] = useState<DeathConfirmationRequest | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const pendingPayload = useRef<PersonSubmitPayload | null>(null);

  const send = useCallback(
    async (
      payload: PersonSubmitPayload,
      options: SubmitOverrides
    ): Promise<PersonDto | null> => {
      setIsSubmitting(true);
      setError(null);
      try {
        if (target.mode === "create") {
          const created = await personsApi.create(payload.create(options));
          queryClient.setQueryData(queryKeys.person(created.id), {
            person: created,
            etag: `"v${created.version ?? 1}"`,
          });
          setConflicts(null);
          setDuplicates(null);
          return created;
        }

        const { data, etag } = await personsApi.update(
          target.personId,
          payload.update(options),
          target.etag
        );
        queryClient.setQueryData(queryKeys.person(target.personId), { person: data, etag });
        // The tree projection embeds names and badges, so it is stale now.
        void queryClient.invalidateQueries({ queryKey: ["tree"] });
        void queryClient.invalidateQueries({ queryKey: ["tree-branch"] });
        setConflicts(null);
        setDuplicates(null);
        return data;
      } catch (caught) {
        if (caught instanceof ApiError) {
          const problem = caught.problem as ConflictProblem | undefined;
          // `conflicts[]` nay là một hợp của hai kiểu; phân loại theo KHOÁ
          // riêng của chúng, không theo `code` — cùng một thân lỗi phục vụ cả
          // hai cổng, và một ngày nào đó có thể phục vụ thêm cổng thứ ba.
          if (caught.code === "KY_HUY_CONFLICT" && problem?.conflicts?.length) {
            pendingPayload.current = payload;
            setConflicts(problem.conflicts.filter(isTabooConflict));
            return null;
          }
          if (caught.code === "DUPLICATE_PERSON_SUSPECTED" && problem?.conflicts?.length) {
            pendingPayload.current = payload;
            setDuplicates(problem.conflicts.filter(isDuplicateCandidate));
            return null;
          }
          setError(caught);
          return null;
        }
        throw caught;
      } finally {
        setIsSubmitting(false);
      }
    },
    [queryClient, target]
  );

  /**
   * First attempt — deliberately never carries the override flag.
   *
   * `deathGuard` là chốt chặn TRƯỚC KHI GỬI cho một thao tác khó thu hồi: đánh dấu một người
   * đang sống là đã mất. Không chỉ đổi một ô đánh dấu — theo BA v2 §10 hồ sơ người đã khuất
   * chuyển từ "ẩn mặc định" sang công khai với cả Khách chưa đăng nhập, và hệ thống sinh lịch
   * nhắc giỗ. Sửa lại được, nhưng thứ đã lộ thì không thu về được, nên khác với kỵ húy (máy chủ
   * đáp 409 rồi ta mới hỏi) lần này phải hỏi trước khi có bất kỳ yêu cầu nào rời máy.
   *
   * Cách vận hành thì vẫn y hệt lối kỵ húy: giữ nguyên payload đang chờ, hiện hộp thoại, và chỉ
   * gửi khi người dùng xác nhận.
   */
  const submit = useCallback(
    (payload: PersonSubmitPayload, deathGuard?: DeathConfirmationRequest | null) => {
      acceptedTaboo.current = null;
      if (deathGuard) {
        pendingPayload.current = payload;
        setDeathConfirmation(deathGuard);
        return Promise.resolve(null);
      }
      return send(payload, {});
    },
    [send]
  );

  /** Người dùng đã đọc hệ quả và vẫn xác nhận — giờ mới gửi, vẫn không kèm cờ kỵ húy. */
  const confirmDeath = useCallback(() => {
    const payload = pendingPayload.current;
    if (!payload) return Promise.resolve(null);
    setDeathConfirmation(null);
    return send(payload, {});
  }, [send]);

  /** Huỷ: không gửi gì cả, form giữ nguyên để người dùng soi lại ngày vừa gõ. */
  const cancelDeath = useCallback(() => {
    pendingPayload.current = null;
    setDeathConfirmation(null);
  }, []);

  /** Second attempt, after the user read the collision and accepted it. */
  const confirmOverride = useCallback(
    (reason: string) => {
      const payload = pendingPayload.current;
      if (!payload) return Promise.resolve(null);
      acceptedTaboo.current = { reason };
      return send(payload, { confirmTabooOverride: true, overrideReason: reason });
    },
    [send]
  );

  const cancelOverride = useCallback(() => {
    pendingPayload.current = null;
    acceptedTaboo.current = null;
    setConflicts(null);
  }, []);

  /**
   * Người dùng đã đối chiếu và khẳng định đây là người khác.
   *
   * Không đòi lý do gõ tay như kỵ húy: nghi trùng chỉ là một phỏng đoán thống
   * kê, còn kỵ húy là một điều kiêng kỵ của dòng họ mà việc bỏ qua phải để lại
   * dấu vết. Bắt gõ lý do cho một cảnh báo hay báo nhầm chỉ dạy người dùng gõ
   * bừa — và thói quen ấy sẽ theo họ sang cả hộp thoại kỵ húy.
   */
  const confirmDuplicateOverride = useCallback(() => {
    const payload = pendingPayload.current;
    if (!payload) return Promise.resolve(null);
    const taboo = acceptedTaboo.current;
    return send(payload, {
      confirmDuplicateOverride: true,
      confirmTabooOverride: taboo ? true : undefined,
      overrideReason: taboo?.reason,
    });
  }, [send]);

  const cancelDuplicateOverride = useCallback(() => {
    pendingPayload.current = null;
    acceptedTaboo.current = null;
    setDuplicates(null);
  }, []);

  return {
    submit,
    confirmOverride,
    cancelOverride,
    confirmDuplicateOverride,
    cancelDuplicateOverride,
    confirmDeath,
    cancelDeath,
    conflicts,
    duplicates,
    deathConfirmation,
    isSubmitting,
    error,
  };
}

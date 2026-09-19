"use client";

import { App, Button, Popconfirm } from "antd";
import { useTranslations, useFormatter } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useCancelChangeRequest } from "@/hooks/use-change-requests";
import { usePerson } from "@/hooks/use-person";
import { headlineName } from "@/lib/format/name-layers";
import type { ChangeRequestView } from "@/lib/api/change-requests";
import { ChangeRequestStatusTag } from "./change-request-status-tag";
import { CorrectionDiff } from "./correction-diff";

/**
 * Một đề nghị **của chính người đang đăng nhập**.
 *
 * <h2>Vì sao người gửi phải thấy lại đề nghị của mình</h2>
 * Gửi xong rồi im lặng là cách nhanh nhất để người ta gửi lại lần thứ hai, thứ
 * ba — hàng đợi phình ra vì trùng lặp, còn người gửi thì tin rằng chẳng ai đọc.
 * Trạng thái hiện ra ở đây, kèm **lý do từ chối** khi có: bị từ chối mà không
 * biết vì sao thì lần sau không ai gửi nữa.
 *
 * Rút lại là chuyển sang `CANCELLED`, không phải xoá — lịch sử vẫn đọc được.
 */
export function MyRequestCard({ request }: { request: ChangeRequestView }) {
  const t = useTranslations("correction");
  const format = useFormatter();
  const { message } = App.useApp();
  const cancel = useCancelChangeRequest();
  const personQuery = usePerson(request.personId ?? undefined);
  const person = personQuery.data?.person;

  const onCancel = async () => {
    try {
      await cancel.mutateAsync(request.id);
      message.success(t("mine.cancelled"));
    } catch {
      message.error(t("errors.cancelFailed"));
    }
  };

  return (
    <article className="rounded-lg border border-border bg-bg-card px-4 py-3">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="m-0 font-serif text-base font-semibold text-text-main">
            {request.personId ? (
              <Link href={`/persons/${request.personId}`} className="text-primary">
                {(person && headlineName(person)) ?? t("card.unknownPerson")}
              </Link>
            ) : (
              t("card.noPerson")
            )}
          </h3>
          <p className="m-0 mt-0.5 text-than text-text-muted">
            {t(`type.${request.type}`)}
            {request.createdAt && (
              <>
                {" · "}
                {format.dateTime(new Date(request.createdAt), { dateStyle: "medium" })}
              </>
            )}
          </p>
        </div>
        <ChangeRequestStatusTag status={request.status} />
      </header>

      {request.reason && (
        <p className="m-0 mt-2 whitespace-pre-line text-than leading-snug text-text-main">
          {request.reason}
        </p>
      )}

      <div className="mt-3">
        <CorrectionDiff
          payload={request.payload}
          payloadFields={request.payloadFields}
          person={person}
        />
      </div>

      {request.reviewNote && (
        <p className="m-0 mt-2 text-than text-text-muted">
          {t("card.reviewNote", { note: request.reviewNote })}
        </p>
      )}

      {request.status === "PENDING" && (
        <Popconfirm
          title={t("mine.cancelConfirm")}
          okText={t("mine.cancel")}
          cancelText={t("review.cancel")}
          onConfirm={() => void onCancel()}
        >
          <Button className="!mt-3" size="small" loading={cancel.isPending}>
            {t("mine.cancel")}
          </Button>
        </Popconfirm>
      )}
    </article>
  );
}

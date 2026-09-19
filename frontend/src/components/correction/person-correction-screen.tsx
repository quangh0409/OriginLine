"use client";

import { Alert, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { usePerson } from "@/hooks/use-person";
import { ApiError } from "@/lib/api/http";
import { headlineName } from "@/lib/format/name-layers";
import { CorrectionRequestEntry } from "./correction-request-entry";

/**
 * Trang đề nghị đính chính cho **một** nhân khẩu, mở thẳng hộp thoại.
 *
 * <h2>Ba câu trả lời khác nhau, ba màn hình khác nhau</h2>
 * <ul>
 *   <li>máy chủ nói `canRequestCorrection` → mở luôn hộp thoại, không bắt bấm
 *       thêm một nút nữa: người ta tới đây là đã có ý định rồi;</li>
 *   <li>máy chủ nói `canEdit` → người này sửa thẳng được, chỉ đường sang màn
 *       hình sửa thay vì bắt họ đi vòng qua hàng đợi của chính mình;</li>
 *   <li>404 → hồ sơ không tồn tại, <b>hoặc</b> là người còn sống mà người xem
 *       không được phép biết là có. Hai điều đó cố ý không phân biệt được, nên
 *       câu trả lời cũng chỉ có một.</li>
 * </ul>
 */
export function PersonCorrectionScreen({ personId }: { personId: string }) {
  const t = useTranslations("correction");
  const { data, isPending, error } = usePerson(personId);

  if (isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return notFound ? (
      <Empty description={t("errors.personNotFound")} />
    ) : (
      <Alert type="error" showIcon message={t("errors.loadFailed")} />
    );
  }

  const { person } = data;
  const name = headlineName(person) ?? "";

  return (
    <>
      <h1 className="mb-1 font-serif text-2xl font-bold text-text-main">
        {t("person.title", { name })}
      </h1>
      <p className="mb-4 mt-0 text-than text-text-muted">{t("person.subtitle")}</p>

      {person.meta.canEdit ? (
        <Alert
          type="info"
          showIcon
          message={t("person.canEditInstead")}
          description={t("person.canEditInsteadHint")}
        />
      ) : person.meta.canRequestCorrection ? (
        <CorrectionRequestEntry person={person} autoOpen />
      ) : (
        <Alert type="info" showIcon message={t("errors.notProvisioned")} />
      )}
    </>
  );
}

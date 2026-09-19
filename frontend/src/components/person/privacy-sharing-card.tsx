"use client";

import { useEffect, useMemo, useState } from "react";
import { Alert, Button } from "antd";
import { SafetyOutlined, TeamOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/http";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import { useUpdatePerson } from "@/hooks/use-person";
import {
  PRIVACY_GROUPS,
  type PersonDto,
  type PrivacyGroup,
  type PrivacySettings,
  type ShareScope,
} from "@/types/api";
import { PrivacyAudiencePreview } from "./privacy-audience-preview";
import { PrivacyGroupRow } from "./privacy-group-row";

export interface PrivacySharingCardProps {
  person: PersonDto;
  /** `ETag` của lần đọc hồ sơ gần nhất — bắt buộc cho `If-Match` khi ghi. */
  etag: string | null;
}

/**
 * Màn "chính chủ tự đặt mức chia sẻ" — phần BA v2 §10 gọi là *chủ thể tự kiểm
 * soát*, và là thứ duy nhất biến phân tầng riêng tư từ một chính sách thành
 * một quyền dùng được.
 *
 * <h2>Điều kiện hiện: `person.privacy` CÓ MẶT, không phải vai, không phải tier</h2>
 * Contract trả khối `privacy` **chỉ cho chính chủ và ADMIN**; với mọi vai khác
 * nó vắng hẳn khỏi JSON. Vì thế `isPresent(person.privacy)` là phép kiểm đáng
 * tin nhất — nó đọc kết quả lọc thật của máy chủ thay vì đoán lại.
 *
 * `meta.visibleTier` thì KHÔNG dùng được ở đây, và đây là cái bẫy contract nói
 * đích danh: tier là **tóm tắt suy ra sau khi lọc**, `T2` không hứa trường nào
 * tồn tại. Một `T3` có thể là "chính chủ" mà cũng có thể là "một người lạ đã
 * được mở nhóm liên hệ".
 *
 * Cộng thêm `meta.isSelf`: ADMIN cũng ĐỌC được khối này (để hỗ trợ và kiểm
 * toán) nhưng không đặt hộ được — "chủ thể tự kiểm soát" mà người khác đặt hộ
 * được thì không còn là tự kiểm soát.
 *
 * <h2>Ghi qua `PATCH /persons/{id}`, hợp nhất từng nhóm</h2>
 * Không có endpoint riêng cho mức chia sẻ. Và vì `privacy` là trường **hợp
 * nhất** (khác `names`/`attributes` thay cả danh sách), giao diện chỉ gửi
 * những công tắc đã đổi — không gửi lại cả năm.
 *
 * <h2>Vì sao lời hứa về khách được in ngay trên khối</h2>
 * Rào cản lớn nhất khi mời một người trẻ điền nghề nghiệp và nơi ở không phải
 * là lười, mà là "điền vào rồi ai đọc được". Câu trả lời — khách chưa đăng nhập
 * không thấy bất cứ điều gì của người còn sống, kể cả tên — là một ranh giới
 * pháp lý cố định, không phải một mức có thể chỉnh. Vì vậy nó được in ra như
 * một lời khẳng định đứng ngoài ba nút bấm.
 */
export function PrivacySharingCard({ person, etag }: PrivacySharingCardProps) {
  const t = useTranslations("privacy");

  const server = isPresent(person.privacy) ? (person.privacy as PrivacySettings) : null;
  const canEdit = person.meta.isSelf === true && person.isAlive && server !== null;

  const mutation = useUpdatePerson(person.id);
  const [draft, setDraft] = useState<PrivacySettings | null>(server);

  // Bản nháp bám theo phản hồi máy chủ. `version` trong danh sách phụ thuộc là
  // thứ xoá được trạng thái "chưa lưu" sau một lần ghi thành công; thiếu nó,
  // nút Lưu ở lại mãi.
  useEffect(() => {
    if (server) setDraft(server);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- so theo phiên bản máy chủ, không theo tham chiếu object mới mỗi lần render.
  }, [person.version, person.id]);

  const changed = useMemo<Partial<PrivacySettings>>(() => {
    if (!server || !draft) return {};
    const diff: Partial<PrivacySettings> = {};
    for (const group of PRIVACY_GROUPS) {
      if (draft[group] !== server[group]) diff[group] = draft[group];
    }
    return diff;
  }, [server, draft]);

  if (!canEdit || !draft || !server) return null;

  const dirty = Object.keys(changed).length > 0;
  const conflict =
    mutation.error instanceof ApiError && mutation.error.code === "OPTIMISTIC_LOCK_CONFLICT";

  const filledIn: Record<PrivacyGroup, boolean> = {
    occupation: isPresent(person.occupation),
    residenceProvince: isPresent(person.currentPlaceProvince),
    residenceFull: isPresent(person.currentPlaceFull),
    contact: isPresent(person.contact),
    birthDetailAndPhoto: isPresent(person.birth) || isPresent(person.avatarUrl),
  };

  const save = () => {
    // CHỈ gửi công tắc vừa gạt: `privacy` là trường hợp nhất, nên gửi thừa
    // không sai nhưng gửi thiếu cũng không mất gì — và bản vá nhỏ là bản vá
    // đọc được trong nhật ký kiểm toán.
    mutation.mutate({ input: { privacy: changed }, ifMatch: etag ?? '"v1"' });
  };

  return (
    <section
      aria-labelledby="privacy-sharing-title"
      data-privacy-sharing="self"
      className="rounded-lg border border-border bg-bg-card px-4 py-4 sm:px-5 sm:py-5"
    >
      <h2
        id="privacy-sharing-title"
        className="m-0 flex items-center gap-2 font-serif text-lg font-semibold text-primary"
      >
        <SafetyOutlined aria-hidden />
        {t("sharing.title")}
      </h2>

      <p className="m-0 mt-2 text-than leading-relaxed text-text-main">{t("sharing.why")}</p>

      <p className="m-0 mt-2">
        <Link
          href="/danh-ba"
          className="inline-flex min-h-11 items-center gap-2 text-than underline"
        >
          <TeamOutlined aria-hidden />
          {t("sharing.directoryLink")}
        </Link>
      </p>

      {/* Lời khẳng định về khách: đứng riêng, không nằm lẫn vào ba nút bấm, vì
          nó KHÔNG phải một mức người dùng chỉnh được. */}
      <p
        className="m-0 mt-3 rounded-lg border px-3 py-2 text-than leading-relaxed"
        style={{
          background: colorVars.successBg,
          borderColor: colorVars.border,
          color: colorVars.textMain,
        }}
      >
        {t("sharing.guestNever")}
      </p>

      {/* Và cái giá, cùng chỗ, cùng cỡ chữ: Hội đồng Tộc biểu đọc được cả
          những nhóm đang ở Riêng tư. Người dùng phải biết TRƯỚC khi chọn. */}
      <p
        className="m-0 mt-2 rounded-lg border px-3 py-2 text-than leading-relaxed"
        style={{
          background: colorVars.warningBg,
          borderColor: colorVars.borderDark,
          color: colorVars.textMain,
        }}
      >
        {t("sharing.councilAlwaysReads")}
      </p>

      <div className="mt-4">
        {PRIVACY_GROUPS.map((group) => (
          <PrivacyGroupRow
            key={group}
            group={group}
            value={draft[group]}
            filledIn={filledIn[group]}
            editHref={`/persons/${person.id}/edit`}
            disabled={mutation.isPending}
            onChange={(next: ShareScope) => setDraft({ ...draft, [group]: next })}
          />
        ))}
      </div>

      {conflict && (
        <Alert className="!mt-4" type="warning" showIcon message={t("sharing.conflict")} />
      )}
      {mutation.isError && !conflict && (
        <Alert className="!mt-4" type="error" showIcon message={t("sharing.saveError")} />
      )}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Button type="primary" disabled={!dirty} loading={mutation.isPending} onClick={save}>
          {t("sharing.save")}
        </Button>
        {/* `role="status"` chứ không phải một dòng chữ câm: người dùng bàn phím
            và trình đọc màn hình phải biết là đã lưu xong, chứ không chỉ thấy
            nút mờ đi. */}
        <p className="m-0 text-than text-text-muted" role="status" aria-live="polite">
          {dirty
            ? t("sharing.unsaved")
            : mutation.isSuccess
              ? t("sharing.saved")
              : t("sharing.upToDate")}
        </p>
      </div>

      <PrivacyAudiencePreview settings={draft} />
    </section>
  );
}

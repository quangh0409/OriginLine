"use client";

import { useId, useState } from "react";
import { Alert, Button, Input, Skeleton } from "antd";
import { SendOutlined, WarningFilled } from "@ant-design/icons";
import { useFormatter, useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { colorVars } from "@/styles/tokens";
import { passwordResetFailureOf } from "@/lib/api/password-reset";
import type { PersonSummaryDto } from "@/types/api";
import { OneTimeCode } from "./one-time-code";
import { ScopedPersonPicker } from "./scoped-person-picker";
import { useIssuePasswordReset } from "./queries";

/**
 * **Cấp lại liên kết đặt mật khẩu** — ô thứ sáu của `/quan-ly`.
 *
 * <h2>Backend xong từ lâu; đây là lối vào đầu tiên</h2>
 * `POST /api/v1/password-resets` (`PasswordResetController`,
 * `PasswordResetGrantService`, migration `V21`) có 10 test tích hợp xanh
 * nhưng, cho tới màn này, không một dòng frontend nào gọi tới nó — Trưởng chi
 * không có nút nào để bấm, dù việc "cụ quên mật khẩu, gọi điện cho Trưởng
 * chi" là chuyện thật đã xảy ra.
 *
 * <h2>Vì sao KHÔNG gộp vào `InviteScreen`</h2>
 * Đây không phải một biến thể của lời mời: lời mời đưa một người MỚI vào phả,
 * còn ở đây người ấy ĐÃ có tài khoản và chỉ quên mật khẩu. Gộp chung một màn
 * sẽ khiến "phát lời mời" và "cấp lại mật khẩu" — hai việc có hậu quả khác hẳn
 * nhau — trông như hai tuỳ chọn ngang hàng của cùng một biểu mẫu.
 *
 * <h2>Đánh đổi trung tâm phải nói thẳng, không được giấu sau một nút bấm</h2>
 * Người cấp NHÌN THẤY liên kết, nên trong 30 phút họ đặt được mật khẩu của
 * người kia — đó là một quyền lực thật, không phải một chi tiết kỹ thuật.
 * Máy chủ không giấu điều đó đi mà làm cho nó KHÔNG IM LẶNG: chính chủ được
 * báo trong cùng transaction. Màn hình phải nói ra cả hai vế, trước khi bấm
 * (cảnh báo) và sau khi cấp (đã báo cho ai).
 */
export function PasswordResetScreen() {
  const t = useTranslations("membership");
  const meQuery = useMe();
  const me = meQuery.data;

  if (meQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (!me?.appUserId) {
    return <Alert type="info" showIcon message={t("errors.notProvisioned")} />;
  }

  const laHoiDong = me.role === "ADMIN" || me.role === "COUNCIL";
  const laTruongChi = me.role === "BRANCH_HEAD";

  if (!laHoiDong && !laTruongChi) {
    return <Alert type="info" showIcon message={t("passwordReset.errors.noIssueRight")} />;
  }

  if (laTruongChi && me.managedBranches.length === 0) {
    return <Alert type="warning" showIcon message={t("passwordReset.errors.noScope")} />;
  }

  return <PasswordResetForm />;
}

function PasswordResetForm() {
  const t = useTranslations("membership");
  const format = useFormatter();
  const issue = useIssuePasswordReset();

  const noteId = useId();

  const [personId, setPersonId] = useState<string | undefined>(undefined);
  const [picked, setPicked] = useState<PersonSummaryDto | undefined>(undefined);
  const [note, setNote] = useState("");
  const [thieuNguoi, setThieuNguoi] = useState(false);

  const cap = async () => {
    if (!personId) {
      setThieuNguoi(true);
      return;
    }
    await issue
      .mutateAsync({ personId, note: note.trim() || undefined })
      .catch(() => undefined);
  };

  const vuaCap = issue.data;

  if (vuaCap) {
    const tenNguoiDuocCap = vuaCap.grant.target?.displayName ?? picked?.displayName;
    return (
      <section aria-labelledby="cap-lai-mat-khau-ket-qua">
        <h2
          id="cap-lai-mat-khau-ket-qua"
          className="m-0 mb-3 font-serif text-de font-bold text-text-main"
        >
          {t("passwordReset.issuedTitle")}
        </h2>

        {/* Vế thứ nhất của đánh đổi: đây là một quyền lực thật, phải nói TRƯỚC
            khi trao liên kết đi, không phải sau. */}
        <Alert
          className="!mb-3"
          type="warning"
          showIcon
          icon={<WarningFilled aria-hidden />}
          message={t("passwordReset.powerWarningTitle")}
          description={t("passwordReset.powerWarningBody")}
        />

        <OneTimeCode
          code={vuaCap.url}
          caption={
            tenNguoiDuocCap
              ? t("passwordReset.linkFor", { name: tenNguoiDuocCap })
              : undefined
          }
          hint={t("passwordReset.linkHint")}
        />

        <p className="m-0 mt-3 max-w-prose text-than text-text-main">
          {t("passwordReset.expiresAt", {
            at: format.dateTime(new Date(vuaCap.expiresAt), {
              dateStyle: "medium",
              timeStyle: "short",
            }),
          })}
        </p>

        {/* Vế thứ hai của đánh đổi: máy chủ không giấu việc này — chính chủ
            đã được báo, trong cùng transaction với lượt cấp. */}
        <p className="m-0 mt-2 max-w-prose text-than font-semibold text-text-main">
          {t("passwordReset.selfNotified")}
        </p>

        <Button
          className="!mt-4"
          size="large"
          onClick={() => {
            issue.reset();
            setPersonId(undefined);
            setPicked(undefined);
            setNote("");
          }}
        >
          {t("code.done")}
        </Button>
      </section>
    );
  }

  return (
    <section aria-labelledby="cap-lai-mat-khau-tieu-de">
      <h2 id="cap-lai-mat-khau-tieu-de" className="sr-only">
        {t("passwordReset.issueTitle")}
      </h2>

      <Alert
        className="!mb-4"
        type="info"
        showIcon
        message={t("passwordReset.powerWarningTitle")}
        description={t("passwordReset.powerWarningBody")}
      />

      <div className="rounded-lg border border-border bg-bg-card px-4 py-3">
        {issue.error && (
          <Alert
            className="!mb-3"
            type="error"
            showIcon
            message={t(`passwordReset.errors.${passwordResetFailureOf(issue.error)}`)}
          />
        )}

        <div className="flex flex-col gap-3">
          <div>
            <ScopedPersonPicker
              id="chon-nhan-khau-quen-mat-khau"
              label={t("passwordReset.personLabel")}
              placeholder={t("passwordReset.personPlaceholder")}
              value={personId}
              seed={picked}
              status={thieuNguoi && !personId ? "error" : undefined}
              onChange={(id, person) => {
                setPersonId(id);
                setPicked(person);
                if (id) setThieuNguoi(false);
              }}
            />
            {thieuNguoi && !personId && (
              <p role="alert" className="m-0 mt-1 text-than" style={{ color: colorVars.danger }}>
                {t("passwordReset.personRequired")}
              </p>
            )}
            <p className="m-0 mt-1 text-than text-text-muted">{t("passwordReset.scopeHint")}</p>
          </div>

          <label className="block" htmlFor={noteId}>
            <span className="mb-1 block text-than font-medium text-text-main">
              {t("passwordReset.noteLabel")}
            </span>
            <Input
              id={noteId}
              size="large"
              value={note}
              maxLength={500}
              onChange={(e) => setNote(e.target.value)}
              placeholder={t("passwordReset.notePlaceholder")}
            />
          </label>

          <div>
            <Button
              type="primary"
              size="large"
              icon={<SendOutlined aria-hidden />}
              loading={issue.isPending}
              onClick={() => void cap()}
            >
              {t("passwordReset.issue")}
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}

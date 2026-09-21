"use client";

import { useId, useState } from "react";
import { Alert, Button, Empty, Input, InputNumber, Skeleton } from "antd";
import { SendOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { PersonPicker } from "@/components/person/person-picker";
import { colorVars } from "@/styles/tokens";
import { invitationFailureOf } from "@/lib/api/invitation";
import type { PersonSummaryDto } from "@/types/api";
import { InvitationRow } from "./invitation-row";
import { OneTimeCode } from "./one-time-code";
import { useInvitations, useIssueInvitation } from "./queries";

/** Mặc định của backend là 7 ngày; biểu mẫu nói ra con số ấy thay vì để trống. */
const HAN_MAC_DINH_NGAY = 7;

/**
 * **Nửa Trưởng chi** của màn phát mã: lời mời cá nhân cho một cụ cụ thể.
 *
 * <h2>Vì sao nửa này tồn tại song song với mã dòng họ</h2>
 * design 07 §1.1: mã cá nhân dành cho **các cụ lớn tuổi** — họ sẽ không tự đăng
 * ký rồi tự tìm mình trên phả đồ giữa 1.500 người; Trưởng chi làm hộ từ đầu tới
 * cuối. Mã dòng họ dành cho người trẻ và người ở xa. Hai cơ chế, hai nhóm
 * người, không thay nhau được.
 *
 * <h2>Backend đã xong, giao diện là thứ còn thiếu</h2>
 * `POST/GET/DELETE /api/v1/invitations` đã dựng và đã kiểm chạy thật, nhưng
 * **không màn hình nào gọi tới**, nên tới hôm nay chỉ phát được bằng dòng lệnh.
 * Panel này không thêm một lớp API nào — nó gọi thẳng `invitationApi`.
 *
 * <h2>Mã hiện một lần, ngay cạnh tên người được mời</h2>
 * Trưởng chi đọc mã qua điện thoại hoặc dán vào Zalo. Nếu mã hiện ra mà không
 * kèm tên thì trong một buổi phát mười mã, mã thứ bảy rất dễ đi nhầm người —
 * và một mã cá nhân đi nhầm người là một tài khoản gắn nhầm hồ sơ.
 */
export function PersonalInvitePanel() {
  const t = useTranslations("membership");
  const invitations = useInvitations();
  const issue = useIssueInvitation();

  const ttlId = useId();
  const noteId = useId();

  const [personId, setPersonId] = useState<string | undefined>(undefined);
  const [picked, setPicked] = useState<PersonSummaryDto | undefined>(undefined);
  const [ttlDays, setTtlDays] = useState<number | null>(HAN_MAC_DINH_NGAY);
  const [note, setNote] = useState("");
  const [thieuNguoi, setThieuNguoi] = useState(false);

  const phat = async () => {
    if (!personId) {
      setThieuNguoi(true);
      return;
    }
    await issue
      .mutateAsync({
        personId,
        ttlDays: ttlDays ?? undefined,
        note: note.trim() || undefined,
      })
      .catch(() => undefined);
  };

  const vuaPhat = issue.data;

  return (
    <section aria-labelledby="loi-moi-ca-nhan-tieu-de">
      <h2
        id="loi-moi-ca-nhan-tieu-de"
        className="m-0 mb-1 font-serif text-de font-bold text-text-main"
      >
        {t("personal.title")}
      </h2>
      <p className="m-0 mb-3 text-than text-text-muted">{t("personal.lead")}</p>

      {vuaPhat && (
        <div className="mb-4">
          <OneTimeCode
            code={vuaPhat.code}
            caption={
              picked?.displayName
                ? t("personal.codeFor", { name: picked.displayName })
                : undefined
            }
          />
          <Button
            className="!mt-2"
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
        </div>
      )}

      {!vuaPhat && (
        <div className="mb-4 rounded-lg border border-border bg-bg-card px-4 py-3">
          <h3 className="m-0 mb-2 font-serif text-than font-semibold text-text-main">
            {t("personal.issueTitle")}
          </h3>

          {issue.error && (
            <Alert
              className="!mb-3"
              type="error"
              showIcon
              message={t(`personal.errors.${invitationFailureOf(issue.error)}`)}
            />
          )}

          <div className="flex flex-col gap-3">
            <div>
              <PersonPicker
                id="chon-nhan-khau-moi"
                label={t("personal.personLabel")}
                placeholder={t("personal.personPlaceholder")}
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
                  {t("personal.personRequired")}
                </p>
              )}
              <p className="m-0 mt-1 text-than text-text-muted">{t("personal.scopeHint")}</p>
            </div>

            <label className="block" htmlFor={ttlId}>
              <span className="mb-1 block text-than font-medium text-text-main">
                {t("personal.ttlLabel")}
              </span>
              <InputNumber
                id={ttlId}
                size="large"
                className="w-full sm:!w-48"
                min={1}
                max={30}
                value={ttlDays}
                onChange={setTtlDays}
              />
            </label>

            <label className="block" htmlFor={noteId}>
              <span className="mb-1 block text-than font-medium text-text-main">
                {t("personal.noteLabel")}
              </span>
              <Input
                id={noteId}
                size="large"
                value={note}
                maxLength={500}
                onChange={(e) => setNote(e.target.value)}
                placeholder={t("personal.notePlaceholder")}
              />
            </label>

            <div>
              <Button
                type="primary"
                size="large"
                icon={<SendOutlined aria-hidden />}
                loading={issue.isPending}
                onClick={() => void phat()}
              >
                {t("personal.issue")}
              </Button>
            </div>
          </div>
        </div>
      )}

      <h3 className="m-0 mb-2 font-serif text-than font-semibold text-text-main">
        {t("personal.listTitle")}
      </h3>

      {invitations.isPending && <Skeleton active paragraph={{ rows: 3 }} />}

      {invitations.error && (
        <Alert
          type="error"
          showIcon
          message={t(`personal.errors.${invitationFailureOf(invitations.error)}`)}
        />
      )}

      {!invitations.isPending && !invitations.error && (invitations.data?.length ?? 0) === 0 && (
        <Empty
          description={<span style={{ color: colorVars.textMuted }}>{t("personal.empty")}</span>}
        />
      )}

      <div className="flex flex-col gap-3">
        {invitations.data?.map((invitation) => (
          <InvitationRow key={invitation.id} invitation={invitation} />
        ))}
      </div>
    </section>
  );
}

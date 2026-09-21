"use client";

import { useState } from "react";
import { Alert, App, Button, Input, Modal, Tag } from "antd";
import { StopOutlined } from "@ant-design/icons";
import { useFormatter, useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import { clanInviteFailureOf } from "@/lib/api/membership-admin";
import type { InvitationDto, InvitationUsability } from "@/lib/api/invitation";
import { useRevokeInvitation } from "./queries";

export interface InvitationRowProps {
  invitation: InvitationDto;
}

const MAU_TRANG_THAI: Record<InvitationUsability, string> = {
  USABLE: colorVars.success,
  EXPIRED: colorVars.textMuted,
  ALREADY_USED: colorVars.secondary,
  REVOKED: colorVars.danger,
};

/**
 * Một lời mời cá nhân đã phát.
 *
 * <h2>Tên người được mời đi THẲNG trong `InvitationDto.invitee`</h2>
 * Cho tới gần đây `InvitationDto` chỉ mang `personId`, nên hàng này gọi thêm
 * `GET /persons/&#123;id&#125;` một lượt cho **mỗi dòng** để có một cái tên đọc
 * được — một bài toán N+1 sinh ra chỉ vì thiếu ba trường, và nó là cách chống
 * đỡ chứ không phải thiết kế. Contract nay trả khối `InviteeSummary`
 * (`displayName`, `generation`, `branchName`), nên lượt gọi ấy **đã gỡ**.
 *
 * <h2>Ba trường, và đúng ba</h2>
 * Không năm sinh, không nghề nghiệp, không nơi ở, không điện thoại, không ảnh —
 * cùng ranh giới mà màn nhận lời mời đã đặt. Đừng "tiện tay" đọc thêm hồ sơ đầy
 * đủ ở đây: danh sách này là một bề mặt quản trị, không phải một danh bạ.
 *
 * Khối `invitee` **có thể vắng**; khi ấy hàng vẫn dùng được, vì `personId` mới
 * là thứ thu hồi cần.
 */
export function InvitationRow({ invitation }: InvitationRowProps) {
  const t = useTranslations("membership");
  const format = useFormatter();
  const { message } = App.useApp();
  const revoke = useRevokeInvitation();
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState("");

  const invitee = invitation.invitee;
  const phuChu = [
    isPresent(invitee?.generation) ? t("card.generation", { n: invitee?.generation }) : null,
    invitee?.branchName ?? null,
  ]
    .filter((x): x is string => Boolean(x))
    .join(" · ");

  const conThuHoiDuoc = invitation.usability === "USABLE";

  const submit = async () => {
    try {
      await revoke.mutateAsync({ id: invitation.id, reason: reason.trim() || undefined });
      message.success(t("personal.revoked"));
      setConfirming(false);
      setReason("");
    } catch {
      // Lỗi hiện trong hộp thoại.
    }
  };

  return (
    <article
      data-testid="loi-moi-ca-nhan"
      className="rounded-lg border border-border bg-bg-card px-4 py-3"
    >
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h4 className="m-0 font-serif text-than font-semibold text-text-main">
            {invitee?.displayName ?? t("personal.unknownPerson")}
          </h4>
          {/* Đời và chi chỉ hiện khi máy chủ gửi — không vẽ một ô trống, vì ô
              trống gợi ý rằng có dữ liệu đang bị giấu. */}
          {phuChu && <p className="m-0 mt-0.5 text-than text-text-muted">{phuChu}</p>}
          <p className="m-0 mt-0.5 text-than text-text-muted">
            {t("personal.expiresAt", {
              at: format.dateTime(new Date(invitation.expiresAt), { dateStyle: "medium" }),
            })}
          </p>
          {invitation.note && (
            <p className="m-0 mt-0.5 text-than text-text-main">{invitation.note}</p>
          )}
        </div>
        <Tag
          bordered={false}
          className="!m-0 !text-than"
          style={{
            color: colorVars.bgPage,
            background: MAU_TRANG_THAI[invitation.usability],
          }}
        >
          {t(`personal.usability.${invitation.usability}`)}
        </Tag>
      </header>

      {conThuHoiDuoc && (
        <div className="mt-2">
          <Button
            danger
            size="large"
            icon={<StopOutlined aria-hidden />}
            onClick={() => setConfirming(true)}
          >
            {t("personal.revoke")}
          </Button>
        </div>
      )}

      <Modal
        open={confirming}
        title={t("personal.revokeTitle")}
        okText={t("personal.revoke")}
        cancelText={t("common.cancel")}
        okButtonProps={{ danger: true, size: "large" }}
        cancelButtonProps={{ size: "large" }}
        confirmLoading={revoke.isPending}
        onCancel={() => setConfirming(false)}
        onOk={() => void submit()}
        destroyOnClose
      >
        {revoke.error && (
          <Alert
            className="!mb-3"
            type="error"
            showIcon
            message={t(`clan.errors.${clanInviteFailureOf(revoke.error)}`)}
          />
        )}
        <p className="m-0 mb-2 text-than text-text-main">{t("personal.revokeLead")}</p>
        <label
          className="block text-than font-medium text-text-main"
          htmlFor={`ly-do-moi-${invitation.id}`}
        >
          {t("clan.revokeReasonLabel")}
        </label>
        <Input.TextArea
          id={`ly-do-moi-${invitation.id}`}
          rows={3}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t("personal.revokeReasonPlaceholder")}
        />
      </Modal>
    </article>
  );
}

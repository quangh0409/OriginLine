"use client";

import { useState } from "react";
import { Alert, App, Button, Input, Modal, Tag } from "antd";
import { StopOutlined } from "@ant-design/icons";
import { useFormatter, useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import {
  clanInviteFailureOf,
  type ClanInviteUsability,
  type ClanInviteView,
} from "@/lib/api/membership-admin";
import { useRevokeClanInvite } from "./queries";

export interface ClanCodeCardProps {
  code: ClanInviteView;
  /**
   * Số người **đang sống** trong cả dòng họ — **mẫu số** của bộ đếm.
   *
   * Nó **không thuộc về mã nào**, nên nó đi vào thẻ như một tham số chứ không
   * như một trường của {@link ClanInviteView}: contract trả nó **một lần** trên
   * phong bì của `GET /clan-invites`. Gắn vào từng phần tử là lặp một giá trị
   * toàn cục lên n dòng rồi để chúng có cơ hội lệch nhau.
   */
  clanLivingPersonCount: number;
}

/** Bốn giá trị, bốn nghĩa, không gộp. */
const MAU_TRANG_THAI: Record<ClanInviteUsability, string> = {
  USABLE: colorVars.success,
  EXPIRED: colorVars.textMuted,
  EXHAUSTED: colorVars.accentText,
  REVOKED: colorVars.danger,
};

/**
 * Một mã mời dòng họ trong danh sách của Hội đồng.
 *
 * <h2>Bộ đếm là thứ to nhất trong thẻ, và đó là cả điểm của màn hình</h2>
 * design 07 §1.2 gọi đếm lượt dùng là "chốt quan trọng nhất và dễ bị bỏ qua
 * nhất": <i>"Hội đồng thấy mã đã dùng 400 lần trong khi dòng họ có 600 người
 * thì biết mà thu hồi. Không có bộ đếm thì không ai phát hiện được gì — mã rò
 * ra và mọi thứ trông vẫn bình thường."</i>
 *
 * Vì vậy nó không nằm trong một hàng bảng mà là con số lớn nhất trên thẻ, kèm
 * ngay bên cạnh **mẫu số** để đối chiếu. Mẫu số ấy nay là bắt buộc ở contract
 * (`ClanInviteList.clanLivingPersonCount`), nên nhánh "thiếu thì ẩn dòng đối
 * chiếu" đã gỡ — một bộ đếm không có mẫu số thì không ai phán xét được.
 *
 * <h2>Hiển thị `usability`, không phải `status`</h2>
 * `ACTIVE` của một mã đã quá hạn hoặc đã hết lượt không nói lên điều gì cho
 * người đang nhìn, và đúng ở chỗ ấy Hội đồng sẽ tưởng cửa còn mở (hoặc còn
 * đóng) sai chiều.
 *
 * <h2>Mã đã thu hồi vẫn ở lại danh sách, kèm bộ đếm của nó</h2>
 * Con số ấy chính là bằng chứng rằng việc thu hồi là đúng, và là thứ Hội đồng
 * đối chiếu lần sau.
 */
export function ClanCodeCard({ code, clanLivingPersonCount }: ClanCodeCardProps) {
  const t = useTranslations("membership");
  const format = useFormatter();
  const { message } = App.useApp();
  const revoke = useRevokeClanInvite();
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState("");

  const conThuHoiDuoc = code.status === "ACTIVE";

  const submitRevoke = async () => {
    try {
      await revoke.mutateAsync({ id: code.id, reason: reason.trim() || undefined });
      message.success(t("clan.revoked"));
      setConfirming(false);
      setReason("");
    } catch {
      // Lỗi hiện trong hộp thoại; giữ nguyên lý do vừa gõ.
    }
  };

  return (
    <article
      data-testid="ma-dong-ho"
      className="rounded-lg border border-border bg-bg-card px-4 py-3"
    >
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="m-0 font-serif text-de font-semibold text-text-main">
            {code.label?.trim() || t("clan.untitled")}
          </h3>
          {/* `createdAt` là **tuỳ chọn** ở contract. Vắng thì bỏ hẳn dòng — in
              ra "Invalid Date" tệ hơn mọi cách khác. */}
          {code.createdAt && (
            <p className="m-0 mt-0.5 text-than text-text-muted">
              {t("clan.issuedAt", {
                at: format.dateTime(new Date(code.createdAt), { dateStyle: "medium" }),
              })}
            </p>
          )}
        </div>
        <Tag
          bordered={false}
          className="!m-0 !text-than"
          style={{ color: colorVars.bgPage, background: MAU_TRANG_THAI[code.usability] }}
        >
          {t(`clan.usability.${code.usability}`)}
        </Tag>
      </header>

      {/* ── Bộ đếm lượt dùng — chốt 3 ────────────────────────────────────── */}
      <div
        className="mt-3 rounded border p-3"
        style={{ borderColor: colorVars.borderDark, background: colorVars.bgPage }}
      >
        <p className="m-0 text-than font-medium uppercase tracking-wide text-text-muted">
          {t("clan.useCountLabel")}
        </p>
        <p className="m-0 flex flex-wrap items-baseline gap-x-2">
          <span
            data-testid="bo-dem-luot-dung"
            className="font-serif text-[34px] font-bold leading-none text-text-main"
          >
            {format.number(code.useCount)}
          </span>
          <span className="text-dan text-text-muted">
            {typeof code.maxUses === "number"
              ? t("clan.useCountOfMax", { max: code.maxUses })
              : t("clan.useCountUnit")}
          </span>
        </p>

        <p data-testid="doi-chieu-nhan-khau" className="m-0 mt-1 text-than text-text-main">
          {t("clan.compareToClan", {
            used: code.useCount,
            living: clanLivingPersonCount,
          })}
        </p>

        {typeof code.maxUses !== "number" && code.status === "ACTIVE" && (
          <p className="m-0 mt-1 text-than" style={{ color: colorVars.accentText }}>
            {t("clan.noMaxWarning")}
          </p>
        )}

        <dl className="m-0 mt-2 grid grid-cols-1 gap-x-4 gap-y-1 text-than sm:grid-cols-2">
          <div className="flex flex-wrap gap-x-2">
            <dt className="text-text-muted">{t("clan.expiresAt")}</dt>
            <dd className="m-0 text-text-main">
              {format.dateTime(new Date(code.expiresAt), {
                dateStyle: "medium",
                timeStyle: "short",
              })}
            </dd>
          </div>
          {typeof code.remainingUses === "number" && (
            <div className="flex flex-wrap gap-x-2">
              <dt className="text-text-muted">{t("clan.remainingLabel")}</dt>
              <dd className="m-0 text-text-main">
                {t("clan.remainingValue", { n: code.remainingUses })}
              </dd>
            </div>
          )}
        </dl>
      </div>

      {code.note?.trim() && (
        <p className="m-0 mt-2 text-than text-text-main">{code.note}</p>
      )}

      {code.revokedAt && (
        <p className="m-0 mt-2 text-than text-text-muted">
          {code.revokedReason?.trim()
            ? t("clan.revokedWithReason", {
                at: format.dateTime(new Date(code.revokedAt), { dateStyle: "medium" }),
                reason: code.revokedReason,
              })
            : t("clan.revokedAt", {
                at: format.dateTime(new Date(code.revokedAt), { dateStyle: "medium" }),
              })}
        </p>
      )}

      {conThuHoiDuoc && (
        <div className="mt-3">
          <Button
            danger
            size="large"
            icon={<StopOutlined aria-hidden />}
            onClick={() => setConfirming(true)}
          >
            {t("clan.revoke")}
          </Button>
        </div>
      )}

      <Modal
        open={confirming}
        title={t("clan.revokeTitle")}
        okText={t("clan.revoke")}
        cancelText={t("common.cancel")}
        okButtonProps={{ danger: true, size: "large" }}
        cancelButtonProps={{ size: "large" }}
        confirmLoading={revoke.isPending}
        onCancel={() => setConfirming(false)}
        onOk={() => void submitRevoke()}
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
        <p className="m-0 mb-2 text-than text-text-main">{t("clan.revokeLead")}</p>
        <label className="block text-than font-medium text-text-main" htmlFor={`ly-do-${code.id}`}>
          {t("clan.revokeReasonLabel")}
        </label>
        <Input.TextArea
          id={`ly-do-${code.id}`}
          rows={3}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t("clan.revokeReasonPlaceholder")}
        />
      </Modal>
    </article>
  );
}

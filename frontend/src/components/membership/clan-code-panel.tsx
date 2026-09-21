"use client";

import { useId, useState } from "react";
import { Alert, Button, Empty, InputNumber, Input, Skeleton } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import { clanInviteFailureOf } from "@/lib/api/membership-admin";
import { ClanCodeCard } from "./clan-code-card";
import { OneTimeCode } from "./one-time-code";
import { useClanInvites, useIssueClanInvite } from "./queries";

/** Mặc định của biểu mẫu phát mã. Cả hai là **đề nghị**, Hội đồng sửa được. */
const HAN_MAC_DINH_NGAY = 30;
const TRAN_MAC_DINH = 200;

/**
 * **Nửa Hội đồng** của màn phát mã: mã mời dòng họ — phát, thu hồi, và bộ đếm.
 *
 * <h2>Biểu mẫu điền sẵn hạn và trần lượt dùng, có chủ ý</h2>
 * design 07 §1.2 xếp "có hạn dùng" và "đếm lượt dùng" vào bốn chốt bắt buộc.
 * Một biểu mẫu để trống hai ô ấy sẽ được bấm qua, và kết quả là một mã vĩnh
 * viễn không trần — đúng thứ mà bốn chốt sinh ra để chặn. Nên chúng được **điền
 * sẵn** chứ không chỉ "cho phép": Hội đồng vẫn sửa hoặc xoá trần được, nhưng
 * phải chủ động làm thế, và khi ấy thẻ mã sẽ nói thẳng là mã không có trần.
 *
 * <h2>Mã hiện đúng một lần, ngay tại chỗ phát</h2>
 * CSDL chỉ lưu băm. Khối {@link OneTimeCode} ở lại trên màn cho tới khi Hội
 * đồng chủ động đóng, chứ không tự biến mất sau một nhịp — một thông báo trôi
 * qua trong ba giây là cách mất mã.
 */
export function ClanCodePanel() {
  const t = useTranslations("membership");
  const codes = useClanInvites();
  const issue = useIssueClanInvite();

  const ttlId = useId();
  const maxUsesId = useId();
  const labelId = useId();

  const [ttlDays, setTtlDays] = useState<number | null>(HAN_MAC_DINH_NGAY);
  const [maxUses, setMaxUses] = useState<number | null>(TRAN_MAC_DINH);
  const [label, setLabel] = useState("");

  const phat = async () => {
    await issue
      .mutateAsync({
        ttlDays: ttlDays ?? undefined,
        maxUses: maxUses ?? undefined,
        label: label.trim() || undefined,
      })
      .catch(() => undefined);
  };

  const vuaPhat = issue.data;

  /**
   * `GET /clan-invites` trả một **phong bì** `{ invites, clanLivingPersonCount }`,
   * không phải mảng trần — và đó là chỗ duy nhất trong nhóm này làm thế. Mẫu số
   * không thuộc về mã nào, nên nó được đọc ở đây rồi truyền xuống từng thẻ.
   */
  const danhSach = codes.data?.invites ?? [];
  const soNguoiDangSong = codes.data?.clanLivingPersonCount;

  return (
    <section aria-labelledby="ma-dong-ho-tieu-de">
      <h2
        id="ma-dong-ho-tieu-de"
        className="m-0 mb-1 font-serif text-de font-bold text-text-main"
      >
        {t("clan.title")}
      </h2>
      <p className="m-0 mb-3 text-than text-text-muted">{t("clan.lead")}</p>

      {vuaPhat && (
        <div className="mb-4">
          <OneTimeCode code={vuaPhat.code} caption={vuaPhat.invite.label ?? undefined} />
          <Button
            className="!mt-2"
            size="large"
            onClick={() => {
              issue.reset();
              setLabel("");
            }}
          >
            {t("code.done")}
          </Button>
        </div>
      )}

      {!vuaPhat && (
        <div className="mb-4 rounded-lg border border-border bg-bg-card px-4 py-3">
          <h3 className="m-0 mb-2 font-serif text-than font-semibold text-text-main">
            {t("clan.issueTitle")}
          </h3>

          {issue.error && (
            <Alert
              className="!mb-3"
              type="error"
              showIcon
              message={t(`clan.errors.${clanInviteFailureOf(issue.error)}`)}
            />
          )}

          <div className="flex flex-col gap-3">
            <label className="block" htmlFor={labelId}>
              <span className="mb-1 block text-than font-medium text-text-main">
                {t("clan.labelLabel")}
              </span>
              <Input
                id={labelId}
                size="large"
                value={label}
                maxLength={160}
                onChange={(e) => setLabel(e.target.value)}
                placeholder={t("clan.labelPlaceholder")}
              />
            </label>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="block" htmlFor={ttlId}>
                <span className="mb-1 block text-than font-medium text-text-main">
                  {t("clan.ttlLabel")}
                </span>
                <InputNumber
                  id={ttlId}
                  size="large"
                  className="w-full"
                  min={1}
                  max={90}
                  value={ttlDays}
                  onChange={setTtlDays}
                />
              </label>

              <label className="block" htmlFor={maxUsesId}>
                <span className="mb-1 block text-than font-medium text-text-main">
                  {t("clan.maxUsesLabel")}
                </span>
                <InputNumber
                  id={maxUsesId}
                  size="large"
                  className="w-full"
                  min={1}
                  max={10_000}
                  value={maxUses}
                  onChange={setMaxUses}
                  placeholder={t("clan.maxUsesPlaceholder")}
                />
                <span className="mt-1 block text-than text-text-muted">
                  {t("clan.maxUsesHint")}
                </span>
              </label>
            </div>

            <div>
              <Button
                type="primary"
                size="large"
                icon={<PlusOutlined aria-hidden />}
                loading={issue.isPending}
                onClick={() => void phat()}
              >
                {t("clan.issue")}
              </Button>
            </div>
          </div>
        </div>
      )}

      <h3 className="m-0 mb-2 font-serif text-than font-semibold text-text-main">
        {t("clan.listTitle")}
      </h3>

      {codes.isPending && <Skeleton active paragraph={{ rows: 4 }} />}

      {codes.error && (
        <Alert
          type="error"
          showIcon
          message={t(`clan.errors.${clanInviteFailureOf(codes.error)}`)}
        />
      )}

      {!codes.isPending && !codes.error && danhSach.length === 0 && (
        <Empty
          description={<span style={{ color: colorVars.textMuted }}>{t("clan.empty")}</span>}
        />
      )}

      <div className="flex flex-col gap-3">
        {typeof soNguoiDangSong === "number" &&
          danhSach.map((code) => (
            <ClanCodeCard
              key={code.id}
              code={code}
              clanLivingPersonCount={soNguoiDangSong}
            />
          ))}
      </div>
    </section>
  );
}

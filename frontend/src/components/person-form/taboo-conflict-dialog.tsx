"use client";

import { useState } from "react";
import { Alert, Button, Input, Modal, Tag } from "antd";
import { WarningFilled } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import type { TabooConflict } from "@/types/api";
import { ConflictParty } from "./conflict-party";

export interface TabooConflictDialogProps {
  conflicts: TabooConflict[] | null;
  submitting: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}

const REASON_MIN = 5;

/**
 * Kỵ húy warning (FR-1.6).
 *
 * Naming a descendant after an ancestor's tên húy is a serious breach in
 * Vietnamese practice, so this is a deliberate, explained stop — not a toast
 * and not a silent rejection. It must answer three questions before the user
 * can proceed:
 *
 *   WHO   — hydrated from `GET /persons/{ancestorPersonId}`, NOT from the 409
 *   WHERE — the đời thứ on that same hydrated profile
 *   HOW   — `matchKind`: an EXACT collision is far graver than an UNACCENTED
 *           one (which can be pure coincidence, e.g. Hòa vs Hoà), and the
 *           council has not yet decided whether unaccented matches should even
 *           warn (contracts/README §6.5). Labelling the kind lets a user judge.
 *
 * WHO and WHERE used to arrive inside the 409 as `ancestorDisplayName` /
 * `ancestorGeneration` / `tabooName` / `relationHint`. All four are gone on
 * purpose: the kỵ húy query picks ancestors by **đời thứ alone** — not by
 * living/deceased, not by chi — so the "ancestor" can be a great-uncle still
 * alive in another branch whom the person adding this record may not know
 * anything about. See {@link ConflictParty} for the full reasoning and for why
 * its `404` branch is a NORMAL case rather than a failure.
 *
 * `matchKind` survives because it describes **the user's own input**, never a
 * value read from the tree.
 *
 * Overriding requires a typed reason. It travels to the backend as the
 * request `note` alongside `confirmTabooOverride: true` and lands in the audit
 * log, so a future reader can see the collision was noticed and accepted
 * rather than missed.
 */
export function TabooConflictDialog({
  conflicts,
  submitting,
  onCancel,
  onConfirm,
}: TabooConflictDialogProps) {
  const t = useTranslations("personForm");
  const [reason, setReason] = useState("");
  const open = Boolean(conflicts && conflicts.length > 0);
  const reasonTooShort = reason.trim().length < REASON_MIN;

  return (
    <Modal
      open={open}
      onCancel={() => {
        setReason("");
        onCancel();
      }}
      title={
        <span className="flex items-center gap-2">
          <WarningFilled style={{ color: colorVars.accentText }} />
          {t("taboo.title")}
        </span>
      }
      width={560}
      destroyOnClose
      footer={[
        <Button key="cancel" onClick={onCancel} disabled={submitting}>
          {t("taboo.rename")}
        </Button>,
        <Button
          key="confirm"
          danger
          type="primary"
          loading={submitting}
          disabled={reasonTooShort}
          onClick={() => onConfirm(reason)}
        >
          {t("taboo.confirm")}
        </Button>,
      ]}
    >
      <Alert
        type="warning"
        showIcon={false}
        className="!mb-3"
        message={t("taboo.lead", { count: conflicts?.length ?? 0 })}
        description={t("taboo.explain")}
      />

      <ul className="m-0 mb-4 list-none space-y-2 p-0">
        {(conflicts ?? []).map((conflict) => (
          <li
            key={`${conflict.ancestorPersonId}-${conflict.matchedNameType}-${conflict.matchKind}`}
            className="rounded border px-3 py-2"
            style={{ borderColor: colorVars.borderDark, background: colorVars.warningBg }}
            data-testid={`taboo-conflict-${conflict.ancestorPersonId}`}
          >
            {/* HOW — thuộc về ô người dùng vừa gõ, nên nó luôn hiện được, kể cả
                khi danh tính bậc trên bị bộ lọc riêng tư giữ lại. */}
            <Tag
              bordered={false}
              color={conflict.matchKind === "EXACT" ? colorVars.primary : colorVars.textMuted}
              className="!m-0 !mb-1"
            >
              {t(`taboo.matchKind.${conflict.matchKind}`)}
            </Tag>

            {/* WHO + WHERE — nạp theo khoá, qua đúng bộ lọc phân tầng riêng tư. */}
            <ConflictParty
              personId={conflict.ancestorPersonId}
              matchedNameType={conflict.matchedNameType}
              notVisible={{
                title: t("taboo.ancestorNotVisible.title"),
                body: t("taboo.ancestorNotVisible.body"),
                ask: t("taboo.ancestorNotVisible.ask"),
              }}
              loadFailedLabel={t("taboo.ancestorLoadFailed")}
              testId={`taboo-ancestor-${conflict.ancestorPersonId}`}
            />
          </li>
        ))}
      </ul>

      <label className="block">
        <span className="mb-1 block text-than font-medium text-text-main">
          {t("taboo.reasonLabel")}
        </span>
        <Input.TextArea
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          rows={3}
          maxLength={500}
          showCount
          placeholder={t("taboo.reasonPlaceholder")}
        />
        <span className="mt-1 block text-than text-text-muted">{t("taboo.reasonHint")}</span>
      </label>
    </Modal>
  );
}

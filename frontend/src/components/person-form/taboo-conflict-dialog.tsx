"use client";

import { useState } from "react";
import { Alert, Button, Input, Modal, Tag } from "antd";
import { WarningFilled } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { colorTokens } from "@/styles/tokens";
import type { TabooConflict } from "@/types/api";

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
 *   WHO   — `ancestorDisplayName`
 *   WHERE — `ancestorGeneration` + `relationHint`, so "đời thứ 3, Chi Nhất"
 *           locates the ancestor in the clan rather than naming a stranger
 *   HOW   — `matchKind`: an EXACT collision is far graver than an UNACCENTED
 *           one (which can be pure coincidence, e.g. Hòa vs Hoà), and the
 *           council has not yet decided whether unaccented matches should even
 *           warn (contracts/README §6.5). Labelling the kind lets a user judge.
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
          <WarningFilled style={{ color: colorTokens.accent }} />
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
            key={`${conflict.ancestorPersonId}-${conflict.tabooName}`}
            className="rounded border px-3 py-2"
            style={{ borderColor: colorTokens.borderDark, background: colorTokens.warningBg }}
          >
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="font-serif text-[15px] font-semibold text-text-main">
                {conflict.ancestorDisplayName ?? conflict.tabooName}
              </span>
              <Tag
                bordered={false}
                color={conflict.matchKind === "EXACT" ? colorTokens.primary : colorTokens.textMuted}
                className="!m-0"
              >
                {t(`taboo.matchKind.${conflict.matchKind}`)}
              </Tag>
            </div>
            <div className="mt-0.5 text-[12.5px] text-text-muted">
              {isPresent(conflict.ancestorGeneration) &&
                t("taboo.generation", { n: conflict.ancestorGeneration })}
              {/* Display-only string from the backend — never parsed. */}
              {isPresent(conflict.relationHint) && ` · ${conflict.relationHint}`}
            </div>
            <div className="mt-1 text-[12.5px] text-text-muted">
              {t("taboo.collidesWith", { name: conflict.tabooName })}
            </div>
          </li>
        ))}
      </ul>

      <label className="block">
        <span className="mb-1 block text-[13px] font-medium text-text-main">
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
        <span className="mt-1 block text-[12px] text-text-muted">{t("taboo.reasonHint")}</span>
      </label>
    </Modal>
  );
}

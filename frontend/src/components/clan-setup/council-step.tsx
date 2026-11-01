"use client";

import { useState } from "react";
import { Alert, Button, Checkbox, Input } from "antd";
import { SendOutlined, WarningFilled } from "@ant-design/icons";
import { useFormatter, useTranslations } from "next-intl";
import { FormField } from "@/components/person-form/form-field";
import { ScopedPersonPicker } from "@/components/membership/scoped-person-picker";
import { OneTimeCode } from "@/components/membership/one-time-code";
import { councilAccountFailureOf, type CouncilAccountFailure } from "@/lib/api/clan-profile";
import { ApiError } from "@/lib/api/http";
import type { PersonSummaryDto } from "@/types/api";
import { useClanReadiness, useCreateCouncilAccount } from "./hooks/use-clan-setup";

/**
 * **Bước 2 — Hội đồng đầu tiên**, màn `/quan-tri`.
 *
 * <h2>Không có danh sách tài khoản Hội đồng đã tạo — chỉ có ĐẾM</h2>
 * Hợp đồng đưa cho màn này không có `GET` liệt kê tài khoản Hội đồng, chỉ có
 * `readiness.council.count`. Xem mục "chỗ hợp đồng còn thiếu" ở báo cáo bàn
 * giao — nếu Hội đồng cần soát lại "đã tạo cho những ai", câu trả lời đầy đủ
 * hơn nằm ở `audit_log`, không phải màn này.
 *
 * <h2>Chọn nhân khẩu bằng `ScopedPersonPicker`, không phải `PersonPicker`</h2>
 * `ADMIN` có `clanWide: true` nên `phamViCap()` trả `null` (toàn dòng họ) —
 * `ScopedPersonPicker` với người gọi này hoạt động y hệt một ô tra cả họ,
 * nhưng đã có sẵn phần "bỏ bao nhiêu người ngoài phạm vi" cho vai hẹp hơn nếu
 * sau này màn này được mở cho Trưởng chi. Không hiện ô này nếu phả CHƯA có
 * nhân khẩu nào (`readiness.persons.count === 0`) — một ô tìm kiếm chắc chắn
 * rỗng chỉ gây bối rối ở bước này.
 */
export function CouncilStep() {
  const t = useTranslations("clanSetup.councilStep");
  const format = useFormatter();
  const readiness = useClanReadiness();
  const create = useCreateCouncilAccount();

  const existingCount = readiness.data?.council.count ?? 0;
  const hasPersons = (readiness.data?.persons.count ?? 0) > 0;

  const [displayName, setDisplayName] = useState("");
  const [loginId, setLoginId] = useState("");
  const [attachPerson, setAttachPerson] = useState(false);
  const [personId, setPersonId] = useState<string | undefined>(undefined);
  const [picked, setPicked] = useState<PersonSummaryDto | undefined>(undefined);
  const [touched, setTouched] = useState(false);

  const displayNameError = touched && displayName.trim().length === 0;
  const loginIdError = touched && loginId.trim().length === 0;

  const submit = async () => {
    setTouched(true);
    if (displayName.trim().length === 0 || loginId.trim().length === 0) return;
    await create
      .mutateAsync({
        displayName: displayName.trim(),
        loginId: loginId.trim(),
        personId: attachPerson ? personId : undefined,
      })
      .catch(() => undefined);
  };

  const created = create.data;

  if (created) {
    return (
      <section aria-labelledby="hoi-dong-ket-qua" className="space-y-3">
        <h2 id="hoi-dong-ket-qua" className="m-0 font-serif text-de font-bold text-text-main">
          {t("issuedTitle")}
        </h2>

        <Alert
          type="warning"
          showIcon
          icon={<WarningFilled aria-hidden />}
          message={t("powerWarningTitle")}
          description={t("powerWarningBody")}
        />

        <OneTimeCode code={created.setPasswordUrl} caption={displayName} hint={t("linkHint")} />

        <p className="m-0 text-than text-text-main">
          {t("expiresAt", {
            at: format.dateTime(new Date(created.setPasswordExpiresAt), {
              dateStyle: "medium",
              timeStyle: "short",
            }),
          })}
        </p>

        <Button
          size="large"
          onClick={() => {
            create.reset();
            setDisplayName("");
            setLoginId("");
            setAttachPerson(false);
            setPersonId(undefined);
            setPicked(undefined);
            setTouched(false);
          }}
        >
          {t("createAnother")}
        </Button>
      </section>
    );
  }

  return (
    <section aria-labelledby="hoi-dong-tieu-de" className="space-y-4">
      <h2 id="hoi-dong-tieu-de" className="sr-only">
        {t("title")}
      </h2>

      <p className="m-0 text-than text-text-muted">{t("lead")}</p>

      <p className="m-0 text-than font-medium text-text-main" data-testid="council-existing-count">
        {t("existingCount", { count: existingCount })}
      </p>

      {create.error && <SubmitError error={create.error} />}

      <FormField label={t("displayNameLabel")} required error={displayNameError ? t("validation.displayNameRequired") : undefined}>
        {({ id, status, describedBy }) => (
          <Input
            id={id}
            size="large"
            status={status}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            aria-describedby={describedBy}
          />
        )}
      </FormField>

      <FormField label={t("loginIdLabel")} required error={loginIdError ? t("validation.loginIdRequired") : undefined}>
        {({ id, status, describedBy }) => (
          <Input
            id={id}
            size="large"
            status={status}
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            aria-describedby={describedBy}
          />
        )}
      </FormField>

      {hasPersons && (
        <div>
          <label className="flex min-h-11 items-center gap-2 text-than text-text-main">
            <Checkbox checked={attachPerson} onChange={(e) => setAttachPerson(e.target.checked)} />
            {t("attachPersonToggle")}
          </label>

          {attachPerson && (
            <div className="mt-2">
              <ScopedPersonPicker
                id="hoi-dong-chon-nhan-khau"
                label={t("personLabel")}
                placeholder={t("personPlaceholder")}
                value={personId}
                seed={picked}
                onChange={(newPersonId, person) => {
                  setPersonId(newPersonId);
                  setPicked(person);
                }}
              />
            </div>
          )}
        </div>
      )}

      <div>
        <Alert type="info" showIcon message={t("powerWarningTitle")} description={t("powerWarningBody")} />
      </div>

      <Button
        type="primary"
        size="large"
        icon={<SendOutlined aria-hidden />}
        loading={create.isPending}
        onClick={() => void submit()}
      >
        {t("submit")}
      </Button>
    </section>
  );
}

function SubmitError({ error }: { error: unknown }) {
  const t = useTranslations("clanSetup.councilStep.errors");
  const apiError = error instanceof ApiError ? error : null;
  const reason: CouncilAccountFailure = councilAccountFailureOf(error);
  return <Alert type="error" showIcon message={t(reason)} description={apiError?.problem?.detail} />;
}

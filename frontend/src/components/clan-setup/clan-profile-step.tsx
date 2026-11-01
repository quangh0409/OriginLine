"use client";

import { useEffect, useMemo, useRef } from "react";
import { Controller, useForm } from "react-hook-form";
import { Alert, Button, Input, InputNumber, Radio, Skeleton } from "antd";
import { SaveOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { FormField } from "@/components/person-form/form-field";
import { zodResolver } from "@/lib/form/zod-resolver";
import { ApiError } from "@/lib/api/http";
import {
  clanProfileSaveFailureOf,
  clanProfileValidationErrors,
  suggestRootBranchPath,
  type ClanProfileDto,
  type ClanProfileSaveFailure,
} from "@/lib/api/clan-profile";
import { useClanProfileAdmin, useClanReadiness, useSaveClanProfile } from "./hooks/use-clan-setup";
import {
  clanProfileFormSchema,
  clanProfileToFormValues,
  emptyClanProfileFormValues,
  toClanProfileWriteBody,
  type ClanProfileFormValues,
} from "./clan-profile-form-schema";

/** Trường CSDL lồng nhau (giả định — xem "chỗ hợp đồng còn thiếu" ở báo cáo bàn giao) → ô form phẳng. */
const SERVER_FIELD_MAP: Record<string, keyof ClanProfileFormValues> = {
  name: "name",
  shortName: "shortName",
  region: "region",
  rootBranchPath: "rootBranchPath",
  foundedNote: "foundedNote",
  ancestorName: "ancestorName",
  "ancestralHall.name": "ancestralHallName",
  "ancestralHall.address": "ancestralHallAddress",
  "gioTo.lunarDay": "gioToDay",
  "gioTo.lunarMonth": "gioToMonth",
};

/**
 * **Bước 1 — Hồ sơ dòng họ**, màn `/quan-tri`.
 *
 * <h2>`404 CLAN_NOT_INITIALIZED` mở form TRỐNG, không phải một dải lỗi</h2>
 * Đây là trạng thái bình thường của một cổng vừa dựng xong — xem javadoc
 * `clanProfileApi.getAdmin`. Chỉ một mã lỗi KHÁC mới hiện `Alert` đỏ.
 *
 * <h2>Gợi ý `rootBranchPath` chỉ chạy TRƯỚC khi người dùng tự gõ vào ô đó</h2>
 * `rootBranchTouched` chặn việc mỗi ký tự gõ vào ô TÊN kéo ngược ô GỐC về gợi
 * ý mặc định sau khi người dùng đã tự sửa gốc — xem javadoc
 * `suggestRootBranchPath`.
 */
export function ClanProfileStep() {
  const t = useTranslations("clanSetup.profileStep");
  const schema = useMemo(() => clanProfileFormSchema(t), [t]);

  const profileQuery = useClanProfileAdmin();
  const readinessQuery = useClanReadiness();
  const save = useSaveClanProfile();

  const notInitialized =
    profileQuery.error instanceof ApiError && profileQuery.error.code === "CLAN_NOT_INITIALIZED";
  const realError = profileQuery.isError && !notInitialized;

  if (profileQuery.isPending || readinessQuery.isPending) {
    return <Skeleton active paragraph={{ rows: 8 }} />;
  }

  if (realError) {
    return <Alert type="error" showIcon message={t("loadFailed")} />;
  }

  const personCount = readinessQuery.data?.persons.count ?? 0;
  // Khoá vĩnh viễn: đã có nhân khẩu VÀ gốc đã được ghi nhận xong (readiness
  // nói `rootBranch.done`). Chỉ dựa vào `personCount > 0` cũng đúng theo luật
  // máy chủ, nhưng đợi cả hai tránh khoá một ô mà chính hồ sơ chưa từng lưu.
  const locked = Boolean(readinessQuery.data?.rootBranch.done) && personCount > 0;

  return (
    <ClanProfileForm
      key={profileQuery.data?.data.version ?? "new"}
      existing={profileQuery.data?.data}
      etag={profileQuery.data?.etag ?? undefined}
      locked={locked}
      personCount={personCount}
      schema={schema}
      save={save}
    />
  );
}

function ClanProfileForm({
  existing,
  etag,
  locked,
  personCount,
  schema,
  save,
}: {
  existing: ClanProfileDto | undefined;
  etag: string | undefined;
  locked: boolean;
  personCount: number;
  schema: ReturnType<typeof clanProfileFormSchema>;
  save: ReturnType<typeof useSaveClanProfile>;
}) {
  const t = useTranslations("clanSetup.profileStep");
  const rootBranchTouched = useRef(Boolean(existing?.rootBranchPath));

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors },
  } = useForm<ClanProfileFormValues>({
    resolver: zodResolver<ClanProfileFormValues>(schema),
    defaultValues: existing ? clanProfileToFormValues(existing) : emptyClanProfileFormValues(),
    mode: "onBlur",
  });

  const name = watch("name");

  useEffect(() => {
    if (rootBranchTouched.current || locked) return;
    setValue("rootBranchPath", suggestRootBranchPath(name), { shouldValidate: false });
    // Chỉ phản ứng với TÊN vừa đổi — xem `event-form.tsx` cho cùng khuôn tránh
    // vòng lặp watch → setValue → watch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [name]);

  const onValid = async (values: ClanProfileFormValues) => {
    try {
      await save.mutateAsync({ body: toClanProfileWriteBody(values), ifMatch: etag });
      // KHÔNG có `setState` cục bộ nào ở đây báo "đã lưu" — xem lý do ở khối
      // JSX bên dưới đọc `save.isSuccess`.
    } catch (error) {
      if (error instanceof ApiError && error.code === "VALIDATION_FAILED") {
        const byField = clanProfileValidationErrors(error);
        for (const [serverField, message] of Object.entries(byField)) {
          const formField = SERVER_FIELD_MAP[serverField];
          if (formField) setError(formField, { type: "server", message });
        }
      }
      // Lỗi không phải trường (khoá gốc, lệch phiên bản, quyền...) hiện ở `SubmitError` bên dưới.
    }
  };

  return (
    <form onSubmit={handleSubmit(onValid)} className="space-y-4" noValidate>
      <p className="m-0 text-than text-text-muted">{t("lead")}</p>

      {save.error && !(save.error instanceof ApiError && save.error.code === "VALIDATION_FAILED") && (
        <SubmitError error={save.error} />
      )}
      {/* Đọc `save.isSuccess`/`save.reset()` từ mutation của CHA (`ClanProfileStep`),
          không phải một `useState` trong chính component này: lượt lưu thành
          công đổi `version` → `ClanProfileStep` gắn `key` mới cho form này để
          nạp lại giá trị mặc định từ dữ liệu vừa lưu (ETag mới cho lượt lưu kế
          tiếp) — và một `key` mới nghĩa là React GỠ hẳn thực thể cũ, xoá theo
          mọi `useState` cục bộ của nó. Cờ "đã lưu" phải sống ở NƠI KHÔNG bị gỡ
          theo cùng nhịp, nếu không thông báo thành công biến mất ngay khi vừa
          hiện — người dùng sẽ không bao giờ thấy được nó. */}
      {save.isSuccess && !save.isPending && (
        <Alert type="success" showIcon closable message={t("saved")} onClose={() => save.reset()} />
      )}

      <Controller
        control={control}
        name="name"
        render={({ field }) => (
          <FormField label={t("nameLabel")} required error={errors.name?.message}>
            {({ id, status, describedBy }) => (
              <Input
                id={id}
                {...field}
                size="large"
                status={status}
                maxLength={120}
                placeholder={t("namePlaceholder")}
                aria-describedby={describedBy}
              />
            )}
          </FormField>
        )}
      />

      <Controller
        control={control}
        name="shortName"
        render={({ field }) => (
          <FormField label={t("shortNameLabel")} error={errors.shortName?.message}>
            {({ id, status, describedBy }) => (
              <Input
                id={id}
                {...field}
                size="large"
                status={status}
                placeholder={t("shortNamePlaceholder")}
                aria-describedby={describedBy}
              />
            )}
          </FormField>
        )}
      />

      <Controller
        control={control}
        name="rootBranchPath"
        render={({ field }) => (
          <FormField
            label={t("rootBranchLabel")}
            required
            error={errors.rootBranchPath?.message}
            hint={locked ? t("rootBranchLockedHint", { count: personCount }) : t("rootBranchHint")}
          >
            {({ id, status, describedBy }) => (
              <Input
                id={id}
                {...field}
                onChange={(e) => {
                  rootBranchTouched.current = true;
                  field.onChange(e);
                }}
                size="large"
                status={status}
                disabled={locked}
                aria-describedby={describedBy}
                data-testid="root-branch-path-input"
              />
            )}
          </FormField>
        )}
      />

      <Controller
        control={control}
        name="region"
        render={({ field }) => (
          <FormField label={t("regionLabel")} required error={errors.region?.message} hint={t("regionHint")}>
            {({ id }) => (
              <Radio.Group
                id={id}
                value={field.value || undefined}
                onChange={(e) => field.onChange(e.target.value)}
                size="large"
                buttonStyle="solid"
                className="flex flex-wrap gap-2 [&>label]:!min-h-11 [&>label]:!flex [&>label]:items-center"
              >
                <Radio.Button value="BAC" className="!min-h-11">
                  {t("regionOptions.BAC")}
                </Radio.Button>
                <Radio.Button value="TRUNG" className="!min-h-11">
                  {t("regionOptions.TRUNG")}
                </Radio.Button>
                <Radio.Button value="NAM" className="!min-h-11">
                  {t("regionOptions.NAM")}
                </Radio.Button>
              </Radio.Group>
            )}
          </FormField>
        )}
      />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Controller
          control={control}
          name="ancestralHallName"
          render={({ field }) => (
            <FormField label={t("ancestralHallNameLabel")} error={errors.ancestralHallName?.message}>
              {({ id, status, describedBy }) => (
                <Input id={id} {...field} size="large" status={status} aria-describedby={describedBy} />
              )}
            </FormField>
          )}
        />
        <Controller
          control={control}
          name="ancestralHallAddress"
          render={({ field }) => (
            <FormField label={t("ancestralHallAddressLabel")} error={errors.ancestralHallAddress?.message}>
              {({ id, status, describedBy }) => (
                <Input id={id} {...field} size="large" status={status} aria-describedby={describedBy} />
              )}
            </FormField>
          )}
        />
      </div>

      <Controller
        control={control}
        name="ancestorName"
        render={({ field }) => (
          <FormField label={t("ancestorNameLabel")} error={errors.ancestorName?.message}>
            {({ id, status, describedBy }) => (
              <Input id={id} {...field} size="large" status={status} aria-describedby={describedBy} />
            )}
          </FormField>
        )}
      />

      <div className="rounded-lg border border-border bg-bg-card px-4 py-3 sm:px-5 sm:py-4">
        <h3 className="m-0 mb-2 font-serif text-base font-semibold text-primary">{t("gioToLabel")}</h3>
        <div className="grid grid-cols-2 gap-3 sm:max-w-xs">
          <Controller
            control={control}
            name="gioToDay"
            render={({ field }) => (
              <FormField label={t("gioToDayLabel")} error={errors.gioToDay?.message}>
                {({ id, status, describedBy }) => (
                  <InputNumber
                    id={id}
                    className="w-full"
                    size="large"
                    status={status}
                    min={1}
                    max={30}
                    value={field.value === "" ? undefined : Number(field.value)}
                    onChange={(v) => field.onChange(v === null ? "" : String(v))}
                    onBlur={field.onBlur}
                    aria-describedby={describedBy}
                  />
                )}
              </FormField>
            )}
          />
          <Controller
            control={control}
            name="gioToMonth"
            render={({ field }) => (
              <FormField label={t("gioToMonthLabel")} error={errors.gioToMonth?.message}>
                {({ id, status, describedBy }) => (
                  <InputNumber
                    id={id}
                    className="w-full"
                    size="large"
                    status={status}
                    min={1}
                    max={12}
                    value={field.value === "" ? undefined : Number(field.value)}
                    onChange={(v) => field.onChange(v === null ? "" : String(v))}
                    onBlur={field.onBlur}
                    aria-describedby={describedBy}
                  />
                )}
              </FormField>
            )}
          />
        </div>
      </div>

      <Controller
        control={control}
        name="foundedNote"
        render={({ field }) => (
          <FormField label={t("foundedNoteLabel")} error={errors.foundedNote?.message}>
            {({ id, status, describedBy }) => (
              <Input.TextArea
                id={id}
                {...field}
                rows={4}
                status={status}
                maxLength={2000}
                aria-describedby={describedBy}
              />
            )}
          </FormField>
        )}
      />

      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="primary"
          size="large"
          icon={<SaveOutlined aria-hidden />}
          htmlType="submit"
          loading={save.isPending}
        >
          {t("save")}
        </Button>
      </div>
    </form>
  );
}

function SubmitError({ error }: { error: unknown }) {
  const t = useTranslations("clanSetup.profileStep.errors");
  const apiError = error instanceof ApiError ? error : null;
  const reason: ClanProfileSaveFailure = clanProfileSaveFailureOf(error);
  return <Alert type="error" showIcon message={t(reason)} description={apiError?.problem?.detail} />;
}

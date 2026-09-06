"use client";

import { useMemo } from "react";
import { useForm, Controller } from "react-hook-form";
import { Alert, App, Button, Input, Radio, Select, Switch } from "antd";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { usePersonSubmit, type DeathConfirmationRequest } from "@/hooks/use-person-submit";
import { headlineName } from "@/lib/format/name-layers";
import { zodResolver } from "@/lib/form/zod-resolver";
import { ApiError } from "@/lib/api/http";
import { DateDualField } from "./date-dual-field";
import { FormField } from "./form-field";
import { NameLayersField } from "./name-layers-field";
import { RelationshipLinkField } from "./relationship-link-field";
import { DeathConfirmDialog } from "./death-confirm-dialog";
import { TabooConflictDialog } from "./taboo-conflict-dialog";
import {
  effectiveIsAlive,
  emptyPersonFormValues,
  formToDate,
  personFormSchema,
  personToFormValues,
  toCreateRequest,
  toUpdateRequest,
  type PersonFormValues,
} from "./person-form-schema";
import type { PersonDto } from "@/types/api";

export type PersonFormProps =
  | { mode: "create"; person?: undefined; etag?: undefined }
  | { mode: "edit"; person: PersonDto; etag: string };

/**
 * F4 — form thêm/sửa nhân khẩu.
 *
 * react-hook-form drives state (uncontrolled inputs, so typing a long biography
 * does not re-render the whole form) and zod validates, bridged by the small
 * hand-written resolver in src/lib/form/zod-resolver.ts.
 *
 * Server-owned decisions this form deliberately does NOT make:
 *  - whether the current user may submit at all — that is `meta.canEdit`,
 *    checked by the pages that mount this;
 *  - `generation` — derived from the graph;
 *  - kỵ húy collisions — the backend answers 409 and this form reacts
 *    (see usePersonSubmit + TabooConflictDialog).
 */
export function PersonForm(props: PersonFormProps) {
  const t = useTranslations("personForm");
  const tPerson = useTranslations("person");
  const router = useRouter();
  const { message } = App.useApp();

  const schema = useMemo(() => personFormSchema(t), [t]);

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isDirty },
  } = useForm<PersonFormValues>({
    resolver: zodResolver<PersonFormValues>(schema),
    defaultValues:
      props.mode === "edit" ? personToFormValues(props.person) : emptyPersonFormValues(),
    mode: "onBlur",
  });

  const target = useMemo(
    () =>
      props.mode === "edit"
        ? ({ mode: "update", personId: props.person.id, etag: props.etag } as const)
        : ({ mode: "create" } as const),
    [props]
  );

  const {
    submit,
    confirmOverride,
    cancelOverride,
    confirmDeath,
    cancelDeath,
    conflicts,
    deathConfirmation,
    isSubmitting,
    error,
  } = usePersonSubmit(target);

  const buildPayload = (values: PersonFormValues) => ({
    create: (options: { confirmTabooOverride?: boolean; overrideReason?: string }) =>
      toCreateRequest(values, options),
    update: (options: { confirmTabooOverride?: boolean; overrideReason?: string }) => {
      // `props.person` is the pre-edit snapshot; the mapper needs it so a
      // field hidden by the caller's privacy tier is left alone instead of
      // being cleared. See toUpdateRequest. Unreachable in create mode —
      // usePersonSubmit only calls the mapper matching its target.
      if (props.mode !== "edit") throw new Error("update payload requested in create mode");
      return toUpdateRequest(values, props.person, options);
    },
  });

  /**
   * Lần gửi này có biến một người ĐANG ĐƯỢC GHI NHẬN LÀ CÒN SỐNG thành đã mất hay không.
   *
   * Mốc so sánh là BẢN GHI ĐÃ LƯU, không phải công tắc trên form — người dùng vừa gạt công tắc
   * xong thì lấy nó làm mốc là không còn "trước" nào để so. Nhờ mốc này:
   *
   *  - sửa ngày giỗ của người VỐN ĐÃ MẤT thì không hỏi lại: hồ sơ ấy công khai từ trước, không
   *    có gì để lộ thêm, hỏi nữa chỉ tổ phiền;
   *  - thêm mới một cụ đã khuất cũng không hỏi: chưa từng có hồ sơ người sống nào bị phát tán.
   *
   * Ngược lại, CẢ HAI đường dẫn tới việc phát tán đều bị chặn — gõ ngày mất (máy chủ suy ra đã
   * mất) và tự gạt công tắc sang "đã khuất" — vì hệ quả của chúng y hệt nhau.
   */
  const deathGuardFor = (values: PersonFormValues): DeathConfirmationRequest | null => {
    if (props.mode !== "edit" || !props.person.isAlive) return null;
    // Truyền cả hồ sơ gốc để dùng đúng một luật hợp nhất với hai mapper, không có bản sao lệch.
    if (effectiveIsAlive(values, props.person)) return null;

    return {
      // Nêu đích danh: "bạn chắc chứ?" chung chung thì không ai kiểm được mình đang sửa nhầm hồ sơ.
      personName:
        headlineName(props.person) ??
        values.names.find((n) => n.isPrimary)?.fullName ??
        values.names[0]?.fullName ??
        "",
      deathDate: formToDate(values.death) ?? null,
    };
  };

  const afterSave = (saved: PersonDto) => {
    message.success(t(props.mode === "edit" ? "savedEdit" : "savedCreate"));
    router.push(`/persons/${saved.id}`);
  };

  const onValid = async (values: PersonFormValues) => {
    // Có guard thì submit() KHÔNG gửi gì cả, chỉ mở hộp thoại và giữ payload lại.
    const saved = await submit(buildPayload(values), deathGuardFor(values));
    if (saved) afterSave(saved);
  };

  return (
    <>
      <form onSubmit={handleSubmit(onValid)} className="space-y-4" noValidate>
        {error && <SubmitError error={error} />}

        <Section title={t("sections.names")}>
          <NameLayersField control={control} errors={errors} setValue={setValue} />
        </Section>

        <Section title={t("sections.identity")}>
          <div className="grid gap-3 sm:grid-cols-2">
            <Controller
              control={control}
              name="gender"
              render={({ field }) => (
                <FormField label={t("gender")} required>
                  {({ id }) => (
                    <Radio.Group id={id} {...field} size="large" buttonStyle="solid">
                      <Radio.Button value="MALE">{t("genderOption.MALE")}</Radio.Button>
                      <Radio.Button value="FEMALE">{t("genderOption.FEMALE")}</Radio.Button>
                      <Radio.Button value="UNKNOWN">{t("genderOption.UNKNOWN")}</Radio.Button>
                    </Radio.Group>
                  )}
                </FormField>
              )}
            />

            <Controller
              control={control}
              name="isAlive"
              render={({ field }) => (
                <FormField label={t("status")} hint={t("statusHint")}>
                  {({ id }) => (
                    <span className="flex items-center gap-2">
                      <Switch
                        id={id}
                        checked={field.value}
                        onChange={field.onChange}
                        checkedChildren={tPerson("alive")}
                        unCheckedChildren={tPerson("deceased")}
                      />
                      <span className="text-[13px] text-text-muted">
                        {field.value ? tPerson("alive") : tPerson("deceased")}
                      </span>
                    </span>
                  )}
                </FormField>
              )}
            />
          </div>
        </Section>

        <Section title={t("sections.dates")}>
          <div className="space-y-3">
            <DateDualField control={control} errors={errors} name="birth" legend={tPerson("birth")} />
            {/* Không còn khoá theo công tắc "còn sống". Hội đồng đã chốt: nhập ngày mất tức là
                người đó đã mất — khoá ô nhập lại thì chính quyết định ấy không bao giờ dùng được.
                Chốt chặn nằm ở <DeathConfirmDialog> lúc gửi, chứ không phải ở một ô xám. */}
            <DateDualField
              control={control}
              errors={errors}
              name="death"
              legend={tPerson("death")}
            />
          </div>
        </Section>

        <Section title={t("sections.origin")}>
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField control={control} name="nativePlace" label={tPerson("nativePlace")} />
            <TextField control={control} name="occupation" label={tPerson("occupation")} />
            <TextField
              control={control}
              name="currentPlaceProvince"
              label={t("currentPlaceProvince")}
            />
            <TextField control={control} name="currentPlaceFull" label={t("currentPlaceFull")} />
          </div>
        </Section>

        <Section title={t("sections.contact")} hint={t("contactHint")}>
          <div className="grid gap-3 sm:grid-cols-3">
            <TextField
              control={control}
              name="contact.phone"
              label={tPerson("phone")}
              error={errors.contact?.phone?.message}
              inputMode="tel"
            />
            <TextField
              control={control}
              name="contact.email"
              label={tPerson("email")}
              error={errors.contact?.email?.message}
              inputMode="email"
            />
            <TextField control={control} name="contact.zaloId" label={tPerson("zalo")} />
          </div>

          <div className="mt-3">
            <Controller
              control={control}
              name="privacyLevel"
              render={({ field }) => (
                <FormField label={t("privacyLevel")} hint={t("privacyLevelHint")}>
                  {({ id }) => (
                    <Select
                      id={id}
                      {...field}
                      size="large"
                      className="w-full sm:max-w-sm"
                      options={(
                        ["DEFAULT", "BRANCH_OPT_IN", "CLAN_OPT_IN", "RESTRICTED"] as const
                      ).map((value) => ({ value, label: t(`privacyOption.${value}`) }))}
                    />
                  )}
                </FormField>
              )}
            />
          </div>
        </Section>

        <Section title={t("sections.biography")}>
          <Controller
            control={control}
            name="biography"
            render={({ field }) => (
              <FormField label={tPerson("biography")} error={errors.biography?.message}>
                {({ id, status, describedBy }) => (
                  <Input.TextArea
                    id={id}
                    {...field}
                    rows={5}
                    status={status}
                    aria-describedby={describedBy}
                  />
                )}
              </FormField>
            )}
          />
        </Section>

        {props.mode === "create" && (
          <Section title={t("sections.relation")}>
            <RelationshipLinkField control={control} errors={errors} watch={watch} />
          </Section>
        )}

        <Section title={t("sections.note")} hint={t("noteHint")}>
          <Controller
            control={control}
            name="note"
            render={({ field }) => (
              <FormField label={t("note")} error={errors.note?.message}>
                {({ id, status, describedBy }) => (
                  <Input.TextArea
                    id={id}
                    {...field}
                    rows={2}
                    status={status}
                    aria-describedby={describedBy}
                  />
                )}
              </FormField>
            )}
          />
        </Section>

        <div className="sticky bottom-0 flex gap-2 border-t border-border bg-bg-page/95 py-3 backdrop-blur">
          <Button type="primary" size="large" htmlType="submit" loading={isSubmitting}>
            {t(props.mode === "edit" ? "save" : "create")}
          </Button>
          <Button size="large" onClick={() => router.back()} disabled={isSubmitting}>
            {t("cancel")}
          </Button>
          {isDirty && <span className="self-center text-[12px] text-text-muted">{t("unsaved")}</span>}
        </div>
      </form>

      <DeathConfirmDialog
        request={deathConfirmation}
        submitting={isSubmitting}
        onCancel={cancelDeath}
        onConfirm={() => {
          void confirmDeath().then((saved) => {
            if (saved) afterSave(saved);
          });
        }}
      />

      <TabooConflictDialog
        conflicts={conflicts}
        submitting={isSubmitting}
        onCancel={cancelOverride}
        onConfirm={(reason) => {
          void confirmOverride(reason).then((saved) => {
            if (saved) {
              message.success(t("savedWithOverride"));
              router.push(`/persons/${saved.id}`);
            }
          });
        }}
      />
    </>
  );
}

function Section({
  title,
  hint,
  children,
}: {
  title: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-lg border border-border bg-bg-card px-4 py-3 sm:px-5 sm:py-4">
      <h2 className="m-0 font-serif text-base font-semibold text-primary sm:text-lg">{title}</h2>
      {hint && <p className="m-0 mb-2 mt-0.5 text-[12px] text-text-muted">{hint}</p>}
      <div className="mt-2">{children}</div>
    </section>
  );
}

function TextField({
  control,
  name,
  label,
  error,
  inputMode,
}: {
  control: ReturnType<typeof useForm<PersonFormValues>>["control"];
  name:
    | "nativePlace"
    | "occupation"
    | "currentPlaceProvince"
    | "currentPlaceFull"
    | "contact.phone"
    | "contact.email"
    | "contact.zaloId";
  label: string;
  error?: string;
  inputMode?: "tel" | "email";
}) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <FormField label={label} error={error}>
          {({ id, status, describedBy }) => (
            <Input
              id={id}
              {...field}
              size="large"
              status={status}
              inputMode={inputMode}
              aria-describedby={describedBy}
              autoCorrect="off"
              spellCheck={false}
            />
          )}
        </FormField>
      )}
    />
  );
}

/**
 * Branches on `code`, never on `title`/`detail` — those are localized human
 * text and must not drive control flow (contracts/README §3).
 */
function SubmitError({ error }: { error: ApiError }) {
  const t = useTranslations("personForm");

  const key =
    error.code === "OPTIMISTIC_LOCK_CONFLICT"
      ? "errors.staleRecord"
      : error.code === "FORBIDDEN" || error.code === "BRANCH_SCOPE_VIOLATION"
        ? "errors.forbidden"
        : error.code === "VALIDATION_FAILED"
          ? "errors.serverValidation"
          : "errors.submitFailed";

  return <Alert type="error" showIcon message={t(key)} description={error.problem?.detail} />;
}

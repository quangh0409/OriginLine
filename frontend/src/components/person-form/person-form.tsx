"use client";

import { useCallback, useEffect, useMemo } from "react";
import { useForm, Controller } from "react-hook-form";
import { Alert, App, Button, Input, Radio, Switch } from "antd";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import {
  usePersonSubmit,
  type DeathConfirmationRequest,
  type SubmitOverrides,
} from "@/hooks/use-person-submit";
import { useFormDraft } from "@/hooks/use-form-draft";
import { useMe } from "@/hooks/use-me";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { headlineName } from "@/lib/format/name-layers";
import { zodResolver } from "@/lib/form/zod-resolver";
import { ApiError } from "@/lib/api/http";
import { DateDualField } from "./date-dual-field";
import { FormField } from "./form-field";
import { NameLayersField } from "./name-layers-field";
import { RelationshipLinkField } from "./relationship-link-field";
import { DeathConfirmDialog } from "./death-confirm-dialog";
import { TabooConflictDialog } from "./taboo-conflict-dialog";
import { DuplicateConflictDialog } from "./duplicate-conflict-dialog";
import { DraftRestoreBanner } from "./draft-restore-banner";
import { UnsavedChangesDialog } from "./unsaved-changes-dialog";
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
 *
 * <h2>Không có gì được phép biến mất trong im lặng</h2>
 * Một Trưởng chi 55 tuổi nhập bảy mục trong nửa tiếng; một cú vuốt lùi trước
 * đây xoá sạch, không một câu hỏi. Trước đó `isDirty` chỉ dùng để in dòng chữ
 * "Có thay đổi chưa lưu" rồi thôi. Nay nó điều khiển hai lớp bảo vệ:
 *
 *  1. {@link useUnsavedChanges} — chặn đóng tab, chặn vuốt lùi, chặn liên kết
 *     trong ứng dụng, và hỏi trước khi để đi;
 *  2. {@link useFormDraft} — nháp cục bộ trong `sessionStorage` (tab-scoped, có
 *     chủ ý vì nháp chứa dữ liệu Tầng 3 của người còn sống; xem javadoc của
 *     hook để biết cả quyết định riêng tư).
 *
 * Lớp (1) lo tai nạn có thể hỏi được; lớp (2) lo tai nạn không hỏi kịp — sập
 * trình duyệt, hết pin, trình duyệt di động tự giải phóng tab nền.
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
    reset,
    formState: { errors, isDirty },
  } = useForm<PersonFormValues>({
    resolver: zodResolver<PersonFormValues>(schema),
    defaultValues:
      props.mode === "edit" ? personToFormValues(props.person) : emptyPersonFormValues(),
    mode: "onBlur",
  });

  // --- Nháp cục bộ ---------------------------------------------------------
  // Khoá gắn với CẢ đối tượng đang sửa LẪN tài khoản đang đăng nhập: hai người
  // dùng chung một máy ở nhà thờ họ không bao giờ nhìn thấy nháp của nhau.
  const { data: me } = useMe();
  const draftKey = me
    ? `person:${props.mode === "edit" ? props.person.id : "new"}:${me.appUserId ?? "anon"}`
    : null;
  const draft = useFormDraft<PersonFormValues>({ key: draftKey });

  /**
   * `watch(callback)` chứ không phải `watch()`.
   *
   * Dạng có callback báo thay đổi mà **không** vẽ lại thành phần — giữ nguyên
   * tính chất "gõ một tiểu sử dài không vẽ lại cả biểu mẫu" mà react-hook-form
   * sinh ra để có. Dạng không callback sẽ đăng ký lại toàn bộ giá trị vào lần
   * vẽ và phá đúng điều đó.
   *
   * Điều kiện là `name` chứ **không phải** `isDirty`: `isDirty` chỉ đúng ở lần
   * vẽ SAU thay đổi, nên đọc nó ngay trong callback thì lần sửa đầu tiên luôn
   * bị bỏ qua — và với một người chỉ sửa đúng một ô rồi đóng tab, "lần đầu
   * tiên" cũng là lần duy nhất. `name` có giá trị khi và chỉ khi một ô thật sự
   * đổi; lần gọi khởi tạo của react-hook-form không mang `name`.
   */
  const saveDraft = draft.save;
  useEffect(() => {
    const subscription = watch((values, { name }) => {
      if (!name) return;
      saveDraft(values as PersonFormValues);
    });
    return () => subscription.unsubscribe();
  }, [watch, saveDraft]);

  const restoreDraft = useCallback(() => {
    const found = draft.restorable;
    if (!found) return;
    // `keepDefaultValues` giữ nguyên mốc so sánh ban đầu, nhờ đó dữ liệu vừa
    // khôi phục vẫn được tính là "chưa lưu" — vì nó chưa lưu thật.
    reset(found.values, { keepDefaultValues: true });
    draft.dismiss();
  }, [draft, reset]);

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
    confirmDuplicateOverride,
    cancelDuplicateOverride,
    confirmDeath,
    cancelDeath,
    conflicts,
    duplicates,
    deathConfirmation,
    isSubmitting,
    error,
  } = usePersonSubmit(target);

  const buildPayload = (values: PersonFormValues) => ({
    create: (options: SubmitOverrides) => toCreateRequest(values, options),
    update: (options: Omit<SubmitOverrides, "confirmDuplicateOverride">) => {
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

  /**
   * Chốt chặn rời trang. `release()` phải được gọi TRƯỚC khi điều hướng sau khi
   * lưu — nếu không, chính cú `router.push` của mình lại kích hoạt hộp thoại
   * "bạn có thay đổi chưa lưu", vốn là điều vô lý ngay sau một lần lưu thành
   * công.
   */
  const unsaved = useUnsavedChanges({
    when: isDirty,
    navigate: (href) => router.push(href),
  });

  const afterSave = (saved: PersonDto) => {
    unsaved.release();
    // Lưu xong thì nháp hết lý do tồn tại — và nó có thể đang giữ dữ liệu Tầng
    // 3 của một người còn sống, nên xoá ngay chứ không đợi đóng tab.
    draft.clear();
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

        {draft.restorable && (
          <DraftRestoreBanner
            savedAt={draft.restorable.savedAt}
            onRestore={restoreDraft}
            onDiscard={draft.clear}
          />
        )}

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
                      <span className="text-than text-text-muted">
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

          {/*
            KHÔNG có ô chọn mức chia sẻ ở đây — đó là chủ ý, đừng thêm lại.

            Biểu mẫu này do người khác khai HỘ (trưởng chi khai cho cả chi). Một ô chọn mức
            chia sẻ đặt ở đây cho phép người khai nới rộng quyền riêng tư của một người còn
            sống mà chính người ấy không hề biết — đúng điều mà dòng gợi ý cũ ngay bên dưới
            nó tự cấm: "chỉ nới rộng khi chính người đó đồng ý".

            Mức chia sẻ nay thuộc `PrivacySharingCard`, thẻ chỉ hiện với chính chủ
            (`meta.isSelf`). Ở đây ta không gửi `privacy` gì cả, và máy chủ hiểu vắng mặt là
            `PRIVATE` — mặc định kín, không ai mở hộ ai.
          */}
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

        <div className="sticky bottom-0 flex flex-wrap items-center gap-2 border-t border-border bg-bg-page/95 py-3 backdrop-blur">
          <Button type="primary" size="large" htmlType="submit" loading={isSubmitting}>
            {t(props.mode === "edit" ? "save" : "create")}
          </Button>
          {/* "Huỷ" đi qua chốt: đây là nút dễ bấm nhầm nhất trên màn hình. */}
          <Button
            size="large"
            onClick={() => unsaved.guard(() => router.back())}
            disabled={isSubmitting}
          >
            {t("cancel")}
          </Button>
          {isDirty && <span className="self-center text-than text-text-muted">{t("unsaved")}</span>}
          {/* Nháp nằm trên MÁY NÀY. Người dùng phải xoá được ngay, không phải đi
              tìm trong cài đặt — nhất là khi đang ngồi ở máy dùng chung. */}
          {draft.hasStoredDraft && !draft.restorable && (
            <Button size="small" type="link" onClick={draft.clear}>
              {t("draft.discard")}
            </Button>
          )}
        </div>
      </form>

      <UnsavedChangesDialog
        open={unsaved.pending !== null}
        onStay={unsaved.stay}
        onLeave={unsaved.confirmLeave}
      />

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
              unsaved.release();
              draft.clear();
              message.success(t("savedWithOverride"));
              router.push(`/persons/${saved.id}`);
            }
          });
        }}
      />

      {/* Cổng thứ hai của cùng một lần gửi: vượt kỵ húy xong vẫn có thể đụng
          nghi trùng, nên hai hộp thoại nối tiếp chứ không loại trừ nhau. */}
      <DuplicateConflictDialog
        candidates={duplicates}
        submitting={isSubmitting}
        onCancel={cancelDuplicateOverride}
        onConfirm={() => {
          void confirmDuplicateOverride().then((saved) => {
            if (saved) {
              unsaved.release();
              draft.clear();
              message.success(t("savedWithDuplicateOverride"));
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
      {hint && <p className="m-0 mb-2 mt-0.5 text-than text-text-muted">{hint}</p>}
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

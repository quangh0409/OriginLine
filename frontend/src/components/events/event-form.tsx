"use client";

import { useEffect, useMemo } from "react";
import { Controller, useForm } from "react-hook-form";
import { Alert, App, Button, Input, InputNumber, Radio, Select, Switch } from "antd";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { branchDepth, useBranches } from "@/hooks/use-branches";
import { useCreateEvent, useUpdateEvent } from "@/hooks/use-event-submit";
import { useMe } from "@/hooks/use-me";
import { zodResolver } from "@/lib/form/zod-resolver";
import { ApiError } from "@/lib/api/http";
import { colorVars } from "@/styles/tokens";
import { FormField } from "@/components/person-form/form-field";
import { PersonPicker } from "@/components/person/person-picker";
import {
  EVENT_TYPES,
  emptyEventFormValues,
  eventFormSchema,
  eventToFormValues,
  eventTypeRequiresPerson,
  forcedScopeFor,
  toCreateRequest,
  toUpdateRequest,
  type EventFormValues,
} from "./event-form-schema";
import type { EventDto, ProblemCode } from "@/types/api";

export type EventFormProps =
  | { mode: "create"; event?: undefined; etag?: undefined }
  | { mode: "edit"; event: EventDto; etag: string };

/**
 * F7 Đợt 2 — form tạo/sửa việc họ.
 *
 * Chỉ Trưởng cành/chi/họ được GỌI TỚI form này — cổng thật là backend
 * (`403 FORBIDDEN`/`BRANCH_SCOPE_VIOLATION`), giao diện chỉ ẩn lối vào cho
 * người chắc chắn không có quyền (xem `EventCreateScreen`/`EventEditScreen`).
 * Phạm vi do máy chủ cắt — component này KHÔNG lọc lại danh sách chi được
 * phép; nó chỉ tránh ĐƯA RA những lựa chọn mà chính người dùng đã biết là vô
 * vọng (chi ngoài phạm vi họ quản), để giảm số lần bấm rồi ăn `403` chứ không
 * thay cho phép kiểm thật.
 */
export function EventForm(props: EventFormProps) {
  const t = useTranslations("eventForm");
  const tEvents = useTranslations("events");
  const router = useRouter();
  const { message } = App.useApp();

  const schema = useMemo(() => eventFormSchema(t), [t]);
  const { data: me } = useMe();
  const { data: branches, isLoading: branchesLoading } = useBranches();

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<EventFormValues>({
    resolver: zodResolver<EventFormValues>(schema),
    defaultValues: props.mode === "edit" ? eventToFormValues(props.event) : emptyEventFormValues(),
    mode: "onBlur",
  });

  const recurringAnnually = watch("recurringAnnually");
  const scope = watch("scope");
  const eventType = watch("eventType");
  /** `ck_event_gio_has_person` — một GIỖ THƯỜNG luôn là giỗ của một người. */
  const needsPerson = eventTypeRequiresPerson(eventType);

  /**
   * "Cả dòng họ" chỉ dành cho Hội đồng Tộc biểu / Tộc trưởng / Quản trị — một
   * Trưởng chi phát một lời nhắc cho cả họ là đúng thứ dòng mở đầu Việc 2
   * cảnh báo: "nhắc nhầm cả họ cho việc của một chi là cách nhanh nhất để
   * người ta tắt thông báo." Máy chủ chắc chắn còn kiểm lại; ẩn lựa chọn ở
   * đây chỉ để không mời một Trưởng chi bấm vào một nút chắc chắn `403`.
   */
  const canClanWide = me?.role === "ADMIN" || me?.role === "COUNCIL";

  /**
   * `GIO_HO`/`GIO_CHI` tự ấn định phạm vi — xem javadoc `forcedScopeFor`.
   * Khi loại đã ấn định, khối "Phạm vi" phải TỰ KHỚP theo, không chờ người
   * dùng đoán ra luật ẩn rồi ăn `422 VALIDATION_FAILED` với một câu chung
   * chung "kiểm tra lại các trường".
   */
  const forcedScope = forcedScopeFor(eventType);

  useEffect(() => {
    if (forcedScope) {
      setValue("scope", forcedScope);
    }
    // Chỉ phản ứng với LOẠI vừa đổi — không đưa `forcedScope`/`setValue` vào
    // đây để tránh một vòng lặp `watch` → `setValue` → `watch` không cần
    // thiết; cả hai đều suy được lại từ `eventType` ở mỗi lượt render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventType]);

  /**
   * Một Trưởng chi không bao giờ thoả được `clanWide=true` mà `GIO_HO` đòi
   * (xem `canClanWide` ở trên) — tức họ không có cách nào tự khớp phạm vi lại
   * cho loại này. Lọc nó khỏi danh sách chọn thay vì để họ chọn xong rồi nhận
   * một lỗi 422 không nói rõ vì sao.
   *
   * TRỪ KHI đang sửa một việc CÓ SẴN là `GIO_HO`: `Select` không được rơi vào
   * trạng thái "đang chọn một giá trị không có trong danh sách" — dù ca đó
   * chỉ xảy ra nếu quyền của người xem đã đổi sau khi việc được tạo.
   */
  const editingExistingGioHo = props.mode === "edit" && props.event.eventType === "GIO_HO";
  const availableEventTypes = useMemo(
    () =>
      canClanWide || editingExistingGioHo
        ? EVENT_TYPES
        : EVENT_TYPES.filter((type) => type !== "GIO_HO"),
    [canClanWide, editingExistingGioHo]
  );

  /** Chi ngoài phạm vi quản lý bị bớt khỏi danh sách — cùng lý do với `canClanWide`. */
  const allowedBranches = useMemo(() => {
    if (!branches) return [];
    if (!me || me.clanWide) return branches;
    return branches.filter((b) =>
      me.managedBranches.some(
        (managedScope) => b.path === managedScope || b.path.startsWith(`${managedScope}.`)
      )
    );
  }, [branches, me]);

  const createEvent = useCreateEvent();
  const updateEvent = useUpdateEvent();
  const isSubmitting = createEvent.isPending || updateEvent.isPending;
  const submitError = createEvent.error ?? updateEvent.error;

  const onValid = async (values: EventFormValues) => {
    try {
      if (props.mode === "edit") {
        const saved = await updateEvent.mutateAsync({
          id: props.event.id,
          input: toUpdateRequest(values),
          ifMatch: props.etag,
        });
        message.success(t("savedEdit"));
        router.push(`/events?event=${saved.id}`);
        return;
      }
      const saved = await createEvent.mutateAsync(toCreateRequest(values));
      message.success(t("savedCreate"));
      router.push(`/events?event=${saved.id}`);
    } catch {
      // Lỗi đã có trong `submitError` (`createEvent.error`/`updateEvent.error`),
      // hiển thị bằng <SubmitError> bên dưới — không cần làm gì thêm ở đây.
    }
  };

  return (
    <form onSubmit={handleSubmit(onValid)} className="space-y-4" noValidate>
      {submitError && <SubmitError error={submitError} />}

      {props.mode === "edit" && props.event.recurringAnnually === undefined && (
        // `EventDto` từ TRƯỚC khi backend thêm `recurringAnnually` vào bản đọc
        // (bản ghi cũ trong mock) không có trường này — lùi về `true` và nói
        // thẳng ra, thay vì lặng lẽ điền một giá trị có thể sai.
        <Alert type="warning" showIcon message={t("editGapWarning")} />
      )}

      <section className="rounded-lg border border-border bg-bg-card px-4 py-3 sm:px-5 sm:py-4">
        <h2 className="m-0 font-serif text-base font-semibold text-primary sm:text-lg">
          {t("sections.basics")}
        </h2>
        <div className="mt-2 space-y-3">
          <Controller
            control={control}
            name="eventType"
            render={({ field }) => (
              <FormField label={t("eventType")} required>
                {({ id, status }) => (
                  <Select
                    id={id}
                    className="w-full"
                    size="large"
                    status={status}
                    value={field.value}
                    onChange={field.onChange}
                    options={availableEventTypes.map((type) => ({
                      value: type,
                      label: tEvents(`eventType.${type}`),
                    }))}
                  />
                )}
              </FormField>
            )}
          />
          {!canClanWide && !editingExistingGioHo && (
            <p className="m-0 text-than text-text-muted">{t("eventTypeGioHoHiddenHint")}</p>
          )}

          <Controller
            control={control}
            name="title"
            render={({ field }) => (
              <FormField label={t("title")} required error={errors.title?.message}>
                {({ id, status, describedBy }) => (
                  <Input
                    id={id}
                    {...field}
                    size="large"
                    status={status}
                    aria-describedby={describedBy}
                    placeholder={t("titlePlaceholder")}
                  />
                )}
              </FormField>
            )}
          />

          <Controller
            control={control}
            name="description"
            render={({ field }) => (
              <FormField label={t("description")} hint={t("descriptionHint")}>
                {({ id, describedBy }) => (
                  <Input.TextArea id={id} {...field} rows={3} aria-describedby={describedBy} />
                )}
              </FormField>
            )}
          />

          <Controller
            control={control}
            name="location"
            render={({ field }) => (
              <FormField label={t("location")}>
                {({ id, describedBy }) => (
                  <Input id={id} {...field} size="large" aria-describedby={describedBy} />
                )}
              </FormField>
            )}
          />

          {needsPerson && (
            <Controller
              control={control}
              name="personId"
              render={({ field }) => (
                <PersonPicker
                  id="event-form-person"
                  label={t("person")}
                  status={errors.personId ? "error" : undefined}
                  value={field.value || undefined}
                  onChange={(id) => field.onChange(id ?? "")}
                  seed={props.mode === "edit" ? (props.event.person ?? undefined) : undefined}
                />
              )}
            />
          )}
          {needsPerson && errors.personId && (
            <p role="alert" className="m-0 text-than" style={{ color: colorVars.danger }}>
              {errors.personId.message}
            </p>
          )}
          {needsPerson && (
            <p className="m-0 text-than text-text-muted">{t("personHint")}</p>
          )}
        </div>
      </section>

      <section className="rounded-lg border border-border bg-bg-card px-4 py-3 sm:px-5 sm:py-4">
        <h2 className="m-0 font-serif text-base font-semibold text-primary sm:text-lg">
          {t("sections.lunarDate")}
        </h2>
        <p className="m-0 mb-2 mt-0.5 text-than text-text-muted">{t("lunarDateHint")}</p>
        <div className={`grid grid-cols-2 gap-3 ${recurringAnnually ? "sm:grid-cols-3" : "sm:grid-cols-4"}`}>
          <Controller
            control={control}
            name="lunarDay"
            render={({ field }) => (
              <FormField label={t("lunarDay")} required error={errors.lunarDay?.message}>
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
            name="lunarMonth"
            render={({ field }) => (
              <FormField label={t("lunarMonth")} required error={errors.lunarMonth?.message}>
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

          <Controller
            control={control}
            name="lunarLeap"
            render={({ field }) => (
              <FormField label={t("lunarLeap")} hint={t("lunarLeapHint")}>
                {({ id }) => (
                  <span className="flex min-h-11 items-center gap-2">
                    <Switch id={id} checked={field.value} onChange={field.onChange} />
                    <span className="text-than text-text-muted">
                      {field.value ? t("yes") : t("no")}
                    </span>
                  </span>
                )}
              </FormField>
            )}
          />

          {!recurringAnnually && (
            <Controller
              control={control}
              name="lunarYear"
              render={({ field }) => (
                <FormField label={t("lunarYear")} required error={errors.lunarYear?.message}>
                  {({ id, status, describedBy }) => (
                    <InputNumber
                      id={id}
                      className="w-full"
                      size="large"
                      status={status}
                      min={1}
                      value={field.value === "" ? undefined : Number(field.value)}
                      onChange={(v) => field.onChange(v === null ? "" : String(v))}
                      onBlur={field.onBlur}
                      aria-describedby={describedBy}
                    />
                  )}
                </FormField>
              )}
            />
          )}
        </div>
      </section>

      <section className="rounded-lg border border-border bg-bg-card px-4 py-3 sm:px-5 sm:py-4">
        <h2 className="m-0 font-serif text-base font-semibold text-primary sm:text-lg">
          {t("sections.recurrence")}
        </h2>
        <div className="mt-2 space-y-3">
          <Controller
            control={control}
            name="recurringAnnually"
            render={({ field }) => (
              <FormField label={t("recurringAnnually")} hint={t("recurringAnnuallyHint")}>
                {({ id }) => (
                  <span className="flex min-h-11 items-center gap-2">
                    <Switch id={id} checked={field.value} onChange={field.onChange} />
                    <span className="text-than text-text-muted">
                      {field.value ? t("recurringYes") : t("recurringNo")}
                    </span>
                  </span>
                )}
              </FormField>
            )}
          />

          {!recurringAnnually && (
            <Controller
              control={control}
              name="solarDate"
              render={({ field }) => (
                <FormField
                  label={t("solarDate")}
                  hint={t("solarDateHint")}
                  error={errors.solarDate?.message}
                >
                  {({ id, status, describedBy }) => (
                    <Input
                      id={id}
                      {...field}
                      type="date"
                      size="large"
                      status={status}
                      aria-describedby={describedBy}
                    />
                  )}
                </FormField>
              )}
            />
          )}
        </div>
      </section>

      {/*
        Phạm vi — trường phải NỔI BẬT, vì nó quyết định AI BỊ NHẮC. Nhắc nhầm
        cả họ cho việc của một chi là cách nhanh nhất khiến người ta tắt thông
        báo (Việc 2, chỉ dẫn đợt này) — nên đặt hẳn một khối riêng, không gộp
        chung với các trường khác, và luôn kèm câu giải thích hệ quả ngay dưới
        lựa chọn.
      */}
      <section
        className="rounded-lg border-2 px-4 py-3 sm:px-5 sm:py-4"
        style={{ borderColor: colorVars.primary }}
      >
        <h2 className="m-0 font-serif text-base font-semibold text-primary sm:text-lg">
          {t("sections.scope")}
        </h2>
        <p className="m-0 mb-2 mt-0.5 text-than text-text-muted">{t("scopeHint")}</p>

        <Controller
          control={control}
          name="scope"
          render={({ field }) => (
            <Radio.Group
              value={field.value}
              onChange={(e) => field.onChange(e.target.value)}
              size="large"
              buttonStyle="solid"
              className="flex flex-wrap gap-2 [&>label]:!min-h-11 [&>label]:!flex [&>label]:items-center"
            >
              <Radio.Button
                value="BRANCH"
                disabled={forcedScope === "CLAN"}
                className="!min-h-11"
              >
                {t("scopeBranch")}
              </Radio.Button>
              <Radio.Button
                value="CLAN"
                disabled={!canClanWide || forcedScope === "BRANCH"}
                className="!min-h-11"
              >
                {t("scopeClan")}
              </Radio.Button>
            </Radio.Group>
          )}
        />
        {/*
          `forcedScope` đứng TRƯỚC `!canClanWide`: một Trưởng chi chọn "Giỗ
          chi" cũng đang ở ca bị khoá, và câu đúng cho họ là "loại này đã ấn
          định phạm vi", không phải "chỉ Hội đồng mới phát được cho cả họ" —
          câu thứ hai đúng nhưng lạc đề, vì họ chưa từng đụng tới ô "Cả dòng
          họ".
        */}
        {forcedScope ? (
          <p className="m-0 mt-1.5 text-than text-text-muted">
            {t("scopeForcedByType", { type: tEvents(`eventType.${eventType}`) })}
          </p>
        ) : (
          !canClanWide && (
            <p className="m-0 mt-1.5 text-than text-text-muted">{t("scopeClanDisabledHint")}</p>
          )
        )}

        {scope === "BRANCH" && (
          <div className="mt-3">
            <Controller
              control={control}
              name="scopeBranchId"
              render={({ field }) => (
                <FormField
                  label={t("scopeBranchLabel")}
                  required
                  error={errors.scopeBranchId?.message}
                >
                  {({ id, status }) => (
                    <Select
                      id={id}
                      className="w-full"
                      size="large"
                      status={status}
                      loading={branchesLoading}
                      showSearch
                      optionFilterProp="label"
                      value={field.value || undefined}
                      onChange={field.onChange}
                      options={allowedBranches.map((b) => ({
                        value: b.id,
                        label: b.name,
                        depth: branchDepth(b),
                      }))}
                      optionRender={(option) => (
                        <span
                          style={{
                            paddingLeft: `${(option.data as { depth: number }).depth * 12}px`,
                          }}
                        >
                          {option.label}
                        </span>
                      )}
                    />
                  )}
                </FormField>
              )}
            />
          </div>
        )}

        {scope === "CLAN" && <p className="m-0 mt-2 text-than text-text-muted">{t("scopeClanNote")}</p>}
      </section>

      <div className="sticky bottom-0 flex flex-wrap items-center gap-2 border-t border-border bg-bg-page/95 py-3 backdrop-blur">
        <Button type="primary" size="large" htmlType="submit" loading={isSubmitting}>
          {t(props.mode === "edit" ? "save" : "create")}
        </Button>
        <Button size="large" onClick={() => router.back()} disabled={isSubmitting}>
          {t("cancel")}
        </Button>
      </div>
    </form>
  );
}

/** Branches trên `code`, không trên `title`/`detail` (contracts/README §3). */
function SubmitError({ error }: { error: unknown }) {
  const t = useTranslations("eventForm");
  const apiError = error instanceof ApiError ? error : null;

  // Một bảng tra thay cho một chuỗi ba ngôi lồng sáu tầng: thêm một mã lỗi mới
  // phải là thêm một dòng, chứ không phải đếm lại dấu ngoặc. Khoá đi theo
  // `ProblemCode` nên một mã KHÔNG có trong enum đóng của contract là lỗi biên
  // dịch ngay tại đây, không phải một nhánh lặng lẽ rơi về câu chung. (`"UNKNOWN"`
  // là giá trị `ApiError` đặt khi máy chủ không gắn `code` — nó không có câu
  // riêng, và đó là đúng: lúc ấy ta thật sự không biết chuyện gì.)
  const CAU_THEO_MA: Partial<Record<ProblemCode | "UNKNOWN", string>> = {
    OPTIMISTIC_LOCK_CONFLICT: "errors.staleRecord",
    PRECONDITION_REQUIRED: "errors.missingEtag",
    GIO_DATE_FROM_PERSON: "errors.gioDateFromPerson",
    // `EVENT_DELETED` vừa được bổ sung vào enum đóng của contract — máy chủ ném
    // nó từ lâu. Chừng nào nó còn thiếu ở đó thì màn này rơi về "không lưu
    // được" đúng lúc người dùng cần biết rằng bản ghi đã bị xoá mềm.
    EVENT_DELETED: "errors.eventDeleted",
    FORBIDDEN: "errors.forbidden",
    BRANCH_SCOPE_VIOLATION: "errors.forbidden",
    VALIDATION_FAILED: "errors.serverValidation",
  };
  const key =
    (apiError?.code ? CAU_THEO_MA[apiError.code] : undefined) ?? "errors.submitFailed";

  return <Alert type="error" showIcon message={t(key)} description={apiError?.problem?.detail} />;
}

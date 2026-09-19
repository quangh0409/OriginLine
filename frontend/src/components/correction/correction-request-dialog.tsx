"use client";

import { useMemo } from "react";
import { Alert, App, Checkbox, Input, Modal, Radio, Select } from "antd";
import { useTranslations } from "next-intl";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/person-form/form-field";
import { useSubmitChangeRequest } from "@/hooks/use-change-requests";
import { ApiError } from "@/lib/api/http";
import { headlineName } from "@/lib/format/name-layers";
import { zodResolver } from "@/lib/form/zod-resolver";
import type { DateDual, PersonDto } from "@/types/api";
import {
  CORRECTABLE_FIELDS,
  OTHER_TOPIC,
  fieldSpec,
  type CorrectionTopic,
} from "./correctable-fields";
import { CorrectionValue } from "./correction-value";

/**
 * **Điểm bắt đầu của luồng đính chính** — người gửi chỉ ra chỗ ghi sai và đề
 * nghị giá trị đúng.
 *
 * <h2>Vì sao một trường một lần</h2>
 * Người dùng điển hình là thành viên 30–50 tuổi, dùng thưa, vừa nhận ra "cụ
 * nhà mình mất ngày 18 chứ không phải 15". Họ không mở ứng dụng để soạn thảo
 * một hồ sơ; họ mở để báo một việc. Một biểu mẫu đầy đủ như màn hình sửa nhân
 * khẩu sẽ khiến họ bỏ giữa chừng — và đề nghị nhiều trường một lúc còn buộc
 * người duyệt phải quyết "cả gói", trong khi thực tế họ thường đồng ý chỗ này
 * và không đồng ý chỗ kia.
 *
 * <h2>Lý do là bắt buộc</h2>
 * Người duyệt không ở trong nhà người gửi. "Ngày mất là 18 tháng Chạp" không
 * quyết được; "gia phả chép tay nhà cháu ghi 18, nhà cháu vẫn làm giỗ ngày ấy
 * từ đời ông nội" thì quyết được. Bắt buộc nêu lý do là cách rẻ nhất để hàng
 * đợi duyệt không biến thành một đống việc không ai dám động vào.
 *
 * <h2>Không suy diễn về dữ liệu bị ẩn</h2>
 * "Đang ghi trong gia phả" chỉ hiện khi hồ sơ **thực sự mang** trường ấy. Hồ sơ
 * đã được máy chủ lọc theo tầng riêng tư của người đang xem, và một trường vắng
 * mặt có thể là chưa ai ghi, cũng có thể là không được phép thấy — hợp đồng cố
 * ý không cho phân biệt hai điều đó.
 */

export interface CorrectionRequestDialogProps {
  person: PersonDto;
  open: boolean;
  onClose: () => void;
}

const DATE_PRECISIONS = ["DAY", "MONTH", "YEAR", "UNKNOWN"] as const;

interface CorrectionFormValues {
  topic: CorrectionTopic;
  text: string;
  gender: "MALE" | "FEMALE" | "UNKNOWN";
  date: {
    solar: string;
    precision: (typeof DATE_PRECISIONS)[number];
    lunarDay: string;
    lunarMonth: string;
    lunarYear: string;
    lunarLeap: boolean;
  };
  reason: string;
}

type Translate = (key: string, values?: Record<string, string | number>) => string;

/**
 * Chỉ nhánh đang hoạt động mới bị kiểm.
 *
 * Kiểm cả ba nhánh sẽ chặn người dùng vì một ô họ không nhìn thấy — lỗi kinh
 * điển của biểu mẫu có nhánh, và là kiểu lỗi khiến người dùng thưa bỏ cuộc vì
 * không hiểu mình sai ở đâu.
 */
function correctionSchema(t: Translate) {
  return z
    .object({
      topic: z.string(),
      text: z.string().trim().max(300).default(""),
      gender: z.enum(["MALE", "FEMALE", "UNKNOWN"]),
      date: z.object({
        solar: z.string().trim().default(""),
        precision: z.enum(DATE_PRECISIONS).default("DAY"),
        lunarDay: z.string().trim().default(""),
        lunarMonth: z.string().trim().default(""),
        lunarYear: z.string().trim().default(""),
        lunarLeap: z.boolean().default(false),
      }),
      // 2000 ký tự là giới hạn của `SubmitChangeRequestDto.reason` ở backend.
      reason: z.string().trim().min(10, t("errors.reasonTooShort")).max(2000, t("errors.reasonTooLong")),
    })
    .superRefine((values, ctx) => {
      if (values.topic === OTHER_TOPIC) return;
      const spec = fieldSpec(values.topic);
      if (!spec) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["topic"], message: t("errors.topicRequired") });
        return;
      }
      if (spec.kind === "text" && values.text.length === 0) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["text"], message: t("errors.valueRequired") });
      }
      if (spec.kind === "date") {
        const num = (raw: string) => (raw === "" ? null : Number(raw));
        const day = num(values.date.lunarDay);
        const month = num(values.date.lunarMonth);
        const hasSolar = values.date.solar.length > 0;
        if (!hasSolar && day === null && month === null) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["date", "solar"],
            message: t("errors.dateRequired"),
          });
        }
        if (day !== null && (!Number.isInteger(day) || day < 1 || day > 30)) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["date", "lunarDay"],
            message: t("errors.lunarDay"),
          });
        }
        if (month !== null && (!Number.isInteger(month) || month < 1 || month > 12)) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["date", "lunarMonth"],
            message: t("errors.lunarMonth"),
          });
        }
        // Ngày âm mà thiếu tháng thì không tính được giỗ — cặp ấy đi liền nhau.
        if (day !== null && month === null) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["date", "lunarMonth"],
            message: t("errors.lunarMonthRequired"),
          });
        }
      }
    });
}

/** Ghép các ô đã gõ thành `DateDual` đúng hợp đồng. Không quy đổi âm–dương. */
function toDateDual(date: CorrectionFormValues["date"]): DateDual {
  const num = (raw: string) => (raw === "" ? null : Number(raw));
  const day = num(date.lunarDay);
  const month = num(date.lunarMonth);
  const year = num(date.lunarYear);
  const hasLunar = day !== null || month !== null || year !== null;

  return {
    solar: date.solar === "" ? null : date.solar,
    lunar: hasLunar
      ? {
          year: year ?? 0,
          month: month ?? 0,
          day: day ?? 0,
          leap: date.lunarLeap,
        }
      : null,
    precision: date.precision,
  };
}

export function CorrectionRequestDialog({ person, open, onClose }: CorrectionRequestDialogProps) {
  const t = useTranslations("correction");
  const tForm = useTranslations("personForm");
  const { message } = App.useApp();
  const schema = useMemo(() => correctionSchema(t), [t]);
  const submitMutation = useSubmitChangeRequest();

  const {
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<CorrectionFormValues>({
    resolver: zodResolver<CorrectionFormValues>(schema),
    defaultValues: {
      topic: "death",
      text: "",
      gender: person.gender ?? "UNKNOWN",
      date: {
        solar: "",
        precision: "DAY",
        lunarDay: "",
        lunarMonth: "",
        lunarYear: "",
        lunarLeap: false,
      },
      reason: "",
    },
    mode: "onBlur",
  });

  const topic = watch("topic");
  const spec = topic === OTHER_TOPIC ? undefined : fieldSpec(topic);
  const currentValue = spec ? spec.read(person) : undefined;
  const personName = headlineName(person) ?? "";

  const close = () => {
    reset();
    submitMutation.reset();
    onClose();
  };

  const onValid = async (values: CorrectionFormValues) => {
    const payload: Record<string, unknown> = {};
    if (values.topic !== OTHER_TOPIC) {
      const active = fieldSpec(values.topic);
      if (active?.kind === "text") payload[active.key] = values.text;
      if (active?.kind === "gender") payload[active.key] = values.gender;
      if (active?.kind === "date") payload[active.key] = toDateDual(values.date);
    }

    try {
      await submitMutation.mutateAsync({
        // `OTHER` khi không gắn với trường nào: backend không biết áp dụng vào
        // đâu, và nói thật điều đó tốt hơn là giả vờ đây là một `UPDATE_PERSON`.
        requestType: values.topic === OTHER_TOPIC ? "OTHER" : "UPDATE_PERSON",
        personId: person.id,
        payload,
        reason: values.reason,
      });
      message.success(t("dialog.sent"));
      close();
    } catch {
      // Lỗi hiện ngay trong hộp thoại (xem <SubmitError/>) để người dùng còn
      // giữ nguyên những gì đã gõ mà sửa lại.
    }
  };

  return (
    <Modal
      open={open}
      onCancel={close}
      title={t("dialog.title", { name: personName })}
      okText={t("dialog.submit")}
      cancelText={t("dialog.cancel")}
      confirmLoading={submitMutation.isPending}
      onOk={() => void handleSubmit(onValid)()}
      destroyOnClose
      width={640}
    >
      <form className="flex flex-col gap-3" onSubmit={(e) => e.preventDefault()} noValidate>
        <p className="m-0 text-than text-text-muted">{t("dialog.lead")}</p>

        {submitMutation.error && <SubmitError error={submitMutation.error} />}

        <Controller
          control={control}
          name="topic"
          render={({ field }) => (
            <FormField label={t("dialog.fieldLabel")} required error={errors.topic?.message}>
              {({ id }) => (
                <Select
                  id={id}
                  {...field}
                  size="large"
                  className="w-full"
                  options={[
                    ...CORRECTABLE_FIELDS.map((f) => ({
                      value: f.key,
                      label: t(`fields.${f.key}`),
                    })),
                    { value: OTHER_TOPIC, label: t("fields.OTHER") },
                  ]}
                />
              )}
            </FormField>
          )}
        />

        {/* Chỉ hiện khi hồ sơ THẬT SỰ mang giá trị ấy. Vắng thì im lặng — xem
            ghi chú ở đầu tệp về việc không suy diễn dữ liệu bị ẩn. */}
        {currentValue !== undefined && (
          <div className="rounded-md border border-border bg-bg-page px-3 py-2">
            <p className="m-0 text-than text-text-muted">{t("dialog.currentLabel")}</p>
            <div className="text-than text-text-main">
              <CorrectionValue fieldKey={topic} value={currentValue} />
            </div>
          </div>
        )}

        {spec?.kind === "text" && (
          <Controller
            control={control}
            name="text"
            render={({ field }) => (
              <FormField label={t("dialog.proposedLabel")} required error={errors.text?.message}>
                {({ id, status, describedBy }) => (
                  <Input id={id} {...field} size="large" status={status} aria-describedby={describedBy} />
                )}
              </FormField>
            )}
          />
        )}

        {spec?.kind === "gender" && (
          <Controller
            control={control}
            name="gender"
            render={({ field }) => (
              <FormField label={t("dialog.proposedLabel")} required>
                {({ id }) => (
                  <Radio.Group id={id} {...field} size="large" buttonStyle="solid">
                    <Radio.Button value="MALE">{tForm("genderOption.MALE")}</Radio.Button>
                    <Radio.Button value="FEMALE">{tForm("genderOption.FEMALE")}</Radio.Button>
                    <Radio.Button value="UNKNOWN">{tForm("genderOption.UNKNOWN")}</Radio.Button>
                  </Radio.Group>
                )}
              </FormField>
            )}
          />
        )}

        {spec?.kind === "date" && (
          <fieldset className="m-0 rounded-lg border border-border bg-bg-page p-3">
            <legend className="px-1 text-than font-medium text-text-main">
              {t("dialog.proposedLabel")}
            </legend>

            <div className="grid gap-3 sm:grid-cols-2">
              <Controller
                control={control}
                name="date.solar"
                render={({ field }) => (
                  <FormField label={tForm("date.solar")} error={errors.date?.solar?.message}>
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
              <Controller
                control={control}
                name="date.precision"
                render={({ field }) => (
                  <FormField label={tForm("date.precision")} hint={tForm("date.precisionHint")}>
                    {({ id }) => (
                      <Select
                        id={id}
                        {...field}
                        size="large"
                        className="w-full"
                        options={DATE_PRECISIONS.map((value) => ({
                          value,
                          label: tForm(`date.precisionOption.${value}`),
                        }))}
                      />
                    )}
                  </FormField>
                )}
              />
            </div>

            <div className="mt-3">
              <p className="m-0 mb-1.5 text-than font-medium text-text-main">
                {tForm("date.lunar")}
              </p>
              <div className="grid grid-cols-3 gap-2">
                <Controller
                  control={control}
                  name="date.lunarDay"
                  render={({ field }) => (
                    <FormField label={tForm("date.lunarDay")} error={errors.date?.lunarDay?.message}>
                      {({ id, status, describedBy }) => (
                        <Input
                          id={id}
                          {...field}
                          size="large"
                          inputMode="numeric"
                          maxLength={2}
                          status={status}
                          aria-describedby={describedBy}
                        />
                      )}
                    </FormField>
                  )}
                />
                <Controller
                  control={control}
                  name="date.lunarMonth"
                  render={({ field }) => (
                    <FormField
                      label={tForm("date.lunarMonth")}
                      error={errors.date?.lunarMonth?.message}
                    >
                      {({ id, status, describedBy }) => (
                        <Input
                          id={id}
                          {...field}
                          size="large"
                          inputMode="numeric"
                          maxLength={2}
                          status={status}
                          aria-describedby={describedBy}
                        />
                      )}
                    </FormField>
                  )}
                />
                <Controller
                  control={control}
                  name="date.lunarYear"
                  render={({ field }) => (
                    <FormField
                      label={tForm("date.lunarYear")}
                      error={errors.date?.lunarYear?.message}
                    >
                      {({ id, status, describedBy }) => (
                        <Input
                          id={id}
                          {...field}
                          size="large"
                          inputMode="numeric"
                          maxLength={4}
                          status={status}
                          aria-describedby={describedBy}
                        />
                      )}
                    </FormField>
                  )}
                />
              </div>

              <Controller
                control={control}
                name="date.lunarLeap"
                render={({ field }) => (
                  <Checkbox
                    className="!mt-2"
                    checked={field.value}
                    onChange={(event) => field.onChange(event.target.checked)}
                  >
                    {tForm("date.lunarLeap")}
                  </Checkbox>
                )}
              />
              <p className="m-0 mt-1 text-than leading-snug text-text-muted">
                {tForm(topic === "death" ? "date.lunarDeathHint" : "date.lunarHint")}
              </p>
            </div>
          </fieldset>
        )}

        {topic === OTHER_TOPIC && (
          <Alert type="info" showIcon message={t("dialog.otherHint")} />
        )}

        <Controller
          control={control}
          name="reason"
          render={({ field }) => (
            <FormField
              label={t("dialog.reasonLabel")}
              hint={t("dialog.reasonHint")}
              required
              error={errors.reason?.message}
            >
              {({ id, status, describedBy }) => (
                <Input.TextArea
                  id={id}
                  {...field}
                  rows={4}
                  status={status}
                  aria-describedby={describedBy}
                  placeholder={t("dialog.reasonPlaceholder")}
                />
              )}
            </FormField>
          )}
        />
      </form>
    </Modal>
  );
}

/** Phân nhánh theo `code`, không theo `title`/`detail` (contracts/README §3). */
function SubmitError({ error }: { error: ApiError }) {
  const t = useTranslations("correction");
  const key =
    error.code === "ACCOUNT_NOT_PROVISIONED"
      ? "errors.notProvisioned"
      : error.code === "ACCOUNT_NOT_ACTIVE"
        ? "errors.notActive"
        : error.code === "VALIDATION_FAILED"
          ? "errors.serverValidation"
          : "errors.submitFailed";
  return <Alert type="error" showIcon message={t(key)} description={error.problem?.detail} />;
}

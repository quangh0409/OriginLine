"use client";

import { Checkbox, Input, Select } from "antd";
import { useTranslations } from "next-intl";
import { Controller, type Control, type FieldErrors } from "react-hook-form";
import { FormField } from "./form-field";
import type { PersonFormValues } from "./person-form-schema";

export interface DateDualFieldProps {
  control: Control<PersonFormValues>;
  errors: FieldErrors<PersonFormValues>;
  name: "birth" | "death";
  legend: string;
  disabled?: boolean;
}

/**
 * Dual-calendar date entry: a solar date and a lunar date, entered SEPARATELY
 * and never derived from each other.
 *
 * This is the deliberate design, not a missing feature. Lunar<->solar
 * conversion is a backend service (Hồ Ngọc Đức, GMT+7, leap months, tiết
 * khí); auto-filling one side here would be wrong by a whole month around a
 * leap month and — worse — wrong with no error (contracts/README §7.8). Old
 * clan books very often record ONLY the lunar date, which is exactly why both
 * halves are independently optional.
 *
 * `precision` says how much of the date is actually known. `UNKNOWN` is a real
 * answer for an ancestor whose dates were never written down, and is
 * preferable to inventing a January 1st.
 *
 * A native `<input type="date">` is used instead of a JS date picker: it gets
 * the OS's own picker on a phone (the primary form factor here), needs no
 * extra library, and hands back a plain `YYYY-MM-DD` string — the format the
 * contract wants.
 */
export function DateDualField({
  control,
  errors,
  name,
  legend,
  disabled,
}: DateDualFieldProps) {
  const t = useTranslations("personForm");
  const fieldErrors = errors[name];

  return (
    <fieldset className="m-0 rounded-lg border border-border bg-bg-page p-3" disabled={disabled}>
      <legend className="px-1 text-[13px] font-medium text-text-main">{legend}</legend>

      <div className="grid gap-3 sm:grid-cols-2">
        <Controller
          control={control}
          name={`${name}.solar`}
          render={({ field }) => (
            <FormField label={t("date.solar")} error={fieldErrors?.solar?.message}>
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
          name={`${name}.precision`}
          render={({ field }) => (
            <FormField label={t("date.precision")} hint={t("date.precisionHint")}>
              {({ id }) => (
                <Select
                  id={id}
                  {...field}
                  size="large"
                  className="w-full"
                  options={(["DAY", "MONTH", "YEAR", "UNKNOWN"] as const).map((value) => ({
                    value,
                    label: t(`date.precisionOption.${value}`),
                  }))}
                />
              )}
            </FormField>
          )}
        />
      </div>

      <div className="mt-3">
        <p className="m-0 mb-1.5 text-[13px] font-medium text-text-main">{t("date.lunar")}</p>
        <div className="grid grid-cols-3 gap-2">
          <Controller
            control={control}
            name={`${name}.lunarDay`}
            render={({ field }) => (
              <FormField label={t("date.lunarDay")} error={fieldErrors?.lunarDay?.message}>
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
            name={`${name}.lunarMonth`}
            render={({ field }) => (
              <FormField label={t("date.lunarMonth")} error={fieldErrors?.lunarMonth?.message}>
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
            name={`${name}.lunarYear`}
            render={({ field }) => (
              <FormField label={t("date.lunarYear")} error={fieldErrors?.lunarYear?.message}>
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
          name={`${name}.lunarLeap`}
          render={({ field }) => (
            <Checkbox
              className="!mt-2"
              checked={field.value}
              onChange={(event) => field.onChange(event.target.checked)}
            >
              {t("date.lunarLeap")}
            </Checkbox>
          )}
        />
        <p className="m-0 mt-1 text-[12px] leading-snug text-text-muted">
          {t(name === "death" ? "date.lunarDeathHint" : "date.lunarHint")}
        </p>
      </div>
    </fieldset>
  );
}

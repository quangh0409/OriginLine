"use client";

import { Button, Input, Radio, Select } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import {
  Controller,
  useFieldArray,
  useWatch,
  type Control,
  type FieldErrors,
  type UseFormSetValue,
} from "react-hook-form";
import { NAME_TYPE_ORDER } from "@/lib/format/name-layers";
import { FormField } from "./form-field";
import type { PersonFormValues } from "./person-form-schema";

export interface NameLayersFieldProps {
  control: Control<PersonFormValues>;
  errors: FieldErrors<PersonFormValues>;
  setValue: UseFormSetValue<PersonFormValues>;
}

/**
 * Editor for the multi-layer name list.
 *
 * A person is not "one name plus aliases": tên húy, tên tự, tên hiệu, tên
 * thụy, tên thường gọi and pháp danh are distinct records with different
 * ritual weight, and collapsing them would lose information the clan book
 * exists to preserve. So the array is first-class here — add, remove, reorder
 * by intent, each with its own Hán-Nôm and note.
 *
 * Exactly one layer is `isPrimary` (enforced by the schema, expressed as a
 * radio group rather than checkboxes so the UI can't offer an invalid state).
 * The primary layer is what the tree node and profile heading show.
 *
 * The tên húy field is the one the backend scans for kỵ húy collisions — the
 * check itself is server-side, so there is no client-side warning here that
 * could disagree with it.
 */
export function NameLayersField({ control, errors, setValue }: NameLayersFieldProps) {
  const t = useTranslations("personForm");
  const tPerson = useTranslations("person");
  const { fields, append, remove } = useFieldArray({ control, name: "names" });

  // Read the array through useWatch rather than a <Controller name="names">:
  // registering the field-array name as an ordinary field as well makes RHF
  // keep two sets of bookkeeping for it, and remove()/append() then race the
  // controller's own onChange.
  const names = useWatch({ control, name: "names" });
  const primaryIndex = names?.findIndex((n) => n.isPrimary) ?? 0;

  const setPrimary = (index: number) => {
    (names ?? []).forEach((_, i) => {
      setValue(`names.${i}.isPrimary`, i === index, { shouldDirty: true });
    });
  };

  const nameTypeOptions = NAME_TYPE_ORDER.map((type) => ({
    value: type,
    label: tPerson(`nameType.${type}`),
  }));

  return (
    <div className="space-y-3">
      <Radio.Group
        className="w-full"
        value={primaryIndex}
        onChange={(event) => setPrimary(event.target.value as number)}
      >
        <div className="space-y-3">
              {fields.map((item, index) => (
                <div
                  key={item.id}
                  className="rounded-lg border border-border bg-bg-page p-3"
                >
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <Radio value={index} className="!text-than">
                      {t("names.primary")}
                    </Radio>
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      aria-label={t("names.remove")}
                      disabled={fields.length <= 1}
                      onClick={() => remove(index)}
                    />
                  </div>

                  <div className="grid gap-3 sm:grid-cols-[minmax(0,10rem)_minmax(0,1fr)]">
                    <Controller
                      control={control}
                      name={`names.${index}.nameType`}
                      render={({ field }) => (
                        <FormField label={t("names.type")}>
                          {({ id }) => (
                            <Select
                              id={id}
                              {...field}
                              size="large"
                              className="w-full"
                              options={nameTypeOptions}
                            />
                          )}
                        </FormField>
                      )}
                    />

                    <Controller
                      control={control}
                      name={`names.${index}.fullName`}
                      render={({ field }) => (
                        <FormField
                          label={t("names.fullName")}
                          required
                          error={errors.names?.[index]?.fullName?.message}
                        >
                          {({ id, status, describedBy }) => (
                            <Input
                              id={id}
                              {...field}
                              size="large"
                              status={status}
                              aria-describedby={describedBy}
                              // Vietnamese input methods compose diacritics as
                              // the user types; autocapitalise/autocorrect on
                              // mobile keyboards mangles them.
                              autoCapitalize="words"
                              autoCorrect="off"
                              spellCheck={false}
                              placeholder={t("names.fullNamePlaceholder")}
                            />
                          )}
                        </FormField>
                      )}
                    />
                  </div>

                  <div className="mt-3 grid gap-3 sm:grid-cols-2">
                    <Controller
                      control={control}
                      name={`names.${index}.nameHanNom`}
                      render={({ field }) => (
                        <FormField
                          label={t("names.hanNom")}
                          hint={t("names.hanNomHint")}
                          error={errors.names?.[index]?.nameHanNom?.message}
                        >
                          {({ id, status, describedBy }) => (
                            <Input
                              id={id}
                              {...field}
                              size="large"
                              lang="zh-Hant"
                              status={status}
                              aria-describedby={describedBy}
                              className="!font-serif"
                            />
                          )}
                        </FormField>
                      )}
                    />

                    <Controller
                      control={control}
                      name={`names.${index}.note`}
                      render={({ field }) => (
                        <FormField
                          label={t("names.note")}
                          error={errors.names?.[index]?.note?.message}
                        >
                          {({ id, status, describedBy }) => (
                            <Input
                              id={id}
                              {...field}
                              size="large"
                              status={status}
                              aria-describedby={describedBy}
                            />
                          )}
                        </FormField>
                      )}
                    />
                  </div>
                </div>
              ))}
        </div>
      </Radio.Group>

      {/* Schema-level error (e.g. "exactly one primary") has no single input
          to hang off, so it is surfaced next to the list as a whole. */}
      {typeof errors.names?.message === "string" && (
        <p role="alert" className="m-0 text-than text-[color:var(--color-primary)]">
          {errors.names.message}
        </p>
      )}

      <Button
        icon={<PlusOutlined />}
        onClick={() =>
          append({ nameType: "TU", fullName: "", nameHanNom: "", isPrimary: false, note: "" })
        }
      >
        {t("names.add")}
      </Button>
    </div>
  );
}

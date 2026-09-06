"use client";

import { Checkbox, Input, Select } from "antd";
import { useTranslations } from "next-intl";
import { Controller, type Control, type FieldErrors, type UseFormWatch } from "react-hook-form";
import { PersonPicker } from "@/components/person/person-picker";
import { FormField } from "./form-field";
import type { PersonFormValues } from "./person-form-schema";

export interface RelationshipLinkFieldProps {
  control: Control<PersonFormValues>;
  errors: FieldErrors<PersonFormValues>;
  watch: UseFormWatch<PersonFormValues>;
}

/**
 * Attaches a brand-new person to someone already in the tree, at creation time.
 *
 * Without this the record lands as an orphan node: `generation` is derived
 * from parent-child edges server-side, so an unlinked person has no đời at all
 * (contracts/README §5.7) and never appears on the phả đồ.
 *
 * `otherPersonRole` exists because `RelType` is DIRECTED (`PARENT_BIO` means
 * parent -> child) while the new person has no id yet, so the request has to
 * say which END of the edge the existing person occupies. It is phrased here
 * as plain kinship ("this person is the new record's father/mother" vs. "the
 * new record is their child") rather than as SOURCE/TARGET, which means
 * nothing to a clan secretary.
 *
 * Editing an EXISTING edge is not offered: the contract has no endpoint for it
 * yet (contracts/README §6.3 proposes `POST|PATCH|DELETE /relationships`), and
 * a form that pretended otherwise would fail at submit.
 */
export function RelationshipLinkField({ control, errors, watch }: RelationshipLinkFieldProps) {
  const t = useTranslations("personForm");
  const enabled = watch("relationship.enabled");
  const relType = watch("relationship.relType");

  return (
    <div className="rounded-lg border border-border bg-bg-page p-3">
      <Controller
        control={control}
        name="relationship.enabled"
        render={({ field }) => (
          <Checkbox
            checked={field.value}
            onChange={(event) => field.onChange(event.target.checked)}
          >
            {t("relation.enable")}
          </Checkbox>
        )}
      />
      <p className="m-0 mt-1 text-[12px] leading-snug text-text-muted">{t("relation.hint")}</p>

      {enabled && (
        <div className="mt-3 space-y-3">
          <Controller
            control={control}
            name="relationship.otherPersonId"
            render={({ field }) => (
              <FormField
                label={t("relation.person")}
                required
                error={errors.relationship?.otherPersonId?.message}
              >
                {({ id, status }) => (
                  <PersonPicker
                    id={id}
                    value={field.value === "" ? undefined : field.value}
                    status={status}
                    onChange={(next) => field.onChange(next ?? "")}
                  />
                )}
              </FormField>
            )}
          />

          <div className="grid gap-3 sm:grid-cols-2">
            <Controller
              control={control}
              name="relationship.relType"
              render={({ field }) => (
                <FormField label={t("relation.type")}>
                  {({ id }) => (
                    <Select
                      id={id}
                      {...field}
                      size="large"
                      className="w-full"
                      options={(["PARENT_BIO", "PARENT_ADOPT", "SPOUSE"] as const).map((value) => ({
                        value,
                        label: t(`relation.relType.${value}`),
                      }))}
                    />
                  )}
                </FormField>
              )}
            />

            {relType === "SPOUSE" ? (
              <Controller
                control={control}
                name="relationship.spouseOrder"
                render={({ field }) => (
                  <FormField label={t("relation.spouseOrder")} hint={t("relation.spouseOrderHint")}>
                    {({ id }) => (
                      <Input id={id} {...field} size="large" inputMode="numeric" maxLength={2} />
                    )}
                  </FormField>
                )}
              />
            ) : (
              <Controller
                control={control}
                name="relationship.otherPersonRole"
                render={({ field }) => (
                  <FormField label={t("relation.direction")}>
                    {({ id }) => (
                      <Select
                        id={id}
                        {...field}
                        size="large"
                        className="w-full"
                        options={[
                          { value: "SOURCE", label: t("relation.otherIsParent") },
                          { value: "TARGET", label: t("relation.otherIsChild") },
                        ]}
                      />
                    )}
                  </FormField>
                )}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

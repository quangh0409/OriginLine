"use client";

import { MailOutlined, MessageOutlined, PhoneOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { anyPresent } from "@/lib/privacy/present";
import { OptionalField } from "./optional-field";
import { PersonSection } from "./person-section";
import type { PersonDto } from "@/types/api";

export interface PersonContactProps {
  person: PersonDto;
}

/**
 * Tier 3 in full: phone, email, Zalo. The whole `contact` object is absent
 * unless the caller is the person themselves, an admin, or the person opted
 * in — so in practice this entire section is missing far more often than it
 * is present, which is the intended default (BA v2 §10, minors maximally
 * hidden).
 *
 * There is deliberately no "liên hệ riêng tư" placeholder: the absence of the
 * section is the whole answer.
 */
export function PersonContact({ person }: PersonContactProps) {
  const t = useTranslations("person");
  const contact = person.contact;

  return (
    <PersonSection
      title={t("contact")}
      hasContent={anyPresent(contact?.phone, contact?.email, contact?.zaloId)}
    >
      <dl className="m-0 divide-y divide-border">
        <OptionalField label={t("phone")} value={contact?.phone}>
          <a className="text-primary" href={`tel:${contact?.phone}`}>
            <PhoneOutlined className="mr-1.5" />
            {contact?.phone}
          </a>
        </OptionalField>
        <OptionalField label={t("email")} value={contact?.email}>
          <a className="break-all text-primary" href={`mailto:${contact?.email}`}>
            <MailOutlined className="mr-1.5" />
            {contact?.email}
          </a>
        </OptionalField>
        <OptionalField label={t("zalo")} value={contact?.zaloId}>
          <span>
            <MessageOutlined className="mr-1.5" />
            {contact?.zaloId}
          </span>
        </OptionalField>
      </dl>
    </PersonSection>
  );
}

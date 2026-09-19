"use client";

import { useTranslations } from "next-intl";
import { Card, Tag, Typography } from "antd";
import {
  ApartmentOutlined,
  CalendarOutlined,
  TeamOutlined,
} from "@ant-design/icons";

/**
 * "use client" is required here, not optional — this component destructures
 * antd's compound Typography sub-components (`Typography.Title`/`.Text`).
 * Doing that from a Server Component silently breaks (see the long comment
 * in src/components/layout/app-shell.tsx for why). `useTranslations` (the
 * client hook) replaces the server-only `getTranslations` used previously.
 *
 * Deliberately minimal for F0/F1: this sprint only builds chrome (AppShell,
 * Header, LanguageSwitcher) and the design system, not the tree canvas (F2)
 * or any real person data (F3+). The three cards below describe upcoming
 * features and carry no genealogy data of their own, so there is nothing
 * here that could leak privacy-tiered information.
 */
export function HomeHero() {
  const t = useTranslations("home");

  const cards = [
    { key: "tree", icon: <ApartmentOutlined /> },
    { key: "kinship", icon: <TeamOutlined /> },
    { key: "events", icon: <CalendarOutlined /> },
  ] as const;

  return (
    <div className="mx-auto max-w-4xl px-4 py-12 sm:py-16">
      <div className="rounded border border-border bg-bg-card px-6 py-10 text-center shadow-sm sm:px-12 sm:py-14">
        <span className="mb-4 inline-block h-1 w-16 rounded bg-accent" aria-hidden />
        <Typography.Title level={1} className="!mb-3 !text-primary">
          {t("heading")}
        </Typography.Title>
        <Typography.Paragraph className="!mb-1 text-base text-text-muted">
          {t("subheading")}
        </Typography.Paragraph>
        <Typography.Paragraph className="mx-auto max-w-xl text-text-muted">
          {t("welcomeBody")}
        </Typography.Paragraph>
      </div>

      <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
        {cards.map(({ key, icon }) => (
          <Card key={key} className="border-border" variant="outlined">
            <div className="mb-3 text-2xl text-primary">{icon}</div>
            <Typography.Text strong className="block text-text-main">
              {t(`cards.${key}.title`)}
            </Typography.Text>
            <Typography.Text className="mt-1 block text-than text-text-muted">
              {t(`cards.${key}.desc`)}
            </Typography.Text>
            <Tag className="mt-3" color="gold">
              {t("comingSoon")}
            </Tag>
          </Card>
        ))}
      </div>
    </div>
  );
}

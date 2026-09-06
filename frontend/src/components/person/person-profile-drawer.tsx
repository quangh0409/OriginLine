"use client";

import { Drawer, Grid } from "antd";
import { useTranslations } from "next-intl";
import { PersonProfile } from "./person-profile";

export interface PersonProfileDrawerProps {
  personId: string | null;
  onClose: () => void;
}

/**
 * Profile panel opened from a node on the phả đồ canvas. A drawer rather than
 * a route change so the canvas keeps its viewport, expanded branches and
 * loaded data — re-mounting the tree to read one profile would throw away the
 * lazy-loaded subgraph the user just built up.
 *
 * Mobile-first: bottom sheet on phones (thumb reach, and the tree keeps the
 * top of the screen), side panel from `sm` up.
 */
export function PersonProfileDrawer({ personId, onClose }: PersonProfileDrawerProps) {
  const t = useTranslations("person");
  const screens = Grid.useBreakpoint();
  const isDesktop = Boolean(screens.sm);

  return (
    <Drawer
      open={personId !== null}
      onClose={onClose}
      title={t("profileTitle")}
      placement={isDesktop ? "right" : "bottom"}
      width={isDesktop ? 460 : undefined}
      height={isDesktop ? undefined : "82vh"}
      destroyOnClose
      styles={{ body: { background: "var(--color-bg-page)", padding: 12 } }}
    >
      {personId && <PersonProfile personId={personId} compact />}
    </Drawer>
  );
}

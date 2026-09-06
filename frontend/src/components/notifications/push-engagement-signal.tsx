"use client";

import { useRecordPushEngagement } from "@/hooks/use-push-prompt";
import type { EngagementReason } from "@/lib/push/subscription-store";

/**
 * Headless. Renders nothing; its only job is to note that the user has now
 * seen something worth being reminded about, which is what unlocks the Web
 * Push permission card (plan §4 F7 — ask *after* a profile or a giỗ, not on
 * arrival).
 *
 * Mounted from page components rather than from inside <PersonProfile> /
 * <EventsScreen> so the F3/F7 view components stay free of push concerns and
 * the signal stays tied to an actual navigation.
 */
export function PushEngagementSignal({ reason }: { reason: EngagementReason }) {
  useRecordPushEngagement(reason);
  return null;
}

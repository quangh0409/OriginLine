import { isPresent } from "@/lib/privacy/present";
import type { NotificationDto } from "@/types/api";

/**
 * Turns a notification into a route THIS app actually has.
 *
 * `deepLink` is server-composed and app-relative (contracts/openapi.yaml
 * gives `/events/{id}` as the example). The frontend has no per-event route —
 * there is no `GET /events/{id}` in the contract to build one on, only the
 * list — so an event link is rewritten to the events screen with the event
 * pre-selected. Everything else is passed through untouched.
 *
 * Flagged to backend: either add `GET /events/{id}` or let the frontend own
 * deep-link shapes. Until then this adapter is the seam, so a mismatch shows
 * up in one file instead of as a 404 the user hits from a reminder.
 */
export function resolveNotificationHref(notification: NotificationDto): string | undefined {
  const { deepLink, eventId, personId } = notification;

  if (isPresent(deepLink)) {
    const matchedEventId = /^\/events\/([^/?#]+)$/.exec(deepLink)?.[1];
    if (matchedEventId) return `/events?event=${encodeURIComponent(matchedEventId)}`;
    // Only same-origin, app-relative paths are followed — a notification is
    // not a place to accept an absolute URL from.
    if (deepLink.startsWith("/")) return deepLink;
  }

  if (isPresent(eventId)) return `/events?event=${encodeURIComponent(eventId)}`;
  if (isPresent(personId)) return `/persons/${encodeURIComponent(personId)}`;
  return undefined;
}

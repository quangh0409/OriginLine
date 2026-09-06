import { apiFetch } from "./http";
import type { EventPage, EventType } from "@/types/api";

export interface ListEventsParams {
  from?: string; // solar date, YYYY-MM-DD
  to?: string;
  upcomingDays?: number; // shortcut for from/to, max 400
  eventType?: EventType[];
  branchId?: string;
  personId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const eventsApi = {
  list: (params: ListEventsParams = {}) =>
    apiFetch<EventPage>("/api/v1/events", { query: { ...params } }),
};

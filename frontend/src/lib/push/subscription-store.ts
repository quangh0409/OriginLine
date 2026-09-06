/**
 * Small localStorage record of this device's push state and of *when it is
 * polite to ask*.
 *
 * Two things live here that the API deliberately does not provide:
 *
 * 1. **The subscription record id.** `DELETE /push/subscriptions/{id}` takes
 *    the server-side record id, and the contract has no "list my
 *    subscriptions" endpoint — so the id from the POST response is the only
 *    handle we will ever get for turning push off again. Losing it would
 *    strand a subscription server-side that the user believes they revoked.
 *    (Flagged to backend as a contract gap; a `GET /push/subscriptions`
 *    would let us stop persisting this.)
 *
 * 2. **Permission-prompt timing.** The browser gives one shot at
 *    `Notification.requestPermission()`: a "deny" is effectively permanent
 *    and cannot be re-asked from script. Asking on page load — before the
 *    user has any reason to want reminders — is therefore not merely rude,
 *    it burns the only chance. So we count engagement signals (a profile
 *    read, a giỗ read) and only surface the prompt afterwards, per plan §4
 *    F7 "xin quyền đúng lúc".
 */

const RECORD_ID_KEY = "giapha.push.record-id";
const PROMPT_STATE_KEY = "giapha.push.prompt";

/** What the user did that suggests they'd want a reminder. */
export type EngagementReason = "PERSON_PROFILE" | "EVENT";

export interface PromptState {
  /** Distinct engagement kinds seen so far. */
  reasons: EngagementReason[];
  /** Epoch ms of the last "để sau" dismissal, or null if never dismissed. */
  dismissedAt: number | null;
}

const EMPTY_PROMPT_STATE: PromptState = { reasons: [], dismissedAt: null };

/** Long enough that "để sau" means it, short enough to catch the next giỗ season. */
export const DISMISS_COOLDOWN_MS = 30 * 24 * 60 * 60 * 1000;

function safeRead(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    // Safari private mode throws on localStorage access. Push is a nicety;
    // never let its bookkeeping break the page.
    return null;
  }
}

function safeWrite(key: string, value: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* ignore — see safeRead */
  }
}

function safeRemove(key: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

export function readPushRecordId(): string | null {
  return safeRead(RECORD_ID_KEY);
}

export function writePushRecordId(id: string): void {
  safeWrite(RECORD_ID_KEY, id);
}

export function clearPushRecordId(): void {
  safeRemove(RECORD_ID_KEY);
}

export function readPromptState(): PromptState {
  const raw = safeRead(PROMPT_STATE_KEY);
  if (!raw) return EMPTY_PROMPT_STATE;
  try {
    const parsed = JSON.parse(raw) as Partial<PromptState>;
    return {
      reasons: Array.isArray(parsed.reasons) ? parsed.reasons : [],
      dismissedAt: typeof parsed.dismissedAt === "number" ? parsed.dismissedAt : null,
    };
  } catch {
    return EMPTY_PROMPT_STATE;
  }
}

function writePromptState(state: PromptState): void {
  safeWrite(PROMPT_STATE_KEY, JSON.stringify(state));
}

/**
 * Records that the user did something reminder-worthy. Returns the new state
 * so callers can react without a second read.
 */
export function recordEngagement(reason: EngagementReason): PromptState {
  const current = readPromptState();
  if (current.reasons.includes(reason)) return current;
  const next: PromptState = { ...current, reasons: [...current.reasons, reason] };
  writePromptState(next);
  notifyPromptStateChanged();
  return next;
}

export function dismissPrompt(): PromptState {
  const next: PromptState = { ...readPromptState(), dismissedAt: Date.now() };
  writePromptState(next);
  notifyPromptStateChanged();
  return next;
}

/**
 * localStorage fires `storage` only in OTHER tabs, so the prompt sitting in
 * the app shell would never notice the profile page recording an engagement
 * in the same tab. This is that missing same-tab signal.
 */
export const PUSH_PROMPT_STATE_EVENT = "giapha:push-prompt-state";

function notifyPromptStateChanged(): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(PUSH_PROMPT_STATE_EVENT));
}

/**
 * The timing rule itself: at least one real engagement, and not inside the
 * cooldown after a dismissal.
 */
export function isPromptDue(state: PromptState, now = Date.now()): boolean {
  if (state.reasons.length === 0) return false;
  if (state.dismissedAt !== null && now - state.dismissedAt < DISMISS_COOLDOWN_MS) return false;
  return true;
}

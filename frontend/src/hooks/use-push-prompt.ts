"use client";

import { useCallback, useEffect, useState } from "react";
import { MOCKING_ENABLED, getDevRole } from "@/lib/api/dev-role";
import { useAuth } from "@/lib/auth/auth-context";
import {
  PUSH_PROMPT_STATE_EVENT,
  dismissPrompt,
  isPromptDue,
  readPromptState,
  recordEngagement,
  type EngagementReason,
  type PromptState,
} from "@/lib/push/subscription-store";
import { usePushSubscription } from "./use-push-subscription";

/**
 * Records one engagement signal ("this person has now read a profile / a
 * giỗ") on mount. Mount it from a page, not from deep inside a component
 * tree, so the signal maps to something the user actually navigated to.
 *
 * Idempotent per reason — revisiting profiles doesn't inflate the count, and
 * nothing here shows UI.
 */
export function useRecordPushEngagement(reason: EngagementReason, enabled = true): void {
  useEffect(() => {
    if (!enabled) return;
    recordEngagement(reason);
  }, [reason, enabled]);
}

export interface PushPromptController {
  /** True only when it is both due and technically possible to ask. */
  shouldAsk: boolean;
  /** iOS in a tab: can't ask, but CAN teach the install step. */
  shouldSuggestIosInstall: boolean;
  dismiss: () => void;
}

/**
 * Decides whether the just-in-time permission card may appear.
 *
 * Deliberately conservative, because `Notification.requestPermission()` is a
 * one-shot: a browser-level "deny" cannot be re-asked from script, ever. We
 * ask only when ALL of these hold:
 *
 *  - the user has read a profile or a giỗ (plan §4 F7: "sau khi người dùng đã
 *    xem một hồ sơ hoặc một ngày giỗ, không phải ngay khi vào trang");
 *  - the browser can actually take a subscription (not iOS-in-a-tab, not a
 *    build without a service worker);
 *  - permission is still `default` — never nag a user who said no;
 *  - they are not inside the cooldown after tapping "để sau";
 *  - they are signed in. A guest has no inbox to push about, and asking would
 *    be a permission prompt with nothing behind it.
 */
export function usePushPrompt(): PushPromptController {
  const { state } = usePushSubscription();
  const { isAuthenticated } = useAuth();
  const [prompt, setPrompt] = useState<PromptState | null>(null);

  useEffect(() => {
    const sync = () => setPrompt(readPromptState());
    sync();
    window.addEventListener(PUSH_PROMPT_STATE_EVENT, sync);
    // Another tab may have dismissed or enabled it.
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(PUSH_PROMPT_STATE_EVENT, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  const dismiss = useCallback(() => {
    setPrompt(dismissPrompt());
  }, []);

  // F8: nguồn thật là phiên Keycloak. Bản chạy MSW không có phiên nào nên vẫn
  // dùng bộ chuyển vai dev — hai nhánh loại trừ nhau đúng như MOCKING_ENABLED
  // và AUTH_ENABLED.
  //
  // Trước đây nhánh không-mock trả thẳng `true` ("cứ coi như đã đăng nhập"),
  // nghĩa là khách vãng lai cũng bị hỏi xin quyền gửi thông báo đẩy cho một
  // hộp thư mà họ không hề sở hữu.
  const signedIn = MOCKING_ENABLED ? getDevRole() !== "guest" : isAuthenticated;

  const due = prompt !== null && isPromptDue(prompt) && signedIn;

  return {
    shouldAsk: due && state?.status === "OFF" && state.permission === "default",
    shouldSuggestIosInstall: due && state?.status === "NEEDS_IOS_INSTALL",
    dismiss,
  };
}

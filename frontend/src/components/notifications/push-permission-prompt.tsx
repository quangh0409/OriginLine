"use client";

import { useState } from "react";
import { Button } from "antd";
import { BellOutlined, CloseOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { usePushPrompt } from "@/hooks/use-push-prompt";
import { usePushSubscription } from "@/hooks/use-push-subscription";
import { PushEnableError } from "@/lib/push/push-client";
import { colorVars } from "@/styles/tokens";
import { IosInstallGuide } from "./ios-install-guide";

/**
 * The just-in-time Web Push ask (plan §4 F7).
 *
 * Appears only after the user has read a profile or a giỗ — never on arrival
 * — and never for someone who already answered the browser prompt. See
 * `usePushPrompt` for the full set of conditions and why they are strict.
 *
 * The copy leads with what happens if you say no: the in-app inbox keeps
 * every reminder either way. That is not reassurance for its own sake — it
 * is the Giai đoạn 1 exit criterion ("người từ chối quyền push vẫn phải nhận
 * đủ thông báo khi mở app"), and telling the user so is what makes declining
 * a real choice rather than a loss.
 *
 * Sits above the mobile bottom nav (`bottom-16`), full-width on a phone and
 * a floating card from `sm:` up.
 */
export function PushPermissionPrompt() {
  const t = useTranslations("push");
  const { shouldAsk, shouldSuggestIosInstall, dismiss } = usePushPrompt();
  const { enable } = usePushSubscription();
  const [failure, setFailure] = useState<string | null>(null);

  if (!shouldAsk && !shouldSuggestIosInstall) return null;

  const handleEnable = () => {
    setFailure(null);
    enable.mutate(undefined, {
      onSuccess: () => dismiss(),
      onError: (error) => {
        if (error instanceof PushEnableError) {
          setFailure(t(`error.${error.reason}`));
          // A denial is final as far as the browser is concerned; stop asking.
          if (error.reason === "PERMISSION_DENIED") dismiss();
        } else {
          setFailure(t("error.SUBSCRIBE_FAILED"));
        }
      },
    });
  };

  return (
    <div
      role="dialog"
      aria-label={t("prompt.title")}
      className="fixed inset-x-2 bottom-16 z-40 rounded-lg border bg-bg-card p-3 shadow-lg sm:inset-x-auto sm:bottom-4 sm:right-4 sm:w-96"
      style={{ borderColor: colorVars.borderDark }}
    >
      <div className="flex items-start gap-3">
        <BellOutlined className="mt-1 text-lg text-primary" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="m-0 font-serif text-than font-semibold text-text-main">
            {t("prompt.title")}
          </p>
          <p className="mb-0 mt-1 text-than leading-relaxed text-text-muted">
            {shouldSuggestIosInstall ? t("prompt.iosBody") : t("prompt.body")}
          </p>
          <p className="mb-0 mt-1 text-than leading-relaxed text-text-muted">
            {t("inAppAlways")}
          </p>

          {shouldSuggestIosInstall && (
            <div className="mt-2">
              <IosInstallGuide compact />
            </div>
          )}

          {failure && (
            <p className="mb-0 mt-2 text-than" style={{ color: colorVars.danger }}>
              {failure}
            </p>
          )}

          <div className="mt-3 flex flex-wrap gap-2">
            {shouldAsk && (
              <Button type="primary" size="small" loading={enable.isPending} onClick={handleEnable}>
                {t("prompt.enable")}
              </Button>
            )}
            <Button size="small" type="text" onClick={dismiss}>
              {t("prompt.later")}
            </Button>
          </div>
        </div>

        <Button
          type="text"
          size="small"
          aria-label={t("prompt.later")}
          icon={<CloseOutlined />}
          onClick={dismiss}
        />
      </div>
    </div>
  );
}

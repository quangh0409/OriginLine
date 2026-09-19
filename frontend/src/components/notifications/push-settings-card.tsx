"use client";

import { useState } from "react";
import { Alert, Skeleton, Switch, Tag } from "antd";
import { useTranslations } from "next-intl";
import { usePushSubscription } from "@/hooks/use-push-subscription";
import { PushEnableError, type PushDeviceStatus } from "@/lib/push/push-client";
import { colorVars } from "@/styles/tokens";
import { IosInstallGuide } from "./ios-install-guide";

/** Statuses where the switch is meaningless — explain instead of offering it. */
const BLOCKED: PushDeviceStatus[] = ["UNSUPPORTED", "NEEDS_IOS_INSTALL", "NO_SERVICE_WORKER", "DENIED"];

/**
 * The on/off switch for Web Push on THIS device (plan §4 F7: "công tắc
 * bật/tắt trong phần cài đặt").
 *
 * Per-device by nature: a subscription belongs to one browser on one machine,
 * so a member can have push on their phone and not on the office laptop. The
 * copy says so, because a switch that silently means something narrower than
 * it reads is worse than no switch.
 *
 * Every "off" state gets its own explanation rather than a greyed-out
 * control. "Denied" in particular is unrecoverable from JavaScript — the only
 * fix is in browser settings — and a user who isn't told that will keep
 * tapping a switch that cannot move.
 */
export function PushSettingsCard() {
  const t = useTranslations("push");
  const { state, isLoading, enable, disable } = usePushSubscription();
  const [failure, setFailure] = useState<string | null>(null);
  // Máy chủ chưa có khoá VAPID. Chỉ biết được sau lần thử đầu tiên (khoá công khai không được
  // hỏi trước khi người dùng bấm), nhưng biết rồi thì phải cất công tắc đi: bấm nữa cũng chỉ
  // hỏng y như vậy.
  const [serverUnconfigured, setServerUnconfigured] = useState(false);

  if (isLoading || !state) {
    return <Skeleton active paragraph={{ rows: 2 }} />;
  }

  const blocked = BLOCKED.includes(state.status);
  const isOn = state.status === "ON";
  const busy = enable.isPending || disable.isPending;

  const handleToggle = (next: boolean) => {
    setFailure(null);
    const mutation = next ? enable : disable;
    mutation.mutate(undefined, {
      onError: (error) => {
        if (error instanceof PushEnableError && error.reason === "SERVER_NOT_CONFIGURED") {
          setServerUnconfigured(true);
        }
        setFailure(
          error instanceof PushEnableError ? t(`error.${error.reason}`) : t("error.SUBSCRIBE_FAILED")
        );
      },
    });
  };

  return (
    <section className="rounded-lg border border-border bg-bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="m-0 font-serif text-[16px] font-semibold text-text-main">
            {t("settings.title")}
          </h2>
          <p className="mb-0 mt-1 text-than leading-relaxed text-text-muted">
            {t("settings.body")}
          </p>
        </div>

        {!blocked && !serverUnconfigured && (
          <Switch
            checked={isOn}
            loading={busy}
            onChange={handleToggle}
            aria-label={t("settings.title")}
          />
        )}
      </div>

      {isOn && (
        <Tag
          bordered={false}
          className="!mt-3 !text-than"
          style={{ background: colorVars.successBg, color: colorVars.success }}
        >
          {t("settings.onThisDevice")}
        </Tag>
      )}

      {/* The load-bearing sentence of the whole feature: push is additive. */}
      <p className="mb-0 mt-3 text-than leading-relaxed text-text-muted">
        {t("inAppAlways")}
      </p>

      {state.status === "DENIED" && (
        <Alert className="!mt-3" type="info" showIcon message={t("status.DENIED")} />
      )}
      {state.status === "UNSUPPORTED" && (
        <Alert className="!mt-3" type="info" showIcon message={t("status.UNSUPPORTED")} />
      )}
      {state.status === "NO_SERVICE_WORKER" && (
        <Alert className="!mt-3" type="info" showIcon message={t("status.NO_SERVICE_WORKER")} />
      )}
      {state.status === "NEEDS_IOS_INSTALL" && (
        <div className="mt-3">
          <IosInstallGuide />
        </div>
      )}

      {failure && (
        <Alert className="!mt-3" type="warning" showIcon message={failure} />
      )}
    </section>
  );
}

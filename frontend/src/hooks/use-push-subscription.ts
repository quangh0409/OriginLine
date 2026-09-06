import { useCallback } from "react";
import { useLocale } from "next-intl";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationsApi } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { queryKeys } from "@/lib/query/keys";
import {
  detectPushSubscriptionState,
  disablePush,
  enablePush,
  type PushDeviceState,
} from "@/lib/push/push-client";

/**
 * Web Push registration for THIS device.
 *
 * Push is strictly an *extra* delivery channel. The in-app inbox
 * (`use-notifications.ts`) is the source of truth and is complete on its own
 * — a user who denies permission, uses a browser without PushManager, or
 * never installs the PWA on iOS still receives every giỗ reminder the moment
 * they open the app. Nothing in this hook may ever gate inbox content.
 */
export function usePushSubscription() {
  const queryClient = useQueryClient();
  const locale = useLocale();

  const state = useQuery<PushDeviceState>({
    queryKey: queryKeys.pushSubscription(),
    queryFn: detectPushSubscriptionState,
    // Permission can change in browser settings while the tab is open, and
    // there is no event for it — re-check whenever the user comes back.
    refetchOnWindowFocus: true,
    staleTime: 0,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.pushSubscription() });
  }, [queryClient]);

  const enable = useMutation({
    mutationFn: () =>
      enablePush({
        fetchPublicKey: () => notificationsApi.getVapidPublicKey(),
        register: (input) => notificationsApi.subscribePush(input),
        locale: locale === "en" ? "en" : "vi",
      }),
    onSettled: invalidate,
  });

  const disable = useMutation({
    mutationFn: () =>
      disablePush({
        unregister: async (id) => {
          try {
            await notificationsApi.unsubscribePush(id);
          } catch (error) {
            // The backend deletes subscriptions itself when the push gateway
            // reports 404/410 (contracts/openapi.yaml), so the record may
            // already be gone. That is success, not failure.
            if (error instanceof ApiError && error.status === 404) return;
            throw error;
          }
        },
      }),
    onSettled: invalidate,
  });

  return {
    state: state.data,
    isLoading: state.isLoading,
    enable,
    disable,
    refresh: invalidate,
  };
}

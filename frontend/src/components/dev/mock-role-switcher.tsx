"use client";

import { useEffect, useState } from "react";
import { Select, Tooltip } from "antd";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import {
  DEV_ROLES,
  MOCKING_ENABLED,
  getDevRole,
  setDevRole,
  type DevRole,
} from "@/lib/api/dev-role";

/**
 * DEV-ONLY. Renders nothing unless `NEXT_PUBLIC_API_MOCKING=enabled`
 * (`npm run dev:mock`), and is dead code in any other build.
 *
 * Privacy tiering is the requirement most likely to be shipped broken: on the
 * MSW build there is no Keycloak to ask, so every mocked request would be an
 * anonymous guest and nobody would ever SEE a Tier-2 or Tier-3 profile during
 * development. This switch makes the four states one click apart:
 *
 *   guest       -> no living person exists at all (404, not 403)
 *   member      -> Tier 1: name, đời, branch. No birth, no contact.
 *   branch head -> Tier 2: + birth YEAR, occupation, province. Can edit.
 *   admin       -> Tier 3: everything, including contact.
 *
 * Changing role invalidates the whole query cache, because every cached
 * response was shaped by the old role and reusing it would show a guest data
 * an admin fetched a moment earlier.
 *
 * Real tokens have landed (F8): the build that talks to the real backend shows
 * <AuthMenu> instead, and reads roles from the JWT. This switcher survives
 * only for the MSW build — see the module doc on src/lib/api/dev-role.ts for
 * why it is not simply deleted.
 */
export function MockRoleSwitcher() {
  const t = useTranslations("dev");
  const queryClient = useQueryClient();
  const [role, setRole] = useState<DevRole>("guest");
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    // localStorage is client-only; reading it during render would desync SSR.
    setRole(getDevRole());
    setMounted(true);
  }, []);

  if (!MOCKING_ENABLED || !mounted) return null;

  return (
    // Ẩn dưới `md`: đây là công cụ của lập trình viên, chiếm 128px trên thanh đầu trang — đúng
    // bằng phần khiến MỌI trang tràn ngang 34px trên Pixel 5 (393px). Bản production không dựng
    // component này (MOCKING_ENABLED = false) nên thanh đầu trang thật không hề rộng như vậy;
    // giấu nó trên màn hình nhỏ để bản chạy thử đo được đúng bố cục mà người dùng sẽ thấy.
    <div className="hidden md:block">
    <Tooltip title={t("roleTooltip")}>
      <Select<DevRole>
        size="small"
        value={role}
        style={{ minWidth: 128 }}
        aria-label={t("roleLabel")}
        onChange={(next) => {
          setDevRole(next);
          setRole(next);
          void queryClient.invalidateQueries();
        }}
        options={DEV_ROLES.map((value) => ({ value, label: t(`role.${value}`) }))}
      />
    </Tooltip>
    </div>
  );
}

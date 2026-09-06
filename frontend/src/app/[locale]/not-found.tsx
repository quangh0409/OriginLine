import { getTranslations } from "next-intl/server";
import { Result } from "antd";
import { AppShell } from "@/components/layout/app-shell";

export default async function NotFound() {
  const t = await getTranslations("common");

  return (
    <AppShell>
      <div className="flex min-h-[60vh] items-center justify-center px-4">
        <Result status="404" title="404" subTitle={t("error")} />
      </div>
    </AppShell>
  );
}

import { setRequestLocale } from "next-intl/server";
import type { AppLocale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/app-shell";
import { KhungTrang } from "@/components/common/khung-trang";
import { PersonCorrectionScreen } from "@/components/correction/person-correction-screen";

/**
 * Duong dan sau cho mot de nghi dinh chinh ve MOT nguoi cu the.
 *
 * Ton tai de nut "De nghi dinh chinh" tren ho so co cho de tro toi, va de mot
 * lien ket gui qua Zalo ("cu oi, cho nay ghi sai") mo thang duoc dung ho so
 * can sua thay vi bat nguoi ta tu tim lai.
 */
export default async function PersonCorrectionPage({
  params,
}: {
  params: Promise<{ locale: string; id: string }>;
}) {
  const { locale, id } = await params;
  setRequestLocale(locale as AppLocale);

  return (
    <AppShell>
      <KhungTrang beRong="hep">
        <PersonCorrectionScreen personId={id} />
      </KhungTrang>
    </AppShell>
  );
}

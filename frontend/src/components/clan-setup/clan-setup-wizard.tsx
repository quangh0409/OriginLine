"use client";

import { useEffect, useState } from "react";
import { Alert, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { colorVars } from "@/styles/tokens";
import { useClanReadiness } from "./hooks/use-clan-setup";
import { ClanSetupSteps, type ClanSetupStepNumber } from "./clan-setup-steps";
import { ClanProfileStep } from "./clan-profile-step";
import { CouncilStep } from "./council-step";
import { ImportStep } from "./import-step";
import { InviteStep } from "./invite-step";
import { ReadyStep } from "./ready-step";

const STEP_KEY_BY_NUMBER: Record<ClanSetupStepNumber, string> = {
  1: "profile",
  2: "council",
  3: "import",
  4: "invite",
  5: "done",
};

const STEP_NUMBER_BY_KEY: Record<string, ClanSetupStepNumber> = {
  profile: 1,
  council: 2,
  import: 3,
  invite: 4,
  done: 5,
};

/**
 * **Màn "Khởi tạo dòng họ"** — `/quan-tri`, chỉ vai `ADMIN`.
 *
 * Quyết định 26/09/2026 của chủ dự án: hôm nay không có màn hình nào để dựng
 * một dòng họ mới, mọi thứ làm tay dưới CSDL. Đây là bước 1 của kế hoạch đó —
 * năm bước, mỗi bước một thẻ, thanh tiến độ đọc từ `readiness.steps`.
 *
 * <h2>Cổng vai — cùng khuôn `MembershipQueueScreen`/`/quan-ly`</h2>
 * `Skeleton` trong lúc `useMe()` chưa trả lời, `Alert type="info"` khi vai
 * không phải `ADMIN` — không có nút hay biểu mẫu nào lộ ra trước khi biết chắc
 * người xem có quyền. Cổng THẬT là backend (`403 FORBIDDEN`); đây chỉ tránh
 * mời một Trưởng chi/Hội đồng bấm vào một màn chắc chắn bị từ chối, đúng khuôn
 * `EventCreateScreen`.
 *
 * <h2>Bước hiện tại: chọn TỰ ĐỘNG một lần, sau đó do người dùng quyết</h2>
 * Lần vào đầu tiên nhảy tới bước CHƯA XONG đầu tiên (đọc `readiness.steps`) —
 * một Quản trị quay lại sau vài ngày không phải tự dò xem lần trước dừng ở
 * đâu, cùng lý do `ImportIntroScreen` đọc lô đang dở từ máy chủ. Sau đó mọi
 * cú bấm trên thanh tiến độ (`ClanSetupSteps`) đều thắng — tự động hoá không
 * được ghi đè một lựa chọn người dùng vừa tự tay bấm.
 */
export function ClanSetupWizard() {
  const t = useTranslations("clanSetup");
  const meQuery = useMe();
  const me = meQuery.data;
  const isAdmin = Boolean(me?.appUserId) && me?.role === "ADMIN";

  const readiness = useClanReadiness({ enabled: isAdmin });

  const [active, setActive] = useState<ClanSetupStepNumber | null>(null);
  const [userPicked, setUserPicked] = useState(false);

  useEffect(() => {
    if (userPicked || active !== null || !readiness.data) return;
    const firstUnfinished = readiness.data.steps.find((s) => !s.done);
    setActive(firstUnfinished ? (STEP_NUMBER_BY_KEY[firstUnfinished.key] ?? 1) : 1);
  }, [readiness.data, userPicked, active]);

  if (meQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (!isAdmin) {
    return <Alert type="info" showIcon message={t("gate.forbidden")} />;
  }

  const currentStep = active ?? 1;

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold text-text-main">{t("pageTitle")}</h1>
        <p className="m-0 mt-1 text-than text-text-muted">{t("pageSubtitle")}</p>
      </header>

      <ClanSetupSteps
        steps={readiness.data?.steps}
        current={currentStep}
        onSelect={(step) => {
          setUserPicked(true);
          setActive(step);
        }}
      />

      <div
        className="rounded-lg border px-4 py-4 sm:px-6"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
        data-testid={`clan-setup-step-${STEP_KEY_BY_NUMBER[currentStep]}`}
      >
        {currentStep === 1 && <ClanProfileStep />}
        {currentStep === 2 && <CouncilStep />}
        {currentStep === 3 && <ImportStep />}
        {currentStep === 4 && <InviteStep />}
        {currentStep === 5 && <ReadyStep />}
      </div>
    </div>
  );
}

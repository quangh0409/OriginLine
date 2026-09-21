"use client";

import { Alert, Skeleton, Tabs } from "antd";
import { useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { ClanCodePanel } from "./clan-code-panel";
import { PersonalInvitePanel } from "./personal-invite-panel";

/**
 * **Màn phát mã và phát lời mời** — một màn, hai việc, hai vai.
 *
 * <h2>Vì sao gộp một màn chứ không tách hai</h2>
 * design 07 §1.3 nói thẳng "một màn, hai việc". Lý do thực dụng: Hội đồng làm
 * **cả hai** — họ phát mã dòng họ cho người trẻ *và* phát lời mời cá nhân cho
 * các cụ trong chi mình. Tách hai trang là bắt họ nhớ hai đường dẫn cho một
 * buổi làm việc.
 *
 * <h2>Trưởng chi chỉ thấy một tab, và không biết tab kia tồn tại</h2>
 * Cùng cách nghĩ như {@code correction-screen.tsx}. Ẩn tab **không phải** biện
 * pháp bảo mật — máy chủ vẫn trả `403` cho `POST /clan-invite-codes` từ một
 * Trưởng chi. Nó là phép lịch sự: một tab bấm vào là chắc chắn báo lỗi thì
 * không nên có ở đó.
 *
 * <h2>Quyền đọc từ `/me`, và đó chỉ là dữ liệu để vẽ</h2>
 * Người dùng sửa được phản hồi này trong DevTools; sửa xong vẫn không phát được
 * mã nào, vì phép kiểm thật nằm ở `BranchScopeGuard` so `ltree` phía máy chủ.
 */
export function InviteScreen() {
  const t = useTranslations("membership");
  const meQuery = useMe();
  const me = meQuery.data;

  if (meQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (!me?.appUserId) {
    return <Alert type="info" showIcon message={t("errors.notProvisioned")} />;
  }

  const laHoiDong = me.role === "ADMIN" || me.role === "COUNCIL";
  const laTruongChi = me.role === "BRANCH_HEAD";

  if (!laHoiDong && !laTruongChi) {
    return <Alert type="info" showIcon message={t("errors.noIssueRight")} />;
  }

  // Trưởng chi chưa được giao chi nào thì bộ chọn nhân khẩu luôn trả `403` lúc
  // phát. Nói trước còn hơn để họ điền xong biểu mẫu rồi mới biết.
  if (laTruongChi && me.managedBranches.length === 0) {
    return <Alert type="warning" showIcon message={t("errors.noScope")} />;
  }

  const personalTab = {
    key: "personal",
    label: t("personal.tab"),
    children: <PersonalInvitePanel />,
  };

  if (!laHoiDong) return <PersonalInvitePanel />;

  return (
    <Tabs
      items={[
        { key: "clan", label: t("clan.tab"), children: <ClanCodePanel /> },
        personalTab,
      ]}
    />
  );
}

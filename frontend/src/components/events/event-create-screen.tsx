"use client";

import { Alert, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { EventForm } from "./event-form";

/**
 * Cổng vào form tạo việc họ.
 *
 * Khác chủ ý với `PersonForm` (F4): trang thêm nhân khẩu KHÔNG gác vai ở giao
 * diện, vì phạm vi chi/ngành lúc đó chưa lên. Ở đây plan đợt này nói rõ:
 * "Giao diện chỉ ẩn nút cho người không có quyền" — nên màn này gác một lớp
 * hiển thị bằng `useMe()`, và vẫn chỉ là gợi ý vẽ giao diện: cổng thật là
 * `403 FORBIDDEN`/`BRANCH_SCOPE_VIOLATION` của chính `POST /events`. Ai gõ
 * thẳng địa chỉ `/events/new` mà không đủ vai vẫn phải thấy một câu giải
 * thích, không phải một biểu mẫu rồi vấp lỗi cuối cùng.
 */
export function EventCreateScreen() {
  const t = useTranslations("eventForm");
  const { data: me, isPending } = useMe();

  if (isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  const canManage = me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";
  if (!canManage) {
    return <Alert type="info" showIcon message={t("errors.notAllowedToCreate")} />;
  }

  return <EventForm mode="create" />;
}

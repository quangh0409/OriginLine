"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import type { ChangeRequestStatus } from "@/lib/api/change-requests";

const COLOR: Record<ChangeRequestStatus, string> = {
  PENDING: "gold",
  APPROVED: "green",
  REJECTED: "red",
  CANCELLED: "default",
};

/**
 * Trạng thái một yêu cầu đính chính.
 *
 * Màu không bao giờ là tín hiệu duy nhất — chữ luôn đi kèm. Người dùng của
 * cổng này gồm cả các cụ lớn tuổi, và một chấm màu trên nền kem thì phân biệt
 * "đã duyệt" với "bị từ chối" bằng gì?
 */
export function ChangeRequestStatusTag({ status }: { status: ChangeRequestStatus }) {
  const t = useTranslations("correction");
  return (
    <Tag color={COLOR[status]} className="!m-0">
      {t(`status.${status}`)}
    </Tag>
  );
}

"use client";

import { Alert, Badge, Empty, Skeleton, Tabs } from "antd";
import { useTranslations } from "next-intl";
import {
  useMyChangeRequests,
  usePendingChangeRequests,
} from "@/hooks/use-change-requests";
import { useMe } from "@/hooks/use-me";
import { ApiError } from "@/lib/api/http";
import { MyRequestCard } from "./my-request-card";
import { ReviewRequestCard } from "./review-request-card";

/**
 * Màn hình **đính chính** — hai vai, hai tab, một trang.
 *
 * <h2>Vì sao gộp chứ không tách hai trang</h2>
 * Trưởng chi vừa duyệt của người khác vừa gửi đề nghị của mình (chi khác, hoặc
 * hồ sơ ngoài phạm vi). Tách hai trang buộc họ nhớ hai đường dẫn cho cùng một
 * việc. Tab "Chờ tôi duyệt" chỉ hiện với ai thật sự duyệt được — thành viên
 * thường chỉ thấy đúng một tab và không hề biết tab kia tồn tại.
 *
 * <h2>Danh sách rỗng có hai nghĩa, và phải nói rõ nghĩa nào</h2>
 * Trưởng chi chưa được Hội đồng phân công chi nào thì hàng đợi luôn rỗng —
 * không phải vì hết việc mà vì **chưa có phạm vi**. Một màn hình "Không có yêu
 * cầu nào" trong tình huống ấy khiến người ta ngồi đợi một hàng đợi không bao
 * giờ có gì. Hai câu khác nhau cho hai tình huống khác nhau.
 */
export function CorrectionScreen() {
  const t = useTranslations("correction");
  const meQuery = useMe();
  const me = meQuery.data;

  const roleCanReview =
    me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";
  const hasAccount = Boolean(me?.appUserId);

  const pending = usePendingChangeRequests({ enabled: hasAccount && roleCanReview });
  const mine = useMyChangeRequests();

  if (meQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  // Khách: không có tài khoản thì không có gì để xem, và nói thẳng lý do thay
  // vì đưa một màn hình trắng.
  if (!hasAccount) {
    return <Alert type="info" showIcon message={t("errors.notProvisioned")} />;
  }

  const noScope = me?.role === "BRANCH_HEAD" && (me?.managedBranches.length ?? 0) === 0;

  const mineTab = (
    <QueryList
      isPending={mine.isPending}
      error={mine.error}
      isEmpty={(mine.data?.length ?? 0) === 0}
      emptyText={t("mine.empty")}
    >
      {mine.data?.map((request) => (
        <MyRequestCard key={request.id} request={request} />
      ))}
    </QueryList>
  );

  const reviewTab = (
    <QueryList
      isPending={pending.isPending}
      error={pending.error}
      isEmpty={(pending.data?.length ?? 0) === 0}
      emptyText={noScope ? t("queue.emptyNoScope") : t("queue.empty")}
    >
      {pending.data?.map((request) => (
        <ReviewRequestCard
          key={request.id}
          request={request}
          currentAppUserId={me?.appUserId ?? null}
        />
      ))}
    </QueryList>
  );

  const items = [
    ...(roleCanReview
      ? [
          {
            key: "queue",
            label: (
              <span className="pr-3">
                {t("queue.tab")}
                <Badge
                  className="ml-2"
                  count={pending.data?.length ?? 0}
                  size="small"
                  offset={[2, -2]}
                />
              </span>
            ),
            children: reviewTab,
          },
        ]
      : []),
    { key: "mine", label: t("mine.tab"), children: mineTab },
  ];

  return <Tabs items={items} />;
}

function QueryList({
  isPending,
  error,
  isEmpty,
  emptyText,
  children,
}: {
  isPending: boolean;
  error: unknown;
  isEmpty: boolean;
  emptyText: string;
  children: React.ReactNode;
}) {
  const t = useTranslations("correction");

  if (isPending) return <Skeleton active paragraph={{ rows: 4 }} />;

  if (error) {
    const code = error instanceof ApiError ? error.code : "UNKNOWN";
    const key =
      code === "FORBIDDEN"
        ? "errors.forbidden"
        : code === "ACCOUNT_NOT_PROVISIONED"
          ? "errors.notProvisioned"
          : "errors.loadFailed";
    return <Alert type="error" showIcon message={t(key)} />;
  }

  if (isEmpty) return <Empty description={emptyText} />;

  return <div className="flex flex-col gap-3">{children}</div>;
}

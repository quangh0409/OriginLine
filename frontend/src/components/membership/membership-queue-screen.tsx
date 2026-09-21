"use client";

import { Alert, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { usePerson } from "@/hooks/use-person";
import { headlineName } from "@/lib/format/name-layers";
import { colorVars } from "@/styles/tokens";
import { claimReviewFailureOf, type ClaimReviewView } from "@/lib/api/membership-admin";
import { ClaimRequestCard } from "./claim-request-card";
import { NewPersonRequestCard } from "./new-person-request-card";
import { groupClaimQueue, type QueueGroup } from "./grouping";
import { usePendingClaims } from "./queries";

/**
 * **Hàng chờ duyệt đơn tự nhận** — Trưởng chi và Hội đồng Tộc biểu.
 *
 * <h2>Phạm vi do MÁY CHỦ cắt, không phải màn hình này</h2>
 * Trưởng chi Ất chỉ thấy đơn thuộc chi mình; Hội đồng thấy cả họ. Cả hai câu ấy
 * do `ltree` ở `BranchScopeGuard` quyết. Lọc lại ở đây sẽ là lớp thứ hai — và
 * một lớp thứ hai nghĩa là dữ liệu ngoài phạm vi đã đi qua dây mạng rồi, còn
 * lớp thật thì không ai kiểm nữa vì "phía client đã lọc".
 *
 * <h2>Danh sách rỗng có hai nghĩa</h2>
 * "Chưa được Hội đồng giao chi nào" và "không còn đơn nào chờ" là hai tình
 * huống khác hẳn. Phân biệt bằng `useMe().managedBranches`, **không** bằng độ
 * dài mảng — một Trưởng chi chưa có phạm vi sẽ ngồi đợi một hàng đợi không bao
 * giờ có gì.
 *
 * <h2>Hai đơn cùng nhận một nhân khẩu đứng cạnh nhau</h2>
 * Xem {@link groupClaimQueue}. Cụm tranh chấp được vẽ trong một khung chung có
 * tiêu đề nói rõ đang có mấy người cùng nhận, và **không** sắp theo thời gian
 * gửi: design 07 §1.4 chốt rằng người gửi trước không được ưu tiên, vì trùng
 * tên trong dòng họ là chuyện thường.
 */
export function MembershipQueueScreen() {
  const t = useTranslations("membership");
  const meQuery = useMe();
  const me = meQuery.data;

  const roleCanReview =
    me?.role === "ADMIN" || me?.role === "COUNCIL" || me?.role === "BRANCH_HEAD";
  const hasAccount = Boolean(me?.appUserId);

  const queue = usePendingClaims({ enabled: hasAccount && roleCanReview });

  if (meQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (!hasAccount) {
    return <Alert type="info" showIcon message={t("errors.notProvisioned")} />;
  }

  if (!roleCanReview) {
    return <Alert type="info" showIcon message={t("errors.noReviewRight")} />;
  }

  if (queue.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (queue.error) {
    return (
      <Alert
        type="error"
        showIcon
        message={t(`review.errors.${claimReviewFailureOf(queue.error)}`)}
      />
    );
  }

  const requests = queue.data ?? [];

  if (requests.length === 0) {
    const noScope = me?.role === "BRANCH_HEAD" && (me?.managedBranches.length ?? 0) === 0;
    return (
      <Empty
        description={
          <span style={{ color: colorVars.textMuted }}>
            {noScope ? t("queue.emptyNoScope") : t("queue.empty")}
          </span>
        }
      />
    );
  }

  const groups = groupClaimQueue(requests);

  return (
    <div className="flex flex-col gap-4">
      <p className="m-0 text-than text-text-muted">
        {t("queue.scopeNote", { n: requests.length })}
      </p>
      {groups.map((group) =>
        group.requests.length > 1 || group.missingCompeting > 0 ? (
          <ContestedGroup key={group.key} group={group} />
        ) : (
          <RequestCard key={group.key} request={group.requests[0] as ClaimReviewView} />
        )
      )}
    </div>
  );
}

/**
 * Một lá đơn — loại nào thì thẻ ấy.
 *
 * Rẽ nhánh theo `kind === "NEW_PERSON"` chứ không theo vế kia. Đó là vế mang hệ
 * quả nặng — duyệt nó là GHI MỘT NHÂN KHẨU MỚI vào phả, mà xoá mềm là luật
 * tuyệt đối nên tạo nhầm là ở lại vĩnh viễn. Rẽ theo vế nặng thì một loại đơn
 * thứ ba sau này rơi vào nhánh nhẹ, không rơi vào nhánh ghi vào phả.
 */
function RequestCard({
  request,
  contested,
}: {
  request: ClaimReviewView;
  contested?: boolean;
}) {
  return request.kind === "NEW_PERSON" ? (
    <NewPersonRequestCard request={request} />
  ) : (
    <ClaimRequestCard request={request} contested={contested} />
  );
}

/**
 * **Hai (hoặc nhiều) người cùng nhận một nhân khẩu.**
 *
 * Khung chung là cả điểm: hai thẻ rời nhau trong một danh sách dài thì Trưởng
 * chi xử lá đầu rồi cuộn tiếp, không bao giờ biết có lá thứ hai. Đặt chúng
 * trong một khung có tiêu đề biến hai quyết định độc lập thành **một lựa chọn**
 * — đúng việc mà design 07 §1.4 mô tả: "để Trưởng chi thấy *cả hai* rồi chọn".
 */
function ContestedGroup({ group }: { group: QueueGroup }) {
  const t = useTranslations("membership");
  const first = group.requests[0];
  // Lá đơn chỉ mang KHOÁ nhân khẩu — contract cố ý không chở tên, vì nhân khẩu
  // ấy có thể là người còn sống. Tên hiện trong từng thẻ con, nơi lượt gọi
  // `GET /persons/{id}` chạy qua phiên của chính người duyệt; tiêu đề cụm dùng
  // câu không nêu tên chứ không bịa một cái tên ra từ khoá.
  const personQuery = usePerson(first?.personId ?? undefined);
  const person = personQuery.data?.person;
  const tenNhanKhau = (person && headlineName(person)) ?? t("card.unknownPerson");

  return (
    <section
      data-testid="cum-tranh-chap"
      aria-label={t("queue.contestedAria", { name: tenNhanKhau })}
      className="rounded-lg border-2 p-3"
      style={{ borderColor: colorVars.accent, background: colorVars.warningBg }}
    >
      <h3 className="m-0 font-serif text-de font-bold text-text-main">
        {t("queue.contestedTitle", {
          n: group.requests.length + group.missingCompeting,
          name: tenNhanKhau,
        })}
      </h3>
      <p className="m-0 mt-1 text-than text-text-main">{t("queue.contestedLead")}</p>

      {group.missingCompeting > 0 && (
        <Alert
          className="!mt-2"
          type="error"
          showIcon
          message={t("queue.contestedMissing", { n: group.missingCompeting })}
        />
      )}

      <div className="mt-3 grid grid-cols-1 gap-3 lg:grid-cols-2">
        {group.requests.map((request) => (
          <RequestCard key={request.id} request={request} contested />
        ))}
      </div>
    </section>
  );
}

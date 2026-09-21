"use client";

import { Alert, Skeleton, Tag } from "antd";
import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { usePerson } from "@/hooks/use-person";
import { headlineName } from "@/lib/format/name-layers";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { ClaimReviewView } from "@/lib/api/membership-admin";
import { RequestDecision } from "./request-decision";
import { RequesterBlock } from "./requester-block";

export interface ClaimRequestCardProps {
  request: ClaimReviewView;
  /** Lá đơn này đang tranh một nhân khẩu với lá khác trong cùng cụm. */
  contested?: boolean;
}

/**
 * **Đơn loại A — "tôi là người này"**: người dùng chỉ vào một nhân khẩu đã có
 * trong phả. Duyệt = gắn tài khoản vào hồ sơ ấy.
 *
 * <h2>Thẻ này được dựng quanh một cuộc điện thoại</h2>
 * Trưởng chi không đối chiếu bằng cách đọc màn hình — họ gọi. Nên thẻ chỉ cần
 * đúng những gì cần để cầm máy lên: <b>hồ sơ được chỉ</b> (tên, đời, chi, cha
 * mẹ) để biết hỏi về ai, <b>số điện thoại</b> để gọi, và <b>vài dòng tự giới
 * thiệu</b> để biết hỏi gì. Thêm nữa chỉ làm loãng.
 *
 * <h2>Nhẹ hơn đơn loại B, và trông phải nhẹ hơn</h2>
 * Duyệt ở đây không sinh ra gì mới trong phả. Đơn "chưa có trong phả" thì ngược
 * lại — nó **ghi vào phả**, và một nhân khẩu tạo nhầm ở lại vĩnh viễn. Hai hệ
 * quả khác nhau phải nhìn khác nhau, nếu không người duyệt sẽ xử lý cả hai bằng
 * cùng một phản xạ.
 *
 * <h2>Tên nhân khẩu tra bằng MỘT LƯỢT GỌI RIÊNG, và đó là thiết kế</h2>
 * `PersonClaim` cố ý **chỉ mang `personId`** — không tên, không năm sinh.
 * Contract viết lý do ra thành lời: nhân khẩu ấy có thể là một người **còn
 * sống**, và ai được xem gì về họ là câu hỏi của bộ lọc phân tầng riêng tư,
 * thứ chạy ở `GET /persons/&#123;id&#125;` chứ không phải ở đây.
 *
 * Khác hẳn {@code invitation-row.tsx}, nơi một lượt gọi thêm cho mỗi dòng là
 * <em>cách chống đỡ</em> đã gỡ. Ở đây nó là **cách duy nhất đúng**: một cái tên
 * nhúng sẵn trong lá đơn là cái tên đã lọt qua quyền của *người gửi đơn*, không
 * phải của người duyệt — và nó nằm lại trong đơn vĩnh viễn, đi qua mọi lần đọc
 * về sau mà không còn bộ lọc nào chạy trên nó.
 *
 * Lượt gọi ấy **không chặn** việc quyết định: hai nút Duyệt/Từ chối vẫn dùng
 * được khi tên chưa về, vì `personId` mới là thứ chúng cần.
 */
export function ClaimRequestCard({ request, contested }: ClaimRequestCardProps) {
  const t = useTranslations("membership");
  const format = useFormatter();
  const personQuery = usePerson(request.personId ?? undefined);
  const person = personQuery.data?.person;
  const tenNhanKhau = (person && headlineName(person)) ?? null;

  return (
    <article
      data-testid="don-nhan-minh"
      data-request-id={request.id}
      className="rounded-lg border border-border bg-bg-card px-4 py-3"
    >
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="m-0 text-than font-medium uppercase tracking-wide text-text-muted">
            {t("kind.EXISTING")}
          </p>
          <h3 className="m-0 font-serif text-de font-bold text-text-main">
            {personQuery.isPending ? (
              <Skeleton.Input active size="small" style={{ width: 180 }} />
            ) : request.personId ? (
              <Link href={`/persons/${request.personId}`} className="text-primary">
                {tenNhanKhau ?? t("card.unknownPerson")}
              </Link>
            ) : (
              t("card.unknownPerson")
            )}
          </h3>
          <p className="m-0 mt-0.5 text-than text-text-muted">
            {[
              isPresent(person?.generation)
                ? t("card.generation", { n: person?.generation })
                : null,
              person?.primaryBranch?.name ?? null,
            ]
              .filter((x): x is string => Boolean(x))
              .join(" · ")}
          </p>
        </div>
        <Tag bordered={false} className="!m-0 !text-than">
          {t("card.submittedAt", {
            at: format.dateTime(new Date(request.createdAt), {
              dateStyle: "medium",
              timeStyle: "short",
            }),
          })}
        </Tag>
      </header>

      {/* Ca biên "nhận nhầm người đã khuất" bị chặn cứng ngay lúc chọn, nên một
          lá đơn như thế lẽ ra không tồn tại. Nếu nó vẫn tới đây thì phải nhìn
          thấy được — một chốt chặn không quan sát được là một chốt chặn không
          ai biết lúc nó hỏng. */}
      {person && !person.isAlive && (
        <Alert
          className="!mt-2"
          type="error"
          showIcon
          message={t("card.deceasedClaimWarning")}
        />
      )}

      <RequesterBlock request={request} />

      {request.reviewNote && (
        <p className="m-0 mt-2 text-than text-text-muted">
          {t("card.reviewNote", { note: request.reviewNote })}
        </p>
      )}

      <RequestDecision
        request={request}
        contested={contested}
        subject={tenNhanKhau ?? t("card.unknownPerson")}
      />

      {request.status !== "PENDING" && (
        <p className="m-0 mt-2 text-than font-medium" style={{ color: colorVars.textMuted }}>
          {t(`status.${request.status}`)}
        </p>
      )}
    </article>
  );
}

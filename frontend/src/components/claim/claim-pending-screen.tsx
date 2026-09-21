"use client";

import type { ReactNode } from "react";
import { Skeleton, Tag } from "antd";
import { useFormatter, useTranslations } from "next-intl";
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  MinusCircleOutlined,
  UserOutlined,
} from "@ant-design/icons";
import {
  claimFailureOf,
  claimsOf,
  quotaOf,
  type ClaimQuotaDto,
  type ClaimStatus,
  type ClaimView,
} from "@/lib/api/claim";
import { colorVars } from "@/styles/tokens";
import { ClaimBlockNotice } from "./claim-block-notice";
import { ClaimButton, ClaimLink, ClaimNotice, ClaimParagraph } from "./claim-chrome";
import { claimRoutes } from "./routes";
import { useCancelClaim, useClaimTarget, useMyClaims } from "./use-claims";

/**
 * Màn **"đang chờ duyệt"** — `/nhan-dien/cho-duyet`.
 *
 * <h2>Nó chữa một thứ rất cụ thể: sự im lặng</h2>
 * Checklist §1.3: không có màn này thì người dùng gửi đơn xong rơi vào im lặng
 * <b>và sẽ gửi lại lần hai, lần ba</b>. Nên màn này trả lời đúng hai câu, trước
 * mọi thứ khác: <b>đang chờ ai</b>, và <b>trong lúc chờ xem được gì</b>. Mọi
 * thứ còn lại trên màn là phụ.
 *
 * <h2>"Đang chờ ai" nói đến đâu là đủ — và đến đâu là bịa</h2>
 * Nói được: <em>chi nào</em> đang giữ đơn, và <em>chức danh dòng tộc</em> của
 * người giữ. Không nói: tên riêng và số điện thoại của Trưởng chi — thiết kế 06
 * §7 đã gặp đúng chỗ này và kết luận rằng bịa một cái tên thì sai, còn gọi vống
 * lên "liên hệ Hội đồng Tộc biểu" thì không ai biết gọi vào đâu. Chi đích là
 * trường {@code targetBranchId} mà máy chủ chốt <b>lúc gửi</b> và dùng để quyết
 * ai duyệt, nên đây không phải suy diễn của giao diện. Khi máy chủ chưa gửi kèm
 * tên chi thì câu dự phòng {@code waitingForUnknown} chạy, và nó vẫn đúng.
 *
 * <h2>"Xem được gì" phải kể ĐÚNG, kể cả phần không được phép</h2>
 * Hai quyết định đã chốt, và cả hai đều dễ kể nhầm theo hướng rộng rãi hơn thực
 * tế:
 * <ul>
 *   <li>người chưa được duyệt <b>thấy phả đồ</b>, nhưng chỉ ở mức <b>tên, đời,
 *       quan hệ</b> — nên câu chữ nói đúng ba thứ ấy, không hứa "xem được hồ sơ
 *       người trong họ";</li>
 *   <li>người chưa được duyệt <b>KHÔNG viết bài được</b> (§1.4: "viết bài là
 *       tiếng nói của người trong họ"). Một danh sách "làm được gì" quên mục này
 *       sẽ dẫn thẳng tới một nút Viết bài trả về 403.</li>
 * </ul>
 * Vì thế danh sách có <b>hai phần</b>: được và chưa được. Bỏ phần thứ hai đi
 * thì màn này nói dối bằng cách im lặng.
 *
 * <h2>Nút "rút đơn" có mặt vì chính máy chủ bảo người dùng làm việc ấy</h2>
 * Câu từ chối khi đang có đơn mở nói đúng chữ <i>"hãy rút đơn ấy trước khi gửi
 * đơn khác"</i>. Không có nút rút thì lời khuyên ấy dẫn vào ngõ cụt. Rút
 * <b>không tiêu một lượt</b> — bộ đếm của máy chủ đếm đơn <em>bị từ chối</em> —
 * nên nó là một lối ra thật, không phải một cái bẫy.
 */

const MAU_TRANG_THAI: Readonly<Record<ClaimStatus, string>> = {
  PENDING: "gold",
  APPROVED: "green",
  REJECTED: "red",
  CANCELLED: "default",
};

const KHOA_TRANG_THAI: Readonly<Record<ClaimStatus, string>> = {
  PENDING: "statusPending",
  APPROVED: "statusApproved",
  REJECTED: "statusRejected",
  CANCELLED: "statusCancelled",
};

export function ClaimPendingScreen() {
  const t = useTranslations("claim");
  const mine = useMyClaims();

  if (mine.isPending) {
    return (
      <div data-claim-state="LOADING" aria-busy="true">
        <p className="m-0 mb-3 text-than text-text-muted">{t("loading")}</p>
        <Skeleton active paragraph={{ rows: 5 }} />
      </div>
    );
  }

  if (mine.isError) {
    return (
      <ClaimBlockNotice
        as="h1"
        failure={claimFailureOf(mine.error)}
        onRetry={() => void mine.refetch()}
      />
    );
  }

  const claims = claimsOf(mine.data);
  const quota = quotaOf(mine.data);

  if (claims.length === 0) {
    return (
      <ClaimNotice as="h1" titleId="claim-empty" title={t("pending.emptyTitle")}>
        <ClaimParagraph>{t("pending.emptyBody")}</ClaimParagraph>
        <div>
          <ClaimLink href={claimRoutes.start} bac="chinh" icon={<UserOutlined />}>
            {t("pending.emptyAction")}
          </ClaimLink>
        </div>
      </ClaimNotice>
    );
  }

  const coDonDangCho = claims.some((c) => c.status === "PENDING");

  return (
    <div className="space-y-4">
      <h1 className="m-0 font-serif text-de font-bold text-text-main">{t("pending.title")}</h1>

      {claims.map((claim) => (
        <TheDon key={claim.id} claim={claim} quota={quota} />
      ))}

      {coDonDangCho && <TrongLucCho />}
    </div>
  );
}

function TheDon({ claim, quota }: { claim: ClaimView; quota: ClaimQuotaDto | null }) {
  const t = useTranslations("claim");
  const format = useFormatter();
  const cancel = useCancelClaim();

  /**
   * Tên của ô được nhận — **phải gọi sang `/persons/{id}`, không đọc từ đơn**.
   *
   * {@code PersonClaimView} cố ý không chở tên: nhân khẩu ấy có thể là một
   * người <b>còn sống</b>, và ai được xem gì về họ là câu hỏi của bộ lọc phân
   * tầng riêng tư — thứ chạy ở {@code GET /persons/&#123;id&#125;}. Chép sẵn
   * một cái tên vào phản hồi của đơn là dựng một lối đọc thứ hai không có bộ
   * lọc nào.
   *
   * Đơn {@code NEW_PERSON} thì ngược lại: {@code declaredName} là tên người gửi
   * <b>tự khai</b>, chưa phải tên một nhân khẩu nào, nên không có gì để lọc.
   */
  const target = useClaimTarget(claim.kind === "EXISTING" ? (claim.personId ?? null) : null);

  const tenDaKhai = claim.declaredName ?? "";
  const tieuDe =
    claim.kind === "NEW_PERSON"
      ? t("pending.kindNew", { name: tenDaKhai })
      : target.data?.displayName
        ? t("pending.kindExisting", { name: target.data.displayName })
        : // Không tra ra tên thì vẫn phải đọc trôi chảy. Một tiêu đề "Nhận mình
          // là " bỏ lửng còn tệ hơn một câu không có tên.
          t("pending.kindExistingUnknown");

  const branch = claim.targetBranch?.name ?? null;
  const clanTitle = claim.targetBranch?.clanTitle ?? null;

  return (
    <section
      data-claim-id={claim.id}
      data-claim-status={claim.status}
      aria-labelledby={`claim-${claim.id}-title`}
      className="rounded-lg border px-4 py-5 sm:px-6"
      style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
    >
      {/* Màu KHÔNG BAO GIỜ là tín hiệu duy nhất: thẻ mang chữ, và chữ đứng
          trước mọi thứ khác trên dòng. */}
      <Tag color={MAU_TRANG_THAI[claim.status]} className="!m-0 !text-than">
        {t(`pending.${KHOA_TRANG_THAI[claim.status]}`)}
      </Tag>

      <h2
        id={`claim-${claim.id}-title`}
        className="m-0 mt-3 font-serif text-de font-bold text-text-main"
      >
        {tieuDe}
      </h2>

      <p className="m-0 mt-1 text-than text-text-muted">
        {t("pending.submittedAt", {
          date: format.dateTime(new Date(claim.createdAt), { dateStyle: "medium" }),
        })}
        {claim.reviewedAt
          ? ` · ${t("pending.reviewedAt", {
              date: format.dateTime(new Date(claim.reviewedAt), { dateStyle: "medium" }),
            })}`
          : ""}
      </p>

      {claim.status === "PENDING" && (
        <div className="mt-4 space-y-3">
          <h3 className="m-0 flex items-center gap-2 text-than font-semibold text-text-main">
            <span aria-hidden style={{ color: colorVars.accentText }}>
              <ClockCircleOutlined />
            </span>
            {t("pending.waitingForTitle")}
          </h3>
          <p className="m-0 max-w-prose text-dan leading-relaxed text-text-main">
            {branch && clanTitle
              ? t("pending.waitingFor", { branch, title: clanTitle })
              : branch
                ? t("pending.waitingForBranchOnly", { branch })
                : t("pending.waitingForUnknown")}
          </p>
          <p className="m-0 max-w-prose text-than leading-relaxed text-text-muted">
            {t("pending.waitingHint")}
          </p>
          {/* Đơn "chưa có trong phả": nhắc lại rằng hồ sơ CHƯA được lập. Người
              dùng đọc câu này ở màn gửi đơn rồi, nhưng họ quay lại màn chờ sau
              nhiều ngày — và đó đúng là lúc họ bắt đầu tưởng mình đã vào phả. */}
          {claim.kind === "NEW_PERSON" && (
            <p className="m-0 max-w-prose text-than leading-relaxed text-text-muted">
              {t("pending.notCreatedYet")}
            </p>
          )}

          <div className="space-y-2 pt-1">
            <ClaimButton
              bac="phu"
              disabled={cancel.isPending}
              onClick={() => cancel.mutate(claim.id)}
            >
              {cancel.isPending ? t("pending.cancelling") : t("pending.cancel")}
            </ClaimButton>
            <p className="m-0 max-w-prose text-than leading-snug text-text-muted">
              {t("pending.cancelHint")}
            </p>
            {cancel.isError && (
              <p
                role="alert"
                className="m-0 max-w-prose text-than"
                style={{ color: colorVars.danger }}
              >
                {t("pending.cancelFailed")}
              </p>
            )}
          </div>
        </div>
      )}

      {(claim.status === "REJECTED" || claim.status === "CANCELLED") && (
        <div className="mt-4 space-y-3">
          {claim.status === "REJECTED" && claim.reviewNote && (
            <div
              className="rounded-lg border px-4 py-3"
              style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
            >
              <h3 className="m-0 text-than font-semibold text-text-main">
                {t("pending.rejectedReasonTitle")}
              </h3>
              <p className="m-0 mt-2 max-w-prose text-dan leading-relaxed text-text-main">
                {claim.reviewNote}
              </p>
            </div>
          )}
          {/* Gửi lại được — và số lần còn lại phải nói ra ở ĐÂY, ngay cạnh cái
              nút, chứ không ở một màn sau. `quota === null` nghĩa là máy chủ
              chưa gửi bộ đếm: im lặng về con số, KHÔNG đoán. */}
          {quota !== null && quota.remaining <= 0 ? (
            <p className="m-0 max-w-prose text-dan leading-relaxed text-text-main">
              {t("pending.resubmitExhausted")}
            </p>
          ) : (
            <>
              {quota !== null && (
                <p className="m-0 max-w-prose text-than text-text-main">
                  {t("pending.resubmitRemaining", { n: quota.remaining })}
                </p>
              )}
              <div>
                <ClaimLink href={claimRoutes.start} bac="chinh" icon={<UserOutlined />}>
                  {t("pending.resubmit")}
                </ClaimLink>
              </div>
            </>
          )}
        </div>
      )}

      {claim.status === "APPROVED" && (
        <div className="mt-4 space-y-3">
          <p className="m-0 max-w-prose text-dan leading-relaxed text-text-main">
            {t("pending.approvedBody")}
          </p>
          {/* `createdPersonId` với đơn "chưa có trong phả" (nhân khẩu vừa được
              TẠO lúc duyệt), `personId` với đơn nhận mình. */}
          {(claim.createdPersonId ?? claim.personId) && (
            <div>
              <ClaimLink
                href={`/persons/${claim.createdPersonId ?? claim.personId}`}
                bac="chinh"
                icon={<UserOutlined />}
              >
                {t("pending.approvedOpenProfile")}
              </ClaimLink>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

/**
 * "Trong lúc chờ, ông/bà xem được gì" — hai phần, và phần thứ hai bắt buộc.
 *
 * Dấu tích và dấu gạch <b>đi kèm chữ</b>, không bao giờ thay cho chữ: một dấu
 * trên nền kem thì phân biệt "được" với "chưa được" bằng gì, với người đeo
 * kính? Nên mỗi dòng tự mang nghĩa trong câu chữ của nó ("Xem được…", "Chưa…"),
 * và biểu tượng chỉ để nhận diện nhanh.
 */
function TrongLucCho() {
  const t = useTranslations("claim");

  return (
    <ClaimNotice titleId="claim-meanwhile" title={t("pending.meanwhileTitle")}>
      <ul className="m-0 list-none space-y-3 p-0">
        <Dong duoc>{t("pending.canTree")}</Dong>
        <Dong duoc>{t("pending.canDeceased")}</Dong>
        <Dong duoc={false}>{t("pending.cannotWrite")}</Dong>
        <Dong duoc={false}>{t("pending.cannotPrivate")}</Dong>
      </ul>
      <div className="flex flex-wrap gap-3">
        <ClaimLink href="/tree" icon={<ApartmentOutlined />}>
          {t("start.openTree")}
        </ClaimLink>
      </div>
    </ClaimNotice>
  );
}

function Dong({ duoc, children }: { duoc: boolean; children: ReactNode }) {
  return (
    <li className="flex items-start gap-2">
      <span
        aria-hidden
        className="mt-1 shrink-0"
        style={{ color: duoc ? colorVars.successText : colorVars.textMuted }}
      >
        {duoc ? <CheckCircleOutlined /> : <MinusCircleOutlined />}
      </span>
      <span className="max-w-prose text-dan leading-relaxed text-text-main">{children}</span>
    </li>
  );
}

"use client";

import { Alert, Skeleton, Tag } from "antd";
import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { usePerson } from "@/hooks/use-person";
import { headlineName } from "@/lib/format/name-layers";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { ClaimReviewView } from "@/lib/api/membership-admin";
import { DuplicateMatchList } from "./duplicate-match-list";
import { RequestDecision } from "./request-decision";
import { RequesterBlock } from "./requester-block";

export interface NewPersonRequestCardProps {
  request: ClaimReviewView;
}

/**
 * **Đơn loại B — "tôi chưa có trong phả"**: duyệt = <b>tạo một nhân khẩu mới
 * trong gia phả</b>.
 *
 * <h2>Thẻ này phải trông nặng hơn, vì nó nặng hơn</h2>
 * design 07 §1.5 gọi đây là "lối GHI VÀO PHẢ, không phải một biểu mẫu liên hệ".
 * Nó cho một người <em>chưa được duyệt</em> khởi tạo việc thêm người vào gia
 * phả. Xoá mềm là luật tuyệt đối, nên một nhân khẩu tạo nhầm <b>ở lại trong cây
 * vĩnh viễn</b>. Một thẻ trông giống hệt đơn loại A sẽ được xử lý bằng cùng một
 * phản xạ, và phản xạ ấy đúng cho loại A.
 *
 * Vì vậy: viền đậm màu chính, một dải cảnh báo mở đầu nói thẳng hệ quả, bộ dò
 * trùng đặt <b>trước</b> hai nút quyết định, và nhãn nút ghi "Duyệt — tạo nhân
 * khẩu mới" chứ không chỉ "Duyệt".
 *
 * <h2>Người thân đã có trong phả là bắt buộc, và nó quyết định ai duyệt</h2>
 * Ràng buộc 2 của §1.5, phát biểu ở CSDL bằng `ck_person_claim_shape`: thiếu nó
 * thì nhân khẩu mới thành node mồ côi — không tính được đời, không tra được
 * danh xưng. Người mới chưa thuộc chi nào, nên Trưởng chi có thẩm quyền là
 * Trưởng chi của <em>người thân được chỉ ra</em>.
 *
 * Người thân ấy đến trong đơn dưới dạng **khoá** (`relativePersonId`) chứ không
 * phải một khối lồng có sẵn tên: cùng lý do riêng tư với
 * {@code claim-request-card.tsx} — người thân hoàn toàn có thể là một người còn
 * sống, và bộ lọc phân tầng chỉ chạy ở `GET /persons/&#123;id&#125;`.
 */
export function NewPersonRequestCard({ request }: NewPersonRequestCardProps) {
  const t = useTranslations("membership");
  const format = useFormatter();
  const relativeId = request.relativePersonId ?? undefined;
  const relativeQuery = usePerson(relativeId);
  const relative = relativeQuery.data?.person;
  const tenNguoiThan = (relative && headlineName(relative)) ?? null;

  return (
    <article
      data-testid="don-chua-co-trong-pha"
      data-request-id={request.id}
      className="rounded-lg border-2 bg-bg-card px-4 py-3"
      style={{ borderColor: colorVars.primary }}
    >
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="m-0 text-than font-medium uppercase tracking-wide text-text-muted">
            {t("kind.NEW_PERSON")}
          </p>
          <h3 className="m-0 font-serif text-de font-bold text-text-main">
            {request.declaredName?.trim() || t("card.unnamedProposal")}
          </h3>
          <p className="m-0 mt-0.5 text-than text-text-muted">
            {isPresent(request.declaredBirthYear)
              ? t("card.birthYear", { year: request.declaredBirthYear })
              : ""}
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

      <Alert
        className="!mt-2"
        type="warning"
        showIcon
        message={t("card.writesToTree")}
        description={t("card.writesToTreeDetail")}
      />

      {relativeId && request.relativeKind && (
        <section
          className="mt-3 rounded border p-3"
          style={{ borderColor: colorVars.border, background: colorVars.bgPage }}
        >
          <h4 className="m-0 mb-1 text-than font-medium uppercase tracking-wide text-text-muted">
            {t("card.relativeTitle")}
          </h4>
          <p className="m-0 text-dan text-text-main">
            {t(`card.relativeKind.${request.relativeKind}`)}
            {": "}
            <Link href={`/persons/${relativeId}`} className="font-semibold text-primary">
              {relativeQuery.isPending ? (
                <Skeleton.Input active size="small" style={{ width: 150 }} />
              ) : (
                (tenNguoiThan ?? t("card.unknownPerson"))
              )}
            </Link>
          </p>
          <p className="m-0 mt-0.5 text-than text-text-muted">
            {[
              isPresent(relative?.generation)
                ? t("card.generation", { n: relative?.generation })
                : null,
              relative?.primaryBranch?.name ?? null,
            ]
              .filter((x): x is string => Boolean(x))
              .join(" · ")}
          </p>
        </section>
      )}

      <DuplicateMatchList suspects={request.duplicateSuspects} />

      <RequesterBlock request={request} />

      {request.reviewNote && (
        <p className="m-0 mt-2 text-than text-text-muted">
          {t("card.reviewNote", { note: request.reviewNote })}
        </p>
      )}

      <RequestDecision request={request} subject={request.declaredName ?? ""} />

      {request.status !== "PENDING" && (
        <p className="m-0 mt-2 text-than font-medium" style={{ color: colorVars.textMuted }}>
          {t(`status.${request.status}`)}
        </p>
      )}
    </article>
  );
}

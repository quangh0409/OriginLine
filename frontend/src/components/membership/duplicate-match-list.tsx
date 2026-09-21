"use client";

import { Skeleton, Tag } from "antd";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { usePerson } from "@/hooks/use-person";
import { headlineName } from "@/lib/format/name-layers";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { ClaimDuplicateSuspectDto } from "@/lib/api/membership-admin";

export interface DuplicateMatchListProps {
  /**
   * `PersonClaim.duplicateSuspects` — **ba trạng thái, không phải hai**:
   * `undefined` = chưa quét bao giờ · `[]` = đã quét, không nghi ai · có phần
   * tử = đã quét, và đây.
   */
  suspects?: ClaimDuplicateSuspectDto[];
}

/**
 * **"Có thể đây là người này"** — ảnh chụp dò trùng máy chủ chạy lúc người khai
 * bấm gửi, hiện cho Trưởng chi trước khi họ bấm Duyệt.
 *
 * <h2>Vì sao khối này là phần quan trọng nhất của một lá đơn `NEW_PERSON`</h2>
 * Ràng buộc 3 của design 07 §1.5: người khai <b>rất có thể đã</b> có trong phả
 * dưới một tên khác — tên huý, tên thường gọi. Một chị dâu mới về được bà nội
 * ghi vào phả bằng tên huý từ hai tháng trước sẽ tự khai bằng tên thường gọi và
 * không tìm thấy mình. Không có khối này thì Trưởng chi tạo ra một bản trùng,
 * và một bản trùng trong gia phả **không xoá được** — xoá mềm là luật tuyệt đối.
 *
 * <h2>Ba trạng thái, không phải hai</h2>
 * "Chưa dò" · "đã dò, không thấy ai" · "đã dò, đây là ai". Hai cái đầu trông
 * giống nhau nếu giao diện im lặng, mà chúng dẫn tới hai quyết định khác hẳn
 * nhau đối với người sắp bấm Duyệt.
 *
 * <h2>Tên tra qua `GET /persons/{id}`, KHÔNG đọc từ ảnh chụp</h2>
 * `ClaimDuplicateSuspect` cố ý chỉ chứa khoá + điểm + tín hiệu: bên bị nghi có
 * thể là một người **còn sống ở chi khác**. Và ảnh chụp này được **lưu vào
 * đơn** — một giá trị đọc từ phả lọt vào đó là lọt vĩnh viễn, đi qua mọi lần
 * đọc đơn về sau mà không còn bộ lọc nào chạy trên nó. Nên mỗi dòng ở đây gọi
 * thêm một lượt, bằng chính phiên của người duyệt, nơi bộ lọc phân tầng riêng
 * tư thật sự chạy.
 *
 * Đắt hơn, và không có cách nào rẻ hơn mà vẫn đúng.
 */
export function DuplicateMatchList({ suspects }: DuplicateMatchListProps) {
  const t = useTranslations("membership");
  const matches = suspects;

  if (matches === undefined) {
    return (
      <p
        data-testid="do-trung-chua-chay"
        className="m-0 mt-3 text-than"
        style={{ color: colorVars.danger }}
      >
        {t("duplicate.notScanned")}
      </p>
    );
  }

  if (matches.length === 0) {
    return (
      <p data-testid="do-trung-rong" className="m-0 mt-3 text-than text-text-muted">
        {t("duplicate.none")}
      </p>
    );
  }

  return (
    <section
      data-testid="do-trung"
      className="mt-3 rounded border p-3"
      style={{ borderColor: colorVars.accent, background: colorVars.warningBg }}
    >
      <h4 className="m-0 mb-1 text-than font-semibold text-text-main">
        {t("duplicate.title", { n: matches.length })}
      </h4>
      <p className="m-0 mb-2 text-than text-text-muted">{t("duplicate.lead")}</p>

      <ul className="m-0 flex list-none flex-col gap-2 p-0">
        {matches.map((match) => (
          <DuplicateMatchRow key={match.personId} match={match} />
        ))}
      </ul>
    </section>
  );
}

/**
 * Một dòng ứng viên. Tên đến từ một lượt gọi riêng; lượt ấy hỏng **không** làm
 * mất dòng — khoá, điểm và tín hiệu vẫn nói được "vì sao nghi", và người duyệt
 * vẫn mở được hồ sơ bằng đường dẫn.
 */
function DuplicateMatchRow({ match }: { match: ClaimDuplicateSuspectDto }) {
  const t = useTranslations("membership");
  const personQuery = usePerson(match.personId);
  const person = personQuery.data?.person;

  return (
    <li className="rounded border border-border bg-bg-card px-3 py-2">
      <div className="flex flex-wrap items-baseline justify-between gap-x-2">
        <Link href={`/persons/${match.personId}`} className="text-dan font-semibold text-primary">
          {personQuery.isPending ? (
            <Skeleton.Input active size="small" style={{ width: 150 }} />
          ) : (
            ((person && headlineName(person)) ?? t("duplicate.unreadablePerson"))
          )}
        </Link>
        <Tag bordered={false} className="!m-0 !text-than">
          {t("duplicate.score", { score: Math.round(match.score) })}
        </Tag>
      </div>

      {person && (
        <p className="m-0 text-than text-text-muted">
          {[
            isPresent(person.generation) ? t("card.generation", { n: person.generation }) : null,
            person.primaryBranch?.name ?? null,
          ]
            .filter((x): x is string => Boolean(x))
            .join(" · ")}
        </p>
      )}

      {/* `signals` là TUỲ CHỌN ở contract — một ứng viên không kèm tín hiệu vẫn
          hợp lệ, và khi ấy `hint` là thứ nói "vì sao nghi". */}
      {match.hint && (
        <p className="m-0 mt-1 text-than text-text-main">{match.hint}</p>
      )}

      {(match.signals?.length ?? 0) > 0 && (
        <p className="m-0 mt-1 flex flex-wrap gap-1">
          {match.signals?.map((signal) => (
            <Tag key={signal} bordered={false} className="!m-0 !text-than">
              {t(`duplicate.signal.${signal}`)}
            </Tag>
          ))}
        </p>
      )}
    </li>
  );
}

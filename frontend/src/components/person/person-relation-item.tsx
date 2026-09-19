"use client";

import { Tag, Tooltip } from "antd";
import { TeamOutlined } from "@ant-design/icons";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { BADGE_META } from "@/lib/tree/badges";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import { PersonBadgeList } from "./person-badge-list";
import type { RelationEntry, RelationGroupKey } from "./relations-model";

export interface PersonRelationItemProps {
  entry: RelationEntry;
  group: RelationGroupKey;
  /** Đa thê/đa phu mới cần nhãn thứ tự — xem `shouldShowSpouseOrder`. */
  showSpouseOrder: boolean;
}

/**
 * Một dòng quan hệ: tên người ở đầu kia, bối cảnh đời/chi, các nhãn do máy chủ
 * tính, và các sắc thái nằm trên chính cạnh quan hệ (con nuôi · thứ tự vợ ·
 * hôn phối đã kết thúc · kế tự/đích tôn).
 *
 * Không dòng nào ở đây mang **danh xưng**. "Anh", "chị", "em", "bác", "cô" phụ
 * thuộc vùng miền và bộ quy tắc của từng dòng họ, nên chúng chỉ đến từ
 * `/api/v1/kinship` — đó chính là lý do mỗi dòng có một lối tắt sang màn tra
 * danh xưng thay vì tự đoán một chữ xưng hô rồi in ra.
 */
export function PersonRelationItem({ entry, group, showSpouseOrder }: PersonRelationItemProps) {
  const t = useTranslations("person");
  const locale = useLocale();
  const { person } = entry;

  const hasBadge = (badge: keyof typeof BADGE_META) => entry.badges.includes(badge);
  const badgeLabel = (badge: keyof typeof BADGE_META) =>
    locale === "vi" ? BADGE_META[badge].vi : BADGE_META[badge].en;

  // Con nuôi đã có nhãn do máy chủ tính thì không lặp lại nhãn tự suy từ cạnh.
  const showAdoptiveTag = entry.adoptive && !hasBadge("CON_NUOI");
  const showHeirTag = entry.heirKind !== null && !hasBadge(entry.heirKind);

  // Giới tính quyết định "vợ" hay "chồng"; không rõ giới thì dùng bản trung tính
  // thay vì đoán bừa một trong hai.
  const spouseSuffix =
    person.gender === "FEMALE" ? "Wife" : person.gender === "MALE" ? "Husband" : "";

  return (
    <li className="flex flex-wrap items-center gap-x-2 gap-y-1 py-2">
      {/* `min-h-11`: hàng này là một hàng FLEX, nên thẻ <a> bị "khối hoá" và mất
          ngoại lệ "liên kết giữa dòng chữ" của WCAG 2.5.8 — nó đứng riêng, là một
          điều khiển thật, và đo được 24px. Tên người là lối vào chính của cả hàng;
          trên điện thoại đây là thứ người ta chạm nhiều nhất trên hồ sơ. */}
      <Link
        href={`/persons/${person.id}`}
        className="flex min-h-11 items-center rounded font-medium text-text-main underline-offset-2 hover:text-primary hover:underline"
      >
        {person.displayName}
      </Link>

      {/* Sắc thái trên cạnh — mỗi nhãn là một sự kiện đã được ghi trong gia
          phả, không phải suy đoán của giao diện. */}
      {showAdoptiveTag && (
        <Tag bordered={false} color={colorVars.secondary} style={{ color: colorVars.bgPage }} className="!m-0">
          {group === "parents" ? t("relationTag.adoptiveParent") : t("relationTag.adoptedChild")}
        </Tag>
      )}

      {group === "spouses" && showSpouseOrder && entry.spouseOrder != null && (
        // "Vợ cả / vợ thứ hai" là từ ghi chép gia phả gắn với `spouseOrder`,
        // không phải danh xưng xưng hô — nó không đổi theo Bắc/Trung/Nam.
        <Tag bordered={false} color={colorVars.accentText} style={{ color: colorVars.bgPage }} className="!m-0">
          {entry.spouseOrder === 1
            ? t(`relationTag.spouseSenior${spouseSuffix}`)
            : t(`relationTag.spouseOrder${spouseSuffix}`, { n: entry.spouseOrder })}
        </Tag>
      )}

      {entry.ended && (
        // `validTo` chỉ nói hôn phối đã kết thúc, KHÔNG nói vì ly hôn hay vì
        // một bên đã mất — nhãn phải trung tính đúng như dữ liệu.
        <Tooltip title={isPresent(entry.endedOn) ? entry.endedOn : undefined}>
          <Tag bordered={false} className="!m-0">
            {t("relationTag.ended")}
          </Tag>
        </Tooltip>
      )}

      {showHeirTag && entry.heirKind && (
        <Tag
          bordered={false}
          color={BADGE_META[entry.heirKind].color}
          style={{ color: BADGE_META[entry.heirKind].ink }}
          className="!m-0"
        >
          {badgeLabel(entry.heirKind)}
        </Tag>
      )}

      <PersonBadgeList badges={entry.badges} size="sm" />

      <span className="flex items-center gap-2 text-than text-text-muted">
        {isPresent(person.generation) && <span>{t("generationValue", { n: person.generation })}</span>}
        {isPresent(person.primaryBranch?.name) && <span>{person.primaryBranch?.name}</span>}
      </span>

      {/* `to=`, KHÔNG phải `from=`: câu người dùng muốn hỏi là "TÔI gọi người
          này là gì", nên người ở đầu kia là ĐÍCH của phép tra. */}
      <Link
        href={`/kinship?to=${person.id}`}
        aria-label={t("relationKinshipLink", { name: person.displayName })}
        // 16×24px trước đợt sửa: một biểu tượng trần trong hàng flex. `min-w-11`
        // cũng bắt buộc, không chỉ `min-h-11` — vùng chạm là HAI chiều, và đây là
        // điều khiển hẹp nhất trên hồ sơ.
        className="ml-auto flex min-h-11 min-w-11 items-center justify-center rounded text-text-muted hover:bg-primary-light hover:text-primary"
      >
        <TeamOutlined aria-hidden />
      </Link>
    </li>
  );
}

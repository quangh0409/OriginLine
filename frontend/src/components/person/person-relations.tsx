"use client";

import { useMemo } from "react";
import { Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/http";
import { PersonRelationItem } from "./person-relation-item";
import { PersonSection } from "./person-section";
import { usePersonRelations } from "./use-person-relations";
import {
  RELATION_GROUP_ORDER,
  buildRelationGroups,
  hasEmbeddedOtherPersons,
  isEmptyRelationGroups,
  shouldShowSpouseOrder,
} from "./relations-model";
import type { PersonDto } from "@/types/api";

export interface PersonRelationsProps {
  person: PersonDto;
}

/**
 * Mục **Quan hệ** của hồ sơ — cha mẹ · vợ/chồng · con · anh chị em (và nhóm
 * nối dõi khi có kế tự/thừa tự).
 *
 * Không có mục này thì hồ sơ dâu/rể là trang trắng nhất sản phẩm: họ không có
 * tổ tiên trong dòng họ này, nên nếu không hiện quan hệ thì hồ sơ chẳng còn gì
 * — và một sản phẩm gia phả dựng ra một cá nhân đứng lẻ là một mâu thuẫn tự thân.
 *
 * ## Ba lý do khác nhau khiến danh sách có thể ngắn
 *
 * Mục này phân biệt rạch ròi ba lý do, vì trộn chúng vào nhau là cách nhanh
 * nhất làm người dùng mất niềm tin vào dữ liệu:
 *
 * 1. **Chưa ai ghi.** Nói thẳng ("chưa ghi nhận quan hệ nào") — đây mới là
 *    thông tin hữu ích, và cũng là lời mời đóng góp.
 * 2. **Máy chủ cắt bớt cho nhẹ** (`meta.truncated`). Đây là giới hạn kỹ thuật,
 *    không phải quyền hạn; nói rõ và chỉ sang cây phả đồ.
 * 3. **Người gọi chưa đủ quyền.** Chỉ nói được ở đúng một trường hợp — khách
 *    chưa đăng nhập, vì khách không thấy bất kỳ người còn sống nào. Câu này
 *    đúng với mọi hồ sơ, mọi lúc, nên nó không tiết lộ gì về CHÍNH hồ sơ này.
 *
 * Với thành viên đã đăng nhập, mục này cố ý **không** có chú thích quyền hạn
 * nào: máy chủ chỉ bỏ cạnh khi đầu kia bị lọc, và một câu kiểu "có thể còn
 * quan hệ bạn không xem được" dán trên mọi hồ sơ vừa là nhiễu vừa gieo nghi
 * ngờ vào những hồ sơ vốn đầy đủ. Số cạnh bị bỏ thì không bao giờ được đếm ra
 * — con số đó chính là thứ phân tầng đang che.
 *
 * ## Vì sao ở đây không còn khung chờ trong trường hợp thường gặp
 *
 * `PersonDto.relationships` nay mang sẵn `otherPerson` — tóm tắt của người ở
 * đầu kia, đã lọc theo đúng người gọi. Khi mọi cạnh đều có nó, bốn nhóm quan
 * hệ trực tiếp vẽ được **ngay từ phản hồi hồ sơ**, không chờ lượt mạng nào.
 * Trước đây màn này dựng một khung xương chỉ để chờ `/tree` đổi id lấy tên.
 *
 * Lượt `/tree` vẫn còn, nhưng đã rời khỏi đường găng và chỉ bổ khuyết hai thứ
 * không có nguồn nào khác: **anh chị em** (quan hệ hai bậc, `relationships`
 * theo hợp đồng chỉ có một bậc) và **`badges`** (`PersonSummaryDto` không có
 * trường ấy). Nó tới muộn thì nhóm anh chị em hiện thêm; nó hỏng thì bốn nhóm
 * kia vẫn đứng nguyên, và người dùng không phải đọc một lời báo lỗi cho thứ
 * họ đang nhìn thấy đầy đủ.
 */
export function PersonRelations({ person }: PersonRelationsProps) {
  const t = useTranslations("person");
  const tPrivacy = useTranslations("privacy");
  const auth = useAuth();
  const { data, isPending, error } = usePersonRelations(person.id);

  // Bốn nhóm trực tiếp đã đủ dữ liệu ngay trong phản hồi hồ sơ hay chưa.
  const embedded = hasEmbeddedOtherPersons(person.relationships);

  const groups = useMemo(
    () =>
      buildRelationGroups({
        personId: person.id,
        projection: data,
        relationships: person.relationships,
      }),
    [person.id, person.relationships, data]
  );

  // Vai do máy chủ khẳng định luôn thắng phán đoán ở client; chỉ khi hợp đồng
  // không gửi `callerRole` mới rơi về trạng thái phiên của trình duyệt.
  const isGuest = person.meta.callerRole
    ? person.meta.callerRole === "GUEST"
    : auth.status === "guest";

  if (isPending && !embedded) {
    return (
      <PersonSection title={t("relations")} hasContent>
        {/* Chữ chờ đọc được bằng trình đọc màn hình: khung xương của antd không
            có chữ nào, nên nếu thiếu dòng này thì đầu mục "Quan hệ" đứng trên một
            vùng câm — đúng cái hình dạng mà luật "không để đầu mục trống" cấm. */}
        <span role="status" className="sr-only">
          {t("relationsLoading")}
        </span>
        <Skeleton active title={false} paragraph={{ rows: 3 }} />
      </PersonSection>
    );
  }

  // 404 = nhân khẩu này không có mặt trong đồ thị (hồ sơ vừa tạo, hoặc khách
  // hỏi cây của người còn sống). Không có quan hệ để vẽ, và không phải lỗi.
  const notInGraph = error instanceof ApiError && error.status === 404;
  // Có dữ liệu nhúng thì lượt `/tree` chỉ là phần bổ khuyết — hỏng thì im lặng
  // mất nhóm anh chị em, chứ không được biến cả mục thành một lời báo lỗi cho
  // thứ người dùng đang nhìn thấy đầy đủ.
  if (error && !notInGraph && !embedded) {
    return (
      <PersonSection title={t("relations")} hasContent>
        <p role="status" className="m-0 text-than text-text-muted">
          {t("relationsError")}
        </p>
      </PersonSection>
    );
  }

  const empty = isEmptyRelationGroups(groups);
  const truncated = data?.meta.truncated === true;

  return (
    <PersonSection title={t("relations")} hasContent>
      {!empty && (
        <div className="space-y-3">
          {RELATION_GROUP_ORDER.map((key) => {
            const entries = groups[key];
            if (entries.length === 0) return null;

            return (
              <div key={key}>
                {/* Tên nhóm là PHÂN LOẠI gia phả, không phải danh xưng: nó
                    không đổi theo vùng miền, còn danh xưng thì có. */}
                <h3 className="m-0 text-than font-semibold uppercase tracking-wide text-text-muted">
                  {t(`relationGroup.${key}`)}
                </h3>
                <ul className="m-0 list-none divide-y divide-border p-0">
                  {entries.map((entry) => (
                    <PersonRelationItem
                      key={`${key}-${entry.person.id}`}
                      entry={entry}
                      group={key}
                      showSpouseOrder={shouldShowSpouseOrder(entry, groups.spouses.length)}
                    />
                  ))}
                </ul>
              </div>
            );
          })}
        </div>
      )}

      {empty && !isGuest && (
        <p className="m-0 py-1 text-than text-text-muted">{t("relationsEmpty")}</p>
      )}

      {truncated && (
        <p className="m-0 pt-2 text-than text-text-muted">{t("relationsTruncated")}</p>
      )}

      {isGuest && (
        <p
          role="note"
          data-privacy-notice="relations-guest"
          className="m-0 pt-2 text-than leading-relaxed text-text-muted"
        >
          {tPrivacy("guestRelations")}
        </p>
      )}
    </PersonSection>
  );
}

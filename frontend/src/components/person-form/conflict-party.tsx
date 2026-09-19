"use client";

import { Skeleton } from "antd";
import { EyeInvisibleOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { usePerson } from "@/hooks/use-person";
import { ApiError } from "@/lib/api/http";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { NameType } from "@/types/api";

export interface ConflictPartyProps {
  /**
   * Khoá — thứ **duy nhất** mà thân lỗi `409` phát ra về người bên kia.
   * `null` nghĩa là không có gì để tra, và thành phần này không vẽ gì cả.
   */
  personId: string | null;
  /**
   * Lớp tên đã khớp. Khi hồ sơ tra được, ô này quyết định lớp tên nào được nêu
   * ra — với kỵ húy là `HUY`. Bỏ trống thì chỉ hiện tên hiển thị.
   */
  matchedNameType?: NameType;
  /** Câu chữ cho ca `404`, khác nhau giữa kỵ húy và nghi trùng. */
  notVisible: { title: string; body: string; ask: string };
  loadFailedLabel: string;
  testId: string;
}

/**
 * Nửa "người bên kia" của một hộp thoại va chạm — dựng từ khoá, không từ 409.
 *
 * <h2>Vì sao phải tự đi hỏi</h2>
 * `409 KY_HUY_CONFLICT` và `409 DUPLICATE_PERSON_SUSPECTED` **không phát một
 * trường nhân khẩu nào** đọc từ phả. Không phải vì hiện thực còn dở: cả hai bộ
 * dò đều quét **toàn dòng họ** — phép dò kỵ húy chọn bậc trên theo *đời thứ*,
 * không theo sống/mất, không theo chi và không có ngưỡng điểm nào — nên người
 * bị nêu hoàn toàn có thể đang **còn sống ở một chi khác** mà người đang thêm
 * nhân khẩu không có quyền biết gì về họ, kể cả việc họ tồn tại.
 *
 * Bộ lọc phân tầng riêng tư sống ở context `genealogy` và là chỗ **duy nhất**
 * được quyết định trường nào của một người được trả cho ai. Thân lỗi đi thẳng
 * ra HTTP, không đi qua bộ lọc ấy. Vậy nên: cầm khoá, gọi `GET /persons/{id}`.
 * Thêm một vòng gọi, đổi lấy việc chỉ có **một** luật lọc.
 *
 * <h2>`404` ở đây là ca BÌNH THƯỜNG, không phải lỗi kỹ thuật</h2>
 * Đó là câu trả lời đúng khi người gọi không được biết bản ghi có tồn tại hay
 * không — cùng một `404` cho "không có" và cho "có nhưng bạn không được biết",
 * vì phân biệt hai thứ ấy chính là rò rỉ. Giao diện vì thế **không** dùng
 * `role="alert"`, không dùng sắc đỏ, không nói "không tìm thấy", và luôn chỉ
 * ra một chỗ để đi tiếp: Hội đồng Tộc biểu nhìn được cả hai bên.
 *
 * <h2>Không rò rỉ qua đường bố cục</h2>
 * Ca `404` in một khối văn xuôi, **không** in một bảng nhãn–giá trị với các ô
 * trống. Một bảng ba dòng rỗng nói đúng cái mà bộ lọc đang giấu: có ba trường ở
 * đây. Ca `200` cũng chỉ in những trường máy chủ **thật sự** trả về, nên đếm số
 * dòng của hai ca không suy ra được gì.
 *
 * <h2>Mất mát thực tế gần bằng không</h2>
 * Bậc trên gần như luôn là người đã khuất, mà người đã khuất là dữ liệu công
 * khai (BA v2 §10), nên `GET` trả lại đủ tên húy và đời thứ và hộp thoại hiện y
 * như trước. Chỉ khi người bên kia còn sống mới bị cắt — và đó đúng là hành vi
 * mong muốn. Đừng thiết kế như thể `404` là ca thường gặp.
 */
export function ConflictParty({
  personId,
  matchedNameType,
  notVisible,
  loadFailedLabel,
  testId,
}: ConflictPartyProps) {
  const t = useTranslations("personForm.conflictParty");
  const tPerson = useTranslations("person");
  // `usePerson(undefined)` tự tắt truy vấn — ứng viên chưa có trong phả
  // (`personId === null`) thì không có gì để hỏi, và gọi `/persons/null` chỉ
  // thêm một lượt 404 vô nghĩa vào nhật ký máy chủ.
  const query = usePerson(personId ?? undefined);

  if (personId === null) return null;

  if (query.isPending) {
    return <Skeleton active paragraph={{ rows: 1 }} title={false} />;
  }

  if (query.error instanceof ApiError && query.error.status === 404) {
    return (
      <div data-testid={`${testId}-not-visible`}>
        <p
          className="m-0 flex items-center gap-2 text-than font-semibold"
          style={{ color: colorVars.textMain }}
        >
          {/* Biểu tượng luôn kèm chữ — một biểu tượng trần không nói được gì. */}
          <EyeInvisibleOutlined aria-hidden />
          {notVisible.title}
        </p>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
          {notVisible.body}
        </p>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
          {notVisible.ask}
        </p>
      </div>
    );
  }

  const person = query.data?.person;
  if (query.isError || !person) {
    // Lỗi mạng/máy chủ thật thì MỚI là lỗi, và phải đọc khác hẳn ca trên: hai
    // câu ấy dẫn người dùng đi hai hướng khác nhau.
    return (
      <p className="m-0 text-than" style={{ color: colorVars.danger }} role="alert">
        {loadFailedLabel}
      </p>
    );
  }

  const matchedName = matchedNameType
    ? person.names?.find((n) => n.nameType === matchedNameType)?.fullName
    : undefined;

  return (
    <div data-testid={`${testId}-visible`}>
      <div className="flex flex-wrap items-baseline gap-x-2">
        <span className="font-serif text-than font-semibold" style={{ color: colorVars.textMain }}>
          {person.displayName ?? person.names?.[0]?.fullName}
        </span>
        {/* Chỉ hiện trường máy chủ THẬT SỰ trả về: một nhãn kèm ô trống đọc
            thành "phả không ghi" — đúng cái hiểu lầm phải tránh. */}
        {isPresent(person.generation) && (
          <span className="text-than" style={{ color: colorVars.textMuted }}>
            {t("generation", { n: person.generation })}
          </span>
        )}
      </div>
      {isPresent(matchedName) && matchedNameType && (
        <div className="mt-0.5 text-than" style={{ color: colorVars.textMuted }}>
          {t("matchedName", {
            label: tPerson(`nameType.${matchedNameType}`),
            name: matchedName,
          })}
        </div>
      )}
    </div>
  );
}

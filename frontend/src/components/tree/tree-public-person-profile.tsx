"use client";

import { useQuery } from "@tanstack/react-query";
import { Alert, Empty, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { ApiError } from "@/lib/api/http";
import { publicPortalApi, toPersonDetail } from "@/lib/api/public-portal";
import { PersonBiography } from "@/components/person/person-biography";
import { PersonLifeDates } from "@/components/person/person-life-dates";
import { PersonNameLayers } from "@/components/person/person-name-layers";
import { PersonOriginFacts } from "@/components/person/person-origin-facts";
import { PersonProfileHeader } from "@/components/person/person-profile-header";

export interface TreePublicPersonProfileProps {
  personId: string;
}

/**
 * Hồ sơ một người **đã khuất** cho Khách, mở từ một nút trên phả đồ công khai.
 *
 * <h2>Nó dựng từ những mảnh CÓ SẴN, không phải một bản sao</h2>
 * Các mục trình bày (`<PersonProfileHeader>`, `<PersonNameLayers>`,
 * `<PersonLifeDates>`, `<PersonOriginFacts>`, `<PersonBiography>`) đều là
 * component thuần nhận `PersonDto`, nên `toPersonDetail` đổi bản công khai về
 * đúng hình dạng ấy rồi dùng lại nguyên vẹn. Một bản sao riêng cho Khách là một
 * chỗ để hai bên lệch nhau — và lệch ở đây có nghĩa là lệch về những gì người
 * lạ đọc được.
 *
 * <h2>Ba mục cố ý VẮNG, không phải quên</h2>
 * <ul>
 *   <li><b>Quan hệ</b>: bản công khai chỉ gửi id của đầu kia, không gửi tóm
 *       tắt. `<PersonRelations>` sẽ phải tự gọi `/persons/{id}/relations` —
 *       đúng cái `401` mà màn này đang vá. Và dựng "có 3 quan hệ, không rõ với
 *       ai" chính là phép đếm mà `404` của bề mặt công khai đang tránh.</li>
 *   <li><b>Huy hiệu dâu/rể/đích tôn</b>: `/persons/{id}/badges` là bề mặt thành
 *       viên; giao diện không được tự suy nhãn.</li>
 *   <li><b>Câu giải thích phân tầng</b>: người đã khuất là công khai, không có
 *       gì để giải thích — và một câu "bạn đang xem bản rút gọn" ở đây sẽ nói
 *       sai, vì hồ sơ này **đầy đủ** đúng như nó tồn tại trên bề mặt công khai.</li>
 * </ul>
 *
 * `404` được vẽ thành "không tìm thấy" trơn, không thêm chữ nào về quyền: máy
 * chủ trả `404` cho người còn sống **chính vì** nó phải không phân biệt được
 * với `404` của một id bịa đặt.
 */
export function TreePublicPersonProfile({ personId }: TreePublicPersonProfileProps) {
  const t = useTranslations("person");

  const { data, isPending, error } = useQuery({
    queryKey: ["public-person", personId],
    queryFn: async () => toPersonDetail(await publicPortalApi.getPerson(personId)),
    // 404 là câu trả lời CUỐI CÙNG của máy chủ, không phải sự cố tạm thời.
    retry: (failureCount, err) =>
      err instanceof ApiError && err.status < 500 ? false : failureCount < 2,
    staleTime: 5 * 60_000,
  });

  if (isPending) {
    return (
      <div className="space-y-3">
        <Skeleton avatar active paragraph={{ rows: 2 }} />
        <Skeleton active paragraph={{ rows: 4 }} />
      </div>
    );
  }

  if (error) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <div className="py-10">
          <Empty description={t("notFound")} />
        </div>
      );
    }
    return <Alert type="error" showIcon message={t("loadError")} />;
  }

  return (
    <article className="space-y-3" data-testid="public-person-profile">
      {/* `badges` undefined và `showEditAction` tắt: cả hai đều là bề mặt thành viên. */}
      <PersonProfileHeader person={data} badges={undefined} showEditAction={false} />
      <PersonNameLayers person={data} />
      <PersonLifeDates person={data} />
      <PersonOriginFacts person={data} />
      <PersonBiography person={data} />
    </article>
  );
}

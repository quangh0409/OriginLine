"use client";

import { useFormatter, useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import type { PostDto } from "@/lib/api/posts";

/**
 * "Ai duyệt và lúc nào" — design 07 §2: "bài lên trang chủ là tiếng nói của cả
 * dòng họ", nên phải nói rõ ai đứng sau quyết định đăng.
 *
 * <h2>`reviewedByDisplayName` có thể `null` — đó KHÔNG PHẢI lỗi</h2>
 * Backend tra tên qua đúng `PersonDisclosureService` dùng cho
 * `authorDisplayName`, và trả `null` khi người đọc không được thấy người
 * duyệt (khách xem một bài đã đăng vẫn không biết TÊN một Trưởng chi còn
 * sống đã duyệt nó — đúng luật "khách không thấy người còn sống nào", dù bài
 * đã công khai) hoặc khi người duyệt không gắn với nhân khẩu nào (System
 * Admin kỹ thuật). Cả hai ca đều rơi về đúng một câu: chỉ hiện ngày, không
 * hiện tên — **không hiện `"undefined"`, không hiện một dấu gạch ngang gợi ý
 * có tên bị giấu**.
 */
export function PostReviewMeta({
  post,
}: {
  post: Pick<PostDto, "reviewedAt" | "reviewedByDisplayName" | "status">;
}) {
  const t = useTranslations("posts");
  const format = useFormatter();

  if (!isPresent(post.reviewedAt)) return null;

  const date = format.dateTime(new Date(post.reviewedAt), { dateStyle: "medium", timeStyle: "short" });
  const name = post.reviewedByDisplayName;
  const key = post.status === "PUBLISHED" ? "approvedAt" : "reviewedAt";
  const keyWithName = post.status === "PUBLISHED" ? "approvedAtBy" : "reviewedAtBy";

  return (
    <p className="m-0 text-than text-text-muted">
      {isPresent(name) ? t(`reviewMeta.${keyWithName}`, { name, date }) : t(`reviewMeta.${key}`, { date })}
    </p>
  );
}

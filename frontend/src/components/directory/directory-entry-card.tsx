"use client";

import { Button } from "antd";
import { TeamOutlined, UserOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { DirectoryEntryDto } from "@/lib/api/directory";

export interface DirectoryEntryCardProps {
  entry: DirectoryEntryDto;
}

/**
 * Một dòng danh bạ.
 *
 * <h2>Cùng luật vắng mặt như hồ sơ</h2>
 * `occupation` và `currentPlaceProvince` có thể vắng ngay trong một dòng đã lọt
 * vào danh bạ: chủ thể mở tỉnh mà chưa mở nghề là chuyện bình thường. Dòng meta
 * vì thế được ghép từ những mảnh CÓ THẬT, không đổ vào một khuôn cố định —
 * một lưới ô trống thẳng hàng là chỗ rò rỉ dễ nhất trong mọi danh sách, vì nó
 * chỉ đúng vào chỗ có dữ liệu bị giữ lại.
 *
 * <h2>`?to=`, không phải `?from=`</h2>
 * Câu người dùng đang hỏi là "TÔI gọi người này là gì" — người này là ĐÍCH.
 * Dùng `from=` sẽ làm sản phẩm hỏi ngược 180°: "người này gọi ai là gì", một
 * câu chẳng ai cần. Cùng quy ước với nút tra danh xưng trên hồ sơ.
 */
export function DirectoryEntryCard({ entry }: DirectoryEntryCardProps) {
  const t = useTranslations("directory");
  const tPerson = useTranslations("person");

  const metaParts = [
    isPresent(entry.generation)
      ? tPerson("generationValue", { n: entry.generation })
      : undefined,
    isPresent(entry.primaryBranch?.name) ? entry.primaryBranch?.name : undefined,
    isPresent(entry.occupation) ? entry.occupation : undefined,
    isPresent(entry.currentPlaceProvince) ? entry.currentPlaceProvince : undefined,
  ].filter(isPresent);

  return (
    <li className="list-none rounded-lg border border-border bg-bg-card px-3 py-3 sm:px-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <Link
            href={`/persons/${entry.personId}`}
            className="inline-flex min-h-11 items-center gap-2 no-underline"
          >
            <UserOutlined aria-hidden style={{ color: colorVars.textMuted }} />
            <span className="font-serif text-dan font-semibold text-text-main underline">
              {entry.displayName}
            </span>
          </Link>

          {metaParts.length > 0 && (
            <p className="m-0 mt-0.5 text-than leading-relaxed text-text-muted">
              {metaParts.join(" · ")}
            </p>
          )}
        </div>

        {/* Biểu tượng LUÔN kèm chữ: "Tôi gọi là?" là câu hỏi thật của người
            dùng, và nó cũng là nhãn duy nhất giải thích được cái biểu tượng.

            `aria-label` đặt trên chính <a>, không trên <Button> bên trong: một
            danh sách ba mươi nút mang cùng một nhãn "Tôi gọi là?" là vô dụng
            với người dùng trình đọc màn hình, vốn duyệt theo danh sách liên
            kết và chỉ nghe thấy cái nhãn. Nhãn đầy đủ nêu tên người, chữ nhìn
            thấy vẫn ngắn. */}
        <Link
          href={`/kinship?to=${entry.personId}`}
          aria-label={t("kinshipCtaFor", { name: entry.displayName })}
          className="inline-flex shrink-0"
        >
          <Button icon={<TeamOutlined />} tabIndex={-1}>
            {t("kinshipCta")}
          </Button>
        </Link>
      </div>
    </li>
  );
}

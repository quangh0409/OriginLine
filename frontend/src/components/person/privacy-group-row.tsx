"use client";

import { Radio } from "antd";
import { CheckOutlined, EditOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { SHARE_SCOPES, type PrivacyGroup, type ShareScope } from "@/types/api";

export interface PrivacyGroupRowProps {
  group: PrivacyGroup;
  value: ShareScope;
  onChange: (next: ShareScope) => void;
  /**
   * Chủ thể đã điền gì vào nhóm này chưa. Tính ở nơi gọi từ hồ sơ ĐẦY ĐỦ của
   * chính chủ — không bao giờ từ hồ sơ của người khác.
   */
  filledIn: boolean;
  /** Đường tới biểu mẫu sửa, để lời mời "điền ngay" có chỗ đi tới. */
  editHref: string;
  /** Trẻ vị thành niên: ẩn tối đa, không mở được kể cả khi tự nguyện. */
  locked?: boolean;
  disabled?: boolean;
}

/**
 * Một nhóm trường + ba mức chia sẻ.
 *
 * <h2>Vì sao lợi ích viết ngay cạnh ô chọn</h2>
 * Câu "mở nghề nghiệp cho cả họ xem thì bà con cùng nghề tìm được nhau" đặt
 * trong trang trợ giúp là câu không ai đọc. Đặt ngay dưới ô chọn, nó là thứ
 * duy nhất trả lời được câu hỏi người dùng đang thật sự cầm trong đầu lúc ngón
 * tay đã ở trên nút: "mở ra thì tôi được gì".
 *
 * <h2>Và vì sao CÁI GIÁ cũng phải viết ngay cạnh ô chọn</h2>
 * `PRIVATE` **không** có nghĩa "chỉ mình tôi": theo định nghĩa của contract, nó
 * là *"chỉ chính chủ và Hội đồng Tộc biểu / ADMIN"*. Tức là Hội đồng đọc được
 * số điện thoại của một người đã chọn Riêng tư. Đó là hệ quả trực tiếp của định
 * nghĩa mức, không phải lỗi — nhưng nếu người dùng chỉ phát hiện ra điều đó
 * SAU khi đã chọn, thì cái họ mất không phải một trường dữ liệu mà là lòng tin
 * vào toàn bộ hệ thống. Nên câu ấy nằm ngay dưới ba cái nút, không nằm trong
 * trang chính sách.
 *
 * <h2>Vì sao dùng `Radio.Group` chứ không `Segmented`</h2>
 * Ba mức là ba lựa chọn loại trừ nhau — đúng ngữ nghĩa nhóm radio, nên trình
 * đọc màn hình đọc ra "2 trong 3, đã chọn" mà không cần một dòng ARIA viết tay
 * nào. `Segmented` của Ant Design giấu ô radio thật đi và vẽ một cái nhãn, nên
 * trạng thái chỉ còn nằm ở màu.
 *
 * Kiểu `outline` (mặc định) chứ không `solid`: `solid` tô nền bằng
 * `colorPrimary` và ép chữ trắng — ở chế độ tối `colorPrimary` sáng lên và chữ
 * trắng tụt xuống khoảng 2:1, đúng cái bẫy đã gặp ở thẻ trạng thái trên hồ sơ.
 * Dấu tích trong nhãn là lớp thứ hai: trạng thái "đang chọn" không bao giờ chỉ
 * nằm ở màu.
 */
export function PrivacyGroupRow({
  group,
  value,
  onChange,
  filledIn,
  editHref,
  locked = false,
  disabled = false,
}: PrivacyGroupRowProps) {
  const t = useTranslations("privacy");
  const groupId = `privacy-group-${group}`;

  return (
    <div className="border-t border-border py-4 first:border-t-0 first:pt-0">
      <div role="group" aria-labelledby={`${groupId}-label`}>
        <h3
          id={`${groupId}-label`}
          className="m-0 font-serif text-de font-semibold text-text-main"
        >
          {t(`group.${group}`)}
        </h3>
        <p className="m-0 mt-1 text-than leading-relaxed text-text-muted">
          {t(`groupBenefit.${group}`)}
        </p>

        <Radio.Group
          className="mt-3 flex flex-wrap gap-y-2"
          value={value}
          disabled={locked || disabled}
          onChange={(e) => onChange(e.target.value as ShareScope)}
          optionType="button"
          aria-labelledby={`${groupId}-label`}
        >
          {SHARE_SCOPES.map((scope) => (
            <Radio.Button key={scope} value={scope} className="!text-than">
              <span className="inline-flex items-center gap-1.5">
                {/* Dấu tích chỉ là lớp phụ; chữ bên cạnh mới là thứ mang nghĩa. */}
                <CheckOutlined
                  aria-hidden
                  style={{ visibility: value === scope ? "visible" : "hidden" }}
                />
                {t(`scope.${scope}`)}
              </span>
            </Radio.Button>
          ))}
        </Radio.Group>

        <p className="m-0 mt-2 text-than leading-relaxed text-text-muted">
          {t(`scopeHint.${value}`)}
        </p>

        {!locked && !filledIn && (
          // Chỉ chính chủ đọc được dòng này (cả khối chỉ hiện với chính chủ),
          // nên nó KHÔNG phải một chỗ rò: người đọc đã biết thừa mình chưa điền
          // gì. Với người khác thì cả khối biến mất không dấu vết.
          <p className="m-0 mt-2 text-than leading-relaxed" style={{ color: colorVars.accentText }}>
            {t("notFilledYet")}{" "}
            <Link href={editHref} className="inline-flex items-center gap-1 underline">
              <EditOutlined aria-hidden />
              {t("fillItIn")}
            </Link>
          </p>
        )}

        {locked && (
          <p className="m-0 mt-2 text-than leading-relaxed text-text-muted">
            {t("lockedMinor")}
          </p>
        )}
      </div>
    </div>
  );
}

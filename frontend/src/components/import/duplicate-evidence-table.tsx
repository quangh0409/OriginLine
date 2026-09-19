"use client";

import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import { VungCuonNgang } from "@/components/common/vung-cuon-ngang";
import { isDivergence, orderByTrust } from "./evidence-order";
import type { ImportDuplicateEvidence } from "@/lib/api/data-import";

export interface DuplicateEvidenceTableProps {
  evidence: readonly ImportDuplicateEvidence[];
  /** Nhãn cột trái — khác nhau giữa cặp `TREE` và cặp `FILE`↔`FILE`. */
  existingLabel: string;
}

/**
 * Bảng bằng chứng của một cặp nghi trùng, **xếp theo độ tin**.
 *
 * <h2>Thứ tự là nội dung, không phải trang trí</h2>
 * Xem `orderByTrust`: ngày giỗ lên đầu, năm sinh xuống cuối. Với gia phả Việt
 * thì đó là hai đầu của thang tin cậy. Người đối chiếu có vài giây cho mỗi cặp;
 * dòng họ đọc trước quyết định họ kết luận gì.
 *
 * <h2>Không có cột điểm</h2>
 * `points` là trường **tuỳ chọn** của hợp đồng và hôm nay backend không gửi.
 * Trước đây màn này in "40 điểm / 0 điểm" cho từng dòng; những con số ấy đến từ
 * một bảng chép tay trong bộ giả lập, không từ bộ chấm điểm. Một con số sai ở
 * đây tệ hơn không có con số nào: người đối chiếu sẽ cộng nhẩm rồi tin vào tổng
 * của chính mình.
 *
 * <h2>`existingValue` vắng mặt có ĐÚNG MỘT nghĩa</h2>
 * "Nguồn bên kia không ghi mục này". Nó không bao giờ nghĩa là "đã bị cắt theo
 * phân tầng riêng tư" — dấu hiệu nào không được phép trưng ra thì bị bỏ hẳn
 * khỏi mảng, vì một ô rỗng vừa rò rỉ sự tồn tại của dữ liệu đang giấu, vừa dẫn
 * tới một quyết định gộp sai.
 *
 * <h2>Tô bằng màu VÀ bằng chữ</h2>
 * Ô lệch mang thêm nhãn chữ ("Chỗ lệch" / "Tệp bỏ trống") chứ không chỉ đổi
 * nền. Màu một mình không đủ: nó không tồn tại với trình đọc màn hình, và nó
 * yếu đi đúng vào lúc cần nhất — trên điện thoại ngoài sân nhà thờ họ.
 */
export function DuplicateEvidenceTable({
  evidence,
  existingLabel,
}: DuplicateEvidenceTableProps) {
  const t = useTranslations("dataImport.duplicates");
  const tField = useTranslations("dataImport.evidence");
  const ordered = orderByTrust(evidence);
  const hasMissing = ordered.some((row) => row.match === "MISSING_IN_FILE");

  return (
    <section className="mt-3">
      <h4 className="m-0 text-than font-semibold" style={{ color: colorVars.textMain }}>
        {t("evidenceTitle")}
      </h4>
      <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
        {t("trustNote")}
      </p>

      {/* Ba cột — dấu hiệu, bên đã có, bên mới nộp — trong vùng cuộn RIÊNG.
          Giá trị ở đây là dữ liệu thật của dòng họ (địa danh, ngày giỗ ghi theo
          lối cũ), nên bề rộng nội tại của bảng không có trần nào; để trần thì
          nó đẩy thẻ cặp nghi trùng tràn khỏi viền và kéo cả trang cuộn ngang. */}
      <VungCuonNgang nhan={t("evidenceTitle")}>
        <table className="mt-2 w-full border-collapse text-than">
          <caption className="sr-only">{t("evidenceTitle")}</caption>
          <thead>
            <tr>
              <th
                scope="col"
                className="border-b px-2 py-1 text-left text-than font-semibold"
                style={{ borderColor: colorVars.border, color: colorVars.textMuted }}
              >
                {t("evidenceField")}
              </th>
              <th
                scope="col"
                className="border-b px-2 py-1 text-left text-than font-semibold"
                style={{ borderColor: colorVars.border, color: colorVars.textMuted }}
              >
                {existingLabel}
              </th>
              <th
                scope="col"
                className="border-b px-2 py-1 text-left text-than font-semibold"
                style={{ borderColor: colorVars.border, color: colorVars.textMuted }}
              >
                {t("columns.incoming")}
              </th>
            </tr>
          </thead>
          <tbody>
            {ordered.map((row) => {
              const diverges = isDivergence(row);
              const hint =
                row.field === "DEATH_LUNAR" || row.field === "BIRTH_YEAR"
                  ? tField(`hint.${row.field}`)
                  : null;

              return (
                <tr key={row.field} data-testid={`evidence-${row.field}`}>
                  <th
                    scope="row"
                    className="border-b px-2 py-2 text-left align-top text-than font-semibold"
                    style={{ borderColor: colorVars.border, color: colorVars.textMain }}
                  >
                    {tField(row.field)}
                    {hint && (
                      <span
                        className="block text-than font-normal"
                        style={{ color: colorVars.textMuted }}
                      >
                        {hint}
                      </span>
                    )}
                  </th>
                  <td
                    className="border-b px-2 py-2 align-top text-than"
                    style={{ borderColor: colorVars.border, color: colorVars.textMain }}
                  >
                    {row.existingValue ?? (
                      <span style={{ color: colorVars.textMuted }}>{t("missingInTree")}</span>
                    )}
                  </td>
                  <td
                    className="border-b px-2 py-2 align-top text-than"
                    style={{
                      borderColor: colorVars.border,
                      color: colorVars.textMain,
                      backgroundColor: diverges ? colorVars.warningBg : undefined,
                    }}
                  >
                    {row.incomingValue ?? (
                      <span style={{ color: colorVars.accentText }}>{t("missingInFile")}</span>
                    )}
                    {row.match === "DIFFERENT" && (
                      <span
                        className="mt-0.5 block text-than font-semibold"
                        style={{ color: colorVars.accentText }}
                      >
                        {t("divergence")}
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </VungCuonNgang>

      {hasMissing && (
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMain }}>
          {t("missingWarning")}
        </p>
      )}
    </section>
  );
}

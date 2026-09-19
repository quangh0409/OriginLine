"use client";

import { Card, Segmented } from "antd";
import { useTranslations } from "next-intl";
import { useChuDe, type ChuDe } from "@/lib/theme/theme-context";

/**
 * Công tắc chủ đề — ba trạng thái, đúng như {@code design/00-dinh-huong.html} §3.
 *
 * <h2>Vì sao mặc định là "Theo hệ thống" chứ không phải "Sáng"</h2>
 *
 * <p>Người lớn tuổi tự xoay xở với thị lực của mình bằng cài đặt hệ điều hành — cỡ chữ, độ tương
 * phản, chế độ tối. Nếu ứng dụng ghim cứng chế độ sáng thì nó <b>vô hiệu hoá</b> nỗ lực đó và bắt
 * họ đi tìm một công tắc nữa trong một ứng dụng lạ. Mặc định bám theo hệ điều hành nghĩa là phần
 * lớn người dùng <b>không phải làm gì cả</b> — đó mới là thiết kế đúng cho dải tuổi này.</p>
 *
 * <p><b>Đặt tên theo chế độ hiển thị, không theo tuổi.</b> §2.1 của định hướng cấm mọi công tắc
 * kiểu "chế độ dễ nhìn cho người lớn tuổi": không ai tự nhận mình cần nó nên chẳng ai bật, và sự
 * tồn tại của nó cho phép phần còn lại của sản phẩm cẩu thả.</p>
 */
export function ThemeSettingsCard() {
  const t = useTranslations("settings.theme");
  const { chuDe, datChuDe } = useChuDe();

  return (
    <Card size="small" className="mb-4" title={t("title")}>
      <p className="mb-3 mt-0 text-than text-text-muted">{t("description")}</p>
      <Segmented<ChuDe>
        block
        value={chuDe}
        onChange={datChuDe}
        aria-label={t("title")}
        options={[
          { label: t("system"), value: "he-thong" },
          { label: t("light"), value: "sang" },
          { label: t("dark"), value: "toi" },
        ]}
      />
    </Card>
  );
}

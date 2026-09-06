"use client";

import { useId, useState } from "react";
import { Card } from "antd";
import { DownOutlined, UpOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorTokens } from "@/styles/tokens";
import { TREE_STROKES, type StrokeStyle } from "@/lib/tree/to-flow-elements";

/**
 * Purely explanatory — living/deceased distinction is the one visual
 * requirement the F2 brief calls out explicitly. Never mention privacy tiers
 * or "hidden data" here (that would be exactly the leak contracts/README
 * §3 warns against); this legend only explains marks for data that IS shown.
 *
 * `pointer-events-none` là bắt buộc, không phải tinh chỉnh: thẻ chú giải nằm ĐÈ lên phả đồ ở góc
 * dưới-trái và chiếm khoảng một phần năm canvas trên Pixel 5. Chừng nào còn nhận sự kiện chuột/chạm
 * thì mọi thẻ nhân khẩu lọt vào góc đó đều bấm không được — người dùng chỉ thấy "chạm vào cụ tổ mà
 * chẳng ra gì". Ngoại lệ DUY NHẤT là nút mở/thu quy ước bên dưới: nó được trả lại
 * `pointer-events-auto` vì nó là thứ người ta CẦN bấm; phần thân chú giải vẫn cho sự kiện xuyên
 * thẳng xuống canvas.
 *
 * Vì sao có nút mở/thu: quy ước nét sau khi dựng lại phả đồ có tám mục. Đổ hết ra màn hình điện
 * thoại thì chú giải cao gần nửa canvas — đúng thứ mà bản trước đã phải chữa. Bốn mục hay dùng
 * nhất luôn hiển thị; bốn mục còn lại (hôn phối đã kết thúc, kế tự, tuyệt tự, đường vẽ vòng) chỉ
 * mở ra khi được hỏi.
 */
export function TreeLegend() {
  const t = useTranslations("tree");
  const [showAll, setShowAll] = useState(false);
  const detailsId = useId();

  return (
    <Card
      size="small"
      data-testid="tree-legend"
      className="!pointer-events-none !absolute !bottom-3 !left-3 !z-10 !w-56 !border-border !shadow-md"
      styles={{ body: { padding: "8px 12px" } }}
    >
      <div className="mb-1.5 text-xs font-semibold text-text-main">{t("legend.title")}</div>

      <ul className="space-y-1 text-[11px] text-text-muted">
        {/* Còn sống và đã khuất đi chung một dòng: hai chấm màu tự đối chiếu với nhau, và tiết
            kiệm được một dòng cho các quy ước nét. */}
        <li className="flex items-center gap-2">
          <span className="flex shrink-0 items-center gap-1">
            <Dot color={colorTokens.success} />
            <Dot color={colorTokens.textMuted} />
          </span>
          <span>
            {t("legend.alive")} · {t("legend.deceased")}
          </span>
        </li>
        <LegendLine stroke={TREE_STROKES.bioChild} label={t("legend.bioChild")} />
        <LegendLine stroke={TREE_STROKES.adoptedChild} label={t("legend.adoptedChild")} />
        <LegendLine stroke={TREE_STROKES.marriage} label={t("legend.spouse")} />
      </ul>

      {showAll && (
        <ul id={detailsId} className="mt-1 space-y-1 text-[11px] text-text-muted">
          <LegendLine stroke={TREE_STROKES.marriageEnded} label={t("legend.marriageEnded")} />
          <LegendLine stroke={TREE_STROKES.heir} label={t("legend.heir")} />
          <LegendLine stroke={TREE_STROKES.lineageEnd} label={t("legend.lineageEnd")} short />
          <LegendLine stroke={TREE_STROKES.detour} label={t("legend.detour")} />
        </ul>
      )}

      <button
        type="button"
        onClick={() => setShowAll((open) => !open)}
        aria-expanded={showAll}
        aria-controls={detailsId}
        className="pointer-events-auto mt-1.5 flex items-center gap-1 rounded px-0.5 text-[11px] text-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
      >
        {showAll ? <UpOutlined className="text-[9px]" /> : <DownOutlined className="text-[9px]" />}
        {showAll ? t("legend.showLess") : t("legend.showAll")}
      </button>
    </Card>
  );
}

function Dot({ color }: { color: string }) {
  return <span aria-hidden className="h-2 w-2 rounded-full" style={{ background: color }} />;
}

/**
 * Mẫu nét được vẽ bằng CHÍNH `TREE_STROKES` mà canvas dùng, chứ không dựng lại bằng viền CSS. Chú
 * giải vì thế không thể trôi khỏi thứ đang hiện trên phả đồ: đổi quy ước một chỗ là đổi cả hai.
 */
function LegendLine({
  stroke,
  label,
  short = false,
}: {
  stroke: StrokeStyle;
  label: string;
  /** Gạch tuyệt tự vốn là một đoạn NGẮN khép dưới đáy thẻ — vẽ dài ra là nói sai quy ước. */
  short?: boolean;
}) {
  const width = 22;
  const x1 = short ? 7 : 1;
  const x2 = short ? 15 : width - 1;

  return (
    <li className="flex items-center gap-2">
      <svg width={width} height={8} aria-hidden focusable="false" className="shrink-0 overflow-visible">
        <line x1={x1} y1={4} x2={x2} y2={4} strokeLinecap="round" style={{ ...stroke, fill: "none" }} />
      </svg>
      <span>{label}</span>
    </li>
  );
}

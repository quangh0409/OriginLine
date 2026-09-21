"use client";

import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { Tag } from "antd";
import { LoadingOutlined, MinusOutlined, PlusOutlined } from "@ant-design/icons";
import { useLocale, useTranslations } from "next-intl";
import { useTreeCanvasContext } from "./tree-canvas-context";
import { BADGE_META, displayBadges } from "@/lib/tree/badges";
import { nodeDatesState } from "@/lib/tree/life-dates";
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  TOGGLE_HIT_OVERHANG,
  TOGGLE_HIT_SIZE,
  TOGGLE_KNOB_INSET,
  TOGGLE_KNOB_SIZE,
} from "@/lib/tree/layout-constants";
import type { PersonNodeData } from "@/lib/tree/to-flow-elements";
import { colorVars } from "@/styles/tokens";

export type PersonFlowNode = Node<PersonNodeData, "person">;

/**
 * Số con lớn nhất còn in vừa trong vòng tròn 24px. Trên ngưỡng này thì con số mất nghĩa với người
 * đọc ("47 hay 4 rồi 7?") nên quay về dấu `+`; con số đầy đủ vẫn nằm trong `aria-label`/`title`.
 */
const MAX_KNOB_COUNT = 9;

/**
 * Custom React Flow node for one nhân khẩu. Deliberately renders ONLY what
 * `TreeNode`/`PersonSummaryDto` already carries — no kinship title (that's
 * `useKinship`, Sprint 3's F5), no field invented to hint at hidden data.
 * The living/deceased visual split (dot + card tone) is the one thing the
 * F2 brief explicitly asks for; badges come straight from `TreeNode.badges`
 * (never inferred here — see contracts/README §7.6).
 *
 * <h2>Thẻ 160px và HAI mức chi tiết</h2>
 *
 * Thẻ thu từ 208 xuống {@link NODE_WIDTH} = 160px (xem `layout-constants.ts` để biết cái giá và
 * phép tính). Bề ngang mất đi được trả lại bằng việc cho **tên xuống nhiều dòng** thay vì cắt bằng
 * dấu ba chấm — định hướng 00 §3 nói rõ tên người là *dữ liệu*, không phải nhãn giao diện.
 *
 * Và thẻ có hai mức chi tiết, chọn theo **mức phóng của máy quay** (`detail` trong
 * `TreeCanvasContext`, tính một lần ở `<TreeCanvasInner>` chứ không đọc ở từng thẻ):
 *
 * - `"compact"` — cây đang thu nhỏ, người dùng đang *tìm*. Chỉ tên, ở cỡ chữ **17px** thay vì 13px.
 *   Ở mức phóng 0.5 thì đó là 8,5px thật trên màn hình thay vì 6,5px — chênh lệch giữa "đoán được
 *   tên" và "một đám chấm".
 * - `"full"` — đã phóng tới mức đọc từng người. Hiện đủ đời · chi · dòng ngày · huy hiệu.
 *
 * Cỡ thẻ **không** đổi theo mức chi tiết: bố cục được tính trước từ {@link NODE_WIDTH} /
 * {@link NODE_HEIGHT}, nên một tấm thẻ co giãn theo nội dung sẽ làm mọi toạ độ sai đi.
 *
 * Dòng ngày là chỗ duy nhất trên thẻ có một trạng thái "trống" mang nghĩa, và
 * nghĩa ấy khác nhau giữa người còn sống và người đã khuất — phép phân biệt
 * nằm trọn trong `nodeDatesState` (src/lib/tree/life-dates.ts), đọc chỗ đó
 * trước khi sửa bất cứ điều gì ở đây.
 */
export function PersonNode({ id, data }: NodeProps<PersonFlowNode>) {
  const t = useTranslations("tree");
  const locale = useLocale();
  const { rootId, expandedIds, loadingIds, toggle, selfPersonId, focusId, detail } =
    useTreeCanvasContext();
  const { treeNode, hasLoadableChildren } = data;
  const person = treeNode.person;
  const isExpanded = expandedIds.has(id);
  const isLoading = loadingIds.has(id);
  const badges = displayBadges(treeNode.badges);
  const dates = nodeDatesState(person);
  const compact = detail === "compact";
  // Nút gốc luôn ở trạng thái mở và không thu gọn được (useTreeCanvas.collapse bỏ qua nó, vì thu
  // gọn gốc thì canvas trống trơn). Vẫn vẽ nút bấm cho nó là dựng lên một điều khiển bấm vào
  // không xảy ra gì — người dùng đọc ra là "hỏng", chứ không đọc ra là "cố ý".
  const offersToggle = hasLoadableChildren && id !== rootId;

  /** Chính người đang đăng nhập. Máy chủ trả `personId` ở `/me`; giao diện trước đây bỏ không dùng. */
  const isSelf = selfPersonId != null && selfPersonId === id;
  /** Người vừa được "nhảy tới" bằng ô tìm trên canvas, hoặc bằng nút "Về chỗ tôi". */
  const isFocused = focusId != null && focusId === id;

  /**
   * Số người con đang bị giấu dưới nhánh thu gọn.
   *
   * `childCount` là tổng số con **sau khi đã lọc riêng tư** (hợp đồng `TreeNode`), nên in nó ra
   * không tiết lộ thêm gì so với việc bung nhánh ra và tự đếm. `null`/`0` nghĩa là máy chủ không
   * nói — khi ấy giữ dấu `+` chứ không bịa số 0.
   */
  const hiddenChildCount = !isExpanded ? (treeNode.childCount ?? 0) : 0;
  const showsCount = hiddenChildCount > 0 && hiddenChildCount <= MAX_KNOB_COUNT;

  /**
   * Tên gọi của nút — **cố ý giữ nguyên câu cũ, không kèm số con**.
   *
   * Đây là một hợp đồng chứ không phải một chuỗi: bốn tệp e2e đang tìm nút này bằng
   * `getByRole("button", { name: "Mở rộng nhánh này" })`, và Playwright so tên **khớp trọn**. Thêm
   * "— 3 người con" vào đây là làm bốn tệp ấy ngừng tìm thấy nút, mà thông báo lỗi lại nói về
   * "không tìm thấy phần tử" chứ không nói gì về nhãn.
   *
   * Con số vẫn tới được người dùng qua ba đường khác: chữ số **nhìn thấy** trong vòng tròn, câu
   * đầy đủ ở `title` (người dùng chuột), và `data-hidden-children` (bộ kiểm đọc). Câu đầy đủ chỉ
   * là câu mô tả thêm, không phải tên gọi — nên chỗ nào cần tên ổn định thì vẫn có tên ổn định.
   */
  const toggleLabel = isExpanded ? t("collapseBranch") : t("expandBranch");
  const toggleTitle =
    !isExpanded && hiddenChildCount > 0
      ? t("expandBranchCount", { count: hiddenChildCount })
      : toggleLabel;

  const meta = [
    person.generation != null ? t("generationShort", { n: person.generation }) : null,
    person.primaryBranch?.name ?? null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div
      data-testid="person-node"
      data-self={isSelf ? "true" : undefined}
      data-focused={isFocused ? "true" : undefined}
      data-detail={detail}
      className={[
        "relative flex flex-col rounded-md shadow-sm",
        compact ? "justify-center px-2 py-1.5" : "px-2.5 py-2",
        // Viền dày màu đỏ trầm = "đây là bạn". KHÔNG chỉ dựa vào màu: thẻ còn mang chip chữ
        // "Tôi"/"Me" ở hàng huy hiệu, nên người không phân biệt được màu vẫn đọc ra.
        isSelf ? "border-2 border-primary" : "border",
        !isSelf && (person.isAlive ? "border-primary/50" : "border-border-dark"),
        person.isAlive ? "bg-bg-card" : "bg-deceased",
        // Vòng NGOÀI cho người vừa nhảy tới — nằm ngoài viền nên đọc được cùng lúc với viền "Tôi".
        isFocused ? "outline outline-2 outline-offset-2 outline-primary" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      style={{ width: NODE_WIDTH, minHeight: NODE_HEIGHT }}
    >
      <Handle type="target" position={Position.Top} id="top" className="!h-2 !w-2 !border-0 !bg-border-dark" />
      <Handle type="source" position={Position.Bottom} id="bottom" className="!h-2 !w-2 !border-0 !bg-border-dark" />
      <Handle type="source" position={Position.Left} id="left" className="!h-2 !w-2 !border-0 !opacity-0" />
      <Handle type="target" position={Position.Right} id="right" className="!h-2 !w-2 !border-0 !opacity-0" />

      <div className="flex items-start justify-between gap-1">
        <div className="min-w-0 flex-1">
          <div
            data-testid="person-node-name"
            // `break-words` + không `truncate`: tên người là DỮ LIỆU (định hướng 00 §3). Ở 160px
            // một cái tên bốn âm tiết vẫn xuống dòng gọn gàng thay vì mất đuôi sau dấu ba chấm.
            className={[
              "break-words font-serif font-semibold leading-tight text-text-main",
              compact ? "text-dan" : "text-the-ten",
            ].join(" ")}
            title={person.displayName}
          >
            {person.displayName}
          </div>
          {!compact && meta !== "" && (
            <div className="mt-0.5 truncate text-the-phu text-text-muted">{meta}</div>
          )}
        </div>
        <span
          aria-hidden
          className={[
            "mt-1 h-2 w-2 shrink-0 rounded-full",
            person.isAlive ? "bg-success" : "bg-text-muted",
          ].join(" ")}
        />
        <span className="sr-only">{person.isAlive ? t("statusAlive") : t("statusDeceased")}</span>
      </div>

      {/* Ô ngày. Ba trạng thái, không phải hai — xem `nodeDatesState` để biết vì sao
          "chưa ai ghi" và "chưa chia sẻ" phải đọc ra khác nhau, và vì sao phép phân
          biệt ấy không rò rỉ gì. Cả ba đều chiếm đúng một dòng, nên chiều cao thẻ
          không đổi theo dữ liệu: một thẻ cao hơn thẻ bên cạnh tự nó đã là một tín
          hiệu về nội dung.

          Ở mức `compact` cả ba biến mất CÙNG LÚC — phép ẩn không nhìn vào dữ liệu của
          ai cả, nó chỉ nhìn mức phóng, nên nó vẫn không nói được gì về một người cụ thể. */}
      {!compact && dates.kind === "known" && (
        <div className="mt-1 text-the-phu text-text-muted">{dates.text}</div>
      )}

      {!compact && dates.kind === "unrecorded" && (
        // Ô trống CÓ CHỦ ĐÍCH: nét đứt đọc ra là "chỗ này còn chờ được điền", đúng
        // nghĩa với người đã khuất — họ là dữ liệu công khai, nên vắng mặt chỉ có
        // một nghĩa duy nhất là chưa ai ghi.
        <div
          data-testid="person-node-dates-unrecorded"
          title={t("datesUnrecordedHint")}
          className="mt-1 w-fit border-b border-dashed border-border-dark text-the-phu text-text-muted"
        >
          {t("datesUnrecorded")}
        </div>
      )}

      {!compact && dates.kind === "unshared" && (
        // Không nét đứt, không biểu tượng ổ khoá, không dấu ba chấm: đây KHÔNG phải
        // một chỗ trống chờ điền mà là một trạng thái bình thường và trọn vẹn. Câu
        // chữ giống hệt nhau trên MỌI thẻ người còn sống đang trống ngày, nên nó
        // không nói được điều gì riêng về ai.
        <div
          data-testid="person-node-dates-unshared"
          title={t("datesUnsharedHint")}
          className="mt-1 w-fit text-the-phu text-text-muted"
        >
          {t("datesUnshared")}
        </div>
      )}

      {!compact && (badges.length > 0 || isSelf) && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {isSelf && (
            // Chữ, không phải chỉ màu viền — đây là thứ khiến "tô đậm node của chính mình" vẫn
            // đọc được với người không phân biệt được màu, và với người dùng trình đọc màn hình.
            <Tag
              data-testid="person-node-self"
              color={colorVars.primaryLight}
              style={{ color: colorVars.primary }}
              bordered={false}
              className="!m-0 !px-1.5 !text-the-nhan !leading-4"
            >
              {t("youAreHere")}
            </Tag>
          )}
          {badges.map((b) => (
            <Tag
              key={b}
              color={BADGE_META[b].color}
              style={{ color: BADGE_META[b].ink }}
              bordered={false}
              className="!m-0 !px-1.5 !text-the-nhan !leading-4"
            >
              {locale === "vi" ? BADGE_META[b].vi : BADGE_META[b].en}
            </Tag>
          ))}
        </div>
      )}

      {offersToggle && (
        // Nút = ô chạm TRONG SUỐT 80×80 (hệ toạ độ cây), đặt cân đối quanh vòng tròn 24px —
        // phần duy nhất nhìn thấy được, và nó vẫn nằm đúng chỗ cũ, giữa mép đáy thẻ. Ở mức phóng
        // mặc định 0.75 thì 80 × 0.75 = 60px THẬT, vượt sàn chạm 44px; trước đây là 24 × 0.75 =
        // 18px. Xem layout-constants.ts để biết vì sao là 80, vì sao đặt cân đối, và vì sao
        // KHÔNG phản-tỉ-lệ theo mức phóng.
        //
        // Vẫn đúng MỘT <button> cho mỗi thẻ: `.react-flow__node button` là bộ chọn mà cả e2e lẫn
        // phép đo vùng chạm đang dùng, thêm nút thứ hai là làm chúng nhập nhằng.
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            toggle(id);
          }}
          data-testid="tree-node-toggle"
          data-hidden-children={hiddenChildCount > 0 ? hiddenChildCount : undefined}
          className="group/toggle absolute left-1/2 flex -translate-x-1/2 justify-center rounded-full border-0 bg-transparent p-0 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
          style={{
            width: TOGGLE_HIT_SIZE,
            height: TOGGLE_HIT_SIZE,
            bottom: -TOGGLE_HIT_OVERHANG,
            paddingTop: TOGGLE_KNOB_INSET,
            alignItems: "flex-start",
            // Vòng tiêu điểm phải nằm trên CHÍNH phần tử nhận tiêu điểm — bộ kiểm bàn phím đọc
            // `getComputedStyle(document.activeElement)`, nên đẩy vòng sang thẻ con là người dùng
            // bàn phím mất dấu và phép kiểm C-4.3 báo "không có dấu hiệu tiêu điểm nào".
            //
            // Nhưng ô chạm rộng 80px và trong suốt: vẽ vòng quanh mép nó là một khung to đùng
            // quanh khoảng không. `outline-offset` ÂM kéo vòng vào trong đúng (80 − 24) / 2 = 28px,
            // tức nó ôm khít vòng tròn nhìn thấy — và vì nút đã `rounded-full` (bán kính 40), vòng
            // co lại còn bán kính 12, đúng hình tròn 24px.
            outlineOffset: -(TOGGLE_HIT_SIZE - TOGGLE_KNOB_SIZE) / 2,
          }}
          aria-label={toggleLabel}
          title={toggleTitle}
          aria-expanded={isExpanded}
        >
          <span
            data-testid="tree-node-toggle-knob"
            style={{ width: TOGGLE_KNOB_SIZE, height: TOGGLE_KNOB_SIZE }}
            className="flex shrink-0 items-center justify-center rounded-full border border-border-dark bg-bg-card text-the-nut font-semibold text-primary shadow-sm transition-colors group-hover/toggle:bg-primary-light"
          >
            {/* Số người con đang ẩn, khi biết và khi còn in vừa: một chữ số nói ĐÚNG thứ người
                dùng cần biết trước khi bấm ("còn 3 người dưới đây"), trong khi dấu `+` chỉ nói
                "có thêm gì đó". Câu đầy đủ luôn nằm ở `aria-label`/`title` nên biểu tượng không
                bao giờ đứng một mình (định hướng 00 §2.3). */}
            {isLoading ? (
              <LoadingOutlined spin />
            ) : isExpanded ? (
              <MinusOutlined />
            ) : showsCount ? (
              <span aria-hidden>{hiddenChildCount}</span>
            ) : (
              <PlusOutlined />
            )}
          </span>
        </button>
      )}
    </div>
  );
}

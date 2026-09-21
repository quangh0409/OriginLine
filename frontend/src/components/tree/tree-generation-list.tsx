"use client";

import { useMemo, useState } from "react";
import { Button, Empty, Spin, Tag } from "antd";
import { DownOutlined, RightOutlined } from "@ant-design/icons";
import { useLocale, useTranslations } from "next-intl";
import { BADGE_META, displayBadges } from "@/lib/tree/badges";
import { nodeDatesState } from "@/lib/tree/life-dates";
import type { TreeNode } from "@/types/api";

export interface TreeGenerationListProps {
  rootId: string;
  /** Toàn bộ nhân khẩu ĐÃ TẢI — kể cả phần đang gập trên canvas. */
  nodesById: ReadonlyMap<string, TreeNode>;
  childrenOf: (personId: string) => TreeNode[];
  /** Nạp thêm con cháu của một người, nếu máy chủ còn giữ phần chưa tải. */
  expand: (personId: string) => void;
  loadingIds: ReadonlySet<string>;
  /** Đường `[gốc, …, người đang xem]`, nếu có — danh sách mở sẵn ở đúng chỗ ấy. */
  focusPath?: readonly string[] | null;
  selfPersonId: string | null;
  onSelectPerson: (personId: string) => void;
}

/**
 * **Danh sách theo đời** — kiểu xem thay hẳn canvas, dành cho điện thoại.
 *
 * <h2>Bài toán nó giải</h2>
 * Kéo một bức tranh vài nghìn pixel qua một ô cửa 390px bằng hai ngón là thao tác khó với người
 * lớn tuổi, và phần lớn người trong họ ở xa thì dùng điện thoại. Danh sách này **không** cố vẽ lại
 * cây: nó đi **từng đời một**, mỗi màn hình đúng một gia đình, bấm vào một người là xuống đời dưới
 * của người ấy. Đó là lối di chuyển mà điện thoại vốn làm tốt.
 *
 * <h2>Vì sao không phải "canvas nhưng nhỏ hơn"</h2>
 * Canvas trả lời câu hỏi *"cả dòng họ trông như thế nào"*. Danh sách trả lời câu *"ai là con của
 * ai"* — và trên một ô cửa 390px thì chỉ câu thứ hai là trả lời được. Hai kiểu xem không thay thế
 * nhau, nên thanh công cụ để cả hai cạnh nhau chứ không tự đoán hộ người dùng.
 *
 * <h2>Không tự nạp cả cây</h2>
 * Nó đọc **đúng phần đã tải** và gọi `expand()` khi người dùng đi xuống một người chưa có con
 * trong bộ nhớ — cùng giao kèo với canvas. Một danh sách trông "nhẹ" mà lại kéo cả 1.500 người về
 * là cách âm thầm nhất để phá NFR-1.
 *
 * <h2>Sàn tiếp cận</h2>
 * Đây **không** phải nội dung trong `.react-flow__viewport`, nên nó chịu đủ sàn của định hướng 00
 * §2.2: mọi dòng chữ ≥ 16px (`text-than`/`text-dan`), mọi hàng bấm được ≥ 44px (`min-h-11`). Không
 * có ngoại lệ "canvas co theo mức phóng" ở đây, và cũng không cần — chữ ở đây không co.
 */
export function TreeGenerationList({
  rootId,
  nodesById,
  childrenOf,
  expand,
  loadingIds,
  focusPath,
  selfPersonId,
  onSelectPerson,
}: TreeGenerationListProps) {
  const t = useTranslations("tree.list");
  const tTree = useTranslations("tree");
  const locale = useLocale();

  /**
   * Đường đang mở, `[gốc, …, người đang đứng]`. Mặc định bám theo `focusPath` để người vừa nhảy
   * tới không phải đi lại từ đầu; sau đó là trạng thái của riêng danh sách.
   */
  const [trail, setTrail] = useState<string[]>(() =>
    focusPath && focusPath.length > 0 ? [...focusPath] : [rootId]
  );

  // Gốc đổi (người dùng chọn người khác làm gốc) ⇒ đường cũ không còn nghĩa. Khuôn mẫu "trạng thái
  // dẫn xuất từ props" mà `useTreeCanvas` dùng cho `resetKey`; một `useEffect` ở đây sẽ vẽ ra một
  // nhịp danh sách trỏ vào cái gốc cũ.
  const [syncedRootId, setSyncedRootId] = useState(rootId);
  if (rootId !== syncedRootId) {
    setSyncedRootId(rootId);
    setTrail(focusPath && focusPath.length > 0 ? [...focusPath] : [rootId]);
  }

  const currentId = trail[trail.length - 1] ?? rootId;
  const current = nodesById.get(currentId);
  const children = useMemo(() => childrenOf(currentId), [childrenOf, currentId]);
  const isLoading = loadingIds.has(currentId);

  const descend = (personId: string) => {
    expand(personId);
    setTrail((prev) => [...prev, personId]);
  };

  const climbTo = (index: number) => setTrail((prev) => prev.slice(0, index + 1));

  const label = (node: TreeNode): string => {
    const dates = nodeDatesState(node.person);
    return [
      node.person.generation != null
        ? tTree("generationShort", { n: node.person.generation })
        : null,
      node.person.primaryBranch?.name ?? null,
      dates.kind === "known" ? dates.text : null,
    ]
      .filter(Boolean)
      .join(" · ");
  };

  return (
    <section
      data-testid="tree-generation-list"
      aria-label={t("title")}
      className="h-full overflow-y-auto bg-bg-page px-3 py-3 sm:px-4"
    >
      {/* Đường đã đi. Mỗi bậc là một nút bấm được — trên điện thoại đây là cách quay lên duy nhất,
          nên nó không được là chữ trang trí. */}
      <nav aria-label={t("trail")} className="mb-3 flex flex-wrap items-center gap-1">
        {trail.map((id, index) => {
          const node = nodesById.get(id);
          const isLast = index === trail.length - 1;
          return (
            <span key={id} className="flex items-center gap-1">
              {index > 0 && <RightOutlined aria-hidden className="text-text-muted" />}
              <Button
                type="link"
                onClick={() => climbTo(index)}
                aria-current={isLast ? "page" : undefined}
                disabled={isLast}
                className="!min-h-11 !px-2 !text-than"
              >
                {node?.person.displayName ?? t("unknownPerson")}
              </Button>
            </span>
          );
        })}
      </nav>

      {current && (
        <header className="mb-3 rounded-lg border border-border bg-bg-card px-3 py-3">
          <h2 className="m-0 font-serif text-de font-bold text-text-main">
            {current.person.displayName}
          </h2>
          <p className="m-0 mt-1 text-than text-text-muted">{label(current)}</p>
          <Button
            onClick={() => onSelectPerson(current.id)}
            className="mt-2 !min-h-11 !text-than"
          >
            {t("openProfile")}
          </Button>
        </header>
      )}

      <h3 className="m-0 mb-2 text-dan font-semibold text-text-main">
        {t("childrenHeading", { count: children.length })}
      </h3>

      {isLoading && children.length === 0 ? (
        <div className="flex justify-center py-6">
          <Spin tip={tTree("loading")}>
            <div className="h-10 w-10" />
          </Spin>
        </div>
      ) : children.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("noChildren")} />
      ) : (
        <ul className="m-0 list-none space-y-2 p-0">
          {children.map((child) => {
            const badges = displayBadges(child.badges);
            const hasMore = child.hasMoreDescendants || (child.childCount ?? 0) > 0;
            const isSelf = selfPersonId != null && selfPersonId === child.id;
            return (
              <li
                key={child.id}
                data-testid="tree-generation-list-row"
                data-self={isSelf ? "true" : undefined}
                className={[
                  "flex items-stretch gap-2 rounded-lg bg-bg-card",
                  isSelf ? "border-2 border-primary" : "border border-border",
                ].join(" ")}
              >
                {/* Bấm vào hàng = mở hồ sơ, đúng như chạm vào một tấm thẻ trên canvas. Một thao
                    tác có MỘT nghĩa trên cả hai kiểu xem. */}
                <button
                  type="button"
                  onClick={() => onSelectPerson(child.id)}
                  className="flex min-h-11 flex-1 flex-col justify-center px-3 py-2 text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
                >
                  <span className="font-serif text-dan font-semibold text-text-main">
                    {child.person.displayName}
                  </span>
                  <span className="text-than text-text-muted">{label(child)}</span>
                  {(badges.length > 0 || isSelf) && (
                    <span className="mt-1 flex flex-wrap gap-1">
                      {isSelf && (
                        <Tag bordered={false} color="processing" className="!m-0 !text-than">
                          {tTree("youAreHere")}
                        </Tag>
                      )}
                      {badges.map((b) => (
                        <Tag
                          key={b}
                          color={BADGE_META[b].color}
                          style={{ color: BADGE_META[b].ink }}
                          bordered={false}
                          className="!m-0 !text-than"
                        >
                          {locale === "vi" ? BADGE_META[b].vi : BADGE_META[b].en}
                        </Tag>
                      ))}
                    </span>
                  )}
                </button>

                {/* Xuống đời dưới. Nút RIÊNG, có nhãn chữ: nếu gộp vào hàng thì một cú chạm mang
                    hai nghĩa và người dùng không đoán được nghĩa nào sẽ xảy ra. */}
                {hasMore && (
                  <button
                    type="button"
                    data-testid="tree-generation-list-descend"
                    onClick={() => descend(child.id)}
                    aria-label={t("descendTo", { name: child.person.displayName })}
                    className="flex min-h-11 min-w-[5.5rem] shrink-0 flex-col items-center justify-center gap-0.5 rounded-r-lg border-l border-border px-3 text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
                  >
                    <DownOutlined aria-hidden />
                    <span className="text-than">{t("descend")}</span>
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

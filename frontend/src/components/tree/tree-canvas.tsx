"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams, type ReadonlyURLSearchParams } from "next/navigation";
import { ReactFlowProvider, useReactFlow } from "@xyflow/react";
import { usePathname, useRouter } from "@/i18n/navigation";
import { Alert, Button, Empty, Spin } from "antd";
import { useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { useTreeCanvas } from "@/hooks/use-tree-canvas";
import { useTreeDetailLevel } from "@/hooks/use-tree-detail-level";
import { useAuth } from "@/lib/auth/auth-context";
import { WHOLE_TREE_FIT_VIEW_OPTIONS, withMotionPreference } from "@/lib/tree/fit-view";
import { rootIdFromQuery } from "@/lib/tree/root-id";
import { TreeCanvasContext } from "./tree-canvas-context";
import { TreeCanvasInner } from "./tree-canvas-inner";
import { TreeClaimActions } from "./tree-claim-actions";
import { TreeGenerationList } from "./tree-generation-list";
import { TreeToolbar, type TreeToolbarProps } from "./tree-toolbar";
import { TreeLegend } from "./tree-legend";
import { TreePersonDrawer } from "./tree-person-drawer";
import { TreePublicNotice } from "./tree-public-notice";
import { TreeRootBanner } from "./tree-root-banner";
import { TreeRootPicker } from "./tree-root-picker";
import { TreeTruncatedBanner } from "./tree-truncated-banner";
import type { TreeDirection, TreeEdge, TreeNode } from "@/types/api";
import { parseViewMode, VIEW_MODE_SLUG, type TreeViewMode } from "@/types/tree-ui";

/**
 * Tham số truy vấn **đang thật sự nằm trên thanh địa chỉ**.
 *
 * <h2>Vì sao không dùng thẳng `useSearchParams()`</h2>
 * Nó là một ảnh chụp của lượt dựng: sau một `router.replace` nó còn trễ mất một nhịp. Màn này ghi
 * vào URL từ **bốn** chỗ (`?rootId=`, bỏ `?rootId=`, `?focus=`, `?view=`), nên hễ hai chỗ chạy
 * sát nhau là chỗ sau dựng lại URL từ ảnh chụp cũ và **xoá mất thứ chỗ trước vừa ghi**. Đã đo
 * được: chọn một cái tên ở ô tìm thì `?focus=` hiện ra rồi biến mất, và liên kết chia sẻ mang về
 * một phả đồ không nhảy tới ai cả. Cùng lớp lỗi với "đổi ngôn ngữ làm rơi hết tham số" đã sửa một
 * lần ở `<LanguageSwitcher>`.
 *
 * `window.location.search` luôn là hiện tại. Lượt dựng trên máy chủ không có `window`, nên ở đó
 * rơi về ảnh chụp — đúng giá trị duy nhất tồn tại lúc ấy.
 */
function currentSearchParams(snapshot: URLSearchParams | ReadonlyURLSearchParams): URLSearchParams {
  return new URLSearchParams(
    typeof window === "undefined" ? snapshot.toString() : window.location.search
  );
}

export interface TreeCanvasProps {
  /**
   * Gốc lấy từ `?rootId=` trên URL. **Vắng mặt là trạng thái bình thường** —
   * lúc ấy canvas không gửi `rootId` và máy chủ chọn gốc theo vai + phạm vi chi
   * của người gọi. Không có màn chọn gốc chắn ở giữa, và không có id nào của
   * một dòng họ thật nằm trong mã nguồn hay biến môi trường của giao diện.
   * Lý do đầy đủ: `src/lib/tree/root-id.ts`.
   */
  rootId?: string;
  /**
   * Người mà phả đồ phải **mở đường tới**, lấy từ `?focus=`.
   *
   * Nằm trên URL vì cùng lý do với `?rootId=` và `?view=`: một liên kết "đây, chỗ của ông trên phả
   * đồ" gửi qua Zalo là đường lan truyền chính của sản phẩm này. Vắng mặt thì canvas tự nhắm vào
   * hồ sơ của chính người đăng nhập, nếu họ đã được ghép vào phả.
   */
  focus?: string;
}

/**
 * Top-level phả đồ canvas. Composition only — all the actual state lives in
 * useTreeCanvas (data/lazy-load) and TreeCanvasInner (React Flow + layout).
 * See src/mocks/tree-graph/ for what backs this in dev (a ~3,500-node
 * generated graph, not the ~5-node Sprint 1 fixture) and the frontend
 * README for the Sprint-2 performance measurements against it.
 */
export function TreeCanvas({ rootId: rootIdFromUrl, focus: focusFromUrl }: TreeCanvasProps) {
  const t = useTranslations("tree");
  const tAuth = useTranslations("auth");
  const { login } = useAuth();

  /**
   * Hồ sơ của **chính người đang đăng nhập**.
   *
   * Máy chủ đã trả nó ở `/me` từ lâu (`personId`) và giao diện chưa bao giờ dùng — đó là lý do
   * "cuộn ba bước là lạc". `null` với khách và với người chưa được ghép vào phả; cả hai là trạng
   * thái hợp lệ, không phải lỗi, nên không có gì phải vẽ khi thiếu nó.
   */
  const { data: me } = useMe();
  const selfPersonId = me?.personId ?? null;

  /**
   * Gốc do **người dùng** chỉ định. `null` = "để máy chủ chọn" — trạng thái mở
   * trang bình thường, không phải lỗi và không phải câu hỏi.
   *
   * Không còn `localStorage`, không còn biến môi trường, nên lượt dựng trên máy
   * chủ và lượt hydrate ở trình duyệt cho ra **cùng một giá trị**: cái vòng chờ
   * "đợi đọc gốc đã nhớ" trước đây đi cùng nó cũng biến mất.
   */
  const [rootId, setRootId] = useState<string | null>(() => rootIdFromQuery(rootIdFromUrl));
  const [pickerOpen, setPickerOpen] = useState(false);

  /**
   * `?rootId=` đổi trong khi trang vẫn đang mở — người dùng bấm một liên kết
   * phả đồ khác từ nhóm chat của dòng họ, hoặc từ kết quả tìm kiếm. Điều hướng
   * mềm của App Router **không** dựng lại component, nên nếu không đồng bộ ở
   * đây thì liên kết vừa bấm không mở ra gì cả. Cùng khuôn mẫu "trạng thái dẫn
   * xuất từ props" mà `useTreeCanvas` dùng cho `resetKey`.
   *
   * Đồng bộ cả chiều **mất** tham số: đi từ `/tree?rootId=…` về `/tree` trần là
   * một yêu cầu rõ ràng — "mở lại cây mặc định của tôi" — chứ không phải giữ
   * nguyên gốc cũ.
   */
  const [syncedUrlRootId, setSyncedUrlRootId] = useState(rootIdFromUrl);
  if (rootIdFromUrl !== syncedUrlRootId) {
    setSyncedUrlRootId(rootIdFromUrl);
    setRootId(rootIdFromQuery(rootIdFromUrl));
    setPickerOpen(false);
  }

  /**
   * **Người đang xem.** Ba nguồn, theo thứ tự ưu tiên:
   *
   *  1. `?focus=` trên URL — liên kết ai đó gửi cho mình;
   *  2. một cái tên vừa chọn ở ô tìm trên canvas, hoặc nút "Về chỗ tôi";
   *  3. hồ sơ của chính người đăng nhập, nếu họ đã được ghép vào phả.
   *
   * Nguồn 3 chỉ áp **một lần**, lúc `/me` vừa trả lời (xem khối đồng bộ ngay dưới). Không áp lại,
   * nếu không thì mỗi lần người dùng nhảy sang người khác, phả đồ lại tự kéo họ về chỗ mình.
   */
  const [focusId, setFocusId] = useState<string | null>(() => rootIdFromQuery(focusFromUrl));

  /**
   * Cú nhắm này do **người dùng chủ ý** hay do màn hình tự suy ra.
   *
   * Chỉ dùng cho đúng một quyết định: khi không mở được đường tới người ấy thì có **nói ra** hay
   * không. Người vừa gõ một cái tên rồi bấm chọn xứng đáng được trả lời; còn phép tự nhắm vào hồ
   * sơ của chính người dùng thì im lặng rơi về cây mặc định — họ chưa yêu cầu gì cả, và một dải
   * cảnh báo mỗi lần mở trang là cách nhanh nhất để người ta thôi đọc mọi dải cảnh báo.
   */
  const [focusOrigin, setFocusOrigin] = useState<"explicit" | "implicit">(
    focusFromUrl ? "explicit" : "implicit"
  );

  const [syncedUrlFocus, setSyncedUrlFocus] = useState(focusFromUrl);
  if (focusFromUrl !== syncedUrlFocus) {
    setSyncedUrlFocus(focusFromUrl);
    setFocusId(rootIdFromQuery(focusFromUrl));
    setFocusOrigin(focusFromUrl ? "explicit" : "implicit");
  }

  /** Đã tự nhắm vào hồ sơ của chính người dùng chưa. Một lần, rồi thôi. */
  const [selfFocusApplied, setSelfFocusApplied] = useState(false);
  if (!selfFocusApplied && selfPersonId !== null) {
    setSelfFocusApplied(true);
    if (focusId === null) setFocusId(selfPersonId);
  }
  // Chế độ xem sống trên URL, không phải chỉ trong `useState`. Xem VIEW_MODE_SLUG
  // để biết vì sao — tóm tắt: một chế độ xem không chia sẻ được qua Zalo thì chỉ
  // là "thứ mình tôi thấy", và Zalo là đường lan truyền chính của sản phẩm này.
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [viewMode, setViewMode] = useState<TreeViewMode>(() =>
    parseViewMode(searchParams.get("view"))
  );

  /**
   * Người dùng chọn gốc ở `<TreeRootPicker>`.
   *
   * Ghi luôn vào `?rootId=` để liên kết chia sẻ được — đó chính là lối vào số
   * một của cổng công khai, và nay cũng là **chỗ duy nhất** một gốc đã chọn
   * được lưu lại: lịch sử trình duyệt và dấu trang mang nó, chứ không phải một
   * khoá `localStorage` vô hình (xem `src/lib/tree/root-id.ts`). Trạng thái cục
   * bộ vẫn là nguồn sự thật: nếu bộ định tuyến không đổi URL được (trang tĩnh,
   * hoặc bộ định tuyến giả trong test) thì phả đồ vẫn mở đúng gốc vừa chọn.
   */
  const chooseRoot = useCallback(
    (nextRootId: string) => {
      setRootId(nextRootId);
      setPickerOpen(false);
      const params = currentSearchParams(searchParams);
      params.set("rootId", nextRootId);
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [pathname, router, searchParams]
  );

  /**
   * "Mở phả đồ mặc định của dòng họ" — bỏ hẳn `?rootId=` và để máy chủ trả lời.
   *
   * Lối ra của hai ca: một liên kết chia sẻ đã cũ mà máy chủ từ chối, và người
   * dùng mở màn chọn gốc rồi đổi ý. Xoá tham số khỏi URL chứ không chỉ đổi
   * trạng thái trong bộ nhớ, nếu không thì một lần tải lại trang sẽ đưa đúng
   * cái gốc hỏng ấy quay lại.
   */
  /**
   * Nhảy tới một người: ô tìm trên canvas, hoặc nút "Về chỗ tôi".
   *
   * Ghi vào `?focus=` chứ không chỉ đổi trạng thái trong bộ nhớ, vì đúng lý do đã ghi cho
   * `?rootId=`: một liên kết *"đây, chỗ của ông trên phả đồ"* là thứ người trong họ gửi cho nhau.
   * Trạng thái cục bộ vẫn là nguồn sự thật, để phả đồ vẫn nhảy đúng khi bộ định tuyến không đổi
   * được URL (trang tĩnh, hoặc bộ định tuyến giả trong test).
   */
  const jumpToPerson = useCallback(
    (personId: string) => {
      setFocusId(personId);
      setFocusOrigin("explicit");
      setPickerOpen(false);
      const params = currentSearchParams(searchParams);
      params.set("focus", personId);
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [pathname, router, searchParams]
  );

  const useServerDefaultRoot = useCallback(() => {
    setRootId(null);
    setPickerOpen(false);
    const params = currentSearchParams(searchParams);
    params.delete("rootId");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }, [pathname, router, searchParams]);

  // Ghi ngược vào URL. Dựng URLSearchParams TỪ tham số đang có chứ không tạo mới:
  // `?rootId=` phải sống sót qua một lần đổi chế độ xem. Đây đúng là lỗi "đổi ngôn
  // ngữ làm rơi hết tham số" đã sửa một lần ở <LanguageSwitcher> — cùng cái bẫy,
  // khác chỗ.
  useEffect(() => {
    // Đọc tham số từ THANH ĐỊA CHỈ THẬT, không từ `searchParams` của lượt dựng này.
    //
    // `useSearchParams()` là một ảnh chụp: sau một `router.replace` nó còn trễ mất một nhịp. Nếu
    // effect này chạy trong nhịp ấy — và nó có chạy, vì nó dựng lại mỗi khi `router` đổi tham
    // chiếu — thì nó sẽ ghi lại một URL dựng từ ảnh chụp CŨ, tức **xoá mất tham số vừa được ghi**.
    // Đã đo được: bấm một cái tên ở ô tìm thì `?focus=` xuất hiện rồi biến mất ngay sau đó, và
    // liên kết chia sẻ mang về một phả đồ không nhảy tới ai cả. Cùng lớp lỗi với "đổi ngôn ngữ
    // làm rơi hết tham số" đã sửa một lần ở <LanguageSwitcher> — khác chỗ, khác cách lộ ra.
    const params = currentSearchParams(searchParams);
    // Phân cấp là mặc định ⇒ không ghi ra URL. Liên kết ngắn nhất cho trường hợp
    // thường gặp nhất, và `/tree` trần vẫn mở đúng phả đồ quen thuộc.
    const desired = viewMode === "hierarchical" ? null : VIEW_MODE_SLUG[viewMode];
    // Không có gì đổi thì KHÔNG ghi. Một `replace` thừa vẫn là một lượt điều hướng, và mỗi lượt
    // điều hướng thừa là một cơ hội để lỗi trên xảy ra.
    if (params.get("view") === desired) return;
    if (desired === null) params.delete("view");
    else params.set("view", desired);
    const query = params.toString();
    const next = query ? `${pathname}?${query}` : pathname;
    // `replace` chứ không `push`: đổi chế độ xem không phải một bước điều hướng,
    // và nút Quay lại phải đưa người dùng RỜI phả đồ chứ không lùi qua từng chế độ.
    router.replace(next, { scroll: false });
    // `searchParams` cố ý KHÔNG nằm trong danh sách phụ thuộc: chính effect này
    // làm nó đổi, nên thêm vào là tự gọi lại mình vô hạn.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewMode, pathname, router]);
  // F3 profile panel. Kept as a drawer rather than a route change so the
  // canvas keeps its viewport and its lazily-loaded subgraph.
  const [selectedPersonId, setSelectedPersonId] = useState<string | null>(null);
  const [direction, setDirection] = useState<TreeDirection>("DESCENDANTS");

  const {
    audience,
    rootId: effectiveRootId,
    nodes,
    edges,
    expandedIds,
    loadingIds,
    expand,
    toggle,
    isLoadingInitial,
    error,
    rootNotFound,
    unauthorized,
    rootRejected,
    truncated,
    truncatedNodeIds,
    loadedCount,
    focusPath,
    focusUnreachable,
    focusMissing,
    loadedNodesById,
    childrenOf,
  } = useTreeCanvas({
    rootId,
    direction,
    focusId,
    // Chỉ cú nhắm CHỦ Ý mới được gọi mạng — xem `UseTreeCanvasOptions.fetchFocus`. Lượt tự nhắm
    // vào hồ sơ của chính người dùng chỉ mở đường trong phần phả đã tải, nên nó không thêm một
    // lượt gọi nào vào đường mà NFR-1 đang đo.
    fetchFocus: focusOrigin === "explicit",
  });

  /**
   * Người được nhắm tới **không nối được** về gốc đang mở — chi khác, hoặc xa hơn mười đời.
   *
   * Cú nhảy vẫn phải tới đích, nên đổi luôn gốc sang chính người ấy. Đây là lối rẽ duy nhất trong
   * cả màn hình tự đổi `?rootId=`, và nó xứng đáng: phương án kia là vẽ một cái cây có người đang
   * tìm lơ lửng ngoài mọi đường nối, hoặc một dải đỏ cho một hệ thống đang chạy đúng.
   */
  const shouldRerootOnFocus = focusUnreachable && focusId !== null && focusId !== rootId;
  useEffect(() => {
    if (shouldRerootOnFocus && focusId) chooseRoot(focusId);
  }, [shouldRerootOnFocus, focusId, chooseRoot]);

  const rootNode = nodes.find((n) => n.id === effectiveRootId);

  /**
   * Màn chọn gốc nay có **đúng hai** lối vào, và "mở trang lần đầu" không nằm
   * trong số đó:
   *  - máy chủ từ chối chính cái `?rootId=` người dùng mang tới (`400`);
   *  - người dùng chủ động bấm "Chọn người khác làm gốc" trên thanh công cụ.
   */
  if (rootRejected || pickerOpen) {
    return (
      <div className="p-4 sm:p-6">
        <TreeRootPicker
          audience={audience}
          onSelect={chooseRoot}
          afterRejectedRoot={rootRejected}
          onUseDefault={useServerDefaultRoot}
        />
      </div>
    );
  }

  if (rootNotFound) {
    // Same non-disclosure as GET /persons/{id} 404 for a guest: this reads
    // identically whether the id is unknown or a hidden living person.
    return (
      <div className="flex h-[70vh] flex-col items-center justify-center gap-3">
        <Empty description={t("rootNotFound")} />
        <Button onClick={() => setPickerOpen(true)}>{t("rootPicker.changeRoot")}</Button>
      </div>
    );
  }

  if (isLoadingInitial) {
    return (
      <div className="flex h-[70vh] items-center justify-center">
        <Spin size="large" tip={t("loading")}>
          <div className="h-32 w-32" />
        </Spin>
      </div>
    );
  }

  /**
   * `401`/`403` trên bản THÀNH VIÊN.
   *
   * Khách không còn tới được đây — họ đi đường `/public/tree` (xem
   * `useTreeAudience`). Còn lại đúng hai ca: phiên vừa chết giữa chừng, và
   * người có phiên nhưng ngoài phạm vi chi được giao. Cả hai đều là hệ thống
   * chạy đúng, nên vẫn là lời mời đăng nhập trên nền hổ phách chứ không phải
   * dải đỏ "không tải được". Trước đây nhánh này còn đòi `!isAuthenticated`,
   * nên một phiên hết hạn lại rơi xuống dải đỏ và nói sai chuyện đang xảy ra.
   */
  if (unauthorized) {
    return (
      <div className="p-6">
        <Alert
          // "warning" (hổ phách) chứ không phải "info": token `colorInfo` của
          // bộ chủ đề là xanh xám #2c3e50, đổ ra một mảng xám lạnh giữa nền
          // kem — lạc hẳn khỏi bảng màu đỏ sẫm / kem / hổ phách của spec, và
          // trông giống sự cố hơn là một lời mời.
          type="warning"
          showIcon
          message={t("authRequired")}
          description={t("authRequiredHint")}
          action={
            <Button type="primary" size="small" onClick={login}>
              {tAuth("login")}
            </Button>
          }
        />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <Alert type="error" showIcon message={t("loadError")} />
      </div>
    );
  }

  return (
    // Trên điện thoại phải trừ đi chiều cao thanh điều hướng dưới cùng (<MobileNav>, `fixed
    // bottom-0`, 5rem — xem chú thích ở <AppShell>). Không trừ thì dải đáy canvas nằm vĩnh viễn dưới thanh đó: thẻ nhân khẩu
    // rơi vào đấy không chạm được, mà `fitView` lại canh nội dung theo TOÀN BỘ khung canvas nên
    // phả đồ mở ra là đã có thẻ nằm khuất sẵn.
    // Chiều cao trên điện thoại nâng từ `80vh` lên `90vh` (vẫn trừ 5rem cho <MobileNav>).
    //
    // Lý do là số đo, không phải thẩm mỹ: thanh công cụ nay mang thêm ô tìm người và dải ngữ cảnh
    // gốc, nên trên Pixel 5 nó ngốn 253px trong khung 502px và phả đồ chỉ còn ~214px. Một canvas
    // cao 214px thì tâm của nó rơi ngay cạnh thẻ chú giải ở góc dưới-trái, và cử chỉ chụm/lăn để
    // thu nhỏ không tới được canvas nữa. `90vh − 5rem` trả lại ~87px mà vẫn vừa một màn hình:
    // header 69 + khung 589 + thanh điều hướng 85 ≈ đúng chiều cao khung nhìn, nên trang vẫn không
    // phải cuộn dọc để thấy hết phả đồ.
    <div className="flex h-[calc(90vh-5rem)] min-h-[420px] flex-col border border-border md:h-[80vh] md:min-h-[520px]">
      {/* <ReactFlowProvider> bọc CẢ thanh công cụ, không chỉ canvas: nút "Thu toàn cây" phải gọi
          được `fitView` của cùng một phiên bản canvas. Provider chỉ là kho trạng thái, bọc rộng
          hơn không làm canvas dựng lại. */}
      {audience === "public" && <TreePublicNotice onLogin={login} />}
      <ReactFlowProvider>
        <TreeToolbarWithCamera
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          direction={direction}
          onDirectionChange={setDirection}
          // Con số IN RA là số nhân khẩu đang hiện: `nodes` chính là kết quả của
          // computeVisibleSubgraph, nên nó tự vơi đi khi người dùng thu một nhánh lại. Số đã
          // tải đi kèm để giao kèo "không bao giờ tải cả cây" vẫn còn chỗ quan sát được.
          visibleCount={nodes.length}
          loadedCount={loadedCount}
          onChangeRoot={() => setPickerOpen(true)}
          audience={audience}
          onJumpToPerson={jumpToPerson}
          onGoToSelf={selfPersonId ? () => jumpToPerson(selfPersonId) : null}
        />
        {/* Hai lối vào luồng "tôi là ai trong phả", ngay dưới ô tìm — chỉ cho người đã đăng nhập
            mà chưa gắn nhân khẩu. Xem <TreeClaimActions> để biết vì sao nút "Tôi chưa có trong
            phả" phải luôn hiện còn nút "Đây là tôi" thì không. */}
        <TreeClaimActions
          unlinked={Boolean(me?.appUserId) && !me?.personId}
          selectedNode={nodes.find((n) => n.id === selectedPersonId)}
        />
        {/* Một dòng nói rõ đang mở chi nào và từ cụ nào. Đặt DƯỚI thanh công cụ và TRÊN canvas —
            đúng chỗ mắt đi qua trước khi chạm tới bức tranh. */}
        <TreeRootBanner rootNode={rootNode} onChangeRoot={() => setPickerOpen(true)} />
        {/* Cú nhảy không tới được đích. Câu chữ CỐ Ý không nói vì sao: "chưa có trong phả",
            "đã bị lọc" và "ngoài phạm vi chi của bạn" phải đọc ra y hệt nhau, nếu không thì chính
            ô tìm này trở thành cách dò xem ai có mặt trong dữ liệu. Hổ phách, không phải đỏ — hệ
            thống đang chạy đúng. */}
        {focusMissing && focusOrigin === "explicit" && (
          <Alert
            data-testid="tree-focus-missing"
            type="warning"
            showIcon
            banner
            closable
            onClose={() => setFocusId(null)}
            message={t("focusMissing")}
          />
        )}
        {truncated && <TreeTruncatedBanner truncatedCount={truncatedNodeIds.size} />}
        <div className="relative min-h-0 flex-1 bg-bg-page">
          {/* `effectiveRootId`, KHÔNG phải `rootId` của URL: khi máy chủ chọn gốc thì
              URL không có gì, và <PersonNode> cần biết node nào là gốc để không vẽ
              nút thu gọn lên nó. */}
          <TreeCanvasSurface
            rootId={effectiveRootId}
            nodes={nodes}
            edges={edges}
            viewMode={viewMode}
            expandedIds={expandedIds}
            loadingIds={loadingIds}
            toggle={toggle}
            expand={expand}
            selfPersonId={selfPersonId}
            focusId={focusId}
            focusPath={focusPath}
            loadedNodesById={loadedNodesById}
            childrenOf={childrenOf}
            onSelectPerson={setSelectedPersonId}
          />
          {/* Chú giải nói về QUY ƯỚC NÉT VẼ — danh sách theo đời không có nét nào để mà giải
              thích, nên ở đó nó chỉ là một lớp phủ nuốt thao tác chạm. */}
          {viewMode !== "list" && <TreeLegend />}
        </div>
      </ReactFlowProvider>
      {/* Khách bấm vào một nút phải đọc được hồ sơ, không phải một dải đỏ: bản
          thành viên của hồ sơ nằm sau `authenticated()` nên `/persons/{id}` trả
          401. <TreePersonDrawer> rẽ theo đúng `audience` mà canvas đang dùng để
          vẽ cây — cùng một quyết định, một chỗ. */}
      <TreePersonDrawer
        personId={selectedPersonId}
        audience={audience}
        onClose={() => setSelectedPersonId(null)}
      />
    </div>
  );
}

interface TreeCanvasSurfaceProps {
  rootId: string;
  nodes: TreeNode[];
  edges: TreeEdge[];
  viewMode: TreeViewMode;
  expandedIds: ReadonlySet<string>;
  loadingIds: ReadonlySet<string>;
  toggle: (nodeId: string) => void;
  expand: (nodeId: string) => void;
  selfPersonId: string | null;
  focusId: string | null;
  focusPath: readonly string[] | null;
  loadedNodesById: ReadonlyMap<string, TreeNode>;
  childrenOf: (personId: string) => TreeNode[];
  onSelectPerson: (personId: string) => void;
}

/**
 * Bề mặt vẽ: **canvas hoặc danh sách theo đời**, cùng một dữ liệu.
 *
 * Tách ra khỏi `<TreeCanvas>` vì đúng một lý do kỹ thuật: `useTreeDetailLevel()` đọc mức phóng từ
 * kho trạng thái của React Flow, mà kho ấy chỉ tồn tại **bên trong** `<ReactFlowProvider>` — và
 * chính `<TreeCanvas>` là nơi dựng provider nên không tự gọi được. Cùng khuôn mẫu với
 * `<TreeToolbarWithCamera>`.
 */
function TreeCanvasSurface({
  rootId,
  nodes,
  edges,
  viewMode,
  expandedIds,
  loadingIds,
  toggle,
  expand,
  selfPersonId,
  focusId,
  focusPath,
  loadedNodesById,
  childrenOf,
  onSelectPerson,
}: TreeCanvasSurfaceProps) {
  const detail = useTreeDetailLevel();

  if (viewMode === "list") {
    return (
      <TreeGenerationList
        rootId={rootId}
        nodesById={loadedNodesById}
        childrenOf={childrenOf}
        expand={expand}
        loadingIds={loadingIds}
        focusPath={focusPath}
        selfPersonId={selfPersonId}
        onSelectPerson={onSelectPerson}
      />
    );
  }

  return (
    <TreeCanvasContext.Provider
      value={{ rootId, expandedIds, loadingIds, toggle, selfPersonId, focusId, detail }}
    >
      <TreeCanvasInner
        rootId={rootId}
        nodes={nodes}
        edges={edges}
        viewMode={viewMode}
        focusId={focusId}
        onSelectPerson={onSelectPerson}
      />
    </TreeCanvasContext.Provider>
  );
}

/**
 * Nối thanh công cụ với máy quay của canvas.
 *
 * Tách riêng để <TreeToolbar> vẫn là component thuần: `useReactFlow()` chỉ chạy được bên trong
 * <ReactFlowProvider>, mà chính <TreeCanvas> là nơi dựng provider nên không tự gọi được.
 */
function TreeToolbarWithCamera(props: Omit<TreeToolbarProps, "onFitWholeTree">) {
  const { fitView } = useReactFlow();

  // Không áp sàn MIN_INITIAL_ZOOM: đây là lúc người dùng CHỦ Ý xin nhìn toàn cảnh cả họ.
  const fitWholeTree = useCallback(() => {
    // Bỏ sàn phóng, nhưng KHÔNG bỏ nguyện vọng giảm chuyển động: đây là cú bay xa nhất của cả
    // sản phẩm (từ mức 0.75 xuống có khi 0.05), tức đúng thứ gây triệu chứng nặng nhất.
    void fitView(withMotionPreference(WHOLE_TREE_FIT_VIEW_OPTIONS));
  }, [fitView]);

  return <TreeToolbar {...props} onFitWholeTree={fitWholeTree} />;
}

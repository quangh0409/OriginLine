"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ReactFlowProvider, useReactFlow } from "@xyflow/react";
import { usePathname, useRouter } from "@/i18n/navigation";
import { Alert, Button, Empty, Spin } from "antd";
import { useTranslations } from "next-intl";
import { useTreeCanvas } from "@/hooks/use-tree-canvas";
import { useAuth } from "@/lib/auth/auth-context";
import { WHOLE_TREE_FIT_VIEW_OPTIONS, withMotionPreference } from "@/lib/tree/fit-view";
import { rootIdFromQuery } from "@/lib/tree/root-id";
import { TreeCanvasContext } from "./tree-canvas-context";
import { TreeCanvasInner } from "./tree-canvas-inner";
import { TreeToolbar, type TreeToolbarProps } from "./tree-toolbar";
import { TreeLegend } from "./tree-legend";
import { TreePersonDrawer } from "./tree-person-drawer";
import { TreePublicNotice } from "./tree-public-notice";
import { TreeRootPicker } from "./tree-root-picker";
import { TreeTruncatedBanner } from "./tree-truncated-banner";
import type { TreeDirection } from "@/types/api";
import { parseViewMode, VIEW_MODE_SLUG, type TreeViewMode } from "@/types/tree-ui";

export interface TreeCanvasProps {
  /**
   * Gốc lấy từ `?rootId=` trên URL. **Vắng mặt là trạng thái bình thường** —
   * lúc ấy canvas không gửi `rootId` và máy chủ chọn gốc theo vai + phạm vi chi
   * của người gọi. Không có màn chọn gốc chắn ở giữa, và không có id nào của
   * một dòng họ thật nằm trong mã nguồn hay biến môi trường của giao diện.
   * Lý do đầy đủ: `src/lib/tree/root-id.ts`.
   */
  rootId?: string;
}

/**
 * Top-level phả đồ canvas. Composition only — all the actual state lives in
 * useTreeCanvas (data/lazy-load) and TreeCanvasInner (React Flow + layout).
 * See src/mocks/tree-graph/ for what backs this in dev (a ~3,500-node
 * generated graph, not the ~5-node Sprint 1 fixture) and the frontend
 * README for the Sprint-2 performance measurements against it.
 */
export function TreeCanvas({ rootId: rootIdFromUrl }: TreeCanvasProps) {
  const t = useTranslations("tree");
  const tAuth = useTranslations("auth");
  const { login } = useAuth();

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
      const params = new URLSearchParams(searchParams.toString());
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
  const useServerDefaultRoot = useCallback(() => {
    setRootId(null);
    setPickerOpen(false);
    const params = new URLSearchParams(searchParams.toString());
    params.delete("rootId");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }, [pathname, router, searchParams]);

  // Ghi ngược vào URL. Dựng URLSearchParams TỪ tham số đang có chứ không tạo mới:
  // `?rootId=` phải sống sót qua một lần đổi chế độ xem. Đây đúng là lỗi "đổi ngôn
  // ngữ làm rơi hết tham số" đã sửa một lần ở <LanguageSwitcher> — cùng cái bẫy,
  // khác chỗ.
  useEffect(() => {
    const params = new URLSearchParams(searchParams.toString());
    const slug = VIEW_MODE_SLUG[viewMode];
    // Phân cấp là mặc định ⇒ không ghi ra URL. Liên kết ngắn nhất cho trường hợp
    // thường gặp nhất, và `/tree` trần vẫn mở đúng phả đồ quen thuộc.
    if (viewMode === "hierarchical") params.delete("view");
    else params.set("view", slug);
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
    toggle,
    isLoadingInitial,
    error,
    rootNotFound,
    unauthorized,
    rootRejected,
    truncated,
    truncatedNodeIds,
    loadedCount,
  } = useTreeCanvas({ rootId, direction });

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
    <div className="flex h-[calc(80vh-5rem)] min-h-[420px] flex-col border border-border md:h-[80vh] md:min-h-[520px]">
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
        />
        {truncated && <TreeTruncatedBanner truncatedCount={truncatedNodeIds.size} />}
        <div className="relative min-h-0 flex-1 bg-bg-page">
          {/* `effectiveRootId`, KHÔNG phải `rootId` của URL: khi máy chủ chọn gốc thì
              URL không có gì, và <PersonNode> cần biết node nào là gốc để không vẽ
              nút thu gọn lên nó. */}
          <TreeCanvasContext.Provider
            value={{ rootId: effectiveRootId, expandedIds, loadingIds, toggle }}
          >
            <TreeCanvasInner
              rootId={effectiveRootId}
              nodes={nodes}
              edges={edges}
              viewMode={viewMode}
              onSelectPerson={setSelectedPersonId}
            />
          </TreeCanvasContext.Provider>
          <TreeLegend />
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

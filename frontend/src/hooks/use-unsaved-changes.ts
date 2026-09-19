"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Chốt chặn rời trang khi còn **thay đổi chưa lưu**.
 *
 * <h2>Kịch bản nó sinh ra để chặn</h2>
 * Một Trưởng chi 55 tuổi nhập bảy mục trong nửa tiếng, vuốt lùi một cái là mất
 * sạch, im lặng, không một câu hỏi. Đó đúng là người mà hệ thống cần nhất —
 * người có thẩm quyền nhưng sợ làm hỏng — và mất dữ liệu một lần là họ thôi
 * đóng góp. Trước chốt này, `isDirty` chỉ in ra dòng chữ "Có thay đổi chưa lưu"
 * rồi để mặc.
 *
 * <h2>Ba lối rời trang, ba cách chặn khác nhau</h2>
 * <ol>
 *   <li><b>Đóng tab / tải lại / lùi ra khỏi ứng dụng</b> → `beforeunload`. Trình
 *       duyệt tự hiện hộp thoại của nó; ta không chọn được chữ, chỉ chọn được
 *       có hiện hay không.</li>
 *   <li><b>Vuốt lùi / nút Back</b> → `popstate`. Đây là lối nguy hiểm nhất trên
 *       điện thoại vì nó xảy ra do một cử chỉ, không do một cú bấm có chủ đích.
 *       Ta <i>huỷ</i> cú lùi bằng cách đẩy lại đúng URL đang xem vào lịch sử,
 *       rồi mới hỏi.</li>
 *   <li><b>Liên kết trong ứng dụng</b> → bắt sự kiện `click` ở pha capture, chặn
 *       trước khi Next.js kịp điều hướng.</li>
 * </ol>
 *
 * <h2>Vì sao không dùng `useBeforeUnload` của Next</h2>
 * App Router **không** có `router.events`, nên không có móc chính thức nào để
 * chặn một lần điều hướng phía client. Bắt click ở pha capture là cách duy nhất
 * còn lại mà không phải bọc lại toàn bộ `<Link>`. Nó không bắt được lời gọi
 * `router.push()` thẳng từ mã — vì thế có thêm {@link UnsavedChangesGuard.guard}
 * để bọc những lời gọi ấy một cách tường minh.
 */

export interface PendingNavigation {
  /** `link`: bấm một liên kết · `back`: vuốt lùi/Back · `action`: một hành động đã bọc. */
  kind: "link" | "back" | "action";
  /** Đích đến, chỉ có với `link`. */
  href?: string;
}

export interface UseUnsavedChangesOptions {
  /** Bật chốt khi `true`. Thường là `formState.isDirty`. */
  when: boolean;
  /**
   * Cách điều hướng tới `href` khi người dùng chấp nhận rời đi. Thường là
   * `router.push` của next-intl. Không truyền thì rơi về `window.location`.
   */
  navigate?: (href: string) => void;
}

export interface UnsavedChangesGuard {
  /** Chốt đang bật hay không. */
  isBlocking: boolean;
  /** Ý định rời trang đang bị giữ lại, `null` nếu không có. */
  pending: PendingNavigation | null;
  /** Bọc một hành động điều hướng do mã tự gọi (nút "Huỷ", `router.push`, ...). */
  guard: (action: () => void) => void;
  /** Người dùng chấp nhận mất thay đổi — thả chốt rồi đi tiếp. */
  confirmLeave: () => void;
  /** Người dùng ở lại. **Không đụng vào dữ liệu form** — đó là cả điểm của chốt. */
  stay: () => void;
  /** Tắt chốt vĩnh viễn (sau khi lưu thành công) để lần điều hướng sau đi thẳng. */
  release: () => void;
}

/** Liên kết ngoài, tải tệp, hay mở tab mới thì không phải việc của chốt này. */
function isInAppLink(anchor: HTMLAnchorElement): boolean {
  if (anchor.target && anchor.target !== "_self") return false;
  if (anchor.hasAttribute("download")) return false;
  const href = anchor.getAttribute("href");
  if (!href) return false;
  // Neo trong trang (#...) không rời trang, mailto:/tel: không do ta điều hướng.
  if (href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")) return false;
  return anchor.origin === window.location.origin;
}

/** Ctrl/Cmd/Shift-click mở tab mới — trang hiện tại ở nguyên đó, không cần hỏi. */
function opensElsewhere(event: MouseEvent): boolean {
  return event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0;
}

export function useUnsavedChanges({
  when,
  navigate,
}: UseUnsavedChangesOptions): UnsavedChangesGuard {
  const [pending, setPending] = useState<PendingNavigation | null>(null);
  const releasedRef = useRef(false);
  const actionRef = useRef<(() => void) | null>(null);
  /** URL đang được canh giữ — chụp lại để khôi phục sau một cú lùi bị huỷ. */
  const guardedUrlRef = useRef<string | null>(null);

  const armed = when && !releasedRef.current;
  const armedRef = useRef(armed);
  armedRef.current = armed;

  // Bật lại chốt khi form trở nên "bẩn" lần nữa sau một lần lưu thành công.
  useEffect(() => {
    if (when) releasedRef.current = false;
  }, [when]);

  // --- (1) Đóng tab / tải lại ------------------------------------------------
  useEffect(() => {
    if (!armed) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!armedRef.current) return;
      // Cả hai dòng đều cần: Chrome/Safari đọc `preventDefault`, các bản cũ hơn
      // và Firefox đọc `returnValue`. Chuỗi truyền vào bị trình duyệt bỏ qua từ
      // 2017 — không có cách nào viết câu hỏi bằng tiếng Việt ở đây.
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [armed]);

  // --- (2) Vuốt lùi / nút Back ----------------------------------------------
  useEffect(() => {
    if (!armed || typeof window === "undefined") return;
    guardedUrlRef.current = window.location.href;

    const onPopState = () => {
      if (!armedRef.current) return;
      const guardedUrl = guardedUrlRef.current;
      if (guardedUrl) {
        // Huỷ cú lùi: đẩy lại đúng trang đang nhập lên đỉnh lịch sử. Thanh địa
        // chỉ trở về chỗ cũ và form không hề bị tháo khỏi cây React, nên mọi ô
        // đã gõ còn nguyên — chính là thứ phải giữ.
        window.history.pushState(null, "", guardedUrl);
      }
      setPending({ kind: "back" });
    };

    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [armed]);

  // --- (3) Liên kết trong ứng dụng ------------------------------------------
  useEffect(() => {
    if (!armed || typeof document === "undefined") return;

    const onClick = (event: MouseEvent) => {
      if (!armedRef.current || event.defaultPrevented || opensElsewhere(event)) return;
      const target = event.target as Element | null;
      const anchor = target?.closest?.("a[href]") as HTMLAnchorElement | null;
      if (!anchor || !isInAppLink(anchor)) return;

      const href = `${anchor.pathname}${anchor.search}${anchor.hash}`;
      // Bấm vào chính trang đang xem thì không có gì để mất.
      if (href === `${window.location.pathname}${window.location.search}`) return;

      event.preventDefault();
      event.stopPropagation();
      setPending({ kind: "link", href });
    };

    // Pha capture: phải chặn TRƯỚC khi <Link> của Next kịp gọi router.
    document.addEventListener("click", onClick, true);
    return () => document.removeEventListener("click", onClick, true);
  }, [armed]);

  const guard = useCallback((action: () => void) => {
    if (!armedRef.current) {
      action();
      return;
    }
    actionRef.current = action;
    setPending({ kind: "action" });
  }, []);

  const release = useCallback(() => {
    releasedRef.current = true;
    armedRef.current = false;
    setPending(null);
  }, []);

  const confirmLeave = useCallback(() => {
    const target = pending;
    releasedRef.current = true;
    armedRef.current = false;
    setPending(null);
    if (!target) return;

    if (target.kind === "action") {
      const action = actionRef.current;
      actionRef.current = null;
      action?.();
      return;
    }
    if (target.kind === "back") {
      window.history.back();
      return;
    }
    if (target.href) {
      if (navigate) navigate(target.href);
      else window.location.assign(target.href);
    }
  }, [navigate, pending]);

  /**
   * Ở lại. Cố tình **không** làm gì với dữ liệu: form vẫn đang gắn trên cây
   * React, mọi ô đã gõ còn nguyên. Huỷ điều hướng mà vẫn mất chữ thì chốt này
   * vô nghĩa.
   */
  const stay = useCallback(() => {
    actionRef.current = null;
    setPending(null);
  }, []);

  return { isBlocking: armed, pending, guard, confirmLeave, stay, release };
}

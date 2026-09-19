"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Nháp cục bộ cho một biểu mẫu dài.
 *
 * <h2>Vì sao lưu ở `sessionStorage` chứ không `localStorage` — đây là quyết định riêng tư</h2>
 * Nháp của form nhân khẩu có thể chứa **dữ liệu Tầng 3 của một người còn sống**:
 * số điện thoại, thư điện tử, địa chỉ đầy đủ, ngày sinh chính xác (BA v2 §10 /
 * Nghị định 13/2023). Máy tính ở nhà thờ họ hay ở nhà văn hoá thôn là máy dùng
 * chung. `localStorage` sống qua cả lần đóng trình duyệt, nên một bản nháp bỏ
 * dở của Trưởng chi sẽ nằm lại đó chờ người kế tiếp mở máy — một vụ lộ dữ liệu
 * hoàn hảo mà không ai bấm sai nút nào.
 *
 * `sessionStorage` bị giới hạn trong **một tab và một phiên**: đóng tab là mất.
 * Đó là mức bền vừa đủ cho tai nạn thật (vuốt lùi, bấm nhầm liên kết, tải lại
 * trang) mà không biến máy dùng chung thành kho dữ liệu.
 *
 * <h2>Ba lớp phòng thủ nữa</h2>
 * <ul>
 *   <li><b>Khoá có danh tính người gọi</b>: đổi tài khoản (hoặc đổi vai trong bộ
 *       giả lập) là nháp cũ không còn khớp khoá — không ai đọc được nháp của
 *       người khác dù ngồi cùng tab.</li>
 *   <li><b>Xoá ngay khi lưu thành công</b>: nháp chỉ tồn tại đúng quãng nó có ích.</li>
 *   <li><b>Nút "Xoá nháp" hiện ngay trên màn hình</b>: người dùng phải <i>thấy</i>
 *       rằng có dữ liệu đang nằm trên máy này và tự xoá được. Nháp giấu kín là
 *       nháp không ai kiểm soát.</li>
 * </ul>
 *
 * <p>Không bao giờ ghi nháp ra máy chủ: một bản nháp chưa duyệt mà nằm trong cơ
 * sở dữ liệu là dữ liệu chưa ai chịu trách nhiệm.</p>
 */

/** Tăng khi hình dạng giá trị form đổi — nháp phiên bản cũ bị bỏ, không cố đọc. */
const DRAFT_SCHEMA_VERSION = 1;

const KEY_PREFIX = "giapha.draft.v" + DRAFT_SCHEMA_VERSION;

/** 800ms: đủ lâu để không ghi từng phím, đủ nhanh để một cú vuốt lùi vẫn kịp. */
const DRAFT_DEBOUNCE_MS = 800;

interface StoredDraft<T> {
  version: number;
  savedAt: string;
  values: T;
}

export interface UseFormDraftOptions {
  /**
   * Định danh bản nháp. Phải gồm **cả danh tính người gọi lẫn đối tượng đang
   * sửa** — ví dụ `"person:p-010:u-branch-head"`. `null` thì tắt hẳn việc lưu
   * (chưa biết mình là ai thì chưa được ghi gì ra máy).
   */
  key: string | null;
}

export interface FormDraft<T> {
  /** Nháp tìm thấy lúc mở màn hình, `null` nếu không có. */
  restorable: { values: T; savedAt: string } | null;
  /**
   * Ghi nháp, có giảm nhịp.
   *
   * Nơi gọi chủ động đẩy giá trị vào chứ hook **không** tự đọc form: biểu mẫu
   * nhân khẩu dùng ô không kiểm soát để gõ một tiểu sử dài không vẽ lại cả
   * trang, và một `watch()` trả giá trị ở đây sẽ phá đúng tính chất ấy. Dùng
   * `watch(callback)` của react-hook-form — nó báo thay đổi mà không vẽ lại.
   */
  save: (values: T) => void;
  /** Bỏ qua bản nháp (không xoá khỏi máy — người dùng có thể vẫn muốn nó). */
  dismiss: () => void;
  /** Xoá hẳn khỏi máy. Gọi sau khi lưu thành công và khi người dùng bấm "Xoá nháp". */
  clear: () => void;
  /** Có gì đang nằm trên máy này không — để hiện lời nhắc + nút xoá. */
  hasStoredDraft: boolean;
}

function readStorage(): Storage | null {
  if (typeof window === "undefined") return null;
  try {
    // Safari ở chế độ riêng tư ném ngay khi chạm vào sessionStorage.
    return window.sessionStorage;
  } catch {
    return null;
  }
}

function storageKey(key: string): string {
  return `${KEY_PREFIX}:${key}`;
}

export function readDraft<T>(key: string): { values: T; savedAt: string } | null {
  const storage = readStorage();
  if (!storage) return null;
  try {
    const raw = storage.getItem(storageKey(key));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredDraft<T>;
    if (parsed.version !== DRAFT_SCHEMA_VERSION) {
      storage.removeItem(storageKey(key));
      return null;
    }
    return { values: parsed.values, savedAt: parsed.savedAt };
  } catch {
    // Nháp hỏng thì bỏ, không bao giờ để nó làm sập màn hình sửa.
    return null;
  }
}

export function clearDraft(key: string): void {
  readStorage()?.removeItem(storageKey(key));
}

export function useFormDraft<T>({ key }: UseFormDraftOptions): FormDraft<T> {
  /**
   * Đọc **một lần** lúc mở màn hình. Đọc lại ở mỗi lần vẽ sẽ khiến bản nháp
   * vừa ghi đè lên chính những gì người dùng đang gõ.
   */
  const [restorable, setRestorable] = useState<{ values: T; savedAt: string } | null>(null);
  const [hasStoredDraft, setHasStoredDraft] = useState(false);
  const initializedRef = useRef(false);

  useEffect(() => {
    if (initializedRef.current || !key) return;
    initializedRef.current = true;
    const found = readDraft<T>(key);
    if (found) {
      setRestorable(found);
      setHasStoredDraft(true);
    }
  }, [key]);

  // Ghi có giảm nhịp: gõ tiểu sử dài mà mỗi phím một lần ghi thì vừa tốn vừa
  // vô ích — thứ cần cứu là "nửa tiếng công", không phải "ký tự cuối cùng".
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const keyRef = useRef(key);
  keyRef.current = key;

  /**
   * Giá trị mới nhất chưa ghi được vì **chưa biết khoá**.
   *
   * `key` phụ thuộc vào `GET /api/v1/me`, tức là phụ thuộc vào mạng. Bỏ luôn
   * những thay đổi xảy ra trước khi lời gọi ấy trả về sẽ đánh mất đúng khoảng
   * nguy hiểm nhất — vài giây đầu, khi người dùng vừa mở màn hình và bắt đầu
   * gõ. Nhưng cũng **không được** ghi đại xuống máy với một khoá vô danh: khoá
   * mang danh tính chính là thứ ngăn hai người dùng chung một máy đọc nháp của
   * nhau. Nên: giữ trong bộ nhớ, ghi ngay khi biết mình là ai.
   */
  const pendingRef = useRef<T | null>(null);

  const writeNow = useCallback((activeKey: string, values: T) => {
    const storage = readStorage();
    if (!storage) return;
    try {
      const payload: StoredDraft<T> = {
        version: DRAFT_SCHEMA_VERSION,
        savedAt: new Date().toISOString(),
        values,
      };
      storage.setItem(storageKey(activeKey), JSON.stringify(payload));
      setHasStoredDraft(true);
    } catch {
      // Hết dung lượng hoặc bị chặn: nháp là tiện ích, không được phép làm
      // hỏng việc nhập liệu.
    }
  }, []);

  const save = useCallback(
    (values: T) => {
      pendingRef.current = values;
      const activeKey = keyRef.current;
      if (!activeKey) return;

      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        const latest = pendingRef.current;
        const currentKey = keyRef.current;
        if (latest !== null && currentKey) writeNow(currentKey, latest);
      }, DRAFT_DEBOUNCE_MS);
    },
    [writeNow]
  );

  // Khoá vừa có: đẩy ngay thứ đang chờ xuống máy.
  useEffect(() => {
    if (!key || pendingRef.current === null) return;
    writeNow(key, pendingRef.current);
  }, [key, writeNow]);

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  const dismiss = useCallback(() => setRestorable(null), []);

  const clear = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    pendingRef.current = null;
    if (key) clearDraft(key);
    setRestorable(null);
    setHasStoredDraft(false);
  }, [key]);

  return { restorable, save, dismiss, clear, hasStoredDraft };
}

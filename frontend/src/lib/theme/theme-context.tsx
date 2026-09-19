"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

/**
 * Ba trạng thái chủ đề, đúng như `design/00-dinh-huong.html` §3 chốt.
 *
 * <p>{@code "he-thong"} là <b>mặc định</b> và nó KHÔNG đánh dấu gì lên thẻ {@code <html>} — lúc đó
 * chỉ {@code @media (prefers-color-scheme)} quyết định. Người dùng chọn tường minh thì mới ghi
 * {@code .dark} hoặc {@code .light}, và lựa chọn đó phải thắng cả hệ điều hành ở cả hai chiều.</p>
 */
export type ChuDe = "he-thong" | "sang" | "toi";

const KHOA_LUU = "giapha.chu-de";

interface ThemeContextValue {
  /** Lựa chọn của người dùng, gồm cả "theo hệ thống". */
  readonly chuDe: ChuDe;
  /** Chủ đề THỰC SỰ đang hiển thị sau khi đã giải "theo hệ thống". */
  readonly dangToi: boolean;
  readonly datChuDe: (giaTri: ChuDe) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function docLuaChonDaLuu(): ChuDe {
  if (typeof window === "undefined") return "he-thong";
  try {
    const luu = window.localStorage.getItem(KHOA_LUU);
    return luu === "sang" || luu === "toi" ? luu : "he-thong";
  } catch {
    // Chế độ riêng tư hoặc trình duyệt chặn lưu trữ: rơi về mặc định, đừng ném lỗi.
    return "he-thong";
  }
}

/**
 * Nguồn sự thật duy nhất về chủ đề, dùng chung cho Tailwind (qua lớp trên {@code <html>}) và cho
 * Ant Design (qua {@code ConfigProvider}).
 *
 * <p><b>Vì sao phải có cả hai đường:</b> Tailwind đi được bằng biến CSS nên chỉ cần đổi lớp. Ant
 * Design thì <b>không</b> — thuật toán sinh dải màu của nó phải phân tích được mã hex, không nhận
 * {@code var(--…)}. Thiếu nửa sau thì nền và chữ đã tối trong khi toàn bộ nút, ô nhập, hộp thoại
 * vẫn sáng trắng.</p>
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [chuDe, setChuDeState] = useState<ChuDe>("he-thong");
  const [osToi, setOsToi] = useState(false);

  // Đọc lựa chọn đã lưu SAU khi gắn kết, không phải lúc khởi tạo state.
  //
  // Máy chủ không có localStorage, nên đọc lúc khởi tạo sẽ khiến HTML dựng ở máy chủ khác HTML
  // dựng ở trình duyệt và React báo lỗi hydration. Cái nháy sáng một nhịp được xử lý bằng đoạn
  // script chặn trong <head> (xem `theme-script.tsx`), không phải bằng state.
  useEffect(() => {
    setChuDeState(docLuaChonDaLuu());
  }, []);

  // Theo dõi hệ điều hành để chế độ "theo hệ thống" đổi ngay khi người dùng đổi cài đặt máy,
  // không phải chờ tải lại trang.
  useEffect(() => {
    const mql = window.matchMedia("(prefers-color-scheme: dark)");
    setOsToi(mql.matches);
    const nghe = (e: MediaQueryListEvent) => setOsToi(e.matches);
    mql.addEventListener("change", nghe);
    return () => mql.removeEventListener("change", nghe);
  }, []);

  const dangToi = chuDe === "toi" || (chuDe === "he-thong" && osToi);

  // Đồng bộ lớp lên <html>. `color-scheme` cũng phải đặt, nếu không thanh cuộn và các điều khiển
  // gốc của trình duyệt vẫn giữ màu sáng trên nền tối.
  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove("dark", "light");
    if (chuDe === "toi") root.classList.add("dark");
    else if (chuDe === "sang") root.classList.add("light");
    root.style.colorScheme = dangToi ? "dark" : "light";
  }, [chuDe, dangToi]);

  const datChuDe = useCallback((giaTri: ChuDe) => {
    setChuDeState(giaTri);
    try {
      if (giaTri === "he-thong") window.localStorage.removeItem(KHOA_LUU);
      else window.localStorage.setItem(KHOA_LUU, giaTri);
    } catch {
      // Không lưu được thì lựa chọn chỉ sống trong phiên này — vẫn tốt hơn là hỏng.
    }
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({ chuDe, dangToi, datChuDe }),
    [chuDe, dangToi, datChuDe]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useChuDe(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useChuDe phải nằm trong <ThemeProvider>");
  }
  return ctx;
}

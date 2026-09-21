import { type ReactElement, type ReactNode } from "react";
import { render, type RenderOptions, type RenderResult } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider, type AbstractIntlMessages } from "next-intl";
import { ConfigProvider, App as AntdApp } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import viMessages from "../../messages/vi.json";
import enMessages from "../../messages/en.json";
import { antdTheme } from "@/styles/antd-theme";
import { setDevRole, type DevRole } from "@/lib/api/dev-role";
import { setApiLocale } from "@/lib/api/http";
import { AuthContext, type AuthContextValue } from "@/lib/auth/auth-context";
import type { AppRole } from "@/lib/auth/roles";

export type TestLocale = "vi" | "en";

const MESSAGES: Record<TestLocale, AbstractIntlMessages> = {
  vi: viMessages as unknown as AbstractIntlMessages,
  en: enMessages as unknown as AbstractIntlMessages,
};

export interface RenderWithProvidersOptions extends Omit<RenderOptions, "wrapper"> {
  locale?: TestLocale;
  /**
   * Pins the `x-mock-role` header for every request this render makes — **and**
   * the session the component tree sees through `useAuth()`.
   */
  role?: DevRole;
  /**
   * Overrides individual fields of the fake session, for the rare test that
   * needs a shape `role` cannot express (an authenticated account with no
   * roles, a mid-flight `loading`, a display name asserted on screen).
   */
  auth?: Partial<AuthContextValue>;
}

/**
 * Vai ứng dụng tương ứng với mỗi vai giả của bộ mô phỏng.
 *
 * `admin` mang **cả** `ADMIN` lẫn `COUNCIL`, đúng như tài khoản dev
 * `admin.giapha` mà `roles.ts` mô tả — nếu chỉ gán `ADMIN` thì mọi màn hỏi
 * `hasRole("COUNCIL")` sẽ lặng lẽ đi nhánh sai trong test mà vẫn xanh.
 */
const VAI_UNG_DUNG: Record<DevRole, AppRole[]> = {
  guest: [],
  member: ["MEMBER"],
  "branch-head": ["BRANCH_HEAD"],
  admin: ["ADMIN", "COUNCIL"],
};

/**
 * Dựng một phiên giả **khớp với vai đã ghim cho bộ mô phỏng**.
 *
 * <h2>Vì sao phải có, và vì sao nó từng thiếu mà không ai thấy</h2>
 * `useAuth()` cố ý rơi về "khách" khi đứng ngoài cây provider — đúng cho sản
 * phẩm (không màn nào đáng sập vì thiếu thông tin đăng nhập), nhưng trong test
 * thì nó biến **mọi** lượt dựng thành một người chưa đăng nhập. Trước bản vá
 * này, `renderWithProviders({ role: "member" })` chỉ đặt header `x-mock-role`:
 * máy chủ giả trả dữ liệu của một thành viên, còn giao diện vẫn tin mình đang
 * phục vụ khách. Hai nguồn sự thật lệch nhau, và không có gì kêu.
 *
 * <p>Hệ quả: mọi bài kiểm dạng "thành viên thấy X" mà X được canh bằng
 * `isAuthenticated` đều xanh vì lý do sai. Lỗi này lộ ra khi màn nhận lời mời
 * trở thành màn đầu tiên vừa rẽ nhánh theo `isAuthenticated` vừa phụ thuộc
 * `role` để lấy dữ liệu giả.</p>
 *
 * <p>Tên hiển thị cố ý để **rỗng** trừ khi test tự đặt: một chuỗi bịa sẵn sẽ
 * lọt vào DOM và làm hỏng đúng những bài kiểm đang khẳng định "không có tên
 * người còn sống nào trên màn này".</p>
 */
function phienGia(role: DevRole, ghiDe?: Partial<AuthContextValue>): AuthContextValue {
  const roles = VAI_UNG_DUNG[role];
  const daDangNhap = role !== "guest";
  const value: AuthContextValue = {
    status: daDangNhap ? "authenticated" : "guest",
    isAuthenticated: daDangNhap,
    displayName: "",
    username: "",
    roles,
    role: roles[0] ?? null,
    hasRole: (r: AppRole) => roles.includes(r),
    login: () => undefined,
    logout: () => undefined,
    ...ghiDe,
  };
  // `hasRole` phải trả lời theo danh sách CUỐI CÙNG. Không có dòng này thì một
  // test ghi đè `roles` sẽ có `hasRole` vẫn trả lời theo danh sách cũ — đúng
  // kiểu lệch âm thầm mà cả bản vá này sinh ra để dẹp.
  if (ghiDe?.roles && !ghiDe.hasRole) {
    const cuoi = ghiDe.roles;
    value.hasRole = (r: AppRole) => cuoi.includes(r);
  }
  return value;
}

/**
 * Mounts a component inside the same provider stack the app uses, with the
 * REAL message catalogue rather than a stub. Using the real catalogue is
 * deliberate: a missing key surfaces as next-intl's `MISSING_MESSAGE` error,
 * which several tests assert never appears on screen.
 *
 * Retries are off on the QueryClient so a deliberate 404 (guest asking for a
 * living person) resolves in one tick instead of being retried into a
 * timeout.
 */
export function renderWithProviders(
  ui: ReactElement,
  { locale = "vi", role, auth, ...options }: RenderWithProvidersOptions = {}
): RenderResult & { user: ReturnType<typeof userEvent.setup>; queryClient: QueryClient } {
  if (role) setDevRole(role);
  // Giống <Providers>: lớp API phải biết ngôn ngữ đang hiển thị để gửi
  // `Accept-Language`, nếu không test tiếng Anh vẫn hỏi máy chủ bản tiếng Việt.
  setApiLocale(locale);

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <NextIntlClientProvider
        locale={locale}
        messages={MESSAGES[locale]}
        timeZone="Asia/Ho_Chi_Minh"
        now={new Date("2026-08-31T00:00:00+07:00")}
      >
        <ConfigProvider theme={antdTheme}>
          <AntdApp>
            <QueryClientProvider client={queryClient}>
              <AuthContext.Provider value={phienGia(role ?? "guest", auth)}>
                {children}
              </AuthContext.Provider>
            </QueryClientProvider>
          </AntdApp>
        </ConfigProvider>
      </NextIntlClientProvider>
    );
  }

  const result = render(ui, { wrapper: Wrapper, ...options });
  return { ...result, user: userEvent.setup(), queryClient };
}

/**
 * Every rendering that hides a field must hide it COMPLETELY. These are the
 * placeholder shapes that would leak "there is data here you may not see":
 * a dash, an ellipsis, a lock, a tier badge, a "private"/"hidden" hint.
 */
export const FORBIDDEN_PLACEHOLDER_PATTERNS: RegExp[] = [
  /^[\s]*[—–-]{1,3}[\s]*$/, // — / – / -
  /•\s*•\s*•/,
  /\*\*\*/,
  /\bN\/A\b/i,
  /🔒|🔓/,
  /\banonymous\b/i,
  /\brestricted\b/i,
  /\bhidden\b/i,
  /\bprivate\b/i,
  /\bkhông có quyền\b/i,
  /\bbị ẩn\b/i,
  /\bđã ẩn\b/i,
  /\briêng tư\b/i,
  /\bẩn theo phân tầng\b/i,
  /\bT[123]\b/,
  /MISSING_MESSAGE/,
];

/**
 * Asserts that a rendered subtree contains no privacy-leaking placeholder,
 * no empty labelled row, and no next-intl MISSING_MESSAGE.
 *
 * Deliberately checks structure as well as text: an `<dt>` whose `<dd>` is
 * empty is the exact "ô trống gợi ý có dữ liệu bị ẩn" the F3 brief forbids,
 * and it would slip past a pure text scan.
 */
export function expectNoHiddenFieldPlaceholders(container: HTMLElement): void {
  const text = container.textContent ?? "";

  for (const pattern of FORBIDDEN_PLACEHOLDER_PATTERNS) {
    if (pattern.source.startsWith("^")) continue; // handled per-node below
    if (pattern.test(text)) {
      throw new Error(
        `Rendered profile contains a privacy-leaking placeholder matching ${pattern}.\n` +
          `Full text:\n${text}`
      );
    }
  }

  // No labelled row may render with an empty value.
  for (const dt of Array.from(container.querySelectorAll("dt"))) {
    const dd = dt.nextElementSibling;
    const label = dt.textContent?.trim() ?? "";
    const value = dd?.textContent?.trim() ?? "";
    if (label.length === 0) {
      throw new Error("Found a <dt> with no label text — an empty labelled row.");
    }
    if (value.length === 0) {
      throw new Error(
        `Label "${label}" rendered with an empty value. A field hidden by the privacy tier ` +
          `must disappear entirely (label included), never render as a blank row.`
      );
    }
    if (/^[—–-]{1,3}$/.test(value) || /•\s*•\s*•/.test(value)) {
      throw new Error(`Label "${label}" rendered the placeholder "${value}".`);
    }
  }

  // No section heading may stand over nothing.
  for (const section of Array.from(container.querySelectorAll("section"))) {
    const heading = section.querySelector("h1, h2, h3");
    if (!heading) continue;
    const body = (section.textContent ?? "").replace(heading.textContent ?? "", "").trim();
    if (body.length === 0) {
      throw new Error(
        `Section heading "${heading.textContent}" rendered with no content beneath it — ` +
          `an empty section is the same leak as an empty field.`
      );
    }
  }
}

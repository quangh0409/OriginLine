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

export type TestLocale = "vi" | "en";

const MESSAGES: Record<TestLocale, AbstractIntlMessages> = {
  vi: viMessages as unknown as AbstractIntlMessages,
  en: enMessages as unknown as AbstractIntlMessages,
};

export interface RenderWithProvidersOptions extends Omit<RenderOptions, "wrapper"> {
  locale?: TestLocale;
  /** Pins the `x-mock-role` header for every request this render makes. */
  role?: DevRole;
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
  { locale = "vi", role, ...options }: RenderWithProvidersOptions = {}
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
            <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
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

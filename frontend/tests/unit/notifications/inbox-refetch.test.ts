import { describe, expect, it } from "vitest";
import { inboxRefetchInterval, isFinalRefusal } from "@/hooks/use-notifications";
import { ApiError } from "@/lib/api/http";

/**
 * Đo 24/09 trên hệ thật: tài khoản chưa ghép phả để mở trang ⇒ một `403 ACCOUNT_NOT_LINKED` mỗi
 * 60 giây trong nhật ký máy chủ, không bao giờ dừng. 4xx là câu trả lời cuối — thôi hỏi lại;
 * 5xx là trục trặc tạm thời — vẫn hỏi lại, vì hộp thư là kênh nhắc giỗ chính.
 */
const err = (status: number, code: string) =>
  new ApiError(status, { type: "about:blank", title: code, status, code } as never);

describe("inboxRefetchInterval", () => {
  it("chưa có lỗi ⇒ hỏi lại mỗi 60 giây", () => {
    expect(inboxRefetchInterval(null)).toBe(60_000);
  });
  it("403 ACCOUNT_NOT_LINKED / 401 ⇒ thôi hỏi", () => {
    expect(inboxRefetchInterval(err(403, "ACCOUNT_NOT_LINKED"))).toBe(false);
    expect(inboxRefetchInterval(err(401, "UNAUTHENTICATED"))).toBe(false);
  });
  it("5xx hay lỗi mạng ⇒ vẫn hỏi lại", () => {
    expect(inboxRefetchInterval(err(503, "UNAVAILABLE"))).toBe(60_000);
    expect(isFinalRefusal(new TypeError("Failed to fetch"))).toBe(false);
  });
});

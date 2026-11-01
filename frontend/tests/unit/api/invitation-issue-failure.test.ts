import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/http";
import { invitationIssueFailureOf } from "@/lib/api/invitation";
import type { Problem, ProblemCode } from "@/types/api";
import viMsg from "../../../messages/vi.json";
import enMsg from "../../../messages/en.json";

/**
 * Nhánh hỏng của lượt PHÁT lời mời. Đo trên hệ thật 24/09: `truongchi` mời một
 * người Chi Giáp, máy chủ trả `403 BRANCH_SCOPE_VIOLATION`, màn in "Chưa nối
 * được với máy chủ. Xin thử lại." — vì hàm của phía người NHẬN không biết mã ấy.
 */
function err(status: number, code: string): ApiError {
  const problem: Problem = { type: "about:blank", title: "...", status, code: code as ProblemCode };
  return new ApiError(status, problem);
}

describe("invitationIssueFailureOf", () => {
  it("ba phép kiểm lúc phát của contract có nhánh riêng, không rơi về UNAVAILABLE", () => {
    expect(invitationIssueFailureOf(err(403, "BRANCH_SCOPE_VIOLATION"))).toBe("BRANCH_SCOPE");
    expect(invitationIssueFailureOf(err(422, "VALIDATION_FAILED"))).toBe("NOT_INVITABLE");
    expect(invitationIssueFailureOf(err(422, "PERSON_ALREADY_LINKED"))).toBe(
      "PERSON_ALREADY_LINKED"
    );
  });

  it("400 VALIDATION_FAILED (thân hỏng) không bị đọc thành 'người đã khuất'", () => {
    expect(invitationIssueFailureOf(err(400, "VALIDATION_FAILED"))).toBe("UNAVAILABLE");
  });

  it("lỗi không phải ApiError là trục trặc đường truyền", () => {
    expect(invitationIssueFailureOf(new Error("mạng"))).toBe("UNAVAILABLE");
  });

  it("mọi nhánh đều có câu ở cả hai ngôn ngữ", () => {
    const nhanh = [
      "BRANCH_SCOPE", "NOT_INVITABLE", "PERSON_ALREADY_LINKED", "FORBIDDEN",
      "NOT_FOUND", "RATE_LIMITED", "NEEDS_ACCOUNT", "UNAVAILABLE",
    ] as const;
    for (const k of nhanh) {
      expect(viMsg.membership.personal.issueErrors[k]).toBeTruthy();
      expect(enMsg.membership.personal.issueErrors[k]).toBeTruthy();
    }
    expect(viMsg.membership.personal.issueErrors.BRANCH_SCOPE).toMatch(/chi khác/);
  });
});

describe("màn đặt mật khẩu không hứa điều chỉ đúng cho lời mời", () => {
  // /dat-mat-khau phục vụ ba luồng qua cùng một token mờ: lời mời cá nhân, đăng
  // ký bằng mã dòng họ (tài khoản CHƯA nối phả), và cấp lại mật khẩu. Bấm thật
  // 24/09 ở luồng cấp lại: màn "liên kết không còn dùng được" nói "kể từ lúc
  // nhận lời mời" và "vẫn nối với hồ sơ trong phả" — sai cho hai trong ba luồng.
  it("hai câu của màn hết hạn trung tính về luồng", () => {
    for (const s of [
      viMsg.auth.setPassword.expiredAccountSafe,
      viMsg.auth.setPassword.expiredBody,
    ]) {
      expect(s).not.toMatch(/lời mời|nối với hồ sơ/);
    }
    for (const s of [
      enMsg.auth.setPassword.expiredAccountSafe,
      enMsg.auth.setPassword.expiredBody,
    ]) {
      expect(s).not.toMatch(/invitation|linked to your profile/);
    }
  });
});

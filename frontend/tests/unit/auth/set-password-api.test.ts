import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/http";
import {
  passwordRejectionDetail,
  setPasswordFailureOf,
  type SetPasswordFailure,
} from "@/lib/api/invitation";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * PHÂN NHÁNH LỖI CỦA `POST /invitations/set-password`.
 *
 * Bốn nhánh hỏng của lối gọi này dẫn tới bốn màn hình khác hẳn nhau, nên trộn
 * hai nhánh vào nhau không phải một lỗi hiển thị — nó là một lời khuyên sai:
 *
 *  · gọi `410` thành `422` → người dùng đi nghĩ ra một mật khẩu mới trong khi
 *    vấn đề là cái liên kết;
 *  · gọi `503` thành `422` → họ đổi một mật khẩu vốn không có vấn đề gì, và đổi
 *    bao nhiêu lần cũng vẫn hỏng;
 *  · gọi bất cứ thứ gì thành `410` → họ gọi điện xin một liên kết mới, và liên
 *    kết mới cũng sẽ không chạy.
 *
 * Vì vậy phép ánh xạ đi theo {@code code} của RFC 7807, không theo
 * {@code detail} và không theo mã HTTP — trừ đúng một chỗ có ghi lý do.
 */

function loi(status: number, code: string, detail?: string): ApiError {
  const problem: Problem = {
    type: "about:blank",
    title: "…",
    status,
    // Hai mã của lối gọi này đã có trong contract nhưng `types/api.ts` chưa bắt
    // kịp — xem javadoc của `setPasswordFailureOf`.
    code: code as unknown as ProblemCode,
    detail,
  };
  return new ApiError(status, problem);
}

describe("bốn nhánh hỏng, bốn màn hình", () => {
  const bang: ReadonlyArray<readonly [number, string, SetPasswordFailure]> = [
    [410, "SET_PASSWORD_LINK_INVALID", "LINK_INVALID"],
    [422, "VALIDATION_FAILED", "PASSWORD_REJECTED"],
    [429, "RATE_LIMITED", "RATE_LIMITED"],
    [503, "IDENTITY_PROVIDER_UNAVAILABLE", "PROVIDER_DOWN"],
  ];

  for (const [status, code, mong] of bang) {
    it(`${status} ${code} → ${mong}`, () => {
      expect(setPasswordFailureOf(loi(status, code))).toBe(mong);
    });
  }
});

describe("`VALIDATION_FAILED` ở 400 KHÔNG phải 'mật khẩu chưa đạt'", () => {
  it("400 là thân yêu cầu sai khuôn — một lỗi lập trình, không phải thứ gõ lại được", () => {
    // Nói với người dùng "mật khẩu chưa đạt" ở đây là để họ gõ lại mãi một thứ
    // vốn không sai, trong khi lỗi nằm ở chỗ khác hẳn.
    expect(setPasswordFailureOf(loi(400, "VALIDATION_FAILED"))).toBe("UNAVAILABLE");
    expect(setPasswordFailureOf(loi(422, "VALIDATION_FAILED"))).toBe("PASSWORD_REJECTED");
  });
});

describe("mặc định là UNAVAILABLE, không phải LINK_INVALID", () => {
  it("lỗi mạng (không phải ApiError) rơi về ca đường truyền", () => {
    expect(setPasswordFailureOf(new TypeError("Failed to fetch"))).toBe("UNAVAILABLE");
    expect(setPasswordFailureOf(undefined)).toBe("UNAVAILABLE");
    expect(setPasswordFailureOf({ status: 410 })).toBe("UNAVAILABLE");
  });

  it("500 không bị đọc thành liên kết hỏng", () => {
    expect(setPasswordFailureOf(loi(500, "INTERNAL_ERROR"))).toBe("UNAVAILABLE");
  });
});

describe("máy chủ chưa gắn `code`: mã HTTP là phương án dự phòng, không phải luật chính", () => {
  const bang: ReadonlyArray<readonly [number, SetPasswordFailure]> = [
    [410, "LINK_INVALID"],
    [429, "RATE_LIMITED"],
    [503, "PROVIDER_DOWN"],
    [418, "UNAVAILABLE"],
  ];

  for (const [status, mong] of bang) {
    it(`${status} không kèm code → ${mong}`, () => {
      expect(setPasswordFailureOf(new ApiError(status))).toBe(mong);
    });
  }
});

describe("câu từ chối lấy từ MÁY CHỦ, vì luật sống ở realm chứ không ở client", () => {
  it("`detail` được trả nguyên văn", () => {
    const chi = "Mật khẩu cần ít nhất 12 ký tự. Mật khẩu ông/bà vừa gõ có 8 ký tự.";
    expect(passwordRejectionDetail(loi(422, "VALIDATION_FAILED", chi))).toBe(chi);
  });

  it("vắng `detail` thì trả null, để giao diện dùng câu dự phòng của mình", () => {
    expect(passwordRejectionDetail(loi(422, "VALIDATION_FAILED"))).toBeNull();
    expect(passwordRejectionDetail(loi(422, "VALIDATION_FAILED", "   "))).toBeNull();
    expect(passwordRejectionDetail(new TypeError("boom"))).toBeNull();
  });

  it("một chính sách realm ĐỔI vẫn ra câu đúng — đó là cả lý do không sao chép luật", () => {
    // Realm nâng sàn lên 12 ký tự: không một dòng mã nào ở đây phải đổi theo.
    const chi = "A password needs at least 12 characters. The one you typed has 8.";
    const e = loi(422, "VALIDATION_FAILED", chi);
    expect(setPasswordFailureOf(e)).toBe("PASSWORD_REJECTED");
    expect(passwordRejectionDetail(e)).toContain("12");
  });
});

import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/http";
import { passwordResetFailureOf, type PasswordResetFailure } from "@/lib/api/password-reset";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * PHÂN NHÁNH LỖI CỦA `POST /password-resets`.
 *
 * Bốn ca bị chặn của dịch vụ thật (đọc `PasswordResetGrantService` javadoc)
 * chỉ có BA mã lỗi trong contract; `PASSWORD_RESET_NOT_ALLOWED` bó bốn lý do
 * vào một mã và giao `detail` việc phân biệt. Bài kiểm này khoá đúng phép
 * tách đó — và khoá luôn việc một trục trặc hạ tầng KHÔNG được đọc thành một
 * trong bốn ca nghiệp vụ.
 */

function loi(status: number, code: ProblemCode, detail?: string): ApiError {
  const problem: Problem = { type: "about:blank", title: "…", status, code, detail };
  return new ApiError(status, problem);
}

describe("ba mã lỗi → bốn nhánh giao diện", () => {
  it("BRANCH_SCOPE_VIOLATION (403) → BRANCH_SCOPE", () => {
    expect(passwordResetFailureOf(loi(403, "BRANCH_SCOPE_VIOLATION"))).toBe("BRANCH_SCOPE");
  });

  it("SELF_PASSWORD_RESET_FORBIDDEN (403) → SELF", () => {
    expect(passwordResetFailureOf(loi(403, "SELF_PASSWORD_RESET_FORBIDDEN"))).toBe("SELF");
  });

  it("detail CÓ DẤU (máy chủ từ 26/09/2026) vẫn ra HAS_EMAIL — phép so không phụ thuộc dấu", () => {
    const detail =
      "Tài khoản này có địa chỉ thư nên tự lấy lại mật khẩu được: bấm 'Quên mật khẩu'"
      + " ở màn đăng nhập để nhận thư";
    expect(passwordResetFailureOf(loi(422, "PASSWORD_RESET_NOT_ALLOWED", detail))).toBe(
      "HAS_EMAIL"
    );
  });

  it("PASSWORD_RESET_NOT_ALLOWED (422), detail nhắc địa chỉ thư → HAS_EMAIL", () => {
    const detail =
      "Tai khoan nay co dia chi thu nen tu lay lai mat khau duoc: bam 'Quen mat khau'"
      + " o man dang nhap de nhan thu";
    expect(passwordResetFailureOf(loi(422, "PASSWORD_RESET_NOT_ALLOWED", detail))).toBe(
      "HAS_EMAIL"
    );
  });

  const baLyDoConLai: ReadonlyArray<string> = [
    "Nhan khau nay chua co tai khoan — hay phat loi moi thay vi cap lai mat khau",
    "Tai khoan cua nguoi nay dang o trang thai SUSPENDED, khong cap lai lien ket dat mat khau duoc",
    "Tai khoan dang nhap cua nguoi nay khong con ton tai, hay bao Quan tri he thong",
  ];

  for (const detail of baLyDoConLai) {
    it(`PASSWORD_RESET_NOT_ALLOWED (422), "${detail.slice(0, 24)}…" → NOT_READY`, () => {
      expect(passwordResetFailureOf(loi(422, "PASSWORD_RESET_NOT_ALLOWED", detail))).toBe(
        "NOT_READY"
      );
    });
  }

  it("PASSWORD_RESET_NOT_ALLOWED không kèm detail vẫn rơi vào NOT_READY, không vỡ", () => {
    expect(passwordResetFailureOf(loi(422, "PASSWORD_RESET_NOT_ALLOWED"))).toBe("NOT_READY");
  });
});

describe("phần còn lại của contract", () => {
  it("NOT_FOUND (404) → NOT_FOUND", () => {
    expect(passwordResetFailureOf(loi(404, "NOT_FOUND"))).toBe("NOT_FOUND");
  });

  it("RATE_LIMITED (429) → RATE_LIMITED", () => {
    expect(passwordResetFailureOf(loi(429, "RATE_LIMITED"))).toBe("RATE_LIMITED");
  });

  it("IDENTITY_PROVIDER_UNAVAILABLE (503) → PROVIDER_DOWN", () => {
    expect(passwordResetFailureOf(loi(503, "IDENTITY_PROVIDER_UNAVAILABLE"))).toBe(
      "PROVIDER_DOWN"
    );
  });
});

describe("mặc định là UNAVAILABLE, KHÔNG phải một trong bốn ca nghiệp vụ", () => {
  it("lỗi mạng thô (không phải ApiError) → UNAVAILABLE", () => {
    expect(passwordResetFailureOf(new Error("network down"))).toBe("UNAVAILABLE");
  });

  it("500 không gắn `code` (proxy trả HTML) → UNAVAILABLE, không phải NOT_READY", () => {
    // Nếu điều này lỡ đọc thành NOT_READY thì một trục trặc hạ tầng sẽ được kể
    // như "người này chưa có tài khoản" — sai đối tượng để đổ lỗi.
    const problem: Problem = { type: "about:blank", title: "…", status: 500 } as Problem;
    expect(passwordResetFailureOf(new ApiError(500, problem))).toBe("UNAVAILABLE");
  });

  it("bàn tra đủ 8 nhánh, không sót không thừa", () => {
    const banTra: ReadonlyArray<PasswordResetFailure> = [
      "BRANCH_SCOPE",
      "SELF",
      "HAS_EMAIL",
      "NOT_READY",
      "NOT_FOUND",
      "RATE_LIMITED",
      "PROVIDER_DOWN",
      "UNAVAILABLE",
    ];
    expect(new Set(banTra).size).toBe(8);
  });
});

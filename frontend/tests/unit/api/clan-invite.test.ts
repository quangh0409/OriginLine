import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/http";
import {
  clanInviteFailureOf,
  clanInviteValidationDetail,
  loginIdentifierKind,
  normalizeClanInviteCode,
  retryAfterSecondsOf,
  type ClanInviteFailure,
} from "@/lib/api/clan-invite";
import type { Problem, ProblemCode } from "@/types/api";
import {
  MOCK_CLAN_CODE,
  MOCK_CLAN_CODE_EXHAUSTED,
  MOCK_CLAN_CODE_EXPIRED,
  MOCK_CLAN_CODE_PROVIDER_DOWN,
  MOCK_CLAN_CODE_RATE_LIMITED,
  MOCK_CLAN_CODE_REVOKED,
  MOCK_CLAN_CODE_SERVER_DOWN,
} from "@/mocks/handlers/clan-invite";

/**
 * Lớp API của **mã mời dòng họ** — phép chuẩn hoá mã và phép ánh xạ lỗi.
 *
 * Bộ kiểm này canh bốn bất biến, theo thứ tự hậu quả:
 *  1. **ba cách viết cùng một mã đều mở đúng một mã** — phiếu in `K7M-2QD`,
 *     tin nhắn mang `K7M2QD`, cụ bảy mươi gõ `k7m 2qd`;
 *  2. **năm nhánh hỏng không lẫn vào nhau** — gộp chúng là dựng một ngõ cụt
 *     chung cho năm tình huống cần năm việc khác nhau;
 *  3. **mặc định là `UNAVAILABLE`, không phải `NOT_FOUND`** — bảo một người
 *     rằng mã của họ sai trong khi mạng vừa rớt sẽ đẩy họ đi xin một mã mới,
 *     và mã mới cũng sẽ không mở được;
 *  4. **ô định danh nhận cả số điện thoại lẫn thư điện tử**, vì ô đăng nhập
 *     nhận cả hai và người dùng gặp hai màn trong cùng một buổi.
 */

function loi(status: number, code: string, extra: Record<string, unknown> = {}): ApiError {
  const problem = {
    type: "about:blank",
    title: "t",
    status,
    code: code as ProblemCode,
    ...extra,
  } as Problem;
  return new ApiError(status, problem);
}

describe("normalizeClanInviteCode — ba cách viết, một mã", () => {
  it("phiếu giấy · tin nhắn · cụ gõ tay đều ra cùng một chuỗi", () => {
    const tuPhieu = normalizeClanInviteCode("K7M2Q-D9HFX");
    const tuTinNhan = normalizeClanInviteCode("K7M2QD9HFX");
    const cuGoTay = normalizeClanInviteCode("  k7m2q d9hfx  ");

    expect(tuPhieu).toBe("K7M2QD9HFX");
    expect(tuTinNhan).toBe(tuPhieu);
    expect(cuGoTay).toBe(tuPhieu);
  });

  it("dịch O→0 và I/L→1 — bảng chữ Crockford đã loại bốn chữ ấy nên không va nhau", () => {
    // Người gõ nhìn tờ phiếu và đọc số 0 thành chữ O, số 1 thành chữ I hoặc L.
    expect(normalizeClanInviteCode("HONG7V2KDA")).toBe("H0NG7V2KDA");
    expect(normalizeClanInviteCode("HIT1AN")).toBe("H1T1AN");
    expect(normalizeClanInviteCode("HLT1AN")).toBe("H1T1AN");
  });

  it("bỏ mọi dấu ngắt, không chỉ dấu gạch nối", () => {
    expect(normalizeClanInviteCode("k7m.2q_d9\thfx\n")).toBe("K7M2QD9HFX");
  });

  it("mọi mã fixture đều BẤT BIẾN qua phép chuẩn hoá", () => {
    // Đã sập một lần khi viết bộ này: một fixture chứa chữ `L` bị chuẩn hoá
    // thành `1`, nên bộ giả lập so mã đã chuẩn hoá với hằng số THÔ và trả về
    // `NOT_FOUND` cho đúng cái ca nó sinh ra để dựng. Bảng chữ Crockford Base32
    // loại hẳn `I L O U`, nên một fixture chứa chúng là một fixture mà bộ sinh
    // mã thật không bao giờ tạo ra.
    const fixture = {
      MOCK_CLAN_CODE,
      MOCK_CLAN_CODE_EXPIRED,
      MOCK_CLAN_CODE_REVOKED,
      MOCK_CLAN_CODE_EXHAUSTED,
      MOCK_CLAN_CODE_RATE_LIMITED,
      MOCK_CLAN_CODE_PROVIDER_DOWN,
      MOCK_CLAN_CODE_SERVER_DOWN,
    };
    const lech = Object.entries(fixture).filter(
      ([, ma]) => normalizeClanInviteCode(ma) !== ma
    );
    expect(lech, "fixture chứa I/L/O — bộ giả lập sẽ không nhận ra chính nó").toEqual([]);
  });

  it("KHÔNG bao giờ từ chối — chuỗi rỗng ra chuỗi rỗng, máy chủ mới phán quyết", () => {
    // Client chuẩn hoá chỉ để đỡ một vòng gọi. Nếu tệp này tự quyết mã nào hợp
    // lệ thì nó là một bản luật thứ hai, và bản thứ hai sẽ lệch.
    expect(normalizeClanInviteCode("")).toBe("");
    expect(normalizeClanInviteCode("!!!")).toBe("");
  });
});

describe("clanInviteFailureOf — năm nhánh của mã, hai nhánh không phải của mã", () => {
  const bang: ReadonlyArray<readonly [string, number, ClanInviteFailure]> = [
    ["INVITATION_EXPIRED", 410, "EXPIRED"],
    ["INVITATION_REVOKED", 410, "REVOKED"],
    ["CLAN_INVITE_EXHAUSTED", 409, "EXHAUSTED"],
    ["NOT_FOUND", 404, "NOT_FOUND"],
    ["RATE_LIMITED", 429, "RATE_LIMITED"],
    ["IDENTITY_PROVIDER_UNAVAILABLE", 503, "PROVIDER_DOWN"],
    ["IDENTITY_ALREADY_REGISTERED", 422, "IDENTITY_TAKEN"],
  ];

  for (const [code, status, mong] of bang) {
    it(`${code} → ${mong}`, () => {
      expect(clanInviteFailureOf(loi(status, code))).toBe(mong);
    });
  }

  it("bảy nhánh KHÔNG có hai nhánh nào trùng nhau", () => {
    const ra = bang.map(([, , mong]) => mong);
    expect(new Set(ra).size).toBe(ra.length);
  });

  it("hai ca 410 phân biệt bằng `code`, KHÔNG bằng mã HTTP", () => {
    // Cùng 410, hai câu trả lời khác hẳn nhau: "xin mã mới vì cũ rồi" và "có
    // người chủ động đóng nó". Rẽ theo mã HTTP thì không phân biệt nổi.
    expect(clanInviteFailureOf(loi(410, "INVITATION_EXPIRED"))).toBe("EXPIRED");
    expect(clanInviteFailureOf(loi(410, "INVITATION_REVOKED"))).toBe("REVOKED");
  });

  it("mất mạng → UNAVAILABLE, KHÔNG phải NOT_FOUND", () => {
    expect(clanInviteFailureOf(new TypeError("Failed to fetch"))).toBe("UNAVAILABLE");
    expect(clanInviteFailureOf(undefined)).toBe("UNAVAILABLE");
  });

  it("máy chủ 500 không mang `code` → UNAVAILABLE", () => {
    expect(clanInviteFailureOf(new ApiError(500))).toBe("UNAVAILABLE");
  });

  it("410 trần không `code` KHÔNG bị đoán thành hết hạn hay thu hồi", () => {
    // Đoán một trong hai là in ra một câu có thể sai về một sự việc mà người
    // trong họ có thể cần biết ("có ai đó đã đóng mã này").
    expect(clanInviteFailureOf(new ApiError(410))).toBe("UNAVAILABLE");
  });

  it("mã HTTP là lưới hứng khi máy chủ chưa gắn `code`", () => {
    expect(clanInviteFailureOf(new ApiError(404))).toBe("NOT_FOUND");
    expect(clanInviteFailureOf(new ApiError(429))).toBe("RATE_LIMITED");
    expect(clanInviteFailureOf(new ApiError(503))).toBe("PROVIDER_DOWN");
  });
});

describe("retryAfterSecondsOf — không bịa số", () => {
  it("đọc `retryAfterSeconds` của RFC 7807", () => {
    expect(retryAfterSecondsOf(loi(429, "RATE_LIMITED", { retryAfterSeconds: 120 }))).toBe(120);
  });

  it("máy chủ không nói thì trả null, chứ không đoán một con số", () => {
    expect(retryAfterSecondsOf(loi(429, "RATE_LIMITED"))).toBeNull();
    expect(retryAfterSecondsOf(loi(429, "RATE_LIMITED", { retryAfterSeconds: "sau" }))).toBeNull();
    expect(retryAfterSecondsOf(loi(429, "RATE_LIMITED", { retryAfterSeconds: 0 }))).toBeNull();
    expect(retryAfterSecondsOf(new Error("x"))).toBeNull();
  });
});

describe("clanInviteValidationDetail — câu của máy chủ về ô vừa gõ", () => {
  it("chỉ đọc `detail` ở VALIDATION_FAILED", () => {
    expect(
      clanInviteValidationDetail(loi(400, "VALIDATION_FAILED", { detail: "Sai khuôn thư." }))
    ).toBe("Sai khuôn thư.");
  });

  it("không đọc `detail` của một mã lỗi khác", () => {
    // `detail` của backend là tiếng Việt KHÔNG DẤU ("Ma moi khong dung duoc"),
    // nên in nó ra ở một nhánh khác là đưa chữ rác lên màn hình.
    expect(
      clanInviteValidationDetail(loi(410, "INVITATION_EXPIRED", { detail: "Ma het han" }))
    ).toBeNull();
  });

  it("detail rỗng → null, để giao diện dùng câu dự phòng của mình", () => {
    expect(clanInviteValidationDetail(loi(400, "VALIDATION_FAILED", { detail: "   " }))).toBeNull();
    expect(clanInviteValidationDetail(loi(400, "VALIDATION_FAILED"))).toBeNull();
  });
});

describe("loginIdentifierKind — ô nhận cả số điện thoại lẫn thư điện tử", () => {
  it("nhận ra số điện thoại Việt Nam ở mọi cách viết thường gặp", () => {
    for (const so of ["0912345678", "0912 345 678", "0912.345.678", "+84912345678", "84912345678"]) {
      expect(loginIdentifierKind(so)).toBe("PHONE");
    }
  });

  it("nhận ra thư điện tử", () => {
    expect(loginIdentifierKind("ba.lan@gmail.com")).toBe("EMAIL");
    expect(loginIdentifierKind("  ba.lan@ho-nguyen.vn ")).toBe("EMAIL");
  });

  it("ô trống và chuỗi lạ đi thẳng tới máy chủ", () => {
    // Đây KHÔNG phải một phép kiểm hợp lệ — nó chỉ chọn câu trả lời nào hiện
    // ra. Chỗ duy nhất phán quyết là máy chủ.
    expect(loginIdentifierKind("")).toBe("UNKNOWN");
    expect(loginIdentifierKind("   ")).toBe("UNKNOWN");
    expect(loginIdentifierKind("Nguyễn Thị Lan")).toBe("UNKNOWN");
  });
});

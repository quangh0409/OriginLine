import { describe, expect, it, beforeEach } from "vitest";
import { ApiError } from "@/lib/api/http";
import {
  invitationApi,
  invitationFailureOf,
  invitationValidationDetail,
  isInvitationValidationError,
} from "@/lib/api/invitation";
import { setDevRole } from "@/lib/api/dev-role";
import type { Problem, ProblemCode } from "@/types/api";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import {
  MOCK_INVITATION_CODE,
  MOCK_INVITATION_CODE_EXPIRED,
  MOCK_INVITATION_CODE_NEW_ACCOUNT,
  MOCK_INVITATION_CODE_RATE_LIMITED,
  MOCK_INVITATION_CODE_REVOKED,
  MOCK_INVITATION_CODE_USED,
  resetInvitationMockState,
} from "@/mocks/handlers/invitation";

/**
 * Lớp API lời mời — hình dạng **trên dây**, đối chiếu với nhóm `invitations`
 * của `contracts/openapi.yaml`.
 *
 * Bộ kiểm này gọi thẳng `invitationApi` qua đúng bộ MSW mà ứng dụng chạy, nên
 * nó canh **hợp đồng** chứ không canh một bản giả riêng: nếu backend đổi hình
 * dạng, chỗ đỏ đầu tiên nằm ở đây, không phải ở một màn hình.
 */

function apiError(status: number, code: string, detail?: string): ApiError {
  const problem: Problem = {
    type: "about:blank",
    title: "...",
    status,
    code: code as ProblemCode,
    ...(detail !== undefined ? { detail } : {}),
  };
  return new ApiError(status, problem);
}

beforeEach(() => {
  resetInvitationMockState();
  // `/accept` là endpoint DUY NHẤT của nhóm đòi token; mặc định của bộ kiểm là
  // một người đã đăng nhập, còn ca "khách" được dựng riêng bên dưới.
  setDevRole("member");
});

describe("bốn ca mã hỏng ánh xạ đúng nhánh giao diện", () => {
  it("phân nhánh theo `code`, kể cả khi mã HTTP đổi", () => {
    expect(invitationFailureOf(apiError(410, "INVITATION_EXPIRED"))).toBe("EXPIRED");
    expect(invitationFailureOf(apiError(400, "INVITATION_EXPIRED"))).toBe("EXPIRED");
    expect(invitationFailureOf(apiError(409, "INVITATION_ALREADY_USED"))).toBe("ALREADY_USED");
    expect(invitationFailureOf(apiError(410, "INVITATION_REVOKED"))).toBe("REVOKED");
    expect(invitationFailureOf(apiError(404, "NOT_FOUND"))).toBe("NOT_FOUND");
    expect(invitationFailureOf(apiError(429, "RATE_LIMITED"))).toBe("RATE_LIMITED");
  });

  it("ba ca của riêng bước nhận có ba nhánh riêng", () => {
    expect(invitationFailureOf(apiError(401, "UNAUTHENTICATED"))).toBe("NEEDS_ACCOUNT");
    expect(invitationFailureOf(apiError(422, "ACCOUNT_ALREADY_LINKED"))).toBe(
      "ACCOUNT_ALREADY_LINKED"
    );
    expect(invitationFailureOf(apiError(422, "PERSON_ALREADY_LINKED"))).toBe(
      "PERSON_ALREADY_LINKED"
    );
  });

  it("mất mạng KHÔNG bao giờ đọc thành 'mã sai'", () => {
    // Mặc định về NOT_FOUND là cách nhanh nhất để một trục trặc hạ tầng biến
    // thành một cuộc gọi hoảng hốt tới trưởng chi, rồi một mã mới cũng không
    // mở được.
    expect(invitationFailureOf(new TypeError("Failed to fetch"))).toBe("UNAVAILABLE");
    expect(invitationFailureOf(apiError(500, "INTERNAL_ERROR"))).toBe("UNAVAILABLE");
    expect(invitationFailureOf(apiError(503, "INTERNAL_ERROR"))).toBe("UNAVAILABLE");
    expect(invitationFailureOf(undefined)).toBe("UNAVAILABLE");
  });

  it("máy chủ chưa gắn `code` thì mã HTTP ở bốn endpoint này vẫn đọc được", () => {
    // Phòng thủ cho một bản backend cũ hơn: ở ĐÚNG nhóm lời mời, ba mã dưới
    // đây không mang nghĩa nào khác.
    expect(invitationFailureOf(new ApiError(404))).toBe("NOT_FOUND");
    expect(invitationFailureOf(new ApiError(429))).toBe("RATE_LIMITED");
    expect(invitationFailureOf(new ApiError(401))).toBe("NEEDS_ACCOUNT");
  });
});

describe("invitationValidationDetail — câu của máy chủ về CHÍNH `loginId` vừa gửi", () => {
  it("chỉ đọc `detail` ở VALIDATION_FAILED", () => {
    expect(
      invitationValidationDetail(apiError(422, "VALIDATION_FAILED", "Dia chi thu khong hop le"))
    ).toBe("Dia chi thu khong hop le");
  });

  it("không đọc `detail` của một mã lỗi khác", () => {
    expect(
      invitationValidationDetail(apiError(410, "INVITATION_EXPIRED", "Ma het han"))
    ).toBeNull();
  });

  it("detail rỗng → null, để giao diện dùng câu dự phòng của mình", () => {
    expect(invitationValidationDetail(apiError(422, "VALIDATION_FAILED", "   "))).toBeNull();
    expect(invitationValidationDetail(apiError(422, "VALIDATION_FAILED"))).toBeNull();
  });

  it("isInvitationValidationError chỉ đúng với đúng một mã", () => {
    expect(isInvitationValidationError(apiError(422, "VALIDATION_FAILED"))).toBe(true);
    expect(isInvitationValidationError(apiError(401, "UNAUTHENTICATED"))).toBe(false);
    expect(isInvitationValidationError(new TypeError("Failed to fetch"))).toBe(false);
  });
});

describe("hợp đồng `/lookup`", () => {
  it("trả đủ thứ màn hình cần, và không hơn mức tối thiểu", async () => {
    const preview = await invitationApi.preview(MOCK_INVITATION_CODE);

    // Hai trường BẮT BUỘC duy nhất của `InvitationPreview`.
    expect(preview.invitee.displayName).toBeTruthy();
    expect(preview.expiresAt).toBeTruthy();

    // Bề mặt phải HẸP: một trường Tầng 2/Tầng 3 lọt vào đây là lọt cho bất kỳ
    // ai cầm đường dẫn, không qua bộ lọc phân tầng riêng tư nào.
    const json = JSON.stringify(preview);
    for (const cam of ["birth", "phone", "email", "occupation", "address"]) {
      expect(json.toLowerCase(), `DTO lời mời chở trường "${cam}"`).not.toContain(cam);
    }
  });

  it("KHÔNG trả personId — khoá nối bền vững không trao cho người chưa xác thực", async () => {
    const preview = await invitationApi.preview(MOCK_INVITATION_CODE);

    expect(preview.invitee).not.toHaveProperty("personId");
    expect(JSON.stringify(preview)).not.toContain("p-140");
  });

  it("KHÔNG trả relationToInviter ở Giai đoạn 1", async () => {
    // Danh xưng thuộc context `kinship`, bộ tính che tên theo người gọi, và
    // người gọi ở đây là khách không token. Mock cố ý không gửi để không màn
    // hình nào được dựng dựa trên một trường chưa tồn tại.
    const preview = await invitationApi.preview(MOCK_INVITATION_CODE);
    expect(preview.invitee.relationToInviter).toBeUndefined();
  });

  it("tên người mời là tên TRẦN — kính ngữ không đến từ máy chủ", async () => {
    const preview = await invitationApi.preview(MOCK_INVITATION_CODE);

    expect(preview.inviter?.displayName).toBe("Nguyễn Văn Cẩn");
    expect(preview.inviter?.displayName).not.toMatch(/^(ông|bà)\s/i);
    // Sự tôn kính do chức danh DÒNG TỘC gánh, không phải vai kỹ thuật.
    expect(preview.inviter?.clanTitle).toBe("Trưởng Chi Nhất");
    expect(preview.inviter?.clanTitle).not.toContain("BRANCH_HEAD");
  });

  it("chuẩn hoá mã ở MÁY CHỦ, rộng tay theo bảng chữ Crockford Base32", async () => {
    // Phiếu A6 in `K7M2Q-D9HFX`; tin nhắn mang liền; một cụ gõ tay ra chữ
    // thường, có khoảng trắng, và gõ O thay 0 / I thay 1. Bảng chữ Crockford đã
    // loại `I L O U`, nên phép dịch ngược không thể làm hai mã thật va nhau.
    for (const bienThe of [
      "K7M2Q-D9HFX",
      "k7m2qd9hfx",
      "k7m2q d9hfx",
      "K7M2Q.D9HFX",
      "K7M2QD9HFX", // dạng chuẩn
      "K7M2QD9HFX".replace("0", "O"), // không có số 0 — giữ nguyên, vẫn khớp
    ]) {
      await expect(
        invitationApi.preview(bienThe),
        `biến thể "${bienThe}" không mở được`
      ).resolves.toMatchObject({ expiresAt: expect.any(String) });
    }
  });

  it("bốn ca hỏng trả về bốn mã khác nhau, không gộp về một lỗi chung", async () => {
    const ket = await Promise.all(
      [
        MOCK_INVITATION_CODE_EXPIRED,
        MOCK_INVITATION_CODE_USED,
        "KH0NGC0THAT",
        MOCK_INVITATION_CODE_REVOKED,
      ].map((code) => invitationApi.preview(code).catch((e) => invitationFailureOf(e)))
    );
    expect(ket).toEqual(["EXPIRED", "ALREADY_USED", "NOT_FOUND", "REVOKED"]);
  });

  it("mã sai ĐỊNH DẠNG trả lời y hệt mã đúng định dạng mà không tồn tại", async () => {
    // Khác nhau ở đó thì kẻ dò đọc được độ dài và bảng chữ của mã mà không cần
    // đoán trúng lần nào.
    const ngan = (await invitationApi.preview("A").catch((e) => e)) as ApiError;
    const dai = (await invitationApi.preview("ZZZZZZZZZZ").catch((e) => e)) as ApiError;

    expect(invitationFailureOf(ngan)).toBe("NOT_FOUND");
    expect(invitationFailureOf(dai)).toBe("NOT_FOUND");
    expect(ngan.status).toBe(dai.status);
  });

  it("429 kèm Retry-After — giới hạn tần suất là lớp chống đỡ thứ ba, và nó có thật", async () => {
    const error = (await invitationApi
      .preview(MOCK_INVITATION_CODE_RATE_LIMITED)
      .catch((e) => e)) as ApiError;

    expect(error.status).toBe(429);
    expect(invitationFailureOf(error)).toBe("RATE_LIMITED");
  });

  it("thân lỗi không chở tên hay số máy của người mời", async () => {
    // Mã hỏng hoàn toàn có thể đang nằm trong tay người lạ — đó chính là ba ca
    // này. Gắn danh tính người mời vào phản hồi lỗi là biến bộ dò mã thành một
    // máy thu danh bạ trưởng chi.
    for (const code of [MOCK_INVITATION_CODE_EXPIRED, MOCK_INVITATION_CODE_USED, "KH0NGC0THAT"]) {
      const error = (await invitationApi.preview(code).catch((e) => e)) as ApiError;
      const json = JSON.stringify(error.problem ?? {});
      expect(json).not.toContain("Nguyễn Văn Cẩn");
      expect(json).not.toMatch(/\d{9,}/);
    }
  });
});

describe("hợp đồng `/accept` — KHÔNG đòi token, chính mã mời là chứng chỉ", () => {
  it("trả bằng chứng KHÔNG có bước chờ duyệt, và chưa có setPasswordUrl", async () => {
    const accepted = await invitationApi.accept(MOCK_INVITATION_CODE);

    // `status: ACTIVE` + `personId` khác rỗng ngay trong phản hồi này là bằng
    // chứng đọc được từ phía client rằng không `ChangeRequest` nào được tạo.
    expect(accepted.status).toBe("ACTIVE");
    expect(accepted.personId).toBeTruthy();
    expect(accepted.appUserId).toBeTruthy();

    // Vắng `setPasswordUrl` với mã này là CÓ CHỦ Ý, không phải thiếu sót: tài khoản
    // đã có mật khẩu từ trước, và máy chủ cố ý không phát liên kết cho tài khoản như
    // vậy — nếu phát thì ai cầm được một mã mời cộng với đoán đúng email của một
    // thành viên cũ sẽ đổi được mật khẩu của người ta.
    expect(accepted.setPasswordUrl).toBeUndefined();
  });

  it("khách KHÔNG bị chặn khi kèm `loginId` — đây là đúng người mà cả luồng sinh ra để phục vụ", async () => {
    // Hợp đồng: `security: []`. Người chưa có tài khoản thì cũng chưa có mật khẩu nào
    // để đăng nhập bằng; đòi token ở đây là đóng cửa với đúng họ. Nhưng KHÔNG mang
    // token thì máy chủ phải biết lập tài khoản bằng định danh nào — đây là chỗ
    // {@link AcceptInvitationAccount} vào cuộc.
    setDevRole("guest");

    const accepted = await invitationApi.accept(MOCK_INVITATION_CODE_NEW_ACCOUNT, {
      loginId: "ba.lan@example.com",
    });

    expect(accepted.status).toBe("ACTIVE");
    expect(accepted.setPasswordUrl).toBeTruthy();
  });

  it("khách KHÔNG kèm `loginId` → `VALIDATION_FAILED`, không phải `401`", async () => {
    // Đúng luật `LoginIdentifier.of` phía máy chủ: thiếu định danh là lỗi
    // HÌNH DẠNG thân yêu cầu, người dùng sửa được ngay tại ô — không phải một
    // trong chín nhánh nghiệp vụ của `InvitationFailure`.
    setDevRole("guest");

    const error = (await invitationApi
      .accept(MOCK_INVITATION_CODE_NEW_ACCOUNT)
      .catch((e) => e)) as ApiError;

    expect(error.status).toBe(422);
    expect(error.problem?.code).toBe("VALIDATION_FAILED");
    expect(error.problem?.detail).toBeTruthy();
  });

  it("máy chủ cũ trả 401 thì đọc ra CẦN ĐĂNG NHẬP, không phải 'mã sai'", async () => {
    // Nhánh lưới an toàn cho bản máy chủ chưa cập nhật. Dựng ca 401 tường minh chứ
    // không bắt bộ giả lập nói dối về API thật.
    server.use(
      http.post(API_BASE_URL + "/api/v1/invitations/accept", () =>
        HttpResponse.json(
          { type: "about:blank", title: "Chưa đăng nhập", status: 401, code: "UNAUTHENTICATED" },
          { status: 401, headers: { "content-type": "application/problem+json" } }
        )
      )
    );

    const error = (await invitationApi.accept(MOCK_INVITATION_CODE).catch((e) => e)) as ApiError;

    expect(error.status).toBe(401);
    expect(invitationFailureOf(error)).toBe("NEEDS_ACCOUNT");
    expect(invitationFailureOf(error)).not.toBe("NOT_FOUND");
  });

  it("mã dùng MỘT LẦN: nhận lần thứ hai là 409, không phải thành công lặng lẽ", async () => {
    await invitationApi.accept(MOCK_INVITATION_CODE);

    const error = (await invitationApi.accept(MOCK_INVITATION_CODE).catch((e) => e)) as ApiError;
    expect(invitationFailureOf(error)).toBe("ALREADY_USED");
  });
});

describe('hợp đồng `/decline` — "Không phải tôi"', () => {
  it("huỷ mã thật, và mã đã huỷ không mở lại được", async () => {
    await invitationApi.decline(MOCK_INVITATION_CODE);

    const error = (await invitationApi.preview(MOCK_INVITATION_CODE).catch((e) => e)) as ApiError;
    expect(invitationFailureOf(error)).toBe("REVOKED");
  });

  it("không đòi token — đòi token ở đây là vô hiệu hoá chính cái nút", async () => {
    // Người bấm chính là người *không* có tài khoản. Đây là lớp chống đỡ thứ
    // ba của thiết kế: người mời gửi nhầm số không có cách nào khác để biết.
    setDevRole("guest");
    await expect(invitationApi.decline(MOCK_INVITATION_CODE)).resolves.toBeUndefined();
  });
});

describe("mã mời không đi vào path — nó là bí mật, không phải định danh tài nguyên", () => {
  it("cả ba lời gọi đều là POST và mang mã trong THÂN yêu cầu", async () => {
    const seen: Array<{ url: string; body: string }> = [];
    const original = globalThis.fetch;
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      seen.push({
        url: String(input instanceof Request ? input.url : input),
        body: String(init?.body ?? ""),
      });
      return original(input as RequestInfo, init);
    }) as typeof fetch;

    try {
      await invitationApi.preview(MOCK_INVITATION_CODE);
      await invitationApi.decline(MOCK_INVITATION_CODE);
    } finally {
      globalThis.fetch = original;
    }

    expect(seen).toHaveLength(2);
    for (const { url, body } of seen) {
      // Path đi vào nhật ký truy cập của proxy và của Spring nguyên văn; thân
      // POST thì không. Và `GET` là phương thức cacheable — một bộ đệm trung
      // gian được phép giữ lại phản hồi chứa tên một người đang sống.
      expect(url).not.toContain(MOCK_INVITATION_CODE);
      expect(body).toContain(MOCK_INVITATION_CODE);
    }
  });
});

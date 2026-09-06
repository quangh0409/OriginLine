import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearPushRecordId,
  dismissPrompt,
  DISMISS_COOLDOWN_MS,
  isPromptDue,
  PUSH_PROMPT_STATE_EVENT,
  readPromptState,
  readPushRecordId,
  recordEngagement,
  writePushRecordId,
} from "@/lib/push/subscription-store";

/**
 * F9 — luật "xin quyền đúng lúc".
 *
 * Trình duyệt chỉ cho HỎI MỘT LẦN: người dùng bấm "Chặn" là vĩnh viễn, script
 * không thể hỏi lại. Vì vậy hỏi ngay lúc mở trang — khi người dùng còn chưa
 * có lý do gì để muốn nhắc giỗ — không chỉ là bất lịch sự, nó đốt luôn cơ hội
 * duy nhất. Quy tắc: đếm tín hiệu quan tâm (đã xem một hồ sơ, đã xem một ngày
 * giỗ) rồi mới hiện lời mời; "để sau" thì im lặng 30 ngày.
 *
 * Cả id bản ghi đăng ký cũng nằm ở đây, vì hợp đồng không có endpoint liệt kê
 * đăng ký — mất id là người dùng "tắt" mà máy chủ vẫn đẩy thông báo.
 */

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe("id bản ghi đăng ký", () => {
  it("ghi rồi đọc lại được — đây là tay cầm DUY NHẤT để gọi DELETE sau này", () => {
    writePushRecordId("push-mock-1");
    expect(readPushRecordId()).toBe("push-mock-1");
  });

  it("xoá sạch khi tắt thông báo", () => {
    writePushRecordId("push-mock-1");
    clearPushRecordId();
    expect(readPushRecordId()).toBeNull();
  });

  it("không làm sập trang khi localStorage ném lỗi (Safari chế độ riêng tư)", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("SecurityError");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("SecurityError");
    });

    expect(() => writePushRecordId("push-mock-1")).not.toThrow();
    expect(readPushRecordId()).toBeNull();
  });
});

describe("isPromptDue — thời điểm được phép hỏi", () => {
  it("KHÔNG hỏi khi người dùng chưa làm gì thể hiện quan tâm", () => {
    expect(isPromptDue({ reasons: [], dismissedAt: null })).toBe(false);
  });

  it("được hỏi sau một tín hiệu quan tâm thật sự", () => {
    expect(isPromptDue({ reasons: ["EVENT"], dismissedAt: null })).toBe(true);
  });

  it("im lặng trong suốt 30 ngày sau khi người dùng bấm 'để sau'", () => {
    const now = Date.UTC(2026, 7, 31);
    const state = { reasons: ["EVENT" as const], dismissedAt: now - DISMISS_COOLDOWN_MS + 1000 };

    expect(isPromptDue(state, now)).toBe(false);
  });

  it("được hỏi lại khi đã qua hạn 30 ngày — kịp mùa giỗ tiếp theo", () => {
    const now = Date.UTC(2026, 7, 31);
    const state = { reasons: ["EVENT" as const], dismissedAt: now - DISMISS_COOLDOWN_MS - 1000 };

    expect(isPromptDue(state, now)).toBe(true);
  });

  it("hạn chờ đúng 30 ngày", () => {
    expect(DISMISS_COOLDOWN_MS).toBe(30 * 24 * 60 * 60 * 1000);
  });
});

describe("ghi nhận tín hiệu quan tâm", () => {
  it("lưu lại và đọc được ở lần mở sau", () => {
    recordEngagement("PERSON_PROFILE");

    expect(readPromptState().reasons).toEqual(["PERSON_PROFILE"]);
  });

  it("không nhân bản cùng một loại tín hiệu", () => {
    recordEngagement("EVENT");
    recordEngagement("EVENT");
    recordEngagement("EVENT");

    expect(readPromptState().reasons).toEqual(["EVENT"]);
  });

  it("cộng dồn các loại tín hiệu khác nhau", () => {
    recordEngagement("PERSON_PROFILE");
    recordEngagement("EVENT");

    expect(readPromptState().reasons).toEqual(["PERSON_PROFILE", "EVENT"]);
  });

  it("phát sự kiện cùng-tab để lời mời trong khung ứng dụng biết mà hiện lên", () => {
    // `storage` chỉ bắn sang TAB KHÁC, nên nếu không có sự kiện riêng này thì
    // <PushPermissionPrompt> nằm trong khung ứng dụng sẽ không bao giờ biết
    // trang hồ sơ vừa ghi nhận một tín hiệu.
    const listener = vi.fn();
    window.addEventListener(PUSH_PROMPT_STATE_EVENT, listener);

    recordEngagement("PERSON_PROFILE");
    expect(listener).toHaveBeenCalledTimes(1);

    // Ghi trùng thì không phát lại — tránh render thừa.
    recordEngagement("PERSON_PROFILE");
    expect(listener).toHaveBeenCalledTimes(1);

    window.removeEventListener(PUSH_PROMPT_STATE_EVENT, listener);
  });

  it("dismissPrompt đóng dấu thời gian và giữ nguyên các tín hiệu đã có", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-31T00:00:00Z"));

    recordEngagement("EVENT");
    const state = dismissPrompt();

    expect(state.reasons).toEqual(["EVENT"]);
    expect(state.dismissedAt).toBe(Date.UTC(2026, 7, 31));
    expect(isPromptDue(state, Date.UTC(2026, 7, 31))).toBe(false);

    vi.useRealTimers();
  });

  it("dữ liệu hỏng trong localStorage được coi như chưa có gì, không ném lỗi", () => {
    window.localStorage.setItem("giapha.push.prompt", "{không phải json");

    expect(readPromptState()).toEqual({ reasons: [], dismissedAt: null });
  });
});

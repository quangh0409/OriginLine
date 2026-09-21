import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TreeJumpSearch } from "@/components/tree/tree-jump-search";

/**
 * **Ô tìm ngay trên canvas** — việc CHẶN của đợt này.
 *
 * <p>Luồng đăng ký mới có bước "tự nhận mình": người vừa có tài khoản phải tìm **chính họ** trong
 * 1.500 người, và bước ấy chạy trên đúng màn phả đồ. Không có ô tìm ở đây thì cách duy nhất là
 * cuộn một bức tranh vài nghìn pixel — tức bước ấy không dùng được.</p>
 *
 * <p>Chạy qua MSW thật (`src/mocks/handlers/persons.ts` và `public-portal.ts`), không phải một
 * hàm giả: phép rẽ nhánh khách / thành viên chính là thứ cần kiểm, và nó nằm trong lớp API.</p>
 */

function renderSearch(audience: "member" | "public" | null, onJump = vi.fn()) {
  return {
    onJump,
    ...renderWithProviders(<TreeJumpSearch audience={audience} onJump={onJump} />, {
      role: audience === "public" ? "guest" : "member",
    }),
  };
}

describe("ô tìm trên canvas", () => {
  it("gõ đủ dài thì hiện gợi ý, và chọn một cái tên thì báo đúng id lên trên", async () => {
    const { user, onJump } = renderSearch("member");

    await user.type(screen.getByLabelText("Tìm một người trên phả đồ"), "nguyen");

    const results = await screen.findAllByTestId("tree-jump-result", undefined, {
      timeout: 5000,
    });
    expect(results.length).toBeGreaterThan(0);
    const expectedId = results[0]!.getAttribute("data-person-id");
    await user.click(results[0]!);

    await waitFor(() => expect(onJump).toHaveBeenCalledTimes(1));
    // Id thật của một nhân khẩu, không phải chuỗi người dùng vừa gõ.
    expect(onJump).toHaveBeenCalledWith(expectedId);
  });

  it("sau khi chọn, ô tìm giữ lại CÁI TÊN chứ không phải id", async () => {
    const { user } = renderSearch("member");
    const input = screen.getByLabelText("Tìm một người trên phả đồ");
    await user.type(input, "nguyen");

    const results = await screen.findAllByTestId("tree-jump-result", undefined, {
      timeout: 5000,
    });
    const name = results[0]!.textContent ?? "";
    await user.click(results[0]!);

    // Một dãy UUID trong ô tìm đọc ra như hệ thống vừa hỏng.
    await waitFor(() => expect((input as HTMLInputElement).value).not.toMatch(/^[a-f0-9-]{20,}$/i));
    expect(name).toContain((input as HTMLInputElement).value);
  });

  it("một ký tự thì KHÔNG gửi yêu cầu nào — máy chủ công khai trả 400 cho truy vấn ấy", async () => {
    const { user } = renderSearch("public");

    await user.type(screen.getByLabelText("Tìm một người trên phả đồ"), "n");

    // Không có dải đỏ nào, và có một câu hướng dẫn thay vì một lời than.
    expect(screen.getByText(/ít nhất 2 ký tự/)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("khách tìm được, và chỉ ra người đã khuất — bộ lọc nằm ở máy chủ, không ở đây", async () => {
    const { user } = renderSearch("public");

    await user.type(screen.getByLabelText("Tìm một người trên phả đồ"), "nguyen");

    const results = await screen.findAllByTestId("tree-jump-result", undefined, {
      timeout: 5000,
    });
    expect(results.length).toBeGreaterThan(0);
    // Không nơi nào trong giao diện nói "còn N người bạn không được thấy". Một phép đếm như thế
    // là phép đếm dân số dòng họ — chính vì vậy `PublicPersonSummaryPage` không có `totalElements`.
    expect(document.body.textContent).not.toMatch(/\d+\s*(kết quả|người)\s*(được tìm thấy|khớp)/);
  });

  it("không bung 'không có ai' giữa từng phím gõ", async () => {
    const { user } = renderSearch("member");
    const input = screen.getByLabelText("Tìm một người trên phả đồ");

    await user.type(input, "z");
    // Một ký tự: chưa hỏi gì, nên chưa có quyền nói "không có ai".
    expect(screen.queryByText("Không có ai khớp tên này.")).toBeNull();
  });

  it("song ngữ, không lọt khoá i18n ra màn hình", () => {
    renderWithProviders(<TreeJumpSearch audience="member" onJump={vi.fn()} />, {
      locale: "en",
      role: "member",
    });
    expect(screen.getByLabelText("Find a person on the phả đồ")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  });
});

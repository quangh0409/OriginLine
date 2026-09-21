import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TreeClaimActions } from "@/components/tree/tree-claim-actions";
import { claimRoutes, THAM_SO_NGUOI } from "@/components/claim";
import { node } from "../setup/tree-fixtures";

/**
 * **Hai lối vào luồng "tôi là ai trong phả", đặt cạnh ô tìm trên phả đồ.**
 *
 * <p>Đây là ranh giới giữa hai vùng tệp do hai người dựng, nên ca kiểm quan trọng nhất ở đây
 * không phải là "nút có hiện không" mà là <b>địa chỉ đến từ đâu</b>: nó phải đi qua
 * {@code claimRoutes}, không được ghép tay. Một chuỗi chép tay ở bên này sẽ im lặng dẫn tới trang
 * trắng vào ngày bên kia đổi đường dẫn.</p>
 */

const living = node("p-song", 1, {
  person: { id: "p-song", displayName: "Nguyễn Văn Bình", isAlive: true, generation: 5 },
});

const deceased = node("p-khuat", 1, {
  person: { id: "p-khuat", displayName: "Nguyễn Phúc Thiện", isAlive: false, generation: 2 },
});

describe("ai được thấy hai nút này", () => {
  it("người đã đăng nhập mà chưa gắn nhân khẩu — đúng nhóm cần nó", () => {
    renderWithProviders(<TreeClaimActions unlinked selectedNode={living} />);
    expect(screen.getByTestId("tree-claim-actions")).toBeInTheDocument();
  });

  it("khách và thành viên đã gắn thì KHÔNG thấy gì — với họ đây là nhiễu", () => {
    const { container } = renderWithProviders(
      <TreeClaimActions unlinked={false} selectedNode={living} />
    );
    expect(container.querySelector('[data-testid="tree-claim-actions"]')).toBeNull();
  });
});

describe('nút "Tôi chưa có trong phả"', () => {
  it("LUÔN hiện, kể cả khi chưa chọn ai — người không tìm thấy mình chính là người cần nó", () => {
    renderWithProviders(<TreeClaimActions unlinked />);
    const link = screen.getByTestId("tree-claim-not-in-tree");
    expect(link).toHaveTextContent("Tôi chưa có trong phả");
    expect(link.getAttribute("href")).toContain(claimRoutes.newPerson);
  });
});

describe('nút "Đây là tôi"', () => {
  it("dẫn tới địa chỉ do claimRoutes sinh ra, mang mã nhân khẩu ở THAM SỐ TRUY VẤN", () => {
    renderWithProviders(<TreeClaimActions unlinked selectedNode={living} />);
    const href = screen.getByTestId("tree-claim-this-is-me").getAttribute("href") ?? "";

    // Ghim cả hai đầu sợi dây: đúng chuỗi claimRoutes sinh ra, và đúng hình dạng `?nguoi=`.
    expect(href).toContain(claimRoutes.forPerson("p-song"));
    expect(href).toContain(`${THAM_SO_NGUOI}=p-song`);
    // KHÔNG phải đoạn đường dẫn: `/nhan-dien/<id>` đụng hai tuyến tĩnh cùng cấp và sẽ vỡ lặng lẽ.
    expect(href).not.toMatch(/\/nhan-dien\/p-song/);
  });

  it("mang TÊN người đang chọn, vì 'Đây là tôi' một mình không nói rõ ô nào", () => {
    renderWithProviders(<TreeClaimActions unlinked selectedNode={living} />);
    expect(screen.getByTestId("tree-claim-this-is-me")).toHaveTextContent(
      "Đây là tôi — Nguyễn Văn Bình"
    );
  });

  it("chưa chọn ai thì không vẽ — không dựng một nút chưa biết trỏ vào đâu", () => {
    renderWithProviders(<TreeClaimActions unlinked />);
    expect(screen.queryByTestId("tree-claim-this-is-me")).toBeNull();
  });

  it("người đã khuất thì chặn ngay ở đây, kèm đúng một câu nói vì sao", () => {
    renderWithProviders(<TreeClaimActions unlinked selectedNode={deceased} />);
    expect(screen.queryByTestId("tree-claim-this-is-me")).toBeNull();
    expect(screen.getByTestId("tree-claim-deceased-note")).toHaveTextContent(
      "không nhận được"
    );
    // Lối "tôi chưa có trong phả" vẫn còn: chọn nhầm một cụ không phải là ngõ cụt.
    expect(screen.getByTestId("tree-claim-not-in-tree")).toBeInTheDocument();
  });
});

describe("sàn tiếp cận và song ngữ", () => {
  it("cả hai nút đạt sàn chạm 44px và chữ 16px, biểu tượng đi kèm chữ", () => {
    renderWithProviders(<TreeClaimActions unlinked selectedNode={living} />);
    for (const id of ["tree-claim-this-is-me", "tree-claim-not-in-tree"]) {
      const el = screen.getByTestId(id);
      expect(el.className).toContain("min-h-[44px]");
      expect(el.className).toContain("text-than");
      // Biểu tượng có `aria-hidden`, nên tên gọi của liên kết là CHỮ, không phải biểu tượng.
      expect(el.querySelector("[aria-hidden]")).not.toBeNull();
      expect((el.textContent ?? "").trim().length).toBeGreaterThan(3);
    }
  });

  it("không lọt khoá i18n ở bản tiếng Anh", () => {
    renderWithProviders(<TreeClaimActions unlinked selectedNode={living} />, { locale: "en" });
    expect(screen.getByTestId("tree-claim-not-in-tree")).toHaveTextContent(
      "I am not in the phả yet"
    );
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  });
});

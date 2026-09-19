import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ConfigProvider, Button, Input } from "antd";
import { antdTheme, antdThemeDark } from "@/styles/antd-theme";

/**
 * ĐO THẬT lớp widget Ant Design, không suy luận từ tài liệu.
 *
 * Bối cảnh: `antd-theme.ts` trước đây không khai `fontSize` cũng không khai
 * `controlHeight`, nên cả lớp widget chạy theo hạt giống mặc định của thư viện.
 * Con số 14px/32px vẫn được truyền miệng là "mặc định đã biết của AntD v5" —
 * nhưng chưa ai đo. Tệp này đo, và đo lại mỗi lần chạy CI, nên nếu bản nâng cấp
 * AntD nào đó đổi mặc định thì nó gãy ở đây chứ không gãy trên màn hình người
 * dùng.
 *
 * Sàn của 00 §2.2, "không có ngoại lệ": thân bài ≥16px, vùng chạm ≥44×44px.
 *
 * Ghi chú về jsdom: nó không dựng bố cục nên `getComputedStyle().height` không
 * phải chiều cao ĐO ĐƯỢC trên màn hình, mà là giá trị `height` mà CSS của AntD
 * khai cho phần tử. Với nút AntD thì đúng bằng `controlHeight` — chính là thứ
 * cần kiểm. Kích thước hiển thị thật (kể cả khi nội dung xuống dòng làm nút cao
 * hơn) là việc của kiểm thử trình duyệt.
 */
function do_(el: Element) {
  const cs = getComputedStyle(el);
  return {
    fontSize: parseFloat(cs.fontSize),
    height: parseFloat(cs.height),
  };
}

describe("mặc định trần của Ant Design 5.x — số ĐO ĐƯỢC, để đối chiếu", () => {
  it("là 14px / 32px, và đó là lý do phải ghi đè", () => {
    render(
      <div>
        <Button>Nút</Button>
        <Button size="small">Nhỏ</Button>
      </div>,
    );
    const macDinh = do_(screen.getByRole("button", { name: "Nút" }));
    const nho = do_(screen.getByRole("button", { name: "Nhỏ" }));

    expect(macDinh.fontSize).toBe(14);
    expect(macDinh.height).toBe(32); // 73% ngưỡng chạm 44px
    expect(nho.height).toBe(24); // 55% — đúng bằng sàn tối thiểu WCAG 2.5.8
  });
});

describe("theme của dự án — chế độ sáng", () => {
  it("nút cỡ mặc định đạt sàn 16px / 44px", () => {
    render(
      <ConfigProvider theme={antdTheme}>
        <Button>Lưu thay đổi</Button>
      </ConfigProvider>,
    );
    const m = do_(screen.getByRole("button", { name: "Lưu thay đổi" }));
    expect(m.fontSize).toBeGreaterThanOrEqual(16);
    expect(m.height).toBeGreaterThanOrEqual(44);
  });

  it('size="small" KHÔNG được tụt xuống dưới 44px', () => {
    // AntD tự suy controlHeightSM = 24 nếu không khai. Mã hiện có dùng
    // size="small" ở nút Sửa trên person-profile-header.tsx.
    render(
      <ConfigProvider theme={antdTheme}>
        <Button size="small">Sửa</Button>
      </ConfigProvider>,
    );
    expect(do_(screen.getByRole("button", { name: "Sửa" })).height).toBeGreaterThanOrEqual(44);
  });

  it('size="large" vẫn ≥44px', () => {
    render(
      <ConfigProvider theme={antdTheme}>
        <Button size="large">Xem phả đồ</Button>
      </ConfigProvider>,
    );
    expect(do_(screen.getByRole("button", { name: "Xem phả đồ" })).height).toBeGreaterThanOrEqual(44);
  });

  it("ô nhập đạt sàn chữ 16px", () => {
    render(
      <ConfigProvider theme={antdTheme}>
        <Input aria-label="Tên huý" />
      </ConfigProvider>,
    );
    expect(do_(screen.getByLabelText("Tên huý")).fontSize).toBeGreaterThanOrEqual(16);
  });
});

describe("theme của dự án — chế độ tối", () => {
  it("giữ nguyên sàn 16px / 44px, chỉ đổi màu", () => {
    render(
      <ConfigProvider theme={antdThemeDark}>
        <Button>Nút tối</Button>
      </ConfigProvider>,
    );
    const m = do_(screen.getByRole("button", { name: "Nút tối" }));
    expect(m.fontSize).toBeGreaterThanOrEqual(16);
    expect(m.height).toBeGreaterThanOrEqual(44);
  });
});

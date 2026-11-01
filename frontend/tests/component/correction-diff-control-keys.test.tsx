import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { CorrectionDiff } from "@/components/correction/correction-diff";

/**
 * Máy chủ thật đóng mốc `_baseVersion` vào payload của mọi đề nghị UPDATE_PERSON. Bấm thật
 * 24/09: thẻ "Đề nghị của tôi" in ra "_BASEVERSION · Đề nghị sửa thành 0" cạnh "Nghề nghiệp".
 * Khoá `_…` là khoá điều khiển, không bao giờ là một dòng so sánh.
 */
describe("CorrectionDiff với payload đúng hình dạng máy chủ thật", () => {
  it("chỉ hiện trường hồ sơ, không hiện khoá điều khiển _baseVersion", () => {
    renderWithProviders(
      <CorrectionDiff
        payload={{ occupation: "Giáo viên tiểu học", _baseVersion: 3 }}
        payloadFields={["occupation"]}
      />
    );
    expect(screen.getByText("Nghề nghiệp")).toBeInTheDocument();
    expect(screen.getByText("Giáo viên tiểu học")).toBeInTheDocument();
    expect(screen.queryByText(/_baseVersion/i)).not.toBeInTheDocument();
    expect(screen.queryByText("3")).not.toBeInTheDocument();
  });

  it("payload chỉ còn khoá điều khiển (đã bị cắt phía người xem) vẫn tóm tắt theo payloadFields", () => {
    renderWithProviders(
      <CorrectionDiff payload={{ _baseVersion: 3 }} payloadFields={["occupation"]} />
    );
    expect(screen.getByText("Nghề nghiệp")).toBeInTheDocument();
    expect(screen.queryByText(/_baseVersion/i)).not.toBeInTheDocument();
  });
});

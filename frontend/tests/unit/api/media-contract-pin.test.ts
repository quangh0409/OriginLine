import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { setDevRole } from "@/lib/api/dev-role";
import { mediaApi } from "@/lib/api/media";

/**
 * Ghim hình dạng `media` theo `contracts/openapi.yaml` — LẦN THỨ SÁU cái bẫy
 * "mock giả lập một API dễ hơn/khác hình dạng hơn API thật" đã xảy ra ở dự án
 * này (`MediaPolicy` lồng thay vì phẳng, bốn điểm cuối tưởng tượng dưới
 * `/posts/{id}/media/**`). Bài kiểm này không canh MỘT lần lệch cụ thể — nó
 * canh cho họ KHÔNG BAO GIỜ lệch lần thứ bảy một cách im lặng: đọc thẳng
 * khối `required:` của hai schema trong hợp đồng, rồi đối chiếu với những gì
 * `mediaApi` (chạy trên MSW) thật sự trả về.
 *
 * <h2>Vì sao tự viết một bộ phân tích YAML thô sơ, không dùng `js-yaml`</h2>
 * `js-yaml` có mặt trong `node_modules` nhưng chỉ là phụ thuộc BẮC CẦU của
 * một công cụ khác (không đứng trong `package.json` của dự án) — dựa vào nó
 * ở đây là dựa vào một thứ có thể biến mất sau một lượt `npm install` không
 * liên quan gì tới `media`. Một bộ phân tích thô sơ, chỉ đọc đúng khối
 * `required:` (cả dạng khối `- x` lẫn dạng dòng `[x, y]`), là đủ cho việc này
 * và không mượn thêm một phụ thuộc nào.
 */

const OPENAPI_PATH = join(__dirname, "..", "..", "..", "..", "contracts", "openapi.yaml");
const openapiText = readFileSync(OPENAPI_PATH, "utf8");

function indentOf(line: string): number {
  return (line.match(/^(\s*)/)?.[1] ?? "").length;
}

/**
 * Đọc danh sách `required:` của một schema trong `components.schemas` —
 * chấp cả hai cách viết YAML hợp lệ mà `contracts/openapi.yaml` dùng:
 * khối nhiều dòng (`MediaPolicyDto`) và một dòng kiểu mảng (`UploadTicketDto`).
 */
function requiredFieldsOf(schemaName: string): string[] {
  const lines = openapiText.split(/\r?\n/);
  const startIndex = lines.findIndex((line) => line.trim() === `${schemaName}:`);
  if (startIndex === -1) {
    throw new Error(`Không tìm thấy schema "${schemaName}" trong contracts/openapi.yaml`);
  }
  const schemaIndent = indentOf(lines[startIndex] ?? "");

  for (let i = startIndex + 1; i < lines.length; i += 1) {
    const line = lines[i] ?? "";
    const trimmed = line.trim();
    // Hết khối của schema này khi gặp một dòng không rỗng thụt BẰNG hoặc ÍT
    // HƠN chính tên schema (nghĩa là đã sang mục kế tiếp của `schemas:`).
    if (trimmed.length > 0 && indentOf(line) <= schemaIndent) break;
    if (!trimmed.startsWith("required:")) continue;

    const inline = trimmed.slice("required:".length).trim();
    if (inline.startsWith("[")) {
      return inline
        .replace(/^\[/, "")
        .replace(/\]$/, "")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
    }

    const requiredIndent = indentOf(line);
    const fields: string[] = [];
    for (let j = i + 1; j < lines.length; j += 1) {
      const itemLine = lines[j] ?? "";
      const itemTrim = itemLine.trim();
      if (!itemTrim.startsWith("- ") || indentOf(itemLine) <= requiredIndent) break;
      fields.push(itemTrim.slice(2).trim());
    }
    return fields;
  }
  throw new Error(`Schema "${schemaName}" không có khoá "required:" trong contracts/openapi.yaml`);
}

describe("Ghim theo contracts/openapi.yaml — mock media không được lệch lần bảy", () => {
  it("MediaPolicyDto: mock trả ĐÚNG các khoá required, không hơn không kém", async () => {
    const required = requiredFieldsOf("MediaPolicyDto");
    expect(required.length).toBeGreaterThan(0); // bộ phân tích thô — canh nó không âm thầm trả rỗng

    setDevRole("member");
    const policy = await mediaApi.policy();

    expect(Object.keys(policy).sort()).toEqual([...required].sort());
  });

  it("UploadTicketDto: mock trả ĐỦ các khoá required (cộng đúng một khoá tuỳ chọn đã biết — maxDurationSeconds)", async () => {
    const required = requiredFieldsOf("UploadTicketDto");
    expect(required.length).toBeGreaterThan(0);

    setDevRole("member");
    const ticket = await mediaApi.requestUploadTicket({ kind: "IMAGE", sizeBytes: 1024 });

    for (const field of required) {
      expect(ticket).toHaveProperty(field);
    }
    // Không có khoá lạ ngoài `required` + `maxDurationSeconds` (tuỳ chọn,
    // `null` với ảnh — xem `UploadTicketDto.maxDurationSeconds` trong hợp
    // đồng). Một khoá thừa ở đây là đúng loại lệch mà bài kiểm này tồn tại để
    // bắt.
    expect(Object.keys(ticket).sort()).toEqual([...required, "maxDurationSeconds"].sort());
  });

  it("MediaReportDto: mock trả ĐÚNG hình dạng hợp đồng — không còn postId/postTitle/reporterDisplayName bịa", async () => {
    const required = requiredFieldsOf("MediaReportDto");
    expect(required).toContain("reportedBy");

    setDevRole("member");
    // `media-seed-2` (không phải seed-1): bài kiểm màn hàng chờ dùng seed-1, và một người chỉ
    // mở được MỘT đơn đang mở cho mỗi tệp — dùng chung tệp là để thứ tự chạy quyết kết quả.
    const report = await mediaApi.report("media-seed-2", { reason: "RIENG_TU" });

    for (const field of required) {
      expect(report).toHaveProperty(field);
    }
    // Ba trường này là lần lệch thứ bảy: mock từng bịa chúng, màn duyệt dựng trên chúng, và
    // trên máy chủ thật màn ấy hiện một đường dẫn `/bai-viet/undefined` nhãn rỗng (đo bằng
    // trình duyệt 24/09/2026).
    expect(report).not.toHaveProperty("postId");
    expect(report).not.toHaveProperty("postTitle");
    expect(report).not.toHaveProperty("reporterDisplayName");
    expect(report.status).toBe("OPEN");
  });
});

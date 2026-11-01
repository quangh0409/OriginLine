import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { setDevRole } from "@/lib/api/dev-role";
import { ApiError } from "@/lib/api/http";
import { postsApi } from "@/lib/api/posts";

/**
 * Ghim quyết định 24/09/2026 ở CẢ BA nơi phải khớp nhau: hợp đồng, mock, và (qua
 * `PostDraftBodyTest`/`ContentFlowIT` phía backend) máy chủ thật.
 *
 * **Nháp được phép thân rỗng; `/submit` mới đòi thân.** Trước đó hợp đồng ghi
 * `required: [title, body]`, backend `@NotBlank body`, còn mock thì nhận thân rỗng — tức mock
 * "dễ hơn thật", và màn soạn (tự lưu nháp khi rời ô tiêu đề) xanh trên MSW mà nhận 400 trên máy
 * chủ thật ở đúng ô đầu tiên người viết chạm vào.
 */
const OPENAPI_PATH = join(__dirname, "..", "..", "..", "..", "contracts", "openapi.yaml");
const openapi = readFileSync(OPENAPI_PATH, "utf8");

function schemaBlock(name: string): string {
  const lines = openapi.split(/\r?\n/);
  const start = lines.findIndex((l) => l.trim() === `${name}:`);
  if (start === -1) throw new Error(`Không tìm thấy schema ${name}`);
  const indent = (lines[start]?.match(/^(\s*)/)?.[1] ?? "").length;
  const out: string[] = [];
  for (let i = start + 1; i < lines.length; i += 1) {
    const line = lines[i] ?? "";
    if (line.trim() && (line.match(/^(\s*)/)?.[1] ?? "").length <= indent) break;
    out.push(line);
  }
  return out.join("\n");
}

describe("Ghim: bản nháp bài viết được phép thân rỗng", () => {
  it("hợp đồng: CreatePostRequest chỉ bắt buộc `title`", () => {
    const block = schemaBlock("CreatePostRequest");
    expect(block).toMatch(/^\s*required: \[title\]\s*$/m);
  });

  it("mock: tạo nháp chỉ có tiêu đề ⇒ 201; gửi duyệt khi thân rỗng ⇒ 422 VALIDATION_FAILED", async () => {
    setDevRole("member");
    const { data } = await postsApi.create({ title: "Chỉ có tiêu đề", body: "" });
    expect(data.status).toBe("DRAFT");
    expect(data.body).toBe("");

    const caught = await postsApi.submit(data.id).then(
      () => null,
      (e: unknown) => e
    );
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(422);
    expect((caught as ApiError).code).toBe("VALIDATION_FAILED");
  });

  it("mock: người duyệt KHÔNG sửa được bài của người khác (khớp PATCH thật: chỉ tác giả)", async () => {
    setDevRole("branch-head");
    const { etag } = await postsApi.getById("post-2");
    const caught = await postsApi.update("post-2", { body: "Trưởng chi sửa" }, etag ?? "").then(
      () => null,
      (e: unknown) => e
    );
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(403);
  });
});

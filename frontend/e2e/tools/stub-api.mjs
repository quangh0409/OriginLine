// Máy chủ API giả tối giản, chỉ dùng để ĐO NFR-1 trên bản production.
// Không phải MSW: bản production tắt hẳn MSW (NODE_ENV === "production"),
// nên cần một endpoint thật để `next start` gọi tới.
import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const TREE = readFileSync(resolve(here, "tree-p-001.json"), "utf8");
const PORT = Number(process.env.STUB_PORT ?? 3200);

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "*",
  "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS",
  "access-control-expose-headers": "ETag",
};

createServer((req, res) => {
  if (req.method === "OPTIONS") {
    res.writeHead(204, CORS);
    return res.end();
  }
  const url = new URL(req.url ?? "/", `http://127.0.0.1:${PORT}`);
  const json = (body, status = 200) => {
    res.writeHead(status, { ...CORS, "content-type": "application/json" });
    res.end(typeof body === "string" ? body : JSON.stringify(body));
  };
  if (url.pathname === "/api/v1/tree") return json(TREE);
  if (url.pathname === "/api/v1/notifications")
    return json({ items: [], page: { page: 0, size: 20, totalElements: 0, totalPages: 1, hasNext: false }, unreadCount: 0 });
  if (url.pathname === "/api/v1/graphql") return json({ data: { branches: [] } });
  if (url.pathname.startsWith("/api/v1/persons")) return json({ items: [], page: { page: 0, size: 20, totalElements: 0, totalPages: 1, hasNext: false } });
  return json({ type: "about:blank", title: "not found", status: 404, code: "NOT_FOUND" }, 404);
}).listen(PORT, "127.0.0.1", () => console.log("stub api on", PORT));

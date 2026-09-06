/**
 * MSW không có JWT nào để đọc vai, nên handler đọc header `x-mock-role` thay
 * thế. Header này KHÔNG tồn tại trên backend thật và `src/lib/api/http.ts`
 * chỉ gắn nó khi `MOCKING_ENABLED`; ở bản chạy thật (F8) chỗ đó là
 * `Authorization: Bearer <JWT>` và vai đọc từ `realm_access.roles`.
 *
 * Default is "guest" specifically so the default dev experience matches the
 * legally required default: guests see no living person at all.
 */
export type MockRole = "guest" | "member" | "branch-head" | "admin";

export function resolveMockRole(request: Request): MockRole {
  const header = request.headers.get("x-mock-role");
  if (
    header === "member" ||
    header === "branch-head" ||
    header === "admin"
  ) {
    return header;
  }
  return "guest";
}

export function canSeeLivingPersons(role: MockRole): boolean {
  return role !== "guest";
}

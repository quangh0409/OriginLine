import { getMockGraph } from "./tree-graph/build-graph";
import { canSeeBirthYearMock } from "./privacy";
import { canSeeLivingPersons, type MockRole } from "./handlers/role";
import type { RawEdge, RawPerson } from "./tree-graph/generate-large-tree";
import type { PersonSummaryDto, RelationshipDto } from "@/types/api";

/**
 * `PersonDto.relationships` của bộ giả lập — **quan hệ trực tiếp một bậc**, kèm
 * `otherPerson`.
 *
 * <h2>Vì sao tệp này tồn tại</h2>
 *
 * Trước đây bộ giả lập **không gửi `relationships` bao giờ**, nên mục "Quan hệ"
 * trên hồ sơ không có lựa chọn nào ngoài việc gọi thêm một lượt `/tree` chỉ để
 * đổi một danh sách id lấy một danh sách tên. Đó là bộ giả lập đang giả lập một
 * API **nghèo hơn** API thật — và cái giá là một khung chờ trên màn hình mà lẽ
 * ra không cần tồn tại.
 *
 * <h2>Ba điều phải giữ đúng hình dạng của hợp đồng</h2>
 *
 * 1. **Một bậc, không phải cả cây.** Chỉ những cạnh chạm thẳng vào nhân khẩu
 *    này. Anh chị em là hai bậc nên **không** có mặt ở đây — thêm chúng vào cho
 *    tiện là dựng một API dễ hơn bản thật, và giao diện sẽ được viết cho một
 *    thế giới không tồn tại.
 * 2. **Đầu kia không hiển thị được thì bỏ CẢ CẠNH.** Không có khái niệm cạnh
 *    "đã che": `otherPerson` không bao giờ là một tóm tắt bị làm mờ. Vì vậy số
 *    cạnh bị bỏ không xuất hiện ở đâu cả — đếm ra là tiết lộ.
 * 3. **`otherPerson` là trường TUỲ CHỌN.** Hợp đồng để nó ngoài `required` vì
 *    lối ra không có khái niệm "hồ sơ đang xem" (GraphQL) không gửi nó. Bộ giả
 *    lập phải mô phỏng cả việc ấy, nếu không đường lùi của client sẽ không bao
 *    giờ được chạy thử — xem `HO_SO_KHONG_KEM_TOM_TAT`.
 *
 * Tóm tắt đi kèm cũng phải qua đúng bộ lọc của người gọi: năm sinh của người
 * còn sống chỉ ra khi chủ thể đã mở nhóm `birthDetailAndPhoto`
 * (`canSeeBirthYearMock`). Một tóm tắt "rộng rãi hơn" ở đây sẽ là một lối rò
 * vòng qua hồ sơ chính.
 */

/**
 * Các hồ sơ mà bộ giả lập **cố ý** trả `relationships` KHÔNG kèm `otherPerson`.
 *
 * Đây là cách duy nhất giữ cho đường lùi (quay về chiếu `/tree`) còn sống và
 * còn thử được bằng tay. Chọn `p-011` — Chi Nhị, nhiều con — vì hồ sơ ấy có đủ
 * quan hệ để nhìn ra ngay nếu đường lùi hỏng, và nó không nằm trong bất kỳ
 * khẳng định nào về `otherPerson` ở bộ kiểm.
 */
export const HO_SO_KHONG_KEM_TOM_TAT: ReadonlySet<string> = new Set(["p-011"]);

function summaryFor(person: RawPerson, role: MockRole): PersonSummaryDto {
  return {
    id: person.id,
    displayName: person.displayName,
    gender: person.gender,
    generation: person.generation,
    isAlive: person.isAlive,
    birthYear: canSeeBirthYearMock(
      {
        id: person.id,
        isAlive: person.isAlive,
        birthYear: person.birthYear ?? null,
        primaryBranch: person.primaryBranch ?? null,
      },
      role
    )
      ? (person.birthYear ?? undefined)
      : undefined,
    deathYear: person.deathYear ?? undefined,
    primaryBranch: person.primaryBranch ?? undefined,
    nativePlace: person.nativePlace ?? undefined,
  };
}

function toDto(edge: RawEdge, other: RawPerson, role: MockRole, personId: string): RelationshipDto {
  return {
    id: edge.id,
    fromPersonId: edge.source,
    toPersonId: edge.target,
    relType: edge.relType,
    heirKind: null,
    spouseOrder: edge.spouseOrder ?? null,
    validFrom: null,
    validTo: edge.validTo ?? null,
    note: null,
    ...(HO_SO_KHONG_KEM_TOM_TAT.has(personId)
      ? {}
      : { otherPerson: summaryFor(other, role) }),
  };
}

/**
 * Quan hệ một bậc của `personId`, đã lọc theo người gọi.
 *
 * Trả `undefined` khi nhân khẩu không có trong đồ thị (hồ sơ vừa tạo trong
 * phiên) — khác hẳn mảng rỗng, vốn có nghĩa "đã tra và người này thật sự không
 * còn quan hệ nào hiển thị được".
 */
export function relationshipsForMock(
  personId: string,
  role: MockRole
): RelationshipDto[] | undefined {
  const graph = getMockGraph();
  if (!graph.personsById.has(personId)) return undefined;

  const out: RelationshipDto[] = [];
  for (const edge of graph.edges) {
    if (edge.source !== personId && edge.target !== personId) continue;
    const otherId = edge.source === personId ? edge.target : edge.source;
    const other = graph.personsById.get(otherId);
    if (!other) continue;
    // Đầu kia bị lọc ⇒ cạnh biến mất hoàn toàn. Không đếm, không đánh dấu.
    if (other.isAlive && !canSeeLivingPersons(role)) continue;
    out.push(toDto(edge, other, role, personId));
  }
  return out;
}

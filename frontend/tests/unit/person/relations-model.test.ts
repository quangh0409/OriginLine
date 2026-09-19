import { describe, expect, it } from "vitest";
import {
  RELATION_GROUP_ORDER,
  buildRelationGroups,
  countRelations,
  hasEmbeddedOtherPersons,
  isEmptyRelationGroups,
  shouldShowSpouseOrder,
} from "@/components/person/relations-model";
import type {
  Gender,
  RelType,
  RelationshipDto,
  TreeEdge,
  TreeNode,
  TreeProjection,
} from "@/types/api";

/**
 * Nhóm quan hệ cho hồ sơ nhân khẩu — kiểm thử thuần, không React.
 *
 * Hai bất biến quan trọng nhất ở đây:
 *  - đầu kia không có trong `nodes` (đã bị lọc phân tầng) thì KHÔNG sinh ra
 *    dòng nào, cũng không sinh ra con số nào;
 *  - không hàm nào trong mô hình này sinh ra danh xưng (anh/chị/em/bác/chú).
 */

let seq = 0;

function node(
  id: string,
  overrides: Partial<TreeNode["person"]> & { badges?: TreeNode["badges"] } = {}
): TreeNode {
  const { badges, ...person } = overrides;
  return {
    id,
    person: {
      id,
      displayName: person.displayName ?? id,
      isAlive: person.isAlive ?? false,
      gender: person.gender,
      generation: person.generation,
      birthYear: person.birthYear,
      primaryBranch: person.primaryBranch,
    },
    depth: 0,
    parentIds: [],
    spouseIds: [],
    hasMoreDescendants: false,
    badges: badges ?? [],
  };
}

function edge(
  source: string,
  target: string,
  relType: RelType,
  extra: Partial<TreeEdge> = {}
): TreeEdge {
  seq += 1;
  return { id: `e-${seq}`, source, target, relType, ...extra };
}

function projection(nodes: TreeNode[], edges: TreeEdge[]): TreeProjection {
  return {
    rootId: nodes[0]?.id ?? "",
    nodes,
    edges,
    meta: {
      depth: 2,
      direction: "BOTH",
      nodeCount: nodes.length,
      edgeCount: edges.length,
      truncated: false,
    },
  };
}

const male: Gender = "MALE";
const female: Gender = "FEMALE";

describe("buildRelationGroups — bốn nhóm gia phả", () => {
  it("tách cha mẹ, vợ/chồng, con và anh chị em khỏi cùng một tập cạnh", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [
          node("me", { gender: male }),
          node("cha", { gender: male }),
          node("me-ruot", { gender: female }),
          node("vo", { gender: female }),
          node("con", { gender: male, birthYear: 1990 }),
          node("em", { gender: female, birthYear: 1975 }),
        ],
        [
          edge("cha", "me", "PARENT_BIO"),
          edge("me-ruot", "me", "PARENT_BIO"),
          edge("me", "vo", "SPOUSE", { spouseOrder: 1 }),
          edge("me", "con", "PARENT_BIO"),
          edge("cha", "em", "PARENT_BIO"),
        ]
      ),
    });

    expect(groups.parents.map((e) => e.person.id)).toEqual(["cha", "me-ruot"]);
    expect(groups.spouses.map((e) => e.person.id)).toEqual(["vo"]);
    expect(groups.children.map((e) => e.person.id)).toEqual(["con"]);
    expect(groups.siblings.map((e) => e.person.id)).toEqual(["em"]);
    expect(countRelations(groups)).toBe(5);
  });

  it("không bao giờ xếp chính mình vào nhóm anh chị em", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("cha"), node("anh")],
        [edge("cha", "me", "PARENT_BIO"), edge("cha", "anh", "PARENT_BIO")]
      ),
    });

    expect(groups.siblings.map((e) => e.person.id)).toEqual(["anh"]);
  });

  it("gộp anh chị em chung cả cha lẫn mẹ thành MỘT dòng, không nhân đôi", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("cha"), node("mother"), node("chi")],
        [
          edge("cha", "me", "PARENT_BIO"),
          edge("mother", "me", "PARENT_BIO"),
          edge("cha", "chi", "PARENT_BIO"),
          edge("mother", "chi", "PARENT_BIO"),
        ]
      ),
    });

    expect(groups.siblings).toHaveLength(1);
  });

  it("giữ anh chị em cùng cha khác mẹ — gia phả Việt ghi nhận họ đầy đủ", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("cha"), node("me-ca"), node("me-hai"), node("em-khac-me")],
        [
          edge("cha", "me", "PARENT_BIO"),
          edge("me-ca", "me", "PARENT_BIO"),
          edge("cha", "em-khac-me", "PARENT_BIO"),
          edge("me-hai", "em-khac-me", "PARENT_BIO"),
        ]
      ),
    });

    expect(groups.siblings.map((e) => e.person.id)).toEqual(["em-khac-me"]);
  });

  it("xếp con theo thứ tự sinh khi biết, người thiếu năm sinh xuống cuối", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [
          node("me"),
          node("con-ut", { birthYear: 1995 }),
          node("con-ca", { birthYear: 1980 }),
          node("con-khong-ro"),
        ],
        [
          edge("me", "con-ut", "PARENT_BIO"),
          edge("me", "con-ca", "PARENT_BIO"),
          edge("me", "con-khong-ro", "PARENT_BIO"),
        ]
      ),
    });

    expect(groups.children.map((e) => e.person.id)).toEqual([
      "con-ca",
      "con-ut",
      "con-khong-ro",
    ]);
  });
});

describe("buildRelationGroups — sắc thái nằm trên cạnh", () => {
  it("đánh dấu con nuôi qua cạnh PARENT_ADOPT, không phải qua cờ trên cạnh ruột", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("con-ruot"), node("con-nuoi")],
        [
          edge("me", "con-ruot", "PARENT_BIO"),
          edge("me", "con-nuoi", "PARENT_ADOPT"),
        ]
      ),
    });

    const byId = Object.fromEntries(groups.children.map((e) => [e.person.id, e]));
    expect(byId["con-ruot"]?.adoptive).toBe(false);
    expect(byId["con-nuoi"]?.adoptive).toBe(true);
  });

  it("giữ thứ tự vợ cho đa thê và xếp vợ cả lên trước", () => {
    const groups = buildRelationGroups({
      personId: "ong",
      projection: projection(
        [node("ong", { gender: male }), node("vo-hai", { gender: female }), node("vo-ca", { gender: female })],
        [
          edge("ong", "vo-hai", "SPOUSE", { spouseOrder: 2 }),
          edge("ong", "vo-ca", "SPOUSE", { spouseOrder: 1 }),
        ]
      ),
    });

    expect(groups.spouses.map((e) => e.person.id)).toEqual(["vo-ca", "vo-hai"]);
    expect(groups.spouses.map((e) => e.spouseOrder)).toEqual([1, 2]);
  });

  it("nhận cạnh SPOUSE theo cả hai chiều — hôn phối không có chiều về ý nghĩa", () => {
    const groups = buildRelationGroups({
      personId: "ba",
      projection: projection(
        [node("ba", { gender: female }), node("ong", { gender: male })],
        [edge("ong", "ba", "SPOUSE", { spouseOrder: 1 })]
      ),
    });

    expect(groups.spouses.map((e) => e.person.id)).toEqual(["ong"]);
  });

  it("đánh dấu hôn phối đã kết thúc khi cạnh có validTo, và giữ nguyên ngày", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("vo-truoc", { gender: female })],
        [edge("me", "vo-truoc", "SPOUSE", { spouseOrder: 1, validTo: "1998-04-02" })]
      ),
    });

    expect(groups.spouses[0]?.ended).toBe(true);
    expect(groups.spouses[0]?.endedOn).toBe("1998-04-02");
  });

  it("hôn phối còn hiệu lực thì không bị đánh dấu kết thúc", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("vo", { gender: female })],
        [edge("me", "vo", "SPOUSE", { spouseOrder: 1, validTo: null })]
      ),
    });

    expect(groups.spouses[0]?.ended).toBe(false);
  });

  it("gắn heirKind vào đúng dòng con khi cạnh HEIR trỏ tới người đã có trong nhóm", () => {
    const groups = buildRelationGroups({
      personId: "cu",
      projection: projection(
        [node("cu"), node("chau", { displayName: "Đích tôn" })],
        [
          edge("cu", "chau", "PARENT_BIO"),
          edge("cu", "chau", "HEIR", { heirKind: "DICH_TON" }),
        ]
      ),
    });

    expect(groups.children[0]?.heirKind).toBe("DICH_TON");
    expect(groups.heirs).toHaveLength(0);
  });

  it("người kế tự không có cạnh cha–con vẫn có chỗ đứng riêng, không biến mất", () => {
    // Kế tự lấy từ chi khác là ca chính đáng của FR-1.2: không có cạnh
    // PARENT_*, nên nếu chỉ dựa vào bốn nhóm quen thuộc thì người này mất hút.
    const groups = buildRelationGroups({
      personId: "tuyet-tu",
      projection: projection(
        [node("tuyet-tu"), node("ke-tu", { displayName: "Người kế tự" })],
        [edge("tuyet-tu", "ke-tu", "HEIR", { heirKind: "KE_TU" })]
      ),
    });

    expect(groups.heirs.map((e) => e.person.id)).toEqual(["ke-tu"]);
    expect(groups.heirs[0]?.heirKind).toBe("KE_TU");
  });
});

describe("buildRelationGroups — bù thuộc tính từ PersonDto.relationships", () => {
  it("lấy heirKind và validTo từ relationships khi cạnh /tree không mang", () => {
    const spouseEdge = edge("me", "vo", "SPOUSE");
    const relationships: RelationshipDto[] = [
      {
        id: spouseEdge.id,
        fromPersonId: "me",
        toPersonId: "vo",
        relType: "SPOUSE",
        spouseOrder: 2,
        validTo: "2001-01-01",
      },
    ];

    const groups = buildRelationGroups({
      personId: "me",
      projection: projection([node("me"), node("vo", { gender: female })], [spouseEdge]),
      relationships,
    });

    expect(groups.spouses[0]?.spouseOrder).toBe(2);
    expect(groups.spouses[0]?.ended).toBe(true);
  });

  it("khớp được cả khi id cạnh hai bên khác nhau, nhờ cặp đầu–cuối", () => {
    const relationships: RelationshipDto[] = [
      {
        id: "id-khac-hoan-toan",
        fromPersonId: "me",
        toPersonId: "con",
        relType: "PARENT_BIO",
        note: "con riêng của vợ",
      },
    ];

    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("con")],
        [edge("me", "con", "PARENT_BIO")]
      ),
      relationships,
    });

    expect(groups.children).toHaveLength(1);
  });
});

describe("buildRelationGroups — ranh giới phân tầng riêng tư", () => {
  it("cạnh trỏ tới người bị lọc không sinh ra dòng nào", () => {
    // Máy chủ chỉ trả cạnh khi CẢ HAI đầu được phép hiển thị. Ở đây mô phỏng
    // trường hợp một chiếu cây còn sót cạnh mà thiếu nút: kết quả phải là im
    // lặng tuyệt đối, không phải một dòng "không rõ".
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("con-hien")],
        [
          edge("me", "con-hien", "PARENT_BIO"),
          edge("me", "con-bi-loc", "PARENT_BIO"),
        ]
      ),
    });

    expect(groups.children.map((e) => e.person.id)).toEqual(["con-hien"]);
    expect(countRelations(groups)).toBe(1);
    expect(JSON.stringify(groups)).not.toContain("con-bi-loc");
  });

  it("không có chiếu cây thì trả nhóm rỗng chứ không ném lỗi", () => {
    const groups = buildRelationGroups({ personId: "me", projection: undefined });

    expect(isEmptyRelationGroups(groups)).toBe(true);
    expect(RELATION_GROUP_ORDER.every((k) => groups[k].length === 0)).toBe(true);
  });
});

describe("shouldShowSpouseOrder", () => {
  const entry = (spouseOrder: number | null) => ({
    person: { id: "x", displayName: "x", isAlive: false },
    badges: [],
    adoptive: false,
    spouseOrder,
    ended: false,
    endedOn: null,
    heirKind: null,
  });

  it("im lặng với một vợ/chồng duy nhất mang thứ tự 1 — nhãn 'vợ cả' khi đó gợi ý sai là có người thứ hai", () => {
    expect(shouldShowSpouseOrder(entry(1), 1)).toBe(false);
  });

  it("hiện khi thực sự đa thê/đa phu", () => {
    expect(shouldShowSpouseOrder(entry(1), 2)).toBe(true);
    expect(shouldShowSpouseOrder(entry(2), 2)).toBe(true);
  });

  it("hiện khi bản thân thứ tự đã từ 2 trở lên, dù chỉ thấy được một cạnh", () => {
    // Vợ cả có thể đã bị lọc khỏi phản hồi; thứ tự 2 vẫn phải hiện đúng.
    expect(shouldShowSpouseOrder(entry(2), 1)).toBe(true);
  });

  it("im lặng khi máy chủ không ghi thứ tự", () => {
    expect(shouldShowSpouseOrder(entry(null), 3)).toBe(false);
  });
});


/**
 * `RelationshipDto.otherPerson` — đầu kia đi kèm ngay trong phản hồi hồ sơ.
 *
 * Trước trường này, mục "Quan hệ" phải chờ trọn một lượt `/tree` chỉ để đổi
 * một danh sách id lấy một danh sách tên. Bộ kiểm dưới đây ghim ba điều mà
 * hợp đồng nói rõ và rất dễ dựng sai:
 *
 *  - trường **không nằm trong `required`** → phải có đường lùi;
 *  - **không bao giờ là một tóm tắt "đã che"** → không có giao diện cho
 *    trạng thái "có quan hệ nhưng không rõ với ai";
 *  - nó **không mang `badges`** → khi chiếu `/tree` cũng có mặt thì bản của
 *    chiếu phải thắng, nếu không nhãn dâu/rể biến mất khỏi hồ sơ.
 */
function rel(
  from: string,
  to: string,
  relType: RelType,
  extra: Partial<RelationshipDto> = {}
): RelationshipDto {
  seq += 1;
  return { id: `r-${seq}`, fromPersonId: from, toPersonId: to, relType, ...extra };
}

function summary(id: string, overrides: Partial<RelationshipDto["otherPerson"] & object> = {}) {
  return {
    id,
    displayName: overrides.displayName ?? id,
    isAlive: overrides.isAlive ?? false,
    gender: overrides.gender,
    generation: overrides.generation,
    birthYear: overrides.birthYear,
    primaryBranch: overrides.primaryBranch,
  };
}

describe("buildRelationGroups — dựng thẳng từ `otherPerson`, không cần chiếu `/tree`", () => {
  it("vẽ đủ bốn nhóm trực tiếp mà không có chiếu nào", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: undefined,
      relationships: [
        rel("cha", "me", "PARENT_BIO", { otherPerson: summary("cha", { displayName: "Ông Cha" }) }),
        rel("me", "vo", "SPOUSE", { spouseOrder: 1, otherPerson: summary("vo") }),
        rel("me", "con", "PARENT_BIO", { otherPerson: summary("con") }),
        rel("me", "thua-tu", "HEIR", { heirKind: "THUA_TU", otherPerson: summary("thua-tu") }),
      ],
    });

    expect(groups.parents.map((e) => e.person.displayName)).toEqual(["Ông Cha"]);
    expect(groups.spouses.map((e) => e.person.id)).toEqual(["vo"]);
    expect(groups.children.map((e) => e.person.id)).toEqual(["con"]);
    expect(groups.heirs.map((e) => e.heirKind)).toEqual(["THUA_TU"]);
  });

  it("KHÔNG dựng anh chị em từ dữ liệu một bậc — đó là quan hệ hai bậc", () => {
    // Hợp đồng: `relationships` là "quan hệ trực tiếp một bậc, không phải cả
    // cây". Nếu ca này bắt đầu xanh với một nhóm anh chị em khác rỗng thì ai đó
    // đã suy anh chị em ở client từ dữ liệu không đủ để suy.
    const groups = buildRelationGroups({
      personId: "me",
      projection: undefined,
      relationships: [rel("cha", "me", "PARENT_BIO", { otherPerson: summary("cha") })],
    });

    expect(groups.siblings).toEqual([]);
  });

  it("cạnh thiếu `otherPerson` và không có chiếu thì im lặng biến mất", () => {
    // Không phải một dòng "không rõ": một dòng như thế là lời thông báo "có
    // người ở đây mà bạn không được biết là ai".
    const groups = buildRelationGroups({
      personId: "me",
      projection: undefined,
      relationships: [rel("cha", "me", "PARENT_BIO")],
    });

    expect(isEmptyRelationGroups(groups)).toBe(true);
  });
});

describe("hai nguồn gặp nhau — chiếu `/tree` thắng vì chỉ nó mang `badges`", () => {
  it("giữ nhãn của chiếu, không để tóm tắt nhúng xoá mất nhãn dâu", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("vo", { gender: female, badges: ["DAU"] })],
        [edge("me", "vo", "SPOUSE", { spouseOrder: 1 })]
      ),
      relationships: [rel("me", "vo", "SPOUSE", { spouseOrder: 1, otherPerson: summary("vo") })],
    });

    expect(groups.spouses).toHaveLength(1);
    expect(groups.spouses[0]!.badges).toEqual(["DAU"]);
  });

  it("một cạnh có mặt ở cả hai nguồn chỉ sinh ra MỘT dòng", () => {
    // Hai transport không hứa dùng chung `id` cạnh, nên phép khử trùng phải
    // bắt được cả trường hợp id khác nhau mà hai đầu giống nhau.
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection(
        [node("me"), node("con")],
        [{ id: "edge-tu-tree", source: "me", target: "con", relType: "PARENT_BIO" }]
      ),
      relationships: [
        {
          id: "rel-tu-ho-so",
          fromPersonId: "me",
          toPersonId: "con",
          relType: "PARENT_BIO",
          otherPerson: summary("con"),
        },
      ],
    });

    expect(groups.children.map((e) => e.person.id)).toEqual(["con"]);
  });

  it("người chỉ có trong tóm tắt nhúng vẫn vẽ được, chỉ là không có nhãn", () => {
    const groups = buildRelationGroups({
      personId: "me",
      projection: projection([node("me")], []),
      relationships: [rel("me", "con", "PARENT_BIO", { otherPerson: summary("con") })],
    });

    expect(groups.children.map((e) => e.person.id)).toEqual(["con"]);
    expect(groups.children[0]!.badges).toEqual([]);
  });
});

describe("hasEmbeddedOtherPersons — quyết định có cần chờ `/tree` hay không", () => {
  it("đủ cả thì không cần chờ", () => {
    expect(
      hasEmbeddedOtherPersons([
        rel("a", "me", "PARENT_BIO", { otherPerson: summary("a") }),
        rel("me", "b", "PARENT_BIO", { otherPerson: summary("b") }),
      ])
    ).toBe(true);
  });

  it("thiếu MỘT cạnh là đủ để quay về đường cũ", () => {
    // Vẽ thiếu một người cha là lỗi dữ liệu nhìn thấy được; chờ thêm một lượt
    // mạng thì chỉ là chậm. Giữa hai cái đó thì chọn chậm.
    expect(
      hasEmbeddedOtherPersons([
        rel("a", "me", "PARENT_BIO", { otherPerson: summary("a") }),
        rel("me", "b", "PARENT_BIO"),
      ])
    ).toBe(false);
  });

  it("không có khoá `relationships` (GraphQL) hoặc danh sách rỗng thì không coi là có", () => {
    expect(hasEmbeddedOtherPersons(undefined)).toBe(false);
    expect(hasEmbeddedOtherPersons(null)).toBe(false);
    expect(hasEmbeddedOtherPersons([])).toBe(false);
  });
});

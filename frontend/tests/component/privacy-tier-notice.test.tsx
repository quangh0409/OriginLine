import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, relative, resolve, sep } from "node:path";
import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import {
  renderWithProviders,
  FORBIDDEN_PLACEHOLDER_PATTERNS,
} from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import viMessages from "../../messages/vi.json";
import enMessages from "../../messages/en.json";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-100",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-100",
}));

const { PersonProfile } = await import("@/components/person/person-profile");
const { PrivacyTierNotice } = await import("@/components/privacy/privacy-tier-notice");

/**
 * `meta.visibleTier` được máy chủ gửi kèm MỌI phản hồi hồ sơ và trước đây
 * không nơi nào trong `src/` đọc nó, nên người dùng không có cách nào phân
 * biệt "chưa ai ghi" với "bạn không được xem".
 *
 * Bộ kiểm thử này canh đúng hai bờ vực của việc vá lỗi đó:
 *  - nói quá ít → người dùng tưởng gia phả thiếu dữ liệu;
 *  - nói quá nhiều → để lộ chính thứ đang giấu.
 *
 * Bất biến cốt lõi được kiểm ở đây: **câu chữ chỉ phụ thuộc vào người gọi và
 * vào việc người này còn sống hay đã khuất, tuyệt đối không phụ thuộc dữ liệu
 * của hồ sơ**. Hai hồ sơ khác nhau ở cùng một tầng phải đọc ra y hệt nhau.
 */

type Role = "guest" | "member" | "branch-head" | "admin";

async function renderProfile(personId: string, role: Role, locale: "vi" | "en" = "vi") {
  const view = renderWithProviders(<PersonProfile personId={personId} />, { role, locale });
  await waitFor(
    () => expect(view.container.querySelector(".ant-skeleton")).toBeNull(),
    { timeout: 5000 }
  );
  return view;
}

function tierNotice(container: HTMLElement): HTMLElement | null {
  return container.querySelector('[data-privacy-notice="tier"]');
}

describe("người đã khuất — không có cảnh báo thừa", () => {
  it.each(["guest", "member", "branch-head", "admin"] as const)(
    "hồ sơ tổ tiên không hiện chú thích phân tầng nào với vai %s",
    async (role) => {
      const { container } = await renderProfile("p-001", role);

      await screen.findByRole("heading", { name: /Nguyễn Văn Thủy Tổ/ });
      // Người đã khuất là công khai. Một dòng cảnh báo ở đây chỉ là nhiễu, và
      // nhiễu làm loãng đúng cảnh báo ở chỗ thật sự cần.
      expect(tierNotice(container)).toBeNull();
    }
  );

  it("kể cả khi máy chủ trả một tầng thấp cho người đã khuất, vẫn im lặng", () => {
    // Phòng thủ cho dữ liệu lệch: `isAlive = false` là điều kiện đủ để im lặng.
    const { container } = renderWithProviders(
      <PrivacyTierNotice isAlive={false} meta={{ visibleTier: "T1" }} />
    );

    expect(tierNotice(container)).toBeNull();
    expect(container.textContent).toBe("");
  });
});

describe("người còn sống — giải thích được, không tiết lộ", () => {
  it("thành viên thấy vì sao hồ sơ chỉ có tên, đời và quan hệ", async () => {
    const { container } = await renderProfile("p-100", "member");

    const note = tierNotice(container);
    expect(note).not.toBeNull();
    expect(note?.textContent).toContain("người còn sống");
    expect(note?.textContent).toContain("quan hệ trực tiếp");
  });

  it("câu của tầng cao hơn nói đúng những gì tầng ấy cho xem", () => {
    // Dựng thẳng thành phần với `visibleTier: "T2"` thay vì đi qua một hồ sơ
    // cụ thể. Từ khi `PrivacySettings` vào contract, tầng là thứ SUY RA từ lựa
    // chọn của chủ thể, nên "cho tôi một hồ sơ T2" không còn là chuyện chọn
    // đúng vai — và một ca kiểm về CÂU CHỮ thì không nên phụ thuộc vào việc
    // hôm nay ai đang mở nhóm nào.
    const { container } = renderWithProviders(
      <PrivacyTierNotice isAlive meta={{ visibleTier: "T2" }} />
    );

    expect(tierNotice(container)?.textContent).toContain("năm sinh, nghề nghiệp");
  });

  it("quản trị viên xem trọn hồ sơ thì không còn gì để giải thích", async () => {
    const { container } = await renderProfile("p-100", "admin");

    expect(tierNotice(container)).toBeNull();
  });

  it("khách không tới được đây: hồ sơ người còn sống trả 404, không có chú thích nào", async () => {
    const { container } = await renderProfile("p-100", "guest");

    expect(await screen.findByText("Không tìm thấy nhân khẩu này.")).toBeInTheDocument();
    expect(tierNotice(container)).toBeNull();
  });
});

describe("chú thích không rò rỉ gì về chính hồ sơ", () => {
  it("không chứa con số nào — 'ẩn 3 số điện thoại' đã tiết lộ rằng có đúng 3 số", async () => {
    const { container } = await renderProfile("p-100", "member");

    expect(tierNotice(container)?.textContent ?? "").not.toMatch(/\d/);
  });

  it("không nhắc lại bất kỳ giá trị nào đang bị giữ lại", async () => {
    const { container } = await renderProfile("p-100", "member");
    const text = tierNotice(container)?.textContent ?? "";

    for (const secret of ["+84 912 345 678", "an.nguyen@example.com", "Kỹ sư phần mềm", "Hà Nội"]) {
      expect(text).not.toContain(secret);
    }
  });

  it("hai người còn sống khác nhau ở cùng một tầng đọc ra CÙNG một câu", async () => {
    // Đây là bất biến quan trọng nhất: nếu câu chữ đổi theo hồ sơ thì tự nó
    // đã thành một kênh rò rỉ — người đọc suy ra được hồ sơ nào có gì.
    const adult = await renderProfile("p-100", "member");
    const adultText = tierNotice(adult.container)?.textContent;
    adult.unmount();

    const minor = await renderProfile("p-101", "member");
    const minorText = tierNotice(minor.container)?.textContent;

    expect(adultText).toBeTruthy();
    expect(minorText).toBe(adultText);
  });

  it("không tiết lộ mức chia sẻ do chính chủ đặt", async () => {
    const { container } = await renderProfile("p-101", "member");
    const text = tierNotice(container)?.textContent ?? "";

    expect(text).not.toMatch(/RESTRICTED/i);
    expect(text).not.toMatch(/hạn chế/i);
  });
});

describe("song ngữ", () => {
  it("hiển thị bản tiếng Anh khi giao diện đang ở tiếng Anh", async () => {
    const { container } = await renderProfile("p-100", "member", "en");

    const text = tierNotice(container)?.textContent ?? "";
    expect(text).toContain("living person");
    expect(text).not.toContain("MISSING_MESSAGE");
  });

  it("có đủ khoá ở cả hai ngôn ngữ", () => {
    expect(Object.keys(enMessages.privacy).sort()).toEqual(
      Object.keys(viMessages.privacy).sort()
    );
    const viRelation = Object.keys(viMessages.person).filter((k) => k.startsWith("relation"));
    const enRelation = Object.keys(enMessages.person).filter((k) => k.startsWith("relation"));
    expect(enRelation.sort()).toEqual(viRelation.sort());
  });
});

/**
 * PHẠM VI CỦA BẤT BIẾN DƯỚI ĐÂY — đọc trước khi sửa.
 *
 * Bất biến "không số, không chỗ chèn, không từ ngữ của một ô trống bị che" nói
 * về **câu mà hệ thống nói với NGƯỜI KHÁC về một người**. Trước đây nó được cài
 * đặt bằng một phép xấp xỉ tiện tay: quét toàn bộ nhánh `privacy.*`. Phép xấp xỉ
 * ấy đúng chừng nào nhánh `privacy.*` chỉ chứa chú thích phân tầng.
 *
 * Nó thôi đúng khi nhánh ấy nhận thêm màn "chính chủ tự đặt mức chia sẻ", vì
 * màn đó nói với **CHÍNH CHỦ về dữ liệu của chính họ** — một tình huống mà bất
 * biến trên không những không áp dụng được mà còn tự mâu thuẫn: ba mức chia sẻ
 * bắt buộc phải có tên, và tên mức thứ ba là "Riêng tư" / "Private", khớp thẳng
 * vào `FORBIDDEN_PLACEHOLDER_PATTERNS`. Không có cách viết nào vòng qua được —
 * không đặt tên được cho mức thì không có tính năng.
 *
 * Vì vậy phạm vi được nói rõ ra thay vì xấp xỉ, và nó **không lỏng hơn**:
 * tập chuỗi bị quét nay lấy từ chính mã nguồn của các thành phần nói-với-người-
 * khác, nên một câu mới thêm vào đó không thể lọt, kể cả khi người viết đặt nó
 * ở một nhánh thông điệp khác — điều mà phép quét theo nhánh cũ sẽ bỏ sót.
 */
/**
 * Các tệp chỉ hiện với CHÍNH CHỦ, nên nằm ngoài bất biến trên.
 *
 * Cả ba đều nằm sau đúng MỘT cổng: `PrivacySharingCard` trả `null` khi
 * `person.meta.isSelf !== true`. Cổng ấy được ghim bằng ca kiểm ngay dưới đây —
 * nếu ai đó gỡ nó, danh sách miễn trừ này lập tức mất hiệu lực bằng một ca đỏ,
 * chứ không im lặng trở thành một lỗ hổng.
 */
const THANH_PHAN_CHI_CHINH_CHU = [
  "src/components/person/privacy-sharing-card.tsx",
  "src/components/person/privacy-group-row.tsx",
  "src/components/person/privacy-audience-preview.tsx",
] as const;

/**
 * Danh sách này được <b>suy ra từ mã nguồn</b>, không ghi cứng.
 *
 * Ghi cứng hai tệp thì bất biến chặt hơn phép quét theo nhánh ở một trục (bắt được cả chuỗi
 * đặt ở nhánh thông điệp khác) nhưng <b>lỏng hơn ở trục quan trọng hơn</b>: thêm một thành
 * phần nói-với-người-khác thứ ba là nó lọt êm, không ai biết. Phép quét theo nhánh cũ không
 * có lỗ ấy — nó bắt mọi chuỗi `privacy.*` bất kể tệp nào dùng.
 *
 * Nên phạm vi mới phải tự tìm: mọi tệp trong `src/` có gắn `useTranslations("privacy")`,
 * trừ đúng những tệp chỉ hiện với chính chủ. Thêm thành phần mới thì nó tự vào tầm quét;
 * muốn đứng ngoài thì phải khai tên vào danh sách miễn trừ — tức là một hành động cố ý,
 * đọc được trong diff, chứ không phải một sự im lặng.
 */
function quetThanhPhanDungNhanhPrivacy(): string[] {
  const goc = resolve(__dirname, "../..", "src");
  const ketQua: string[] = [];
  const duyet = (thuMuc: string) => {
    for (const muc of readdirSync(thuMuc, { withFileTypes: true })) {
      const duongDan = join(thuMuc, muc.name);
      if (muc.isDirectory()) duyet(duongDan);
      else if (muc.name.endsWith(".tsx") || muc.name.endsWith(".ts")) {
        if (/useTranslations\(\s*"privacy"\s*\)/.test(readFileSync(duongDan, "utf8"))) {
          ketQua.push(relative(resolve(__dirname, "../.."), duongDan).split(sep).join("/"));
        }
      }
    }
  };
  duyet(goc);
  return ketQua.sort();
}

const THANH_PHAN_NOI_VOI_NGUOI_KHAC = quetThanhPhanDungNhanhPrivacy().filter(
  (tep) => !(THANH_PHAN_CHI_CHINH_CHU as readonly string[]).includes(tep)
);


describe("câu chữ mới không dùng từ ngữ của một chỗ trống bị che", () => {
  function flatten(value: unknown, out: string[] = []): string[] {
    if (typeof value === "string") out.push(value);
    else if (value && typeof value === "object") {
      for (const nested of Object.values(value as Record<string, unknown>)) flatten(nested, out);
    }
    return out;
  }

  /**
   * Mọi khoá thuộc nhánh `privacy` mà một thành phần THẬT SỰ gọi.
   *
   * Lấy tên biến từ chính dòng `useTranslations("privacy")` rồi mới tìm các
   * lời gọi của biến ấy. Bám cứng vào `t(` sẽ bỏ sót `person-relations.tsx`,
   * nơi biến tên là `tPrivacy` vì tệp ấy dùng hai nhánh thông điệp cùng lúc —
   * đúng kiểu bỏ sót âm thầm mà một phép quét theo chuỗi cố định hay mắc.
   */
  function khoaDuocDung(relativePath: string): string[] {
    const source = readFileSync(resolve(__dirname, "../..", relativePath), "utf8");
    const binders = [
      ...source.matchAll(/const\s+(\w+)\s*=\s*useTranslations\(\s*"privacy"\s*\)/g),
    ].map((m) => m[1]!);
    const keys: string[] = [];
    for (const binder of binders) {
      const calls = new RegExp(`\\b${binder}\\(\\s*"([^"$]+)"`, "g");
      for (const call of source.matchAll(calls)) keys.push(call[1]!);
    }
    return keys;
  }

  function tra(messages: Record<string, unknown>, key: string): unknown {
    return key.split(".").reduce<unknown>(
      (node, part) => (node as Record<string, unknown> | undefined)?.[part],
      messages
    );
  }

  const khoaNoiVoiNguoiKhac = [
    ...new Set(THANH_PHAN_NOI_VOI_NGUOI_KHAC.flatMap(khoaDuocDung)),
  ].filter((key) => tra(viMessages.privacy as Record<string, unknown>, key) !== undefined);

  it("phép quét bám được vào mã nguồn — danh sách khoá không bao giờ rỗng", () => {
    // Chống xanh giả: nếu một lần refactor làm biểu thức trích khoá không khớp
    // nữa, tập chuỗi sẽ rỗng và mọi khẳng định bên dưới xanh mà không kiểm gì.
    expect(khoaNoiVoiNguoiKhac).toEqual(
      expect.arrayContaining(["noticeLabel", "livingT1", "livingT2", "guestRelations"])
    );
  });

  it("phép quét tự tìm ra hai thành phần nói-với-người-khác đã biết", () => {
    // Chốt thứ hai, ở tầng TỆP chứ không phải tầng khoá: nếu phép duyệt thư mục
    // hỏng, danh sách tệp rỗng đi mà danh sách khoá vẫn có thể xanh nhờ bộ nhớ
    // đệm hay một thay đổi vô tình. Hai chốt cùng đỏ thì mới yên tâm.
    expect(THANH_PHAN_NOI_VOI_NGUOI_KHAC).toEqual(
      expect.arrayContaining([
        "src/components/privacy/privacy-tier-notice.tsx",
        "src/components/person/person-relations.tsx",
      ])
    );
  });

  it("mọi tệp chỉ-chính-chủ được miễn trừ đều thật sự tồn tại", () => {
    // Một tệp miễn trừ bị đổi tên hay xoá sẽ khiến phép lọc không trừ được gì,
    // và tệp thay thế nó âm thầm rơi vào tầm quét — hoặc ngược lại, một dòng
    // miễn trừ chết nằm lại che cho một tệp tương lai trùng tên.
    for (const tep of THANH_PHAN_CHI_CHINH_CHU) {
      expect(
        existsSync(resolve(__dirname, "../..", tep)),
        `tep mien tru khong ton tai: ${tep}`
      ).toBe(true);
    }
  });

  it("khối tự đặt mức chia sẻ thật sự chỉ hiện với chính chủ", () => {
    // Đây là cổng duy nhất bảo vệ ba tệp được miễn trừ ở trên.
    const card = readFileSync(
      resolve(__dirname, "../..", THANH_PHAN_CHI_CHINH_CHU[0]),
      "utf8"
    );
    // Hai mảnh của cùng một cổng: điều kiện, và lối thoát sớm.
    expect(card).toMatch(/meta\.isSelf === true/);
    expect(card).toMatch(/if \(!canEdit[^)]*\) return null;/);
  });

  const strings = [
    ...khoaNoiVoiNguoiKhac.map((key) => tra(viMessages.privacy as Record<string, unknown>, key)),
    ...khoaNoiVoiNguoiKhac.map((key) => tra(enMessages.privacy as Record<string, unknown>, key)),
  ].flatMap((value) => flatten(value));

  const stringsWithRelations = [
    ...strings,
    ...flatten(
      Object.fromEntries(
        Object.entries(viMessages.person).filter(([k]) => k.startsWith("relation"))
      )
    ),
    ...flatten(
      Object.fromEntries(
        Object.entries(enMessages.person).filter(([k]) => k.startsWith("relation"))
      )
    ),
  ];

  it("không câu nào trùng với các mẫu 'có dữ liệu bị che ở đây'", () => {
    // Chú thích ở cấp trang được phép nói về CHÍNH SÁCH; nó vẫn không được
    // mượn từ ngữ của một ô trống đánh dấu "chỗ này có dữ liệu bạn không xem
    // được", vì đó mới là kiểu rò rỉ theo từng trường.
    for (const text of stringsWithRelations) {
      for (const pattern of FORBIDDEN_PLACEHOLDER_PATTERNS) {
        expect(pattern.test(text), `"${text}" khớp mẫu cấm ${pattern}`).toBe(false);
      }
    }
  });

  it("không câu nào có chỗ chèn số lượng", () => {
    // `{count}`, `{n}` trong câu về quyền riêng tư là con đường ngắn nhất tới
    // "ẩn 3 trường" — trừ `{n}` của thứ tự vợ và `{name}` của liên kết.
    for (const text of strings) {
      expect(text).not.toMatch(/\{\s*\w+\s*\}/);
      expect(text).not.toMatch(/\d/);
    }
  });
});

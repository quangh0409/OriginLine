import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { FRONTEND_ROOT } from "../a11y/source-scan";
import viMessages from "../../../messages/vi.json";
import enMessages from "../../../messages/en.json";

/**
 * HAI MÀN ĐẶT MẬT KHẨU PHẢI GIỐNG NHAU — và tệp này là thứ giữ cho chúng giống.
 *
 * <h2>Vì sao đây là một bất biến chứ không phải một sở thích</h2>
 * Một người được mời gặp <b>cả hai</b> màn trong cùng một buổi, và thường cách
 * nhau vài phút:
 * <ol>
 *   <li>trang {@code /dat-mat-khau} của sản phẩm này, ngay sau khi bấm "Đúng là
 *       tôi";</li>
 *   <li>màn "Đặt mật khẩu mới" của <b>Keycloak</b>
 *       ({@code infra/keycloak/themes/giapha/login/login-update-password.ftl}) —
 *       đường đi của nghi thức đặt lại ngoại tuyến, tức đúng chỗ mà màn `410`
 *       của trang này chỉ người dùng tới.
 *   </li>
 * </ol>
 * Hai màn nằm ở <b>hai kho mã, hai ngôn ngữ lập trình, hai hệ thống kiểu</b>:
 * React/TypeScript một bên, FreeMarker/CSS/properties bên kia. Không một trình
 * biên dịch nào nối được chúng, nên nếu hôm nay chúng giống nhau thì đó là nhờ
 * ai đó vừa nhìn qua — và cái nhìn ấy không lặp lại ở lần sửa tới. Tệp này biến
 * "vừa nhìn qua" thành một phép kiểm.
 *
 * <h2>Ba con số / ba câu chữ được ghim, và lý do của từng cái</h2>
 * <ul>
 *   <li><b>68×44px của nút "Hiện".</b> 44 là sàn vùng chạm WCAG 2.2; 68 là bề
 *       rộng vừa đủ cho nhãn chữ "Hiện"/"Ẩn" mà không xô ô nhập. Lệch một bên
 *       thì cùng một nút nhảy kích thước giữa hai màn liền nhau.</li>
 *   <li><b>Nhãn "Hiện" / "Ẩn".</b> Nếu một bên viết "Xem" thì người dùng phải
 *       học hai lần cùng một thứ.</li>
 *   <li><b>Câu gợi ý mật khẩu.</b> Đây là chỗ nguy hiểm nhất: nó <i>gợi ý</i>
 *       yêu cầu của realm. Hai câu lệch nhau nghĩa là một trong hai đang nói
 *       sai về chính sách thật, và người dùng không có cách nào biết câu nào
 *       đúng.</li>
 * </ul>
 *
 * <p><b>Cái tệp này KHÔNG làm:</b> nó không kiểm chính sách mật khẩu. Chính sách
 * sống trong realm Keycloak và cả hai màn đều chỉ <i>gợi ý</i> nó. Kiểm chính
 * sách ở đây là dựng bản luật thứ ba.</p>
 */

const THEME = join(FRONTEND_ROOT, "..", "infra", "keycloak", "themes", "giapha", "login");

function docTheme(...phan: string[]): string {
  return readFileSync(join(THEME, ...phan), "utf8");
}

/** Đọc một khoá của tệp `.properties` Keycloak. UTF-8, một dòng một khoá. */
function khoaProperties(noiDung: string, khoa: string): string | null {
  for (const dong of noiDung.split(/\r?\n/)) {
    const cat = dong.indexOf("=");
    if (cat < 0) continue;
    if (dong.slice(0, cat).trim() === khoa) return dong.slice(cat + 1).trim();
  }
  return null;
}

const O_MAT_KHAU = readFileSync(
  join(FRONTEND_ROOT, "src", "components", "auth", "set-password-field.tsx"),
  "utf8"
);

const CSS = docTheme("resources", "css", "giapha.css");
const VI_PROPS = docTheme("messages", "messages_vi.properties");
const EN_PROPS = docTheme("messages", "messages_en.properties");

const viSetPassword = viMessages.auth.setPassword;
const enSetPassword = enMessages.auth.setPassword;

describe("nút Hiện mật khẩu · hình học khớp với theme Keycloak", () => {
  it("theme vẫn khai đúng hai con số mà bài kiểm này dựa vào", () => {
    // Chống xanh giả: nếu ai đó đổi tên biến hay bỏ lớp `.gp-hien` thì hai phép
    // so sánh dưới đây sẽ so với `undefined` và im lặng đi qua.
    expect(CSS, "theme không còn khai `--gp-cham`").toMatch(/--gp-cham:\s*44px/);
    expect(CSS, "theme không còn lớp `.gp-hien` rộng 68px").toMatch(
      /\.gp-hien\s*\{[^}]*width:\s*68px/
    );
  });

  it("sàn vùng chạm 44px giống nhau ở cả hai nơi", () => {
    const sanTheme = /--gp-cham:\s*(\d+)px/.exec(CSS)![1]!;
    expect(sanTheme).toBe("44");
    // Nút, và cả ô nhập bên cạnh nó: một nút 44 cạnh một ô 36 vẫn là một hàng
    // không chạm tới được bằng ngón cái.
    expect(O_MAT_KHAU).toContain(`h-[${sanTheme}px]`);
    expect(O_MAT_KHAU).toContain(`min-h-[${sanTheme}px]`);
  });

  it("bề rộng 68px của nút giống nhau ở cả hai nơi", () => {
    const rongTheme = /\.gp-hien\s*\{[^}]*width:\s*(\d+)px/.exec(CSS)![1]!;
    expect(rongTheme).toBe("68");
    expect(O_MAT_KHAU).toContain(`w-[${rongTheme}px]`);
  });

  it("chừa chỗ cho nút bằng đúng phép tính của theme: 68 + 8 lề mỗi bên", () => {
    // `.gp-o-boc .gp-o { padding-right: 84px }` — nếu một bên tính 84 và bên kia
    // tính 76 thì chữ người dùng gõ sẽ chui xuống dưới nút ở đúng một trong hai
    // màn, và chỉ lộ ra với mật khẩu dài.
    const dem = /\.gp-o-boc\s+\.gp-o\s*\{[^}]*padding-right:\s*(\d+)px/.exec(CSS)![1]!;
    expect(dem).toBe("84");
    expect(O_MAT_KHAU).toContain(`paddingRight: ${dem}`);
  });
});

describe("nút Hiện mật khẩu · câu chữ khớp với theme Keycloak", () => {
  it("nhãn 'Hiện' / 'Ẩn' giống hệt nhau, tiếng Việt", () => {
    expect(viSetPassword.show).toBe(khoaProperties(VI_PROPS, "giaphaShowPassword"));
    expect(viSetPassword.hide).toBe(khoaProperties(VI_PROPS, "giaphaHidePassword"));
  });

  it("nhãn 'Show' / 'Hide' giống hệt nhau, tiếng Anh", () => {
    expect(enSetPassword.show).toBe(khoaProperties(EN_PROPS, "giaphaShowPassword"));
    expect(enSetPassword.hide).toBe(khoaProperties(EN_PROPS, "giaphaHidePassword"));
  });

  it("nhãn là CHỮ, không phải một biểu tượng con mắt", () => {
    // 00 §2.3: biểu tượng luôn kèm chữ. Một con mắt gạch chéo còn tệ hơn thế —
    // nó không nói được nó đang mô tả trạng thái hiện tại hay hành động sắp xảy
    // ra, và người dùng đoán sai đúng một nửa số lần.
    for (const nhan of [viSetPassword.show, viSetPassword.hide, enSetPassword.show]) {
      expect(nhan.length, `nhãn "${nhan}" rỗng`).toBeGreaterThan(0);
      expect(nhan, `nhãn "${nhan}" chứa biểu tượng`).not.toMatch(
        /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u
      );
    }
  });

  it("câu gợi ý mật khẩu giống hệt nhau ở cả hai ngôn ngữ", () => {
    // Chỗ nguy hiểm nhất: hai câu lệch nhau nghĩa là một trong hai đang nói sai
    // về chính sách thật của realm.
    expect(viSetPassword.hint).toBe(khoaProperties(VI_PROPS, "giaphaPasswordHint"));
    expect(enSetPassword.hint).toBe(khoaProperties(EN_PROPS, "giaphaPasswordHint"));
  });

  it("câu gợi ý là GỢI Ý, không phải một lời hứa máy chủ sẽ chấp nhận", () => {
    // Nó không được viết dưới dạng một điều kiện đủ ("chỉ cần 8 ký tự là được"):
    // realm có thể từ chối vì một lý do khác, và lúc ấy người dùng sẽ tin rằng
    // hệ thống hỏng chứ không phải mật khẩu chưa đạt.
    expect(viSetPassword.hint).toContain("Ít nhất");
    expect(viSetPassword.hint).not.toMatch(/chỉ cần|là được|đủ rồi/i);
  });

  it("phép dò khoá properties thật sự đọc được thứ nó nói — ca kiểm ngược", () => {
    expect(khoaProperties(VI_PROPS, "khong-he-ton-tai")).toBeNull();
    expect(khoaProperties(VI_PROPS, "giaphaShowPassword")).not.toBeNull();
  });
});

describe("trang này chỉ đường tới đúng màn Keycloak mà nghi thức đặt lại dùng", () => {
  it("màn 410 mô tả đúng quy trình `UPDATE_PASSWORD` mà theme đã dựng sẵn", () => {
    // `login-update-password.ftl` tồn tại là vì trưởng chi cấp một mật khẩu tạm
    // qua điện thoại rồi Keycloak bắt đổi ngay. Câu chữ ở màn 410 phải dẫn tới
    // đúng quy trình ấy, không phải một lời an ủi chung chung.
    expect(() => docTheme("login-update-password.ftl")).not.toThrow();
    expect(viSetPassword.expiredNext).toContain("mật khẩu tạm");
    expect(viSetPassword.expiredNext).toContain("đăng nhập đầu");
    expect(enSetPassword.expiredNext).toContain("temporary password");
  });
});

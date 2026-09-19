import { beforeEach, describe, expect, it } from "vitest";
import { setDevRole } from "@/lib/api/dev-role";
import { personsApi } from "@/lib/api";
import { PRIVACY_GROUPS, type PrivacySettings } from "@/types/api";
import { resetPrivacySettingsStore } from "@/mocks/privacy-settings";

/**
 * HỢP ĐỒNG của khối `PersonDto.privacy` (contracts/openapi.yaml →
 * `PrivacySettings` / `ShareScope`), kiểm qua đúng bộ MSW mà ứng dụng chạy.
 *
 * <h2>Vì sao tệp này tồn tại ở tầng API chứ không chỉ ở tầng giao diện</h2>
 * Đây là chỗ hợp đồng với backend được ghim lại dưới dạng chạy được. Bản dựng
 * đầu tiên của giao diện đã đoán SAI hình dạng này theo đúng ba cách mà bài học
 * "bộ giả lập đang giả lập một API dễ hơn API thật" cảnh báo:
 *
 *   1. dựng một endpoint riêng `/persons/{id}/privacy-settings` — thực tế khối
 *      này nằm TRONG `PersonDto` và ghi qua `PATCH /persons/{id}`;
 *   2. đặt tên nhóm theo kiểu hằng số (`CURRENT_PLACE_PROVINCE`) thay vì tên
 *      trường của contract (`residenceProvince`);
 *   3. cho `PUT` ghi đè toàn phần, trong khi bản thật **hợp nhất**.
 *
 * Ba lỗi ấy đều im lặng — mã vẫn chạy, màn hình vẫn đẹp — cho tới ngày nối vào
 * backend thật. Nên chúng được ghim ở đây, không ở đâu khác.
 */

const SELF_ID = "p-102"; // hồ sơ của tài khoản vai "member"
const OTHER_LIVING_ID = "p-100";
const MINOR_ID = "p-101";
const DECEASED_ID = "p-001";

async function read(id: string) {
  const { data, etag } = await personsApi.getById(id);
  return { person: data, etag: etag ?? '"v1"' };
}

beforeEach(() => {
  resetPrivacySettingsStore();
});

describe("ai đọc được khối `privacy`", () => {
  it("chính chủ nhận đủ năm nhóm", async () => {
    setDevRole("member");
    const { person } = await read(SELF_ID);

    expect(person.privacy).toBeDefined();
    expect(Object.keys(person.privacy as PrivacySettings).sort()).toEqual(
      [...PRIVACY_GROUPS].sort()
    );
    for (const group of PRIVACY_GROUPS) {
      expect(["CLAN", "BRANCH", "PRIVATE"]).toContain((person.privacy as PrivacySettings)[group]);
    }
  });

  it("Hội đồng / ADMIN cũng đọc được — họ cần nó để hỗ trợ và kiểm toán", async () => {
    setDevRole("admin");
    const { person } = await read(OTHER_LIVING_ID);
    expect(person.privacy).toBeDefined();
  });

  it("người khác thì khối ấy VẮNG HẲN, không phải null", async () => {
    // Biết người khác đang siết quyền riêng tư cũng là một dạng rò rỉ. `null`
    // sẽ nói "có khối này nhưng bạn không được xem"; vắng mặt thì không nói gì.
    setDevRole("branch-head");
    const { person } = await read(OTHER_LIVING_ID);
    expect(person.privacy).toBeUndefined();
    expect("privacy" in person).toBe(false);
  });

  it("người đã khuất không mang bản đồng thuận nào", async () => {
    setDevRole("admin");
    const { person } = await read(DECEASED_ID);
    expect(person.privacy).toBeUndefined();
  });
});

describe("mặc định là kín", () => {
  it("hồ sơ chưa ai đặt gì thì cả năm nhóm đều PRIVATE", async () => {
    setDevRole("admin");
    const { person } = await read(OTHER_LIVING_ID);
    const settings = person.privacy as PrivacySettings;
    expect(PRIVACY_GROUPS.every((g) => settings[g] === "PRIVATE")).toBe(true);
  });

  it("và khi ấy KHÔNG ai ngoài chính chủ/Hội đồng thấy nghề, tỉnh, liên hệ", async () => {
    // Đây là điểm đảo chiều lớn nhất so với bản dựng trước contract: Trưởng chi
    // KHÔNG còn một nền Tầng 2 mặc định. Trong chi mình họ là "người cùng chi",
    // nên họ chỉ thấy những nhóm chủ thể đã mở tới mức BRANCH.
    setDevRole("branch-head");
    const { person } = await read(OTHER_LIVING_ID);

    expect(person.occupation).toBeUndefined();
    expect(person.currentPlaceProvince).toBeUndefined();
    expect(person.contact).toBeUndefined();
    expect(person.currentPlaceFull).toBeUndefined();
  });
});

describe("PATCH hợp nhất, không thay thế", () => {
  it("gửi MỘT nhóm thì bốn nhóm còn lại GIỮ NGUYÊN", async () => {
    // Ngoại lệ so với `names`/`attributes`, và là chỗ dễ dựng sai nhất: nếu
    // chỗ này ghi đè toàn phần, giao diện năm công tắc chỉ gửi công tắc vừa
    // gạt sẽ âm thầm đóng bốn nhóm kia sau mỗi lần lưu.
    setDevRole("member");
    const before = await read(SELF_ID);
    expect((before.person.privacy as PrivacySettings).occupation).toBe("CLAN");

    const { data } = await personsApi.update(
      SELF_ID,
      { privacy: { residenceFull: "BRANCH" } },
      before.etag
    );
    const after = data.privacy as PrivacySettings;

    expect(after.residenceFull).toBe("BRANCH");
    expect(after.occupation).toBe("CLAN");
    expect(after.residenceProvince).toBe("CLAN");
    expect(after.contact).toBe("BRANCH");
  });

  it('`clearFields: ["privacy"]` đóng cả năm nhóm về PRIVATE', async () => {
    setDevRole("member");
    const before = await read(SELF_ID);

    const { data } = await personsApi.update(SELF_ID, { clearFields: ["privacy"] }, before.etag);
    const after = data.privacy as PrivacySettings;

    // Đóng lại, KHÔNG phải xoá khối đi: xoá khối sẽ làm chính chủ mất luôn bảng
    // điều khiển của mình, và "không có lựa chọn nào" đọc ra giống hệt "chưa
    // từng chọn".
    expect(after).toBeDefined();
    expect(PRIVACY_GROUPS.every((g) => after[g] === "PRIVATE")).toBe(true);
  });

  it("mức mới đổi ngay dữ liệu mà NGƯỜI KHÁC nhận được", async () => {
    setDevRole("member");
    const before = await read(SELF_ID);
    // Đóng nghề nghiệp lại.
    await personsApi.update(SELF_ID, { privacy: { occupation: "PRIVATE" } }, before.etag);

    setDevRole("branch-head");
    const seen = await read(SELF_ID);
    expect(seen.person.occupation).toBeUndefined();
  });
});

describe("ba luật thắng mọi lựa chọn của người dùng", () => {
  it("khách vẫn nhận 404 cho người còn sống, kể cả khi người đó mở CLAN", async () => {
    setDevRole("member");
    const before = await read(SELF_ID);
    await personsApi.update(
      SELF_ID,
      { privacy: { occupation: "CLAN", contact: "CLAN", residenceFull: "CLAN" } },
      before.etag
    );

    setDevRole("guest");
    await expect(personsApi.getById(SELF_ID)).rejects.toMatchObject({ status: 404 });
  });

  it("trẻ vị thành niên ẩn tối đa, không một nhóm nào mở ra được", async () => {
    setDevRole("admin");
    const { person, etag } = await read(MINOR_ID);
    // Ngay cả khi bản đồng thuận bị đặt mở, phép lọc vẫn phải giữ kín.
    await personsApi.update(
      MINOR_ID,
      { privacy: { occupation: "CLAN", contact: "CLAN" } },
      etag
    );
    expect(person.isAlive).toBe(true);

    setDevRole("member");
    const seen = await read(MINOR_ID);
    expect(seen.person.occupation).toBeUndefined();
    expect(seen.person.contact).toBeUndefined();
    expect(seen.person.birth).toBeUndefined();
  });

  it("chính chủ luôn thấy trọn hồ sơ của mình — nếu không thì không chỉnh được gì", async () => {
    setDevRole("member");
    const { person } = await read(SELF_ID);
    expect(person.meta.isSelf).toBe(true);
    expect(person.contact?.phone).toBeTruthy();
  });
});

describe("`meta.visibleTier` là tóm tắt, không phải lời hứa", () => {
  it("suy ra từ kết quả lọc thật: mở nhóm nhẹ ⇒ T2, mở nhóm nhạy cảm ⇒ T3", async () => {
    setDevRole("member");
    const before = await read(SELF_ID);
    await personsApi.update(
      SELF_ID,
      { privacy: { occupation: "CLAN", residenceProvince: "PRIVATE", contact: "PRIVATE" } },
      before.etag
    );

    setDevRole("branch-head");
    const light = await read(SELF_ID);
    expect(light.person.meta.visibleTier).toBe("T2");
    expect(light.person.occupation).toBeTruthy();

    setDevRole("member");
    const again = await read(SELF_ID);
    await personsApi.update(SELF_ID, { privacy: { contact: "CLAN" } }, again.etag);

    setDevRole("branch-head");
    const sensitive = await read(SELF_ID);
    expect(sensitive.person.meta.visibleTier).toBe("T3");
  });

  it("T1 KHÔNG có nghĩa hồ sơ trống, và T2 KHÔNG hứa `occupation` tồn tại", async () => {
    // Ghim bằng chữ: giao diện phải rẽ nhánh theo TRƯỜNG CÓ HAY VẮNG, không
    // theo tier. `p-100` toàn PRIVATE nên Trưởng chi nhận T1 kèm một hồ sơ
    // hoàn toàn hợp lệ — có tên, có đời, có chi.
    setDevRole("branch-head");
    const { person } = await read(OTHER_LIVING_ID);
    expect(person.meta.visibleTier).toBe("T1");
    expect(person.displayName).toBeTruthy();
    expect(person.generation).toBeTruthy();
    expect(person.primaryBranch?.name).toBeTruthy();
  });
});

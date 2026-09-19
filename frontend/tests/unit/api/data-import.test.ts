import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { dataImportApi, fileNameFrom } from "@/lib/api/data-import";
import { setDevRole } from "@/lib/api/dev-role";
import {
  DUP_PAIR_IN_FILE,
  DUP_PAIR_TREE_HIDDEN,
  DUP_PAIR_TREE_VISIBLE,
  markEditedAfterCommit,
  markNewWarningAfterAck,
  resetImportMockDb,
  seedConflictingMergePair,
  seedMockBatch,
} from "@/mocks/data-import";

/**
 * Lớp API nhập liệu, chạy qua **chính bộ giả lập MSW mà ứng dụng đang dùng**.
 *
 * <p>Vì sao không stub `fetch`: hình dạng yêu cầu là thứ duy nhất backend và
 * giao diện phải khớp nhau, và một stub viết tay sẽ khớp với chính nó chứ không
 * khớp với hợp đồng. Đi qua MSW thì mã lỗi RFC 7807, header vai và thứ tự sắp
 * xếp đều là thật.</p>
 *
 * <h2>Ngoại lệ: lượt TẢI TỆP LÊN không kiểm được ở đây</h2>
 * <p>Dưới `jsdom`, `FormData` là bản của jsdom còn `fetch` là bản của undici
 * (Node). Undici không nhận ra một `FormData` lạ nên không đặt
 * `Content-Type: multipart/form-data`, và `request.formData()` phía MSW ném
 * ngay. Đây là lệch <b>môi trường thử</b>, không phải lỗi sản phẩm.</p>
 *
 * <p>Vì vậy đường multipart được kiểm ở `e2e/data-import.spec.ts` (trình duyệt
 * thật, `setInputFiles` thật), còn ở đây lô được gieo bằng `seedMockBatch()` —
 * <b>chính hàm mà handler `POST /batches` gọi</b>.</p>
 */

beforeEach(() => {
  resetImportMockDb();
  setDevRole("branch-head");
});

afterEach(() => {
  resetImportMockDb();
});

describe("lớp API nhập liệu · phạm vi chi", () => {
  it("trả CẢ CÂY chi cho mọi vai đã khởi tạo — phạm vi nằm ở canImport", async () => {
    const branches = await dataImportApi.branches();
    // Bốn chi, đã sắp theo `ltree`. Giấu ba chi kia không bảo vệ thêm được gì:
    // danh sách chi là cấu trúc tổ chức, vốn đã hiện trên phả đồ.
    expect(branches.map((b) => b.path)).toEqual([
      "root.chi_nhat",
      "root.chi_nhi",
      "root.chi_tam",
      "root.chi_tu",
    ]);
    expect(branches.filter((b) => b.canImport).map((b) => b.path)).toEqual(["root.chi_nhat"]);
  });

  it("Quản trị toàn dòng họ nhập được cả bốn chi", async () => {
    setDevRole("admin");
    const branches = await dataImportApi.branches();
    expect(branches.every((b) => b.canImport)).toBe(true);
  });

  it("Thành viên thường vẫn thấy cây chi, nhưng không chi nào nhập được", async () => {
    setDevRole("member");
    const branches = await dataImportApi.branches();
    expect(branches).toHaveLength(4);
    expect(branches.some((b) => b.canImport)).toBe(false);
  });

  it("Khách bị chặn bằng ACCOUNT_NOT_PROVISIONED, không phải một mảng rỗng", async () => {
    setDevRole("guest");
    await expect(dataImportApi.branches()).rejects.toMatchObject({
      status: 403,
      code: "ACCOUNT_NOT_PROVISIONED",
    });
  });

  it("KHÔNG có codePrefix — bảng branch không có cột ấy", async () => {
    const [first] = await dataImportApi.branches();
    expect(first).not.toHaveProperty("codePrefix");
    expect(first).toHaveProperty("kind");
  });
});

describe("lớp API nhập liệu · ngưỡng nghi trùng do máy chủ công bố", () => {
  it("trả đúng ba trường, và autoMerge LUÔN false", async () => {
    const policy = await dataImportApi.duplicatePolicy();
    expect(policy.suspectThreshold).toBe(70);
    expect(policy.preselectMergeThreshold).toBe(85);
    // Lời hứa của hợp đồng, không phải một câu trong tài liệu.
    expect(policy.autoMerge).toBe(false);
  });
});

describe("lớp API nhập liệu · tải lên và đối soát", () => {
  it("một tệp bẩn ra 6 lỗi chặn và 11 mục cần xem lại, hai nhóm tách bạch", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });

    expect(batch.blockingCount).toBe(6);
    expect(batch.warningCount).toBe(11);
    // Lô còn lỗi chặn thì KHÔNG ở trạng thái chờ duyệt, và không duyệt được.
    expect(batch.status).toBe("FAILED");
    expect(batch.canApprove).toBe(false);

    const blocking = await dataImportApi.issues(batch.id, "BLOCKING");
    const warnings = await dataImportApi.issues(batch.id, "WARNING");
    expect(blocking).toHaveLength(6);
    expect(warnings).toHaveLength(11);
    expect(blocking.every((i) => i.severity === "BLOCKING")).toBe(true);
  });

  it("lô mang đủ các trường hợp đồng công bố, và KHÔNG mang trường đã bị bác", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    for (const field of [
      "fileSizeBytes",
      "validatedAt",
      "canApprove",
      "uploadedBy",
      "version",
    ] as const) {
      expect(batch).toHaveProperty(field);
    }
    // Bốn trường giao diện từng tự nghĩ ra và máy chủ không bao giờ gửi.
    for (const dead of [
      "uploadedByName",
      "rollbackDeadline",
      "committedPersonCount",
      "committedRelationshipCount",
    ]) {
      expect(batch).not.toHaveProperty(dead);
    }
    // Cơ chế ở lại máy chủ: client không có cách nào tự suy điều kiện duyệt.
    expect(batch).not.toHaveProperty("warningsDigest");
  });

  it("lô mới: undecidedDuplicateCount BẰNG suspectDuplicateCount, và cả hai đếm CẶP", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    const pairs = await dataImportApi.duplicates(batch.id);
    expect(batch.suspectDuplicateCount).toBe(pairs.length);
    // Lô mới thì mọi cặp đều `PENDING`, nên hai con số bằng nhau LÚC NÀY. Chúng
    // tách ra ngay khi có người quyết cặp đầu tiên.
    expect(batch.undecidedDuplicateCount).toBe(batch.suspectDuplicateCount);
    // Ba cặp, nhưng chỉ ba dòng cảnh báo nghi trùng — con số là số CẶP.
    const suspectWarnings = (await dataImportApi.issues(batch.id, "WARNING")).filter(
      (i) => i.code === "IMP_SUSPECT_DUPLICATE"
    );
    expect(pairs.length).toBe(suspectWarnings.length);
  });

  it("lỗi 'mã cha không tìm thấy' mang khoá TIẾNG VIỆT: goiY và maKhongTimThay", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const issues = await dataImportApi.issues(batch.id, "BLOCKING");
    const parent = issues.find((i) => i.code === "IMP_PARENT_NOT_FOUND")!;
    expect(parent.context?.goiY?.length).toBeGreaterThan(0);
    expect(parent.context?.maKhongTimThay).toBe("AT-04-O03");
    // Khoá tiếng Anh cũ không còn tồn tại.
    expect(parent.context).not.toHaveProperty("suggestions");
  });

  it("lỗi vòng lặp mang `chuoi` — đường đi thật, đã đóng kín", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const issues = await dataImportApi.issues(batch.id, "BLOCKING");
    const cycle = issues.find((i) => i.code === "IMP_CYCLE")!;
    const path = cycle.context?.chuoi ?? [];
    expect(path.length).toBeGreaterThanOrEqual(3);
    expect(path[0]).toBe(path[path.length - 1]);
  });

  it("`cacDong` là một MẢNG số dòng, không phải một số", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const dup = (await dataImportApi.issues(batch.id, "BLOCKING")).find(
      (i) => i.code === "IMP_DUP_CODE"
    )!;
    expect(Array.isArray(dup.context?.cacDong)).toBe(true);
    expect(dup.context?.cacDong?.length).toBeGreaterThan(1);
  });

  it("`doiKhai`/`doiSuyRa` là SỐ, không phải chuỗi", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const gen = (await dataImportApi.issues(batch.id, "BLOCKING")).find(
      (i) => i.code === "IMP_GENERATION_MISMATCH"
    )!;
    expect(typeof gen.context?.doiKhai).toBe("number");
    expect(typeof gen.context?.doiSuyRa).toBe("number");
  });

  it("lỗi của CẢ LÔ mang sheet LO và KHÔNG có rowNo", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const whole = (await dataImportApi.issues(batch.id, "BLOCKING")).find(
      (i) => i.sheet === "LO"
    )!;
    expect(whole.code).toBe("IMP_MASS_CREATE_GUARD");
    expect(whole.rowNo).toBeUndefined();
  });

  it("cảnh báo nghi trùng đã CẮT sạch dữ liệu người trong phả, chỉ còn khoá", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const suspect = (await dataImportApi.issues(batch.id, "WARNING")).find(
      (i) => i.code === "IMP_SUSPECT_DUPLICATE" && i.rowNo === 12
    )!;
    const [nghiNgo] = suspect.context?.nghiNgo ?? [];
    expect(nghiNgo?.nguon).toBe("TREE");
    expect(Object.keys(nghiNgo ?? {}).sort()).toEqual(["diem", "nguon", "personId"]);
  });

  it("`IMP_TABOO_COLLISION` chỉ còn tên huý và KHOÁ của bậc trên", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const taboo = (await dataImportApi.issues(batch.id, "WARNING")).find(
      (i) => i.code === "IMP_TABOO_COLLISION"
    )!;
    expect(taboo.context?.tenHuy).toBeTruthy();
    expect(taboo.context?.bacTrenId).toBeTruthy();
    // Không tên, không đời của bậc trên.
    expect(taboo.context).not.toHaveProperty("bacTrenName");
  });

  it("danh sách lỗi ổn định giữa hai lần đọc — sắp tất định theo (sheet, dòng, mã, cột)", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const first = await dataImportApi.issues(batch.id);
    const second = await dataImportApi.issues(batch.id);
    expect(JSON.stringify(first)).toBe(JSON.stringify(second));
    // Trang `LO` xuống cuối, vì nó không gắn với dòng nào.
    expect(first[first.length - 1]?.sheet).toBe("LO");
  });

  it("chạy lại bộ kiểm KHÔNG tích luỹ — danh sách y hệt, không dài thêm", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const before = await dataImportApi.issues(batch.id);
    await dataImportApi.revalidate(batch.id);
    const after = await dataImportApi.issues(batch.id);
    expect(JSON.stringify(after)).toBe(JSON.stringify(before));
  });
});

describe("lớp API nhập liệu · các dòng đang chờ", () => {
  it("mỗi dòng nói rõ sẽ TẠO MỚI hay CẬP NHẬT, suy từ mã chứ không từ tên", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const rows = await dataImportApi.rows(batch.id);
    expect(rows.length).toBeGreaterThan(0);
    expect(rows.every((r) => ["CREATE", "UPDATE", "SKIP"].includes(r.plannedAction))).toBe(true);
    // Đã sắp theo `rowNo`.
    expect(rows.map((r) => r.rowNo)).toEqual([...rows.map((r) => r.rowNo)].sort((a, b) => a - b));
  });

  it("lọc theo hành động dự kiến được, và `resolvedPersonId` chỉ là một khoá", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const updates = await dataImportApi.rows(batch.id, "UPDATE");
    expect(updates.every((r) => r.plannedAction === "UPDATE")).toBe(true);
    for (const row of updates) {
      expect(typeof row.resolvedPersonId).toBe("string");
      // Không có một trường dữ liệu nào của phả đi kèm khoá ấy.
      expect(row).not.toHaveProperty("resolvedPersonName");
    }
  });

  it("ô 'Còn sống' để trống thì VẮNG MẶT, không bị đoán thành true", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const rows = await dataImportApi.rows(batch.id);
    const unknown = rows.find((r) => r.rowNo === 26)!;
    expect(unknown.alive).toBeUndefined();
  });
});

describe("lớp API nhập liệu · cặp nghi trùng và bất biến riêng tư", () => {
  it("bên TREE chỉ có personId, evidence RỖNG, không có hint", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const pairs = await dataImportApi.duplicates(batch.id);
    const fromTree = pairs.filter((p) => p.existing.source === "TREE");
    expect(fromTree.length).toBeGreaterThan(0);
    for (const pair of fromTree) {
      expect(Object.keys(pair.existing).sort()).toEqual(["personId", "source"]);
      expect(pair.evidence).toEqual([]);
      expect(pair.hint).toBeUndefined();
    }
  });

  it("bên FILE có đủ dữ liệu và đủ bằng chứng — không có gì để giấu với tác giả của nó", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const fromFile = (await dataImportApi.duplicates(batch.id)).find(
      (p) => p.existing.source === "FILE"
    )!;
    expect(fromFile.existing.displayName).toBeTruthy();
    expect(fromFile.evidence.length).toBe(7);
  });

  it("evidence KHÔNG mang `points` — và giao diện không được tự cộng bù", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const pairs = await dataImportApi.duplicates(batch.id);
    for (const pair of pairs) {
      for (const ev of pair.evidence) {
        expect(ev.points).toBeUndefined();
      }
    }
  });

  it("`preselectMerge` tính ở MÁY CHỦ, và cặp chưa ai động tới thì PENDING", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const policy = await dataImportApi.duplicatePolicy();
    for (const pair of await dataImportApi.duplicates(batch.id)) {
      expect(pair.preselectMerge).toBe(pair.score >= policy.preselectMergeThreshold);
      expect(pair.status).toBe("PENDING");
      // Chưa quyết thì KHÔNG có người quyết và không có mốc thời gian — hai
      // trường ấy vắng mặt chứ không phải rỗng.
      expect(pair.decidedBy).toBeUndefined();
      expect(pair.decidedAt).toBeUndefined();
      // `id` là khoá CHÍNH (UUID), thứ gửi lên khi ghi quyết định.
      expect(pair.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    }
  });

  it("cặp TREE mang `signals` — ô vì-sao-nghi duy nhất mà nó được phép có", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const fromTree = (await dataImportApi.duplicates(batch.id)).filter(
      (p) => p.existing.source === "TREE"
    );
    expect(fromTree.length).toBeGreaterThan(0);
    for (const pair of fromTree) {
      // `hint` VẮNG (nó có thể nhắc tới giá trị trường của người bên phả) trong
      // khi `signals` CÓ: nhãn tín hiệu nói vì sao nghi mà không nói người ấy là
      // ai. Đọc nhầm khoá thì ô vì-sao-nghi trống ở đúng loại cặp quan trọng
      // nhất — loại mà bảng bằng chứng cũng rỗng theo bất biến riêng tư.
      expect(pair.hint).toBeUndefined();
      expect(pair.signals).toBeTruthy();
      // Và nhãn ấy không mang năm sinh hay năm mất của người bên phả.
      expect(pair.signals).not.toMatch(/\d{4}/);
    }
  });
});

describe("lớp API nhập liệu · ghi quyết định nghi trùng", () => {
  it("một quyết định trả về CẢ cặp lẫn lô đã tính lại — không phải gọi lại gì", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    const result = await dataImportApi.decideDuplicate(batch.id, DUP_PAIR_IN_FILE, {
      decision: "DISTINCT",
      note: "đối chiếu với sổ chi Giáp bản 1998",
    });

    expect(result.pair.status).toBe("DISTINCT");
    expect(result.pair.note).toBe("đối chiếu với sổ chi Giáp bản 1998");
    expect(result.pair.decidedAt).toBeTruthy();
    expect(result.pair.decidedBy).toBeTruthy();
    // Lô về cùng, đã tính lại — đây là điều làm cho nút duyệt không hiện sai
    // trong khoảng thời gian giữa hai lời gọi.
    expect(result.batch.id).toBe(batch.id);
    expect(result.batch.undecidedDuplicateCount).toBe(batch.undecidedDuplicateCount - 1);
  });

  it("GỘP một cặp TREE: một dòng chuyển từ thêm-mới sang cập-nhật", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    const before = { create: batch.plannedCreateCount, update: batch.plannedUpdateCount };

    const { batch: after } = await dataImportApi.decideDuplicate(batch.id, DUP_PAIR_TREE_VISIBLE, {
      decision: "MERGED",
    });

    expect(after.plannedCreateCount).toBe(before.create - 1);
    expect(after.plannedUpdateCount).toBe(before.update + 1);
  });

  it("GỘP một cặp FILE: một dòng biến mất hẳn, không chuyển sang cập-nhật", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    const before = { create: batch.plannedCreateCount, update: batch.plannedUpdateCount };

    const { batch: after } = await dataImportApi.decideDuplicate(batch.id, DUP_PAIR_IN_FILE, {
      decision: "MERGED",
    });

    expect(after.plannedCreateCount).toBe(before.create - 1);
    expect(after.plannedUpdateCount).toBe(before.update);
  });

  it("HOÃN không mở khoá nút duyệt: cặp vẫn nằm trong số chưa quyết", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    const pairs = await dataImportApi.duplicates(batch.id);

    let last = batch;
    for (const pair of pairs) {
      last = (await dataImportApi.decideDuplicate(batch.id, pair.id, { decision: "DEFERRED" }))
        .batch;
    }

    // Hoãn HẾT cả ba cặp mà số chưa quyết vẫn y nguyên — `DEFERRED` là một lời
    // khai, không phải một lối thoát. Nếu nó mở được cửa duyệt thì nó là nút
    // cho-tôi-qua và cả cơ chế dò trùng thành trang trí.
    expect(last.undecidedDuplicateCount).toBe(pairs.length);
    expect(last.canApprove).toBe(false);
    await expect(dataImportApi.commit(batch.id)).rejects.toMatchObject({
      status: 422,
    });

    // Và kế hoạch ghi KHÔNG đổi: hoãn không gộp gì cả.
    expect(last.plannedCreateCount).toBe(batch.plannedCreateCount);
    expect(last.plannedUpdateCount).toBe(batch.plannedUpdateCount);
  });

  it("KIỂM LẠI không xoá quyết định cũ — cặp đã quyết giữ nguyên trạng thái và thời điểm", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    const decided = await dataImportApi.decideDuplicate(batch.id, DUP_PAIR_TREE_HIDDEN, {
      decision: "DISTINCT",
      note: "hai cụ khác chi, đã hỏi bác trưởng",
    });

    await dataImportApi.revalidate(batch.id);

    const after = await dataImportApi.duplicates(batch.id);
    const again = after.find((p) => p.id === DUP_PAIR_TREE_HIDDEN)!;
    expect(again.status).toBe("DISTINCT");
    expect(again.decidedAt).toBe(decided.pair.decidedAt);
    expect(again.note).toBe("hai cụ khác chi, đã hỏi bác trưởng");
    // Cặp không đổi giữ nguyên KHOÁ: đó là điều làm quyết định cũ dính đúng cặp cũ.
    expect(again.id).toBe(DUP_PAIR_TREE_HIDDEN);
    // Cặp chưa ai động tới vẫn PENDING và vẫn chặn lô.
    expect(after.filter((p) => p.status === "PENDING")).toHaveLength(2);
  });

  it("gửi PENDING là 400 — rút lại một lời khai đã ghi không phải là xoá nó đi", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    await expect(
      dataImportApi.decideDuplicate(batch.id, DUP_PAIR_IN_FILE, {
        decision: "PENDING" as never,
      })
    ).rejects.toMatchObject({ status: 400 });
  });

  it("cặp không thuộc lô này là 404 — đường dẫn là thứ duy nhất đã qua phép kiểm phạm vi", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    await expect(
      dataImportApi.decideDuplicate(batch.id, "00000000-0000-4000-8000-000000000000", {
        decision: "DISTINCT",
      })
    ).rejects.toMatchObject({ status: 404 });
  });

  it("chi ngoài phạm vi: 403, không phải một lần ghi im lặng", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    setDevRole("member");
    await expect(
      dataImportApi.decideDuplicate(batch.id, DUP_PAIR_IN_FILE, { decision: "MERGED" })
    ).rejects.toMatchObject({ status: 403, code: "BRANCH_SCOPE_VIOLATION" });
  });

  it("gộp vào HAI người đã có trong phả bị từ chối bằng một lỗi CHẶN, và lô dừng", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    const second = seedConflictingMergePair(batch.id);

    await dataImportApi.decideDuplicate(batch.id, DUP_PAIR_TREE_VISIBLE, { decision: "MERGED" });
    const { batch: after } = await dataImportApi.decideDuplicate(batch.id, second, {
      decision: "MERGED",
    });

    // Lô DỪNG: không có gì vào phả, và cổng duyệt đóng ở điều kiện thứ nhất.
    expect(after.status).toBe("FAILED");
    expect(after.canApprove).toBe(false);
    const blocking = await dataImportApi.issues(batch.id, "BLOCKING");
    expect(blocking.map((i) => i.code)).toContain("IMP_MERGE_NOT_APPLICABLE");
  });

  it("lô đã chốt thì không nhận quyết định nữa — gửi lô mới, đừng sửa lô cũ", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    // Một lô mới của cùng chi đẩy lô này sang SUPERSEDED.
    seedMockBatch({ branchId: "b-chi1", fileName: "da-sua-lan-hai.xlsx" });
    await expect(
      dataImportApi.decideDuplicate(batch.id, DUP_PAIR_IN_FILE, { decision: "MERGED" })
    ).rejects.toMatchObject({ status: 422, code: "IMP_BATCH_CLOSED" });
  });

  it("bản lỗi Excel về kèm tên tệp CÓ DẤU, đọc từ dạng RFC 5987", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    const file = await dataImportApi.issuesWorkbook(batch.id, "roi.xlsx");

    // Server gửi CẢ HAI dạng tên tệp. Đọc `filename=` trước thì lượt tải vẫn
    // "thành công" và người dùng nhận về một tên đã rụng hết dấu.
    expect(file.fileName).toContain("Danh sách cần sửa");
    expect(file.fileName).toContain("Chi Nhất");
    expect(file.fileName.endsWith(".xlsx")).toBe(true);
    expect(file.blob.size).toBeGreaterThan(0);
  });

  it("bản lỗi Excel KHÔNG nhận tham số severity — một lối gọi, một nút", () => {
    // Chữ ký chỉ có `(batchId, fallbackName)`. Hai nút "tải riêng phần cảnh báo"
    // sẽ sinh ra một tệp mang tên "Danh sách cần sửa" mà thiếu phần phải sửa.
    expect(dataImportApi.issuesWorkbook.length).toBe(2);
  });

  it("chi ngoài phạm vi không tải được bản lỗi Excel", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    setDevRole("member");
    await expect(dataImportApi.issuesWorkbook(batch.id, "roi.xlsx")).rejects.toMatchObject({
      status: 403,
    });
  });

  it("mọi cặp đều đạt ngưỡng nghi ngờ của máy chủ — dưới ngưỡng thì không sinh cảnh báo", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const policy = await dataImportApi.duplicatePolicy();
    for (const pair of await dataImportApi.duplicates(batch.id)) {
      expect(pair.score).toBeGreaterThanOrEqual(policy.suspectThreshold);
    }
  });
});

describe("lớp API nhập liệu · tải lại tệp đã sửa", () => {
  it("tệp đã sửa ra 0 lỗi chặn, và lô cũ chuyển SUPERSEDED chứ không bị xoá", async () => {
    const dirty = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    expect(dirty.blockingCount).toBe(6);

    const fixed = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-da-sua.xlsx" });
    expect(fixed.blockingCount).toBe(0);
    expect(fixed.status).toBe("VALIDATED");
    expect(fixed.id).not.toBe(dirty.id);

    // Lô cũ vẫn đọc được — không bao giờ xoá.
    const old = await dataImportApi.batch(dirty.id);
    expect(old.status).toBe("SUPERSEDED");
  });
});

describe("lớp API nhập liệu · cổng duyệt", () => {
  it("còn lỗi chặn thì máy chủ TỪ CHỐI duyệt, dù giao diện đã ẩn nút", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    await expect(dataImportApi.commit(batch.id)).rejects.toMatchObject({
      status: 422,
      code: "IMP_BLOCKING_ISSUES_PRESENT",
    });
  });

  it("mỗi dòng lỗi mang `id` riêng — khoá danh sách, không phải một bộ ghép", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    const ids = (await dataImportApi.issues(batch.id)).map((i) => i.id);
    expect(ids.every(Boolean)).toBe(true);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("sạch lỗi nhưng còn cảnh báo chưa xác nhận thì vẫn chưa duyệt được", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    expect(batch.canApprove).toBe(false);
    await expect(dataImportApi.commit(batch.id)).rejects.toMatchObject({
      code: "IMP_WARNINGS_NOT_ACKNOWLEDGED",
    });
  });

  it("tệp sạch hoàn toàn thì canApprove = true và duyệt được, sang COMMITTING chứ không xong ngay", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    expect(batch.warningCount).toBe(0);
    expect(batch.suspectDuplicateCount).toBe(0);
    expect(batch.canApprove).toBe(true);

    const committing = await dataImportApi.commit(batch.id);
    expect(committing.status).toBe("COMMITTING");

    const progress = await dataImportApi.commitProgress(batch.id);
    // Ước lượng theo BẢN CHẤT: con số đếm ngoài giao dịch ghi.
    expect(progress.estimated).toBe(true);
    expect(progress.totalRows).toBe(batch.personRowCount);
  });

  it("chưa duyệt thì đường ống KHÔNG chạm vào phả — không endpoint nào ghi gì", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    await dataImportApi.issues(batch.id);
    await dataImportApi.rows(batch.id);
    await dataImportApi.duplicates(batch.id);
    await dataImportApi.revalidate(batch.id);
    const after = await dataImportApi.batch(batch.id);
    expect(after.committedAt).toBeUndefined();
    expect(after.status).toBe("VALIDATED");
  });
});

describe("lớp API nhập liệu · gỡ lô", () => {
  async function commitAndSettle(fileName: string) {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName });
    await dataImportApi.commit(batch.id);
    await new Promise((resolve) => setTimeout(resolve, 2_000));
    return dataImportApi.batch(batch.id);
  }

  it("lô chưa ghi thì preflight nói rõ KHÔNG CÓ GÌ ĐỂ GỠ, không nói 'quá hạn'", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    const preflight = await dataImportApi.rollbackPreflight(batch.id);
    expect(preflight.canRollback).toBe(false);
    expect(preflight.blockers[0]).toMatch(/chưa từng được ghi/i);
  });

  it("gỡ xong trả 200 và lô VẪN mang COMMITTED — nó đã từng được ghi", async () => {
    const committed = await commitAndSettle("chi-nhat-sach.xlsx");
    expect(committed.status).toBe("COMMITTED");
    expect(committed.rolledBackAt).toBeUndefined();

    const after = await dataImportApi.rollback(committed.id, "Nhầm sổ, nhập lại bản 1998.");
    // Không có ROLLED_BACK. `committedAt` là mốc mọi phép kiểm dựa vào, và việc
    // đã gỡ là một CỘT RIÊNG — đó là cách phân biệt "đã gỡ" với "chưa bao giờ ghi".
    expect(after.status).toBe("COMMITTED");
    expect(after.committedAt).toBeTruthy();
    expect(after.rolledBackAt).toBeTruthy();
  });

  it("lý do là TUỲ CHỌN — gỡ không kèm lý do vẫn đi qua", async () => {
    const committed = await commitAndSettle("chi-nhat-sach.xlsx");
    await expect(dataImportApi.rollback(committed.id)).resolves.toMatchObject({
      status: "COMMITTED",
    });
  });

  it("đã có người sửa hồ sơ sau khi ghi → 422 IMP_ROLLBACK_REFUSED kèm blockers[]", async () => {
    const committed = await commitAndSettle("chi-nhat-sach.xlsx");
    markEditedAfterCommit(committed.id, 12);

    const preflight = await dataImportApi.rollbackPreflight(committed.id);
    expect(preflight.canRollback).toBe(false);
    // Câu chữ đủ cụ thể để người bấm biết mình đang đánh đổi cái gì.
    expect(preflight.blockers.join(" ")).toMatch(/12 hồ sơ/);

    await expect(dataImportApi.rollback(committed.id, "thử")).rejects.toMatchObject({
      status: 422,
      code: "IMP_ROLLBACK_REFUSED",
    });
  });
});

describe("lớp API nhập liệu · danh sách lô", () => {
  it("chỉ trả lô trong phạm vi người gọi", async () => {
    seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    setDevRole("admin");
    seedMockBatch({ branchId: "b-chi2", fileName: "b.xlsx" });

    setDevRole("branch-head");
    const rows = await dataImportApi.batches();
    expect(rows.length).toBeGreaterThan(0);
    expect(rows.every((b) => b.branchId === "b-chi1")).toBe(true);
  });

  it("hỏi một chi NGOÀI phạm vi ra 403, không ra một danh sách rỗng", async () => {
    // "Không có lô nào" và "chi này không phải của bạn" là hai câu khác hẳn.
    await expect(dataImportApi.batches({ branchId: "b-chi2" })).rejects.toMatchObject({
      status: 403,
      code: "BRANCH_SCOPE_VIOLATION",
    });
  });

  it("người không quản chi nào nhận danh sách RỖNG — đúng, họ thật sự không có lô", async () => {
    seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    setDevRole("member");
    await expect(dataImportApi.batches()).resolves.toEqual([]);
  });

  it("xin nhiều hơn trần 200 thì client tự ghim lại, không để máy chủ từ chối", async () => {
    seedMockBatch({ branchId: "b-chi1", fileName: "a.xlsx" });
    await expect(dataImportApi.batches({ size: 5_000 })).resolves.toHaveLength(1);
  });
});

describe("lớp API nhập liệu · xác nhận cảnh báo là một LỜI KHAI", () => {
  it("xác nhận xong thì lô ghi lại AI đã xác nhận, và `version` tăng", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "da-sua.xlsx" });
    expect(batch.canApprove).toBe(false);

    const acked = await dataImportApi.acknowledgeWarnings(batch.id);
    expect(acked.warningsAcknowledgedAt).toBeTruthy();
    expect(acked.warningsAcknowledgedBy).toBeTruthy();
    // Vẫn còn cặp nghi trùng chưa quyết, nên cửa vẫn đóng — vì một lý do khác.
    expect(acked.canApprove).toBe(false);
    expect(acked.version).toBeGreaterThan(batch.version);
  });

  it("lô không có cảnh báo nào vẫn trả 200 — bấm nút trên danh sách rỗng là vô hại", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    const acked = await dataImportApi.acknowledgeWarnings(batch.id);
    expect(acked.canApprove).toBe(true);
  });

  it("kiểm lại mà tập cảnh báo Y NGUYÊN thì xác nhận CÒN hiệu lực", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    await dataImportApi.acknowledgeWarnings(batch.id);
    const after = await dataImportApi.revalidate(batch.id);
    // Bắt tick lại một danh sách không đổi là cách chắc chắn để lần thứ ba
    // người ta tick mà không đọc.
    expect(after.canApprove).toBe(true);
  });

  it("sinh cảnh báo MỚI thì xác nhận hết hiệu lực — dấu thời gian VẪN CÒN", async () => {
    const batch = seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat-sach.xlsx" });
    const acked = await dataImportApi.acknowledgeWarnings(batch.id);
    expect(acked.canApprove).toBe(true);

    markNewWarningAfterAck(batch.id);
    const stale = await dataImportApi.batch(batch.id);

    // ĐÂY là ca mà "suy từ warningsAcknowledgedAt" sẽ sai: dấu thời gian còn
    // nguyên, nhưng cửa đã đóng lại.
    expect(stale.warningsAcknowledgedAt).toBe(acked.warningsAcknowledgedAt);
    expect(stale.canApprove).toBe(false);
    await expect(dataImportApi.commit(batch.id)).rejects.toMatchObject({
      code: "IMP_WARNINGS_NOT_ACKNOWLEDGED",
    });

    // Đọc lại danh sách rồi xác nhận lại thì cửa mở lại.
    const reacked = await dataImportApi.acknowledgeWarnings(batch.id);
    expect(reacked.canApprove).toBe(true);
  });
});

describe("lớp API nhập liệu · tiến độ theo chi", () => {
  it("trả MỌI chi cho tài khoản đã khởi tạo, sắp theo `ltree`", async () => {
    const rows = await dataImportApi.progress();
    expect(rows.map((r) => r.branchPath)).toEqual([
      "root.chi_nhat",
      "root.chi_nhi",
      "root.chi_tam",
      "root.chi_tu",
    ]);
  });

  it("chi ngoài phạm vi bị CẮT năm trường — vắng hẳn khỏi JSON, không null hoá", async () => {
    const rows = await dataImportApi.progress();
    const outside = rows.find((r) => r.branchId === "b-chi2")!;
    for (const cut of [
      "coordinatorName",
      "openBatch",
      "blockingCount",
      "warningCount",
      "undecidedDuplicateCount",
    ]) {
      expect(outside).not.toHaveProperty(cut);
    }
    // `missingGioCount` KHÔNG bị cắt: một phép đếm về người đã khuất.
    expect(typeof outside.missingGioCount).toBe("number");
    expect(typeof outside.personsInTree).toBe("number");
  });

  it("chi trong phạm vi có đủ năm trường ấy", async () => {
    seedMockBatch({ branchId: "b-chi1", fileName: "chi-nhat.xlsx" });
    const mine = (await dataImportApi.progress()).find((r) => r.branchId === "b-chi1")!;
    expect(mine.coordinatorName).toBeTruthy();
    expect(mine.openBatch?.id).toBeTruthy();
    expect(mine.blockingCount).toBe(6);
    expect(mine.warningCount).toBe(11);
    expect(mine.undecidedDuplicateCount).toBe(3);
  });

  it("`expectedPersons` VẮNG MẶT khi chưa ai đếm — không phải 0", async () => {
    const rows = await dataImportApi.progress();
    expect(rows.find((r) => r.branchId === "b-chi1")!.expectedPersons).toBe(380);
    // Ba chi còn lại chưa ai mở sổ giấy ra đếm.
    expect(rows.find((r) => r.branchId === "b-chi3")!).not.toHaveProperty("expectedPersons");
  });

  it("không bao giờ phát `TEMPLATE_DOWNLOADED` — hệ thống không ghi lại lượt tải mẫu", async () => {
    const rows = await dataImportApi.progress();
    expect(rows.every((r) => (r.stage as string) !== "TEMPLATE_DOWNLOADED")).toBe(true);
  });

  it("khách bị chặn bằng 403, không nhận một danh sách rỗng", async () => {
    setDevRole("guest");
    await expect(dataImportApi.progress()).rejects.toMatchObject({
      status: 403,
      code: "ACCOUNT_NOT_PROVISIONED",
    });
  });
});

describe("lớp API nhập liệu · tên tệp tải về", () => {
  it("đọc dạng RFC 5987 trước, để tên chi tiếng Việt không rụng dấu", () => {
    const response = new Response(null, {
      headers: {
        "Content-Disposition": `attachment; filename="Mau-nhap-lieu.xlsx"; filename*=UTF-8''${encodeURIComponent(
          "Mẫu nhập liệu Chi Nhất.xlsx"
        )}`,
      },
    });
    expect(fileNameFrom(response, "roi.xlsx")).toBe("Mẫu nhập liệu Chi Nhất.xlsx");
  });

  it("rơi về dạng thường khi server không gửi dạng RFC 5987", () => {
    const response = new Response(null, {
      headers: { "Content-Disposition": 'attachment; filename="loi.xlsx"' },
    });
    expect(fileNameFrom(response, "roi.xlsx")).toBe("loi.xlsx");
  });

  it("rơi về tên dự phòng khi không có header nào", () => {
    expect(fileNameFrom(new Response(null), "roi.xlsx")).toBe("roi.xlsx");
  });
});

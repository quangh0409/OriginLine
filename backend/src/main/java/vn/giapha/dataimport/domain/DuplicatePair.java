package vn.giapha.dataimport.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Một <b>cặp nghi trùng</b> đang chờ người quyết, cùng quyết định đã ghi (nếu có).
 *
 * <p>POJO thuần, ứng với một dòng {@code import_duplicate_pair}.</p>
 *
 * <h2>Hai loại cặp, và chúng khác nhau về mọi thứ</h2>
 * <ul>
 *   <li>{@link Kind#TREE} — bên kia là một <b>nhân khẩu đã có trong phả</b>. Bản ghi này giữ
 *       {@code existingPersonId} và <b>không một trường nhân khẩu nào khác</b>: bộ dò quét toàn
 *       dòng họ nên người bị nghi có thể là một người <b>còn sống ở chi khác</b> mà người nhập
 *       liệu không có quyền biết là tồn tại.</li>
 *   <li>{@link Kind#FILE} — bên kia là một dòng khác <b>trong chính tệp vừa nộp</b>. Không có gì
 *       để giấu với chính tác giả của nó, nên bên gọi được phép tra dòng ấy ra và trưng đủ.</li>
 * </ul>
 *
 * @param pairKey <b>vân tay danh tính</b> của cặp — xem {@link #khoa}. Đây là thứ làm cho quyết
 *        định sống sót qua các lần kiểm lại.
 * @param signals <b>chỉ nhãn tín hiệu</b> ("trùng ngày giỗ", "cùng chi"), không bao giờ giá trị
 *        trường. Đường nhập liệu không phát một trường nhân khẩu nào của người trong phả.
 */
public record DuplicatePair(UUID id,
                            UUID batchId,
                            String pairKey,
                            int rowNo,
                            String incomingCode,
                            Kind kind,
                            UUID existingPersonId,
                            String existingCode,
                            int score,
                            String signals,
                            DuplicateDecision decision,
                            UUID decidedBy,
                            Instant decidedAt,
                            String note) {

    /** Bên bị nghi nằm ở đâu. */
    public enum Kind {
        /** Một nhân khẩu đã có trong phả — chỉ được phát ra ngoài dưới dạng khoá. */
        TREE,
        /** Một dòng khác trong chính tệp vừa nộp. */
        FILE
    }

    public DuplicatePair {
        decision = decision == null ? DuplicateDecision.PENDING : decision;
    }

    /**
     * Vân tay danh tính của một cặp.
     *
     * <h2>Vì sao {@code score} KHÔNG nằm trong đây</h2>
     * Quyết định là một khẳng định về <b>danh tính</b> — "dòng AT-03-001 và người kia là cùng một
     * người". Danh tính không đổi khi điểm nhích từ 86 xuống 84 vì ai đó vừa bổ sung năm sinh cho
     * hồ sơ bên kia. Nhét điểm vào vân tay thì mỗi lần kiểm lại huỷ một nắm quyết định mà không
     * giải thích được cho ai, và người đối chiếu sẽ học được rằng bấm gì cũng thế.
     *
     * <p>Chuỗi đọc được chứ không phải mã băm: thứ duy nhất người ta phải làm với nó lúc 2 giờ
     * sáng là <b>đọc</b> nó trong psql.</p>
     */
    public static String khoa(String incomingCode, Kind kind, UUID personId, String otherCode) {
        String benKia = kind == Kind.TREE ? String.valueOf(personId) : otherCode;
        return incomingCode + "::" + kind.name() + "::" + benKia;
    }

    public String khoa() {
        return khoa(incomingCode, kind, existingPersonId, existingCode);
    }

    /** Còn chặn cổng duyệt hay không — {@code PENDING} và {@code DEFERRED} đều chặn. */
    public boolean chuaQuyet() {
        return decision.chuaQuyet();
    }

    public boolean laGop() {
        return decision == DuplicateDecision.MERGED;
    }

    // ---------------------------------------------------------------------------------------
    // Đọc các cặp ra khỏi cảnh báo của bộ kiểm
    // ---------------------------------------------------------------------------------------

    /**
     * Rút các cặp ra khỏi tập cảnh báo {@code IMP_SUSPECT_DUPLICATE} vừa sinh.
     *
     * <h2>Vì sao phép đọc này nằm ở domain chứ không ở tầng đọc của api</h2>
     * Tầng api cũng đọc {@code context} của cảnh báo, nhưng để <b>hiển thị</b>, và ở đó nó đi qua
     * một danh sách khoá được phép trước khi rời máy chủ. Phép đọc ở đây thì để <b>ghi xuống một
     * bảng</b>, và nó phải tất định: hai lần kiểm trên cùng một tệp phải ra đúng cùng tập
     * {@code pairKey}, nếu không thì mọi quyết định tự bốc hơi sau mỗi lần kiểm lại. Hai mục đích
     * khác nhau, hai nơi, và cả hai đều không được tự bịa thêm trường.
     *
     * <p>Chỉ những cảnh báo có {@code rowNo} và có dòng tương ứng trong khu vực chờ mới ra cặp:
     * một cặp không tra được về dòng nào thì người đối chiếu không có gì để nhìn.</p>
     *
     * @param issues toàn bộ vấn đề của lần kiểm vừa rồi; mã khác {@code IMP_SUSPECT_DUPLICATE}
     *        bị bỏ qua
     * @param rowsByNo các dòng Nhân khẩu theo số dòng
     * @return danh sách cặp <b>chưa mang quyết định</b> ({@code PENDING}), đã khử trùng
     *         {@code pairKey}, thứ tự tất định theo (số dòng, khoá cặp)
     */
    public static List<DuplicatePair> tuCanhBao(UUID batchId, List<ImportIssue> issues,
                                                Map<Integer, PersonRow> rowsByNo) {
        Map<String, DuplicatePair> theoKhoa = new LinkedHashMap<>();
        for (ImportIssue issue : issues) {
            if (issue.code() != IssueCode.IMP_SUSPECT_DUPLICATE || issue.rowNo() == null) {
                continue;
            }
            PersonRow row = rowsByNo.get(issue.rowNo());
            if (row == null || row.externalCode() == null || row.externalCode().isBlank()) {
                continue;
            }
            for (Map<String, Object> ungVien : danhSachNghiNgo(issue.context())) {
                DuplicatePair cap = doc(batchId, row, ungVien);
                if (cap != null) {
                    // putIfAbsent: bo do co the neu cung mot ung vien hai lan; cap dau tien thang,
                    // va no la cap co diem cao nhat vi danh sach da sap giam dan theo diem.
                    theoKhoa.putIfAbsent(cap.pairKey(), cap);
                }
            }
        }
        List<DuplicatePair> ketQua = new ArrayList<>(theoKhoa.values());
        ketQua.sort((a, b) -> a.rowNo() != b.rowNo()
                ? Integer.compare(a.rowNo(), b.rowNo())
                : a.pairKey().compareTo(b.pairKey()));
        return List.copyOf(ketQua);
    }

    private static DuplicatePair doc(UUID batchId, PersonRow row, Map<String, Object> ungVien) {
        int diem = Math.max(0, Math.min(100, soNguyen(ungVien.get("diem"))));
        String personId = chuoi(ungVien.get("personId"));
        if (personId != null) {
            UUID id;
            try {
                id = UUID.fromString(personId);
            } catch (IllegalArgumentException ex) {
                return null;
            }
            return new DuplicatePair(null, batchId,
                    khoa(row.externalCode(), Kind.TREE, id, null), row.rowNo(),
                    row.externalCode(), Kind.TREE, id, null, diem,
                    // Ve trong pha: CHI nhan tin hieu. `giaiThich` cua ve trong tep mang gia tri
                    // truong ("trung nam sinh 1975") nen no khong duoc doc o nhanh nay.
                    chuoi(ungVien.get("tinHieu")), DuplicateDecision.PENDING, null, null, null);
        }
        String ref = chuoi(ungVien.get("ref"));
        if (ref == null || ref.isBlank() || ref.equals(row.externalCode())) {
            return null;
        }
        return new DuplicatePair(null, batchId,
                khoa(row.externalCode(), Kind.FILE, null, ref), row.rowNo(),
                row.externalCode(), Kind.FILE, null, ref, diem,
                chuoi(ungVien.get("giaiThich")), DuplicateDecision.PENDING, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> danhSachNghiNgo(Map<String, Object> context) {
        Object raw = context == null ? null : context.get("nghiNgo");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> ketQua = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                ketQua.add((Map<String, Object>) map);
            }
        }
        return ketQua;
    }

    private static int soNguyen(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static String chuoi(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }
}

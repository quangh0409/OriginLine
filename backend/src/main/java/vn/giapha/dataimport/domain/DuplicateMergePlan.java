package vn.giapha.dataimport.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Biến các quyết định <b>"gộp"</b> thành một phép biến đổi tất định trên tập dòng chờ.
 *
 * <h2>"Gộp" có hai nghĩa hoàn toàn khác nhau, và nhầm lẫn chúng là hỏng nặng</h2>
 *
 * <h3>1. Gộp với một hồ sơ ĐÃ CÓ TRONG PHẢ ({@link DuplicatePair.Kind#TREE})</h3>
 * Dòng trong tệp <b>không tạo người mới</b> — nó <b>cập nhật</b> hồ sơ đã có. Hai hệ quả bắt buộc:
 * <ul>
 *   <li>{@link PlannedAction#CREATE} phải đổi thành {@link PlannedAction#UPDATE}, và
 *       {@code resolvedPersonId} trỏ về người ấy;</li>
 *   <li>{@code person_external_ref} phải trỏ mã trong tệp sang {@code person.id} đã tồn tại, để
 *       <b>lần tải lại sau nhận ra ngay</b> mà không hỏi lại người đối chiếu. Không ghi khoá ấy thì
 *       lô kế tiếp lại thấy một mã lạ, lại sinh cặp nghi trùng, và cả thao tác gộp trở thành việc
 *       phải làm lại mỗi lần.</li>
 * </ul>
 *
 * <h3>2. Gộp với MỘT DÒNG KHÁC TRONG CHÍNH TỆP ({@link DuplicatePair.Kind#FILE})</h3>
 * Hai dòng cùng mô tả một người, nên một trong hai phải <b>biến mất trước khi ghi</b>, và
 * <b>mọi mã cha / mã mẹ / mã kế tự / mã hôn phối trỏ tới dòng bị bỏ phải được trỏ lại</b>.
 *
 * <p><b>Đây là chỗ dễ sinh người mồ côi nhất trong cả đường ống.</b> Bỏ một dòng mà quên trỏ lại
 * thì con cháu của người ấy mất cha trong phả đồ, và mất một cách im lặng: không ràng buộc nào của
 * CSDL biết rằng đáng lẽ phải có một cạnh ở đó. Vì vậy phép trỏ lại ở đây là <b>toàn phần</b> — cả
 * bốn loại mã, cộng phép hút ô trống ({@link PersonRow#donNhan}) để dòng ở lại không mất mã cha
 * của dòng bị bỏ.</p>
 *
 * <h2>Chuỗi gộp và vòng gộp: hợp nhất theo NHÓM, không theo từng cặp</h2>
 * Người đối chiếu quyết từng cặp, nhưng ba cặp có thể mô tả một chuỗi: A≡B, B≡C. Xử lý từng cặp
 * độc lập thì A trỏ sang B <i>đã bị bỏ</i> — một mã chết. Vì vậy các mã được gom thành <b>nhóm
 * liên thông</b> (hợp nhất kiểu union–find) rồi mới chọn <b>đúng một</b> đại diện cho cả nhóm.
 * Cách này chịu được cả vòng (A≡B, B≡C, C≡A) mà không lặp vô hạn.
 *
 * <h2>Ai ở lại, và vì sao luật ấy phải tất định</h2>
 * <ol>
 *   <li>Dòng nào đã có chủ trong phả ({@code resolvedPersonId != null}) thì <b>dòng ấy ở lại</b> —
 *       bỏ nó đi nghĩa là bỏ luôn phép cập nhật một hồ sơ có thật.</li>
 *   <li>Không dòng nào có chủ thì <b>số dòng nhỏ nhất ở lại</b>. Tất định và đoán trước được, và
 *       thường là lần xuất hiện đầu tiên trong sổ.</li>
 * </ol>
 * Toàn bộ dữ kiện của các dòng bị bỏ vẫn được hút sang dòng ở lại, nên luật này quyết định
 * <i>mã nào sống</i> chứ không quyết định <i>dữ liệu nào sống</i>.
 *
 * <h2>Ca từ chối: một nhóm dính vào HAI người khác nhau trong phả</h2>
 * Nếu một nhóm gộp lại trỏ tới hai {@code person} khác nhau thì câu trả lời đúng không nằm ở đường
 * nhập liệu: nó là "hợp nhất hai hồ sơ đã có trong phả", một thao tác của {@code genealogy} với
 * hoàn tác riêng. Kế hoạch <b>không tự chọn một trong hai</b> — chọn bừa ở đây là hợp nhất hai
 * nhánh con cháu vào một node sai. Nhóm ấy đi vào {@link #vuongMac()} và bước ghi dừng lại.
 *
 * <p>POJO thuần: không Spring, không JPA, không CSDL. Tất cả đầu vào truyền vào qua tham số.</p>
 */
public final class DuplicateMergePlan {

    /** Mã bị bỏ → mã ở lại. Đã đóng bắc cầu: không giá trị nào của bản đồ này lại là một khoá. */
    private final Map<String, String> maThayThe;

    /** Mã ở lại → nhân khẩu đã có trong phả mà nó được gộp vào. Chỉ các mã <b>chưa</b> có chủ. */
    private final Map<String, UUID> gopVaoHoSoDaCo;

    /** Mã ở lại → các dòng bị bỏ vào nó, theo thứ tự số dòng. */
    private final Map<String, List<String>> daGomVao;

    /** Các nhóm không áp dụng được, kèm câu giải thích cho người đối chiếu. */
    private final List<String> vuongMac;

    private DuplicateMergePlan(Map<String, String> maThayThe, Map<String, UUID> gopVaoHoSoDaCo,
                               Map<String, List<String>> daGomVao, List<String> vuongMac) {
        this.maThayThe = Map.copyOf(maThayThe);
        this.gopVaoHoSoDaCo = Map.copyOf(gopVaoHoSoDaCo);
        this.daGomVao = Map.copyOf(daGomVao);
        this.vuongMac = List.copyOf(vuongMac);
    }

    /** Kế hoạch rỗng — lô không có cặp nào được quyết là "gộp". */
    public static DuplicateMergePlan trong() {
        return new DuplicateMergePlan(Map.of(), Map.of(), Map.of(), List.of());
    }

    /**
     * Dựng kế hoạch từ các cặp đã quyết và tập dòng chờ.
     *
     * @param pairs mọi cặp của lô; chỉ {@link DuplicateDecision#MERGED} có tác dụng ở đây. Cặp
     *        {@code DISTINCT} <b>không</b> sinh gì cả — đó chính là nghĩa của nó: cả hai cùng vào
     *        phả, không ai bị bỏ.
     * @param rows các dòng Nhân khẩu <b>đã qua bước đối soát</b> với {@code person_external_ref}
     */
    public static DuplicateMergePlan cua(List<DuplicatePair> pairs, List<PersonRow> rows) {
        Map<String, PersonRow> theoMa = new LinkedHashMap<>();
        for (PersonRow row : rows) {
            if (row.externalCode() != null && !row.externalCode().isBlank()) {
                theoMa.putIfAbsent(row.externalCode(), row);
            }
        }

        HopNhat nhom = new HopNhat();
        Map<String, Set<UUID>> nguoiCuaMa = new LinkedHashMap<>();
        boolean coGopNao = false;

        for (DuplicatePair cap : pairs) {
            if (!cap.laGop() || !theoMa.containsKey(cap.incomingCode())) {
                continue;
            }
            if (cap.kind() == DuplicatePair.Kind.FILE) {
                // Dong kia da bien mat khoi tep (nguoi nhap xoa no roi tai lai) thi khong con gi
                // de gop — bo qua lang le la dung, cap se tu bien mat o lan kiem sau.
                if (!theoMa.containsKey(cap.existingCode())) {
                    continue;
                }
                nhom.gop(cap.incomingCode(), cap.existingCode());
                coGopNao = true;
            } else if (cap.existingPersonId() != null) {
                nguoiCuaMa.computeIfAbsent(cap.incomingCode(), k -> new LinkedHashSet<>())
                        .add(cap.existingPersonId());
                coGopNao = true;
            }
        }
        if (!coGopNao) {
            return trong();
        }

        // Ma da co chu san trong pha cung la mot rang buoc cua nhom, nhung CHI xet cho nhung ma
        // dang dinh liu toi mot quyet dinh gop. Keo ca 400 dong vao day thi mot lo binh thuong
        // cung phai chay thuat toan hop nhat cho khong.
        for (String ma : new ArrayList<>(nguoiCuaMa.keySet())) {
            themChuSan(nguoiCuaMa, theoMa.get(ma));
        }
        for (String ma : nhom.cacPhanTu()) {
            themChuSan(nguoiCuaMa, theoMa.get(ma));
        }

        // Hai nhom cung tro toi mot nguoi trong pha thi chinh chung la mot nhom: chay toi khi on
        // dinh. So vong lap bi chan boi so nhom nen no luon dung.
        gopCacNhomChungMotNguoi(nhom, nguoiCuaMa);

        // --- Chot dai dien cho tung nhom ---
        Map<String, List<String>> cacNhom = new TreeMap<>();
        for (String ma : nhom.cacPhanTu()) {
            cacNhom.computeIfAbsent(nhom.tim(ma), k -> new ArrayList<>()).add(ma);
        }
        for (String ma : nguoiCuaMa.keySet()) {
            cacNhom.computeIfAbsent(nhom.tim(ma), k -> new ArrayList<>());
            if (!cacNhom.get(nhom.tim(ma)).contains(ma)) {
                cacNhom.get(nhom.tim(ma)).add(ma);
            }
        }

        Map<String, String> thayThe = new LinkedHashMap<>();
        Map<String, UUID> gopVao = new LinkedHashMap<>();
        Map<String, List<String>> gomVao = new LinkedHashMap<>();
        List<String> vuong = new ArrayList<>();

        for (List<String> thanhVien : cacNhom.values()) {
            List<String> co = new ArrayList<>();
            for (String ma : thanhVien) {
                if (theoMa.containsKey(ma)) {
                    co.add(ma);
                }
            }
            if (co.isEmpty()) {
                continue;
            }
            co.sort((a, b) -> Integer.compare(theoMa.get(a).rowNo(), theoMa.get(b).rowNo()));

            Set<UUID> nguoi = new LinkedHashSet<>();
            for (String ma : co) {
                nguoi.addAll(nguoiCuaMa.getOrDefault(ma, Set.of()));
            }
            if (nguoi.size() > 1) {
                // KHONG tu chon mot trong hai. Chon bua o day la hop nhat hai nhanh con chau vao
                // mot node sai, va sau ba muoi ngay thi khong hoan tac duoc nua.
                vuong.add("Các dòng " + String.join(", ", co) + " được quyết là cùng một người,"
                        + " nhưng chúng lại dính tới " + nguoi.size() + " hồ sơ khác nhau đã có"
                        + " trong phả. Hợp nhất hai hồ sơ đã có trong phả là việc của màn quản lý"
                        + " nhân khẩu, không phải của đường nhập liệu — hãy hợp nhất bên đó trước"
                        + " rồi kiểm lại lô này.");
                continue;
            }

            String oLai = chonNguoiOLai(co, theoMa);
            List<String> biBo = new ArrayList<>();
            for (String ma : co) {
                if (!ma.equals(oLai)) {
                    thayThe.put(ma, oLai);
                    biBo.add(ma);
                }
            }
            if (!biBo.isEmpty()) {
                gomVao.put(oLai, List.copyOf(biBo));
            }
            if (nguoi.size() == 1) {
                UUID personId = nguoi.iterator().next();
                // Chi ghi khi dong o lai CHUA co chu: neu no da la UPDATE san thi khoa bat bien
                // da ton tai va khong co gi phai lam them.
                if (theoMa.get(oLai).resolvedPersonId() == null) {
                    gopVao.put(oLai, personId);
                }
            }
        }
        return new DuplicateMergePlan(thayThe, gopVao, gomVao, vuong);
    }

    private static void themChuSan(Map<String, Set<UUID>> nguoiCuaMa, PersonRow row) {
        if (row != null && row.resolvedPersonId() != null) {
            nguoiCuaMa.computeIfAbsent(row.externalCode(), k -> new LinkedHashSet<>())
                    .add(row.resolvedPersonId());
        }
    }

    private static void gopCacNhomChungMotNguoi(HopNhat nhom, Map<String, Set<UUID>> nguoiCuaMa) {
        boolean doi = true;
        while (doi) {
            doi = false;
            Map<UUID, String> daiDienCuaNguoi = new HashMap<>();
            for (Map.Entry<String, Set<UUID>> e : nguoiCuaMa.entrySet()) {
                for (UUID personId : e.getValue()) {
                    String cu = daiDienCuaNguoi.putIfAbsent(personId, e.getKey());
                    if (cu != null && !nhom.tim(cu).equals(nhom.tim(e.getKey()))) {
                        nhom.gop(cu, e.getKey());
                        doi = true;
                    }
                }
            }
        }
    }

    /** Dòng đã có chủ trong phả ở lại; nếu không có thì dòng có số dòng nhỏ nhất ở lại. */
    private static String chonNguoiOLai(List<String> co, Map<String, PersonRow> theoMa) {
        for (String ma : co) {
            if (theoMa.get(ma).resolvedPersonId() != null) {
                return ma;
            }
        }
        return co.get(0);
    }

    // ---------------------------------------------------------------------------------------
    // Đọc kế hoạch
    // ---------------------------------------------------------------------------------------

    public boolean rong() {
        return maThayThe.isEmpty() && gopVaoHoSoDaCo.isEmpty();
    }

    /** Mã bị bỏ → mã ở lại. */
    public Map<String, String> maThayThe() {
        return maThayThe;
    }

    /** Mã ở lại → nhân khẩu đã có trong phả mà lô này phải <b>cập nhật</b> thay vì tạo mới. */
    public Map<String, UUID> gopVaoHoSoDaCo() {
        return gopVaoHoSoDaCo;
    }

    /**
     * Nhóm gộp không áp dụng được, kèm câu giải thích.
     *
     * <p>Bước ghi dừng lại khi danh sách này không rỗng. Dừng có giải thích còn hơn ghi một nửa:
     * một phép gộp áp dụng dở nghĩa là vài dòng đã bị trỏ lại còn vài dòng thì chưa.</p>
     */
    public List<String> vuongMac() {
        return vuongMac;
    }

    public boolean coVuongMac() {
        return !vuongMac.isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Áp dụng
    // ---------------------------------------------------------------------------------------

    /**
     * Áp kế hoạch lên <b>kết quả đối soát</b> — thứ người nhập nhìn thấy ở màn xem trước.
     *
     * <p>Không đụng tới mã tham chiếu và không bỏ dòng nào: khu vực chờ phải giữ nguyên tệp người
     * ta nộp, kể cả những dòng sẽ bị bỏ. Chỗ này chỉ trả lời câu "dòng này rồi sẽ làm gì":</p>
     * <ul>
     *   <li>dòng bị bỏ vì gộp → {@link PlannedAction#SKIP};</li>
     *   <li>dòng gộp vào hồ sơ đã có → {@link PlannedAction#UPDATE} kèm {@code resolvedPersonId}.
     *       Đây là chỗ {@code CREATE} đổi thành {@code UPDATE}.</li>
     * </ul>
     */
    public List<PersonRow> apLenDoiSoat(List<PersonRow> rows) {
        if (rong()) {
            return rows;
        }
        List<PersonRow> ketQua = new ArrayList<>(rows.size());
        for (PersonRow row : rows) {
            String ma = row.externalCode();
            if (ma != null && maThayThe.containsKey(ma)) {
                ketQua.add(row.withResolution(null, PlannedAction.SKIP));
            } else if (ma != null && gopVaoHoSoDaCo.containsKey(ma)) {
                ketQua.add(row.withResolution(gopVaoHoSoDaCo.get(ma), PlannedAction.UPDATE));
            } else {
                ketQua.add(row);
            }
        }
        return List.copyOf(ketQua);
    }

    /** Tập dòng sau khi gộp — đầu vào thật của bước ghi. */
    public record SauGop(List<PersonRow> personRows, List<MarriageRow> marriageRows) {
    }

    /**
     * Áp kế hoạch lên tập dòng <b>trước khi ghi</b>: bỏ dòng, hút dữ kiện, trỏ lại mọi mã.
     *
     * <h2>Thứ tự các bước không đổi được</h2>
     * <ol>
     *   <li>Dòng ở lại <b>hút</b> ô trống từ các dòng bị bỏ ({@link PersonRow#donNhan}) —
     *       <b>trước</b> khi trỏ lại, để mã cha mà nó vừa nhận cũng được trỏ lại ở bước sau.</li>
     *   <li>Bỏ các dòng bị gộp.</li>
     *   <li>Trỏ lại mã cha / mã mẹ / mã kế tự của <b>mọi</b> dòng còn lại, và mã chồng / mã vợ của
     *       mọi dòng hôn phối.</li>
     *   <li>Dọn những thứ phép trỏ lại vừa làm suy biến: tự làm cha chính mình, và hôn phối mà
     *       chồng với vợ hoá ra là một người. Không dọn thì lô chết ở bước ghi với một lỗi ràng
     *       buộc khó hiểu, sau khi đã chạy xong phần việc nặng nhất.</li>
     * </ol>
     */
    public SauGop apDung(List<PersonRow> rows, List<MarriageRow> marriages) {
        if (rong()) {
            return new SauGop(rows, marriages);
        }
        Map<String, PersonRow> theoMa = new LinkedHashMap<>();
        for (PersonRow row : rows) {
            if (row.externalCode() != null) {
                theoMa.putIfAbsent(row.externalCode(), row);
            }
        }

        List<PersonRow> giuLai = new ArrayList<>(rows.size());
        for (PersonRow row : rows) {
            String ma = row.externalCode();
            if (ma == null || maThayThe.containsKey(ma)) {
                // Dong bi bo. Du kien cua no khong mat: no da duoc hut sang dong o lai ngay duoi.
                continue;
            }
            PersonRow daGop = row;
            for (String maBo : daGomVao.getOrDefault(ma, List.of())) {
                daGop = daGop.donNhan(theoMa.get(maBo));
            }
            UUID gopVao = gopVaoHoSoDaCo.get(ma);
            if (gopVao != null) {
                daGop = daGop.withResolution(gopVao, PlannedAction.UPDATE);
            } else if (daGop.resolvedPersonId() != null
                    && daGop.plannedAction() == PlannedAction.SKIP) {
                daGop = daGop.withResolution(daGop.resolvedPersonId(), PlannedAction.UPDATE);
            }
            giuLai.add(daGop.withThamChieu(
                    tuChoiTuTro(ma, doi(daGop.fatherCode())),
                    tuChoiTuTro(ma, doi(daGop.motherCode())),
                    tuChoiTuTro(ma, doi(daGop.heirOfCode()))));
        }

        List<MarriageRow> honPhoi = new ArrayList<>(marriages.size());
        Set<String> daCo = new LinkedHashSet<>();
        for (MarriageRow m : marriages) {
            String chong = doi(m.husbandCode());
            String vo = doi(m.wifeCode());
            if (chong != null && chong.equals(vo)) {
                // Hai dong hon phoi cua cung mot nguoi voi hai ma cua CUNG mot ba: sau khi tro lai
                // thanh "tu ket hon voi chinh minh". Bo han, khong co canh SPOUSE nao duoc sinh ra.
                continue;
            }
            if (chong != null && vo != null && !daCo.add(chong + "|" + vo)) {
                // Hai dong hon phoi tro thanh MOT sau khi tro lai. Giu dong dau (so dong nho nhat),
                // bo dong sau: hai canh SPOUSE trung nhau se dam vao ux_relationship_spouse_order.
                continue;
            }
            honPhoi.add(m.withCap(chong, vo));
        }
        return new SauGop(List.copyOf(giuLai), List.copyOf(honPhoi));
    }

    private String doi(String ma) {
        if (ma == null || ma.isBlank()) {
            return ma;
        }
        return maThayThe.getOrDefault(ma, ma);
    }

    /** Sau khi trỏ lại, một dòng có thể hoá ra đang trỏ tới chính nó — bỏ hẳn mã ấy đi. */
    private static String tuChoiTuTro(String cuaMinh, String ma) {
        return ma != null && ma.equals(cuaMinh) ? null : ma;
    }

    // ---------------------------------------------------------------------------------------

    /** Hợp nhất kiểu union–find trên mã, đủ dùng cho vài chục nhóm. */
    private static final class HopNhat {

        private final Map<String, String> cha = new LinkedHashMap<>();

        String tim(String x) {
            String p = cha.putIfAbsent(x, x);
            if (p == null || p.equals(x)) {
                return x;
            }
            String goc = tim(p);
            cha.put(x, goc);
            return goc;
        }

        void gop(String a, String b) {
            String ga = tim(a);
            String gb = tim(b);
            if (!ga.equals(gb)) {
                // Goc nho hon theo thu tu chuoi luon thang: nho vay ket qua khong phu thuoc vao
                // thu tu nguoi dung bam ba muoi cap, va hai lan chay cho ra cung mot ke hoach.
                if (ga.compareTo(gb) <= 0) {
                    cha.put(gb, ga);
                } else {
                    cha.put(ga, gb);
                }
            }
        }

        Set<String> cacPhanTu() {
            return cha.keySet();
        }
    }
}

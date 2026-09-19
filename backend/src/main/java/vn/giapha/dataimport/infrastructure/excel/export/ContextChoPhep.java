package vn.giapha.dataimport.infrastructure.excel.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <b>Danh sách khoá được phép của {@code import_issue.context}, theo từng mã lỗi</b> — chốt riêng
 * tư cuối cùng trước khi một giá trị trở thành chữ trong một tệp {@code .xlsx} nằm trên máy người
 * khác.
 *
 * <h2>Vì sao lại có chốt thứ hai, khi tầng api đã có một chốt</h2>
 * {@code ImportIssueRedactor} canh <b>hai</b> mã ({@code IMP_SUSPECT_DUPLICATE},
 * {@code IMP_TABOO_COLLISION}) — đúng hai mã từng rò tên và năm sinh người trong phả. Hai mươi mã
 * còn lại đi qua nó <b>nguyên vẹn</b>, và với một phản hồi JSON thì điều đó chấp nhận được: giao
 * diện chỉ đọc vài khoá nó biết trước, khoá lạ rơi vào khoảng không.
 *
 * <p>Một cột "Chi tiết" trong Excel thì <b>không</b> có khoảng không ấy. Cách viết tự nhiên là
 * duyệt cả map rồi nối thành chữ, và lúc đó mọi khoá đều thành chữ — kể cả khoá không ai lường
 * trước. Đó chính là tình huống phải chặn: bên ghi {@code context} và bên đọc nó bị ngăn cách bởi
 * một cơ sở dữ liệu, tức bởi <b>thời gian</b>. Dòng đang đọc có thể do một bản nhị phân cũ hơn ghi
 * ra (triển khai cuốn chiếu, khôi phục bản sao lưu, hoặc một lô đã kiểm từ trước lần vá riêng tư
 * gần nhất và chưa ai kiểm lại). Bên đọc <b>không được giả định</b> bên ghi là mã nguồn hiện
 * tại.</p>
 *
 * <h2>Danh sách trắng, không phải danh sách đen</h2>
 * Mã lạ (một {@code IssueCode} mới thêm mà quên khai ở đây) ra <b>rỗng</b>, không ra "mọi khoá".
 * Đánh đổi có ý thức: cột Chi tiết của mã mới sẽ trống cho tới khi có người khai nó — một thiệt hại
 * nhìn thấy được và sửa trong năm phút. Chiều ngược lại, mặc định cho qua, hỏng theo kiểu không ai
 * nhìn thấy: một khoá mới mang tên người trong phả lặng lẽ có mặt trong mọi tệp tải về.
 *
 * <h2>Từng khoá dưới đây là dữ liệu của ai</h2>
 * Chỉ hai loại được vào danh sách:
 * <ul>
 *   <li><b>Thứ người nhập vừa gõ</b> — mã, năm sinh, ngày mất âm, đời, nguyên quán... đọc ra từ
 *       chính tệp họ vừa nộp. Giấu đi chỉ làm họ không đối chiếu được.</li>
 *   <li><b>Khoá trỏ sang phả</b> ({@code personId}, {@code bacTrenId}) — một UUID, không mang một
 *       trường nhân khẩu nào. Nó phải có mặt: nó là đường duy nhất để người đối chiếu mở
 *       {@code GET /api/v1/persons/&#123;id&#125;} và xem phần mình <i>được phép</i> xem.</li>
 * </ul>
 * Không khoá nào mang một <b>giá trị đọc từ phả</b>. {@code ten}, {@code doi}, {@code giaiThich}
 * của vế "hồ sơ trong phả" nằm ngoài danh sách một cách cố ý.
 */
final class ContextChoPhep {

    private ContextChoPhep() {
    }

    /** Khoá {@code context} chở danh sách mã gần giống — có cột riêng, không vào cột Chi tiết. */
    static final String GOI_Y = "goiY";

    /** Khoá {@code context} của cảnh báo nghi trùng: một mảng ứng viên, mỗi ứng viên một map. */
    private static final String NGHI_NGO = "nghiNgo";

    /** Khoá được phép, theo mã lỗi. Mã không có mặt ở đây ra cột Chi tiết rỗng. */
    private static final Map<String, Set<String>> THEO_MA = Map.ofEntries(
            // --- Loi chan cua bo kiem ---
            Map.entry("IMP_PARENT_NOT_FOUND", Set.of("maKhongTimThay", "vaiTro", GOI_Y)),
            Map.entry("IMP_CYCLE", Set.of("chuoi")),
            Map.entry("IMP_SELF_PARENT", Set.of("ma")),
            Map.entry("IMP_CHILD_BEFORE_PARENT", Set.of("namSinhCon", "namSinhCha", "maCha")),
            Map.entry("IMP_LUNAR_DATE_NOT_EXIST", Set.of("ngay", "nam", "thangNhuan")),
            Map.entry("IMP_LUNAR_DATE_UNPARSEABLE", Set.of("oGoc")),
            Map.entry("IMP_LUNAR_DATE_IS_SERIAL", Set.of("oGoc")),
            Map.entry("IMP_DUP_CODE", Set.of("ma", "cacDong")),
            Map.entry("IMP_MISSING_CODE", Set.of()),
            Map.entry("IMP_MISSING_NAME", Set.of("ma")),
            Map.entry("IMP_BAD_CODE_FORMAT", Set.of("ma", "chiSoHuu")),
            Map.entry("IMP_ALIVE_WITH_DEATH", Set.of("ngayMatAm")),
            Map.entry("IMP_GENERATION_MISMATCH", Set.of("doiKhai", "doiSuyRa", "maCha")),
            Map.entry("IMP_SPOUSE_ORDER_CONFLICT", Set.of("maChong", "bac", "cacVo", "cacDong")),
            Map.entry("IMP_MASS_CREATE_GUARD", Set.of("soTao", "tongDong", "tiLe", "nguong")),

            // --- Loi chan phat sinh o buoc ghi ---
            Map.entry("IMP_COMMIT_CYCLE", Set.of("ma")),
            Map.entry("IMP_COMMIT_REF_NOT_FOUND", Set.of("ma")),
            Map.entry("IMP_UNDECIDED_INLAW_DOI", Set.of("externalCode", "doiDaKhai")),
            Map.entry("IMP_UNDECIDED_ADOPTION", Set.of("externalCode", "fatherCode", "motherCode")),
            Map.entry("IMP_UNDECIDED_LIFE_STATUS", Set.of("externalCode")),

            // --- Canh bao ---
            // nghiNgo di duong rieng (moTaNghiNgo); de no o day de phep duyet nhan ra ma nay CO
            // duoc khai, khac voi mot ma la.
            Map.entry("IMP_SUSPECT_DUPLICATE", Set.of(NGHI_NGO)),
            // tenHuy la o Ten huy cua CHINH DONG DANG NHAP, khong phai ten huy doc tu pha — xem
            // javadoc TabooCollisionRule, do la mot cai bay da co nguoi sap.
            Map.entry("IMP_TABOO_COLLISION", Set.of("tenHuy", "bacTrenId")),
            Map.entry("IMP_MISSING_GIO", Set.of()),
            Map.entry("IMP_GIO_DAY_30", Set.of("thang")),
            Map.entry("IMP_LONE_NODE", Set.of()),
            Map.entry("IMP_UNKNOWN_GENDER", Set.of()),
            Map.entry("IMP_MISSING_MOTHER", Set.of()),
            Map.entry("IMP_ROW_DISAPPEARED", Set.of("soMaVang", "maVang")),
            Map.entry("IMP_UNKNOWN_PLACE_CODE", Set.of("ma")),
            Map.entry("IMP_MISSING_PLACE_CODE", Set.of("nguyenQuan")),
            Map.entry("IMP_HEIR_TARGET_NOT_FOUND", Set.of("maKhongTimThay", GOI_Y)));

    /**
     * Khoá được phép của <b>một ứng viên nghi trùng đã có trong phả</b> ({@code personId != null}).
     *
     * <p>Giống hệt danh sách của {@code ImportIssueRedactor}, và đó là chủ ý: hai chốt phải nói
     * cùng một điều, nếu không thì tệp Excel và màn đối chiếu hiện hai mức dữ liệu khác nhau. Chép
     * lại chứ không dùng chung, vì lớp kia là {@code package-private} của {@code api.rest} — mượn
     * nó xuống đây là dựng đúng cái vòng phụ thuộc {@code infrastructure → api} mà kiến trúc
     * cấm.</p>
     */
    private static final Set<String> UNG_VIEN_TRONG_PHA = Set.of("personId", "diem", "tinHieu");

    /** Ứng viên nghi trùng là <b>một dòng khác trong chính tệp này</b>: toàn bộ là của người nhập. */
    private static final Set<String> UNG_VIEN_TRONG_TEP =
            Set.of("ref", "ten", "doi", "diem", "giaiThich");

    /** Nhãn tiếng Việt của từng khoá. Khoá không có nhãn thì dùng chính tên khoá. */
    private static final Map<String, String> NHAN = Map.ofEntries(
            Map.entry("maKhongTimThay", "Mã không tìm thấy"),
            Map.entry("vaiTro", "Vai trò"),
            Map.entry("chuoi", "Đường vòng lặp"),
            Map.entry("ma", "Mã"),
            Map.entry("maCha", "Mã cha/mẹ"),
            Map.entry("maChong", "Mã chồng"),
            Map.entry("cacVo", "Các mã vợ"),
            Map.entry("bac", "Bậc"),
            Map.entry("cacDong", "Các dòng liên quan"),
            Map.entry("chiSoHuu", "Thuộc chi"),
            Map.entry("namSinhCon", "Năm sinh con"),
            Map.entry("namSinhCha", "Năm sinh cha/mẹ"),
            Map.entry("doiKhai", "Đời đã khai"),
            Map.entry("doiSuyRa", "Đời hệ thống suy ra"),
            Map.entry("doiDaKhai", "Đời đã khai"),
            Map.entry("externalCode", "Mã"),
            Map.entry("fatherCode", "Mã cha"),
            Map.entry("motherCode", "Mã mẹ"),
            Map.entry("ngay", "Ngày âm đã gõ"),
            Map.entry("nam", "Năm"),
            Map.entry("thang", "Tháng"),
            Map.entry("thangNhuan", "Tháng nhuận"),
            Map.entry("oGoc", "Ô gốc trong tệp"),
            Map.entry("ngayMatAm", "Ngày mất âm"),
            Map.entry("nguyenQuan", "Nguyên quán"),
            Map.entry("tenHuy", "Tên huý đã gõ"),
            Map.entry("bacTrenId", "Hồ sơ bậc trên"),
            Map.entry("soMaVang", "Số mã vắng"),
            Map.entry("maVang", "Mã vắng"),
            Map.entry("soTao", "Số dòng sẽ tạo mới"),
            Map.entry("tongDong", "Tổng số dòng"),
            Map.entry("tiLe", "Tỉ lệ (%)"),
            Map.entry("nguong", "Ngưỡng (%)"));

    // -------------------------------------------------------------------------------------
    // Hai cot ma bo xuat dung tu context
    // -------------------------------------------------------------------------------------

    /**
     * Cột <b>"Gợi ý mã gần giống"</b>.
     *
     * <p>Có cột riêng chứ không nằm lẫn trong Chi tiết, vì nó là thứ <b>duy nhất</b> trong cả tệp
     * sửa được lỗi ngay tại chỗ: gõ nhầm {@code AT-01-OO3} (hai chữ O) thay vì {@code AT-01-003} là
     * lỗi phổ biến nhất của cả đường ống, và câu trả lời nằm sẵn ở đây. Chìm trong một ô ghi chú
     * dài thì không ai đọc.</p>
     */
    static String goiY(String code, Map<String, Object> context) {
        if (context == null || !duocPhep(code, GOI_Y)) {
            return null;
        }
        return chuoiHoa(context.get(GOI_Y));
    }

    /**
     * Cột <b>"Chi tiết"</b>: mọi khoá được phép còn lại, dạng {@code Nhãn: giá trị}, ngăn bằng
     * {@code " · "}.
     *
     * <p>Khoá ngoài danh sách bị bỏ <b>lặng lẽ</b> — không ghi "(đã ẩn 1 trường)". Câu ấy nghe có
     * vẻ minh bạch nhưng tự nó là một rò rỉ: nó xác nhận rằng hồ sơ bên kia <b>có</b> trường đó, mà
     * với một người còn sống thì chính sự tồn tại của dữ liệu đã là điều phải giấu.</p>
     */
    static String chiTiet(String code, Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        if (duocPhep(code, NGHI_NGO)) {
            return moTaNghiNgo(context.get(NGHI_NGO));
        }
        Set<String> choPhep = THEO_MA.getOrDefault(code, Set.of());
        List<String> manh = new ArrayList<>();
        context.forEach((khoa, giaTri) -> {
            if (GOI_Y.equals(khoa) || giaTri == null || !choPhep.contains(khoa)) {
                return;
            }
            String v = chuoiHoa(giaTri);
            if (v != null && !v.isBlank()) {
                manh.add(NHAN.getOrDefault(khoa, khoa) + ": " + v);
            }
        });
        return manh.isEmpty() ? null : String.join(" · ", manh);
    }

    // -------------------------------------------------------------------------------------
    // Nghi trung: hai ve, hai muc du lieu
    // -------------------------------------------------------------------------------------

    /**
     * Vế <b>trong tệp</b> ra đủ mã/tên/đời/điểm; vế <b>trong phả</b> ra đúng một khoá và điểm.
     *
     * <p>Phân biệt bằng sự có mặt của {@code personId} — <b>không</b> bằng khoá {@code nguon}.
     * {@code nguon} là một nhãn do bộ kiểm ghi, và một dòng dữ liệu cũ có thể thiếu nó hoặc ghi sai
     * nó; {@code personId} thì chính là thứ đang phải bảo vệ, nên hỏi thẳng nó là phép kiểm không
     * phụ thuộc vào việc bên ghi có đúng phiên bản hay không.</p>
     */
    private static String moTaNghiNgo(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<String> ve = new ArrayList<>(list.size());
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> ungVien = ep(map);
            String personId = chuoiHoa(ungVien.get("personId"));
            ve.add(personId != null ? veTrongPha(ungVien, personId) : veTrongTep(ungVien));
        }
        ve.removeIf(s -> s == null || s.isBlank());
        return ve.isEmpty() ? null : String.join(" · ", ve);
    }

    /** Khoá + điểm + nhãn tín hiệu. Không tên, không đời, không lời giải thích mang giá trị trường. */
    private static String veTrongPha(Map<String, Object> ungVien, String personId) {
        StringBuilder sb = new StringBuilder("Hồ sơ trong phả: ").append(personId);
        String diem = giaTriChoPhep(ungVien, "diem", UNG_VIEN_TRONG_PHA);
        if (diem != null) {
            sb.append(" (").append(diem).append(" điểm)");
        }
        String tinHieu = giaTriChoPhep(ungVien, "tinHieu", UNG_VIEN_TRONG_PHA);
        if (tinHieu != null) {
            sb.append(", khớp ở: ").append(tinHieu);
        }
        return sb.toString();
    }

    private static String veTrongTep(Map<String, Object> ungVien) {
        String ref = giaTriChoPhep(ungVien, "ref", UNG_VIEN_TRONG_TEP);
        String ten = giaTriChoPhep(ungVien, "ten", UNG_VIEN_TRONG_TEP);
        String diem = giaTriChoPhep(ungVien, "diem", UNG_VIEN_TRONG_TEP);
        String vi = giaTriChoPhep(ungVien, "giaiThich", UNG_VIEN_TRONG_TEP);
        StringBuilder sb = new StringBuilder("Dòng khác trong tệp: ").append(ref == null ? "?" : ref);
        if (ten != null) {
            sb.append(" (").append(ten).append(")");
        }
        if (diem != null) {
            sb.append(" — ").append(diem).append(" điểm");
        }
        if (vi != null) {
            sb.append(", khớp ở: ").append(vi);
        }
        return sb.toString();
    }

    private static String giaTriChoPhep(Map<String, Object> nguon, String khoa, Set<String> choPhep) {
        return choPhep.contains(khoa) ? chuoiHoa(nguon.get(khoa)) : null;
    }

    // -------------------------------------------------------------------------------------
    // Tien ich
    // -------------------------------------------------------------------------------------

    private static boolean duocPhep(String code, String khoa) {
        return THEO_MA.getOrDefault(code, Set.of()).contains(khoa);
    }

    /**
     * Một giá trị JSONB thành chữ.
     *
     * <p>Số nguyên đi qua Jackson thành {@code Integer}, nhưng số thực thành {@code Double} và
     * {@code Objects.toString} cho ra {@code "1945.0"}. Cắt đuôi {@code .0} — không phải để đẹp:
     * cột này chở năm sinh và số dòng, và một "dòng 137.0" làm người đọc ngờ cả bảng.</p>
     */
    private static String chuoiHoa(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? "Có" : "Không";
        }
        if (value instanceof Double d && !d.isInfinite() && !d.isNaN() && d == Math.floor(d)) {
            return String.valueOf((long) (double) d);
        }
        if (value instanceof List<?> list) {
            List<String> phan = new ArrayList<>(list.size());
            for (Object o : list) {
                String s = chuoiHoa(o);
                if (s != null && !s.isBlank()) {
                    phan.add(s);
                }
            }
            return phan.isEmpty() ? null : String.join(", ", phan);
        }
        String s = Objects.toString(value, null);
        return s == null || s.isBlank() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ep(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}

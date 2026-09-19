package vn.giapha.membership.domain.event;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Hợp đồng đóng</b> của {@code change_request.payload} — nội dung một đề nghị đính chính.
 *
 * <h2>Vì sao lớp này nằm trong gói {@code domain.event}</h2>
 * Nó phải đọc được từ <b>hai phía</b>: {@code membership} kiểm lúc <b>gửi</b>, {@code genealogy}
 * kiểm lại lúc <b>áp dụng</b>. Chỉ {@code membership.domain.event} mang {@code @NamedInterface} nên
 * đây là gói duy nhất mà {@code genealogy} nhìn thấy được mà không làm {@code ModularityTests} đỏ.
 * Đặt ở {@code membership.domain} thì {@code genealogy} phải chép lại danh sách trường — và hai bản
 * chép sẽ lệch nhau đúng vào lúc không ai để ý.
 *
 * <h2>Kiểm lúc GỬI, không phải lúc DUYỆT</h2>
 * Trước đây payload là "dạng tự do" và không ai kiểm gì. Hệ quả: một đề nghị sai khoá nằm im trong
 * hàng đợi cả tuần, rồi Trưởng chi mới là người phát hiện — mà lúc ấy người gửi đã quên mình gõ gì.
 * Cửa kiểm vì thế đặt ngay ở {@code ChangeRequestService.submit}: sai thì người gửi biết ngay khi
 * còn đang mở biểu mẫu. Bên {@code genealogy} kiểm lại lần nữa như lưới cuối, phòng dữ liệu cũ hoặc
 * một lối ghi khác lọt vào.
 *
 * <h2>Khoá = tên trường của {@code UpdatePersonRequest}</h2>
 * Quy ước này đã được frontend chốt trong {@code correctable-fields.ts}; ở đây nó thành luật có
 * hiệu lực. Sáu trường phủ gần hết những gì người trong nhà thật sự phát hiện được, dẫn đầu là
 * <b>ngày mất</b> — ghi sai ngày giỗ là sai lệch nặng nhất một cuốn gia phả có thể mắc. Việc đổi
 * tên húy/tự/hiệu, quan hệ cha–con hay chuyển chi đi qua loại {@code OTHER}: mô tả bằng lời cho
 * Trưởng chi đọc và tự thao tác, vì một biểu mẫu một-trường không diễn đạt nổi.
 *
 * <h2>{@code _baseVersion} — khoá điều khiển, bắt buộc</h2>
 * Đề nghị nằm chờ hàng tuần rồi mới được áp dụng. Không có mốc phiên bản thì lệnh ghi lúc duyệt là
 * một cú <b>ghi đè mù</b> lên mọi thay đổi đã xảy ra trong lúc chờ. {@code _baseVersion} là
 * {@code person.version} tại thời điểm người gửi <i>nhìn thấy</i> hồ sơ; lúc áp dụng nó trở thành
 * {@code expectedVersion} của {@code UpdatePersonCommand}, và lệch thì đề nghị bị chặn chứ không
 * âm thầm đè.
 *
 * <p>Tiền tố {@code _} tách nó khỏi không gian tên trường hồ sơ: không có và sẽ không có trường nào
 * của {@code UpdatePersonRequest} bắt đầu bằng dấu gạch dưới.</p>
 */
public final class CorrectionPayload {

    /** Mốc phiên bản hồ sơ mà đề nghị dựa trên. Khoá điều khiển, không phải trường hồ sơ. */
    public static final String BASE_VERSION_KEY = "_baseVersion";

    /** Loại yêu cầu duy nhất mà Giai đoạn 1 có bộ áp dụng tự động. */
    public static final String TYPE_UPDATE_PERSON = "UPDATE_PERSON";

    /**
     * Sáu trường đính chính được, tên khớp từng ký tự với {@code UpdatePersonRequest}.
     *
     * <p>Thêm một trường ở đây <b>bắt buộc</b> phải thêm nhánh dịch tương ứng trong
     * {@code ChangeRequestApplier}, nếu không đề nghị sẽ qua được cửa kiểm rồi bị bỏ qua lặng lẽ
     * lúc áp dụng — đúng cái lỗi mà bộ này sinh ra để chấm dứt.</p>
     */
    public static final Set<String> UPDATE_PERSON_FIELDS = Set.of(
            "death", "birth", "nativePlace", "occupation", "currentPlaceProvince", "gender");

    /**
     * Giới tính hợp lệ — <b>khớp {@code ck_person_gender} của V2</b>, không khớp enum
     * {@code Gender}.
     *
     * <p>Enum có thêm {@code OTHER} nhưng cột {@code person.gender} thì không nhận. Chấp nhận
     * {@code OTHER} ở đây nghĩa là đề nghị đi lọt qua cả cửa gửi lẫn cửa duyệt rồi mới chết ở ràng
     * buộc CHECK — mà Postgres huỷ luôn phần còn lại của transaction, nên Trưởng chi nhận một lỗi
     * 500 không hiểu nổi.</p>
     */
    public static final Set<String> ALLOWED_GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    /** Mức chính xác hợp lệ của một mốc song lịch — khớp {@code DatePrecision}. */
    public static final Set<String> ALLOWED_PRECISIONS = Set.of("DAY", "MONTH", "YEAR", "UNKNOWN");

    /** {@code person.native_place} là {@code VARCHAR(255)}; các trường chữ khác cùng mức cho gọn. */
    public static final int MAX_TEXT_LENGTH = 255;

    private static final Set<String> DATE_FIELDS = Set.of("death", "birth");
    private static final Set<String> DATE_KEYS = Set.of("solar", "lunar", "precision");
    private static final Set<String> LUNAR_KEYS = Set.of("year", "month", "day", "leap");

    private CorrectionPayload() {
    }

    /** {@code true} nếu loại yêu cầu này có bộ áp dụng tự động ở {@code genealogy}. */
    public static boolean isApplicable(String requestType) {
        return TYPE_UPDATE_PERSON.equals(requestType);
    }

    /** Tên các trường hồ sơ trong payload, bỏ mọi khoá điều khiển. Đã sắp xếp. */
    public static List<String> fieldKeys(Map<String, Object> payload) {
        if (payload == null) {
            return List.of();
        }
        return payload.keySet().stream()
                .filter(key -> key != null && !key.startsWith("_"))
                .sorted()
                .toList();
    }

    /**
     * Mốc phiên bản trong payload, hoặc {@code null} nếu vắng mặt / không phải số nguyên không âm.
     *
     * <p>Không ném ngoại lệ: việc báo lỗi là của {@link #violations}, để bên gọi gom được
     * <b>tất cả</b> lỗi trong một lần trả lời thay vì bắt người dùng sửa từng cái một.</p>
     */
    public static Long baseVersion(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = payload.get(BASE_VERSION_KEY);
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value < 0 ? null : value;
        }
        if (raw instanceof String text) {
            try {
                long value = Long.parseLong(text.trim());
                return value < 0 ? null : value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Bản sao payload có {@code _baseVersion} được đóng dấu. Giữ nguyên thứ tự khoá gốc. */
    public static Map<String, Object> withBaseVersion(Map<String, Object> payload, long version) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (payload != null) {
            // KHONG dung Map.copyOf: payload hop le duoc phep chua gia tri null ("xoa truong nay").
            merged.putAll(payload);
        }
        merged.put(BASE_VERSION_KEY, version);
        return merged;
    }

    /**
     * Mọi vi phạm hợp đồng của một payload, mô tả bằng tiếng người.
     *
     * <p>Trả danh sách rỗng nghĩa là hợp lệ. Loại yêu cầu chưa có bộ áp dụng ({@code OTHER},
     * {@code ADD_RELATIONSHIP}…) không bị kiểm: payload của chúng là lời mô tả cho người đọc, và
     * siết một hợp đồng chưa ai hiện thực chỉ chặn người dùng mà không bảo vệ được gì.</p>
     *
     * @param requiresBaseVersion {@code true} khi kiểm bản đã lưu (phải có {@code _baseVersion});
     *        {@code false} khi kiểm bản người dùng vừa gửi, lúc backend còn kịp tự đóng dấu
     */
    public static List<String> violations(String requestType, Map<String, Object> payload,
                                          boolean requiresBaseVersion) {
        List<String> problems = new ArrayList<>();
        if (!isApplicable(requestType)) {
            return problems;
        }
        Map<String, Object> body = payload == null ? Map.of() : payload;

        List<String> fields = fieldKeys(body);
        if (fields.isEmpty()) {
            problems.add("De nghi UPDATE_PERSON phai neu it nhat mot truong can sua; cac truong "
                    + "dinh chinh duoc: " + joined(UPDATE_PERSON_FIELDS));
        }
        for (String field : fields) {
            if (!UPDATE_PERSON_FIELDS.contains(field)) {
                problems.add("Truong '" + field + "' khong nam trong danh muc dinh chinh duoc ("
                        + joined(UPDATE_PERSON_FIELDS)
                        + "). Nhung viec ngoai danh muc gui bang loai OTHER.");
                continue;
            }
            checkValue(field, body.get(field), problems);
        }
        for (String key : body.keySet()) {
            if (key != null && key.startsWith("_") && !BASE_VERSION_KEY.equals(key)) {
                problems.add("Khoa dieu khien '" + key + "' khong duoc cong nhan");
            }
        }

        boolean hasBaseVersionKey = body.containsKey(BASE_VERSION_KEY);
        if (hasBaseVersionKey && baseVersion(body) == null) {
            problems.add(BASE_VERSION_KEY + " phai la so nguyen khong am (lay tu ETag cua lan GET "
                    + "ho so gan nhat)");
        } else if (!hasBaseVersionKey && requiresBaseVersion) {
            problems.add("Thieu " + BASE_VERSION_KEY + ": khong co moc phien ban thi luc duyet se "
                    + "ghi de mu len thay doi cua nguoi khac");
        }
        return problems;
    }

    // -------------------------------------------------------------------------------------
    // Kiểm giá trị từng trường
    // -------------------------------------------------------------------------------------

    /**
     * {@code null} luôn hợp lệ ở mọi trường: đó là đề nghị <b>xoá trắng</b> — "bỏ ngày mất ghi
     * nhầm" là một đính chính hoàn toàn bình thường.
     */
    private static void checkValue(String field, Object value, List<String> problems) {
        if (value == null) {
            return;
        }
        if (DATE_FIELDS.contains(field)) {
            checkDate(field, value, problems);
            return;
        }
        if ("gender".equals(field)) {
            if (!(value instanceof String text) || !ALLOWED_GENDERS.contains(text)) {
                problems.add("gender phai la mot trong " + joined(ALLOWED_GENDERS)
                        + ", nhan duoc: " + value);
            }
            return;
        }
        if (!(value instanceof String text)) {
            problems.add("Truong '" + field + "' phai la chuoi, nhan duoc: "
                    + value.getClass().getSimpleName());
        } else if (text.length() > MAX_TEXT_LENGTH) {
            problems.add("Truong '" + field + "' vuot qua " + MAX_TEXT_LENGTH + " ky tu");
        }
    }

    /**
     * Mốc song lịch, đúng schema {@code DateDual} của contract.
     *
     * <p>Phải có ít nhất một trong hai lịch. Một đối tượng ngày rỗng hoàn toàn không phải là đề
     * nghị "xoá ngày" (cái đó là {@code null}) mà là một biểu mẫu điền dở.</p>
     */
    private static void checkDate(String field, Object value, List<String> problems) {
        if (!(value instanceof Map<?, ?> map)) {
            problems.add("Truong '" + field + "' phai la doi tuong DateDual "
                    + "{solar, lunar:{year,month,day,leap}, precision}, nhan duoc: "
                    + value.getClass().getSimpleName());
            return;
        }
        for (Object key : map.keySet()) {
            if (!DATE_KEYS.contains(String.valueOf(key))) {
                problems.add("Truong '" + field + "' co khoa la '" + key + "'");
            }
        }
        Object solar = map.get("solar");
        if (solar != null) {
            if (!(solar instanceof String text)) {
                problems.add(field + ".solar phai la chuoi ngay ISO yyyy-MM-dd");
            } else {
                try {
                    LocalDate.parse(text);
                } catch (DateTimeParseException ex) {
                    problems.add(field + ".solar khong phai ngay ISO yyyy-MM-dd: " + text);
                }
            }
        }
        Object lunar = map.get("lunar");
        if (lunar != null) {
            checkLunar(field, lunar, problems);
        }
        Object precision = map.get("precision");
        if (precision != null && !ALLOWED_PRECISIONS.contains(String.valueOf(precision))) {
            problems.add(field + ".precision phai la mot trong " + joined(ALLOWED_PRECISIONS));
        }
        if (solar == null && lunar == null) {
            problems.add("Truong '" + field + "' phai co it nhat mot trong hai lich (solar hoac "
                    + "lunar); muon xoa ngay thi gui gia tri null cho ca truong.");
        }
    }

    /**
     * Ngày âm lịch. {@code month} 1–12 và {@code day} 1–30 là ràng buộc của {@code LunarDate}, và
     * cờ {@code leap} là <b>bắt buộc phải đúng</b>: bỏ qua tháng nhuận thì giỗ lệch nguyên một
     * tháng mà không có lỗi nào được ném ra.
     */
    private static void checkLunar(String field, Object lunar, List<String> problems) {
        if (!(lunar instanceof Map<?, ?> map)) {
            problems.add(field + ".lunar phai la doi tuong {year, month, day, leap}");
            return;
        }
        for (Object key : map.keySet()) {
            if (!LUNAR_KEYS.contains(String.valueOf(key))) {
                problems.add(field + ".lunar co khoa la '" + key + "'");
            }
        }
        Integer month = intOrNull(map.get("month"));
        Integer day = intOrNull(map.get("day"));
        Integer year = intOrNull(map.get("year"));
        if (year == null) {
            problems.add(field + ".lunar.year phai la so nguyen");
        }
        if (month == null || month < 1 || month > 12) {
            problems.add(field + ".lunar.month phai trong khoang 1-12, nhan duoc: "
                    + map.get("month"));
        }
        if (day == null || day < 1 || day > 30) {
            problems.add(field + ".lunar.day phai trong khoang 1-30, nhan duoc: " + map.get("day"));
        }
        Object leap = map.get("leap");
        if (leap != null && !(leap instanceof Boolean)) {
            problems.add(field + ".lunar.leap phai la true/false");
        }
    }

    private static Integer intOrNull(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Danh sách trường sắp xếp ổn định — thông điệp lỗi phải giống nhau giữa các lần chạy. */
    private static String joined(Set<String> values) {
        return values.stream().sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}

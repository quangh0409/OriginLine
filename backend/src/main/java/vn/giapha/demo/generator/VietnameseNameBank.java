package vn.giapha.demo.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Kho tên tiếng Việt cho dữ liệu demo — tự dựng, cố ý KHÔNG dùng thư viện faker.
 *
 * <p>Faker không có bộ tên Việt tử tế: nó ghép âm tiết vô nghĩa, ra những cái tên mà không dòng họ
 * nào đặt cho con. Dữ liệu demo này sẽ được đem trình dòng họ, nên tên phải đọc lên nghe như tên
 * thật.</p>
 *
 * <h2>Quy tắc đặt tên đang mô phỏng</h2>
 * <ul>
 *   <li><b>Chữ đệm theo đời</b> lấy từ "bài thơ đặt tên" của dòng họ: mỗi đời một chữ, con cháu cứ
 *       nhìn chữ đệm là biết thuộc đời thứ mấy. Đây là tập quán có thật ở nhiều dòng họ miền Bắc và
 *       cũng là thứ làm cây phả đồ demo trông thuyết phục.</li>
 *   <li><b>Nữ</b> các đời trên dùng đệm "Thị"; các đời gần đây dùng đệm hiện đại (Ngọc, Thuỳ,
 *       Khánh...) — đúng như thực tế đổi thay của cách đặt tên.</li>
 *   <li><b>Tên đa lớp</b> cho các đời trên: huý (tên gọi khi sống, thành tên kiêng sau khi mất),
 *       tự, hiệu, thuỵ (tên đặt sau khi mất). Đời gần đây chỉ có huý + thường gọi.</li>
 * </ul>
 */
public final class VietnameseNameBank {

    /** Họ của dòng họ chính trong bộ dữ liệu. */
    public static final String CLAN_SURNAME = "Nguyễn";

    /**
     * Bài thơ đặt tên — mỗi đời một chữ đệm. Đời 1 dùng "Đình" nên dòng họ được gọi là
     * "họ Nguyễn Đình".
     */
    public static final List<String> GENERATION_MIDDLE_NAMES =
            List.of("Đình", "Phúc", "Danh", "Bá", "Quang", "Hữu", "Thế");

    /** Đệm nữ truyền thống. */
    private static final String FEMALE_MIDDLE_TRADITIONAL = "Thị";

    /** Đệm nữ hiện đại — chỉ dùng cho các đời gần đây. */
    private static final List<String> FEMALE_MIDDLE_MODERN =
            List.of("Ngọc", "Thuỳ", "Khánh", "Diệu", "Phương", "Hoài", "Thanh", "Bảo", "Minh", "Hà");

    /** Họ của người kết hôn vào dòng họ (dâu/rể). */
    private static final List<String> OTHER_SURNAMES = List.of(
            "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô",
            "Dương", "Lý", "Phan", "Trịnh", "Đinh", "Mai", "Cao", "Tạ", "Chu", "Đoàn",
            "Lưu", "Tô", "Kiều", "Quách", "Thái", "Hà", "Nghiêm", "Vương", "La", "Từ");

    /** Tên nam — thiên về chữ Hán-Việt, dùng cho mọi đời. */
    private static final List<String> MALE_GIVEN = List.of(
            "An", "Bách", "Bảo", "Bằng", "Bình", "Cẩn", "Cảnh", "Chấn", "Chính", "Công",
            "Cường", "Danh", "Dũng", "Duy", "Đắc", "Đại", "Đạo", "Đạt", "Đức", "Giang",
            "Hải", "Hàm", "Hân", "Hào", "Hiền", "Hiếu", "Hoà", "Hoàng", "Hùng", "Huy",
            "Khang", "Khánh", "Khiêm", "Khôi", "Kiên", "Kiệt", "Lâm", "Lập", "Liêm", "Lộc",
            "Long", "Lương", "Mẫn", "Minh", "Nam", "Nghĩa", "Nguyên", "Nhân", "Ninh", "Phong",
            "Phú", "Phúc", "Quang", "Quân", "Quảng", "Quý", "Sơn", "Tài", "Tâm", "Tân",
            "Thái", "Thăng", "Thành", "Thắng", "Thiện", "Thịnh", "Thọ", "Thuận", "Tiến", "Tín",
            "Toàn", "Trạch", "Trí", "Triết", "Trung", "Tuấn", "Tùng", "Tường", "Vinh", "Vĩnh");

    /** Tên nữ dùng chung mọi đời. */
    private static final List<String> FEMALE_GIVEN = List.of(
            "Ánh", "Bích", "Cẩm", "Chi", "Cúc", "Diệp", "Dung", "Duyên", "Hạnh", "Hảo",
            "Hằng", "Hiền", "Hoa", "Hoài", "Hồng", "Huệ", "Hương", "Huyền", "Khuê", "Lan",
            "Lệ", "Liên", "Linh", "Loan", "Mai", "Miên", "Mỹ", "Nga", "Ngân", "Ngọc",
            "Nguyệt", "Nhàn", "Nhung", "Oanh", "Phượng", "Quế", "Quỳnh", "Tâm", "Thảo", "Thắm",
            "Thanh", "Thi", "Thu", "Thuỳ", "Thuỷ", "Thư", "Tiên", "Trang", "Trâm", "Trúc",
            "Tuyết", "Vân", "Xuân", "Yến");

    /** Tên nữ mộc mạc của các đời xa — cho đúng không khí gia phả chép tay. */
    private static final List<String> FEMALE_GIVEN_OLD = List.of(
            "Đào", "Gấm", "Lụa", "Mận", "Nhài", "Sen", "Xoan", "Bưởi", "Cải", "Chè",
            "Dâu", "Hến", "Mít", "Nếp", "Nhót", "Quýt", "Tằm", "Thóc", "Vải", "Ổi");

    /** Thành tố tên tự (tên đặt lúc trưởng thành). */
    private static final List<String> TU_PREFIX = List.of("Bá", "Trọng", "Thúc", "Quý", "Tử", "Nhân", "Sĩ", "Hy", "Đức", "Văn");
    private static final List<String> TU_SUFFIX = List.of("Nhân", "Thứ", "Hoà", "Đạt", "Minh", "Khiêm", "Trực", "Chính", "Thành", "Nghị");

    /** Thành tố tên hiệu (biệt hiệu văn chương). */
    private static final List<String> HIEU_PREFIX = List.of("Tùng", "Cúc", "Mai", "Trúc", "Liên", "Thạch", "Vân", "Nguyệt", "Tuyền", "Lâm");
    private static final List<String> HIEU_SUFFIX = List.of("Trai", "Hiên", "Am", "Đường", "Cư Sĩ", "Sơn Nhân");

    /** Thành tố tên thuỵ (đặt sau khi mất). */
    private static final List<String> THUY_MALE = List.of("Đoan Trực", "Trung Cẩn", "Cương Nghị", "Ôn Hoà", "Hậu Đức", "Thuần Chính", "Minh Đạt", "Khiêm Cung");
    private static final List<String> THUY_FEMALE = List.of("Từ Thục", "Trinh Thuận", "Ôn Cần", "Hiền Thục", "Đoan Trang", "Nhu Mẫn");

    /** Pháp danh (người quy y cửa Phật). */
    private static final List<String> PHAP_DANH_MALE = List.of("Minh Đức", "Nguyên Tâm", "Thiện Tín", "Chân Quang", "Tuệ Không");
    private static final List<String> PHAP_DANH_FEMALE = List.of("Diệu Ngọc", "Diệu Tâm", "Diệu Hạnh", "Diệu Liên", "Diệu Thiện");

    /** Quê quán gốc của dòng họ và các làng lân cận (nơi sinh của dâu/rể). */
    private static final List<String> NATIVE_PLACES = List.of(
            "Đông Ngạc, Từ Liêm, Hà Nội",
            "Kim Lũ, Thanh Trì, Hà Nội",
            "Đại Áng, Thanh Trì, Hà Nội",
            "Hành Thiện, Xuân Trường, Nam Định",
            "Mộ Trạch, Bình Giang, Hải Dương",
            "Đông Thái, Đức Thọ, Hà Tĩnh",
            "Phù Lưu, Từ Sơn, Bắc Ninh",
            "Nguyệt Áng, Thanh Trì, Hà Nội");

    /** Nơi ở hiện nay — có cả kiều bào, đúng bối cảnh "con cháu xa quê" của FR-2.2. */
    private static final List<String> CURRENT_PLACES = List.of(
            "Hà Nội", "Hà Nội", "Hà Nội", "Hải Phòng", "Nam Định", "Bắc Ninh",
            "TP. Hồ Chí Minh", "TP. Hồ Chí Minh", "Đà Nẵng", "Cần Thơ", "Bình Dương",
            "Praha, Cộng hoà Séc", "Berlin, Đức", "Warszawa, Ba Lan",
            "California, Hoa Kỳ", "Texas, Hoa Kỳ", "Osaka, Nhật Bản", "Seoul, Hàn Quốc",
            "Sydney, Úc", "Toronto, Canada");

    /** Nghề nghiệp theo thời kỳ — đời xa làm ruộng, đời gần làm phần mềm. */
    private static final List<String> OCCUPATIONS_OLD = List.of(
            "Nông dân", "Thầy đồ", "Thợ mộc", "Lý trưởng", "Thầy lang", "Thợ rèn",
            "Lái đò", "Buôn bán nhỏ", "Hương sư", "Chánh tổng");
    private static final List<String> OCCUPATIONS_MODERN = List.of(
            "Giáo viên", "Bộ đội", "Công nhân", "Kỹ sư xây dựng", "Bác sĩ", "Kế toán",
            "Lái xe", "Thợ may", "Buôn bán", "Lập trình viên", "Điều dưỡng", "Cán bộ xã",
            "Nhân viên ngân hàng", "Đầu bếp", "Kỹ thuật viên", "Du học sinh", "Nội trợ");

    /**
     * Từ điển Hán-Nôm tối giản. Chỉ sinh {@code name_hannom} khi TẤT CẢ âm tiết có trong từ điển —
     * thà thiếu còn hơn khắc sai chữ lên bia đá.
     */
    private static final Map<String, String> HAN_NOM = buildHanNom();

    private static Map<String, String> buildHanNom() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Nguyễn", "阮");
        map.put("Đình", "廷");
        map.put("Phúc", "福");
        map.put("Danh", "名");
        map.put("Bá", "伯");
        map.put("Quang", "光");
        map.put("Hữu", "有");
        map.put("Thế", "世");
        map.put("Thị", "氏");
        map.put("Văn", "文");
        map.put("Khang", "康");
        map.put("Đức", "德");
        map.put("Minh", "明");
        map.put("An", "安");
        map.put("Hoà", "和");
        map.put("Thọ", "壽");
        map.put("Phú", "富");
        map.put("Quý", "貴");
        map.put("Nhân", "仁");
        map.put("Nghĩa", "義");
        map.put("Trung", "忠");
        map.put("Hiếu", "孝");
        map.put("Tâm", "心");
        map.put("Long", "龍");
        map.put("Sơn", "山");
        map.put("Hải", "海");
        map.put("Lâm", "林");
        map.put("Trí", "智");
        map.put("Tín", "信");
        map.put("Thành", "成");
        map.put("Vinh", "榮");
        map.put("Lộc", "祿");
        return Map.copyOf(map);
    }

    private VietnameseNameBank() {
    }

    /** Chữ đệm theo đời cho nam (đời vượt quá bài thơ thì quay vòng — dòng họ thật cũng làm vậy). */
    public static String middleForGeneration(int generation) {
        return GENERATION_MIDDLE_NAMES.get((generation - 1) % GENERATION_MIDDLE_NAMES.size());
    }

    /** Chữ đệm cho nữ: đời 1..5 dùng "Thị", đời 6 trở đi trộn thêm đệm hiện đại. */
    public static String femaleMiddle(int generation, Random random) {
        if (generation <= 5 || random.nextInt(100) < 40) {
            return FEMALE_MIDDLE_TRADITIONAL;
        }
        return pick(FEMALE_MIDDLE_MODERN, random);
    }

    public static String maleGiven(Random random) {
        return pick(MALE_GIVEN, random);
    }

    public static String femaleGiven(int generation, Random random) {
        if (generation <= 4 && random.nextInt(100) < 45) {
            return pick(FEMALE_GIVEN_OLD, random);
        }
        return pick(FEMALE_GIVEN, random);
    }

    public static String otherSurname(Random random) {
        return pick(OTHER_SURNAMES, random);
    }

    public static String tuName(Random random) {
        return pick(TU_PREFIX, random) + " " + pick(TU_SUFFIX, random);
    }

    public static String hieuName(Random random) {
        return pick(HIEU_PREFIX, random) + " " + pick(HIEU_SUFFIX, random);
    }

    public static String thuyName(boolean male, Random random) {
        return male
                ? pick(THUY_MALE, random) + " Phủ Quân"
                : pick(THUY_FEMALE, random) + " Nhụ Nhân";
    }

    public static String phapDanh(boolean male, Random random) {
        return male ? pick(PHAP_DANH_MALE, random) : pick(PHAP_DANH_FEMALE, random);
    }

    public static String nativePlace(Random random) {
        return pick(NATIVE_PLACES, random);
    }

    /** Quê gốc của dòng họ — nhân khẩu huyết thống mặc định lấy nơi này. */
    public static String clanNativePlace() {
        return NATIVE_PLACES.get(0);
    }

    public static String currentPlace(Random random) {
        return pick(CURRENT_PLACES, random);
    }

    public static String occupation(int birthYear, Random random) {
        return birthYear < 1930 ? pick(OCCUPATIONS_OLD, random) : pick(OCCUPATIONS_MODERN, random);
    }

    /** Chuyển tên quốc ngữ sang Hán-Nôm, hoặc {@code null} nếu thiếu chữ trong từ điển. */
    public static String toHanNom(String fullName) {
        StringBuilder sb = new StringBuilder();
        for (String syllable : fullName.split(" ")) {
            String han = HAN_NOM.get(syllable);
            if (han == null) {
                return null;
            }
            sb.append(han);
        }
        return sb.toString();
    }

    private static <T> T pick(List<T> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }
}

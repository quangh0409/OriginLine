package vn.giapha.demo.writer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.giapha.demo.model.DemoPerson;

/**
 * Xếp thuộc tính của một {@link DemoPerson} vào đúng hình dạng mà context {@code genealogy} đọc ra.
 *
 * <h2>Vì sao không đổ thẳng {@code attributes} của generator xuống cột</h2>
 * <p>{@code genealogy} chia {@code person.attributes} làm hai vùng có ý nghĩa <b>pháp lý</b> khác
 * nhau (BA v2 §10):</p>
 * <ul>
 *   <li>khoá dành riêng {@code _profile} — nghề nghiệp, tỉnh (Tầng 2) và {@code contact}
 *       (điện thoại, email — Tầng 3). Bộ lọc phân tầng đọc từng trường ở đây một cách có chọn lọc;</li>
 *   <li>phần còn lại — thuộc tính mở rộng tự do, <b>chỉ hiện ở Tầng 3</b> và hiện nguyên khối.</li>
 * </ul>
 * <p>Generator cố tình không biết về sự phân chia đó (nó không phụ thuộc {@code genealogy}), nên
 * việc xếp chỗ nằm ở writer. Xếp sai thì hậu quả không phải là dữ liệu xấu mà là <b>rò rỉ</b>: số
 * điện thoại của người sống nằm ngoài {@code _profile.contact} sẽ đi theo đường thuộc tính mở rộng
 * và lọt qua một cửa kiểm tra khác với cửa dành cho nó.</p>
 *
 * <p>Cũng vì thế mà bộ dữ liệu demo mới kiểm thử được đúng thứ cần kiểm: ~20 người sống có dữ liệu
 * Tầng 3 thật, nằm đúng chỗ mà {@code PrivacyTierService} sẽ đi tìm.</p>
 */
final class DemoPersonAttributes {

    /** Khoá dành riêng của {@code genealogy} — khớp {@code PersonMapper.PROFILE_KEY}. */
    private static final String PROFILE_KEY = "_profile";

    private DemoPersonAttributes() {
    }

    /**
     * @param anchorNames tên mốc neo của người này (rỗng nếu không phải mốc neo) — ghi vào
     *                    {@code demo_anchor} để truy vấn kiểm chứng tìm được ca biên bằng SQL thuần
     */
    static Map<String, Object> toJson(DemoPerson person, List<String> anchorNames) {
        Map<String, Object> source = person.attributes();
        Map<String, Object> attributes = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> contact = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            switch (entry.getKey()) {
                case "occupation" -> profile.put("occupation", entry.getValue());
                case "phone" -> contact.put("phone", entry.getValue());
                case "email" -> contact.put("email", entry.getValue());
                // address di vao cot current_place (xem DemoRelationalWriter), khong nhan doi o day.
                case "address" -> { }
                default -> attributes.put(entry.getKey(), entry.getValue());
            }
        }

        if (person.currentPlace() != null) {
            // currentPlace cua generator la tinh/thanh — du lieu Tang 2, khac voi dia chi day du.
            profile.put("currentPlaceProvince", person.currentPlace());
        }
        if (!contact.isEmpty()) {
            profile.put("contact", contact);
        }
        if (person.birthSolar() != null) {
            profile.put("birthPrecision", "DAY");
        }
        if (person.deathSolar() != null) {
            profile.put("deathPrecision", "DAY");
        }
        if (!anchorNames.isEmpty()) {
            attributes.put("demo_anchor", new ArrayList<>(anchorNames));
        }
        if (!profile.isEmpty()) {
            attributes.put(PROFILE_KEY, profile);
        }
        return attributes;
    }

    /**
     * Địa chỉ đầy đủ cho cột {@code current_place} (Tầng 3). Không có địa chỉ chi tiết thì dùng
     * tỉnh/thành — người sống luôn phải có ít nhất một nơi ở để bộ lọc Tầng 2 có gì mà lọc.
     */
    static String currentPlaceFull(DemoPerson person) {
        Object address = person.attributes().get("address");
        if (address != null) {
            return address.toString();
        }
        return person.currentPlace();
    }
}

package vn.giapha.dataimport.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Sắp <b>thứ tự ghi</b> các dòng Nhân khẩu của một lô: <b>cha mẹ trước, con sau</b>.
 *
 * <h2>Vì sao không sắp theo cột "Đời"</h2>
 * Vì cột ấy là thứ người nhập gõ, và nó có thể sai — đó chính là lý do bộ kiểm có luật
 * {@link IssueCode#IMP_GENERATION_MISMATCH}. Sắp theo một con số tự khai nghĩa là khi con số ấy
 * sai thì lệnh ghi sẽ cố nối một đứa con vào một người cha <b>chưa tồn tại</b>, và lô hỏng vì một
 * lý do chẳng liên quan gì tới lỗi thật. Thứ tự phải đến từ <b>quan hệ</b> (cột Mã cha / Mã mẹ và
 * trang Hôn phối), tức là từ chính thứ mà cạnh sắp được nối.
 *
 * <h2>Vì sao hôn phối cũng tạo ràng buộc thứ tự</h2>
 * Dâu/rể không có Mã cha lẫn Mã mẹ, nên với phép sắp chỉ theo cha–con thì họ nằm ở lượt đầu tiên,
 * lúc người chồng còn chưa tồn tại. Hệ quả: <b>đời thứ của họ không suy ra được</b> — hệ thống suy
 * đời từ liên kết chứ không nhận từ tệp — và một nhân khẩu không có đời thứ thì không xếp được vào
 * hàng nào trên phả đồ. Vì vậy một dòng không có cha mẹ mà có mặt ở trang Hôn phối sẽ
 * <b>phụ thuộc vào vợ/chồng của mình</b>.
 *
 * <h2>Sắp topo, không đệ quy</h2>
 * Thuật toán Kahn với hàng đợi tất định. Cùng một tệp phải cho ra cùng một thứ tự ghi ở mọi lần
 * chạy: nếu không, hai lần thử nhập cùng một tệp hỏng sẽ dừng ở hai dòng khác nhau và không ai
 * truy được vì sao. Hàng đợi tường minh chứ không đệ quy — cùng đoạn mã này sẽ chạy cho tệp GEDCOM
 * 50.000 dòng ở đợt sau, và một chuỗi trực hệ dài mà duyệt bằng đệ quy là
 * {@code StackOverflowError}.
 *
 * <h2>Còn vòng lặp thì CHẾT RÕ RÀNG, không treo</h2>
 * {@code CycleRule} đã chặn vòng lặp ở bộ kiểm. Lớp này <b>không tin điều đó</b>: nếu sau khi Kahn
 * chạy xong vẫn còn dòng chưa xếp được thì đúng những dòng ấy nằm trên một chu trình, và chúng
 * được trả về kèm mã để bên gọi nói ra. Bỏ qua chúng cho "chạy được" là để lại một cây có chu
 * trình, nơi mọi phép duyệt LCA và danh xưng chạy vô tận.
 *
 * <p>Độ phức tạp O(V+E).</p>
 */
public final class CommitOrder {

    private CommitOrder() {
    }

    /**
     * Kết quả sắp thứ tự.
     *
     * @param thuTu các dòng theo đúng thứ tự được phép ghi; cha mẹ luôn đứng trước con
     * @param dongKetVong các mã <b>không</b> xếp được vì nằm trên một chu trình; rỗng là bình thường
     */
    public record KetQua(List<PersonRow> thuTu, List<String> dongKetVong) {

        public KetQua {
            thuTu = List.copyOf(thuTu);
            dongKetVong = List.copyOf(dongKetVong);
        }

        public boolean coVongLap() {
            return !dongKetVong.isEmpty();
        }
    }

    /**
     * Sắp các dòng theo thứ tự ghi.
     *
     * <p>Chỉ tính phụ thuộc trên các mã <b>nằm trong chính lô này</b>. Mã cha trỏ tới một người
     * <b>đã có sẵn trong phả</b> không tạo ràng buộc thứ tự nào — người ấy đã tồn tại, nối lúc nào
     * cũng được. Đây là khác biệt mấu chốt với {@link CycleFinder}, vốn phải gieo cả chuỗi tổ tiên
     * của các mỏ neo vào để bắt vòng lặp xuyên biên.</p>
     *
     * @param rows các dòng Nhân khẩu, thứ tự đầu vào tuỳ ý
     * @param marriages các dòng Hôn phối — nguồn của ràng buộc "dâu/rể sau vợ/chồng"
     */
    public static KetQua sap(List<PersonRow> rows, List<MarriageRow> marriages) {
        // LinkedHashMap/TreeMap: thu tu duyet phai tat dinh, neu khong hai lan ghi cung mot tep se
        // dung o hai dong khac nhau khi hong.
        Map<String, PersonRow> theoMa = new LinkedHashMap<>();
        for (PersonRow row : rows) {
            if (row.externalCode() != null && !row.externalCode().isBlank()) {
                theoMa.putIfAbsent(row.externalCode(), row);
            }
        }

        Map<String, Set<String>> sauNo = new TreeMap<>();
        Map<String, Integer> bacVao = new TreeMap<>();
        for (String ma : theoMa.keySet()) {
            bacVao.put(ma, 0);
        }
        for (PersonRow row : theoMa.values()) {
            themPhuThuoc(theoMa, sauNo, bacVao, row.externalCode(), row.fatherCode());
            themPhuThuoc(theoMa, sauNo, bacVao, row.externalCode(), row.motherCode());
        }
        for (String[] canh : phuThuocHonPhoi(theoMa, marriages)) {
            // canh = [truoc, sau]; themPhuThuoc nhan (sau, truoc) — cung thu tu voi cap (con, cha).
            themPhuThuoc(theoMa, sauNo, bacVao, canh[1], canh[0]);
        }

        // Hang doi UU TIEN theo ma, khong phai hang doi thuong: thu tu ghi phai tat dinh VA
        // khong phu thuoc thu tu dong trong tep. Hai tep cung noi dung, khac thu tu dong, phai cho
        // cung mot thu tu ghi — neu khong thi hai lan thu nhap cung mot lo hong se dung o hai cho
        // khac nhau va khong ai truy duoc vi sao.
        PriorityQueue<String> hangDoi = new PriorityQueue<>(Comparator.naturalOrder());
        bacVao.forEach((ma, bac) -> {
            if (bac == 0) {
                hangDoi.add(ma);
            }
        });

        List<PersonRow> thuTu = new ArrayList<>(theoMa.size());
        while (!hangDoi.isEmpty()) {
            String ma = hangDoi.poll();
            thuTu.add(theoMa.get(ma));
            for (String sau : sauNo.getOrDefault(ma, Set.of())) {
                if (bacVao.merge(sau, -1, Integer::sum) == 0) {
                    hangDoi.add(sau);
                }
            }
        }

        if (thuTu.size() == theoMa.size()) {
            return new KetQua(thuTu, List.of());
        }
        // Con lai dung bang cac dong nam tren mot chu trinh — tra ve de bao ten, khong im lang bo qua.
        List<String> ketVong = new ArrayList<>();
        bacVao.forEach((ma, bac) -> {
            if (bac > 0) {
                ketVong.add(ma);
            }
        });
        ketVong.sort(Comparator.naturalOrder());
        return new KetQua(thuTu, ketVong);
    }

    /**
     * Ràng buộc "dâu/rể ghi sau vợ/chồng".
     *
     * <p>Chỉ áp cho bên <b>không có cha mẹ trong lô</b> — tức bên lấy về từ họ khác. Khi cả hai
     * bên đều không có cha mẹ (cặp thuỷ tổ), người vợ ghi sau người chồng: dòng họ Việt chép theo
     * trực hệ nam, nên bên có gốc trong sổ gần như luôn là người chồng. Chọn sai chiều ở ca này
     * không làm hỏng dữ liệu, chỉ làm một trong hai người thiếu đời thứ suy ra — và cột Đời của
     * chính dòng ấy sẽ bù vào.</p>
     *
     * @return các cặp {@code [truoc, sau]}
     */
    private static List<String[]> phuThuocHonPhoi(Map<String, PersonRow> theoMa,
                                                  List<MarriageRow> marriages) {
        List<String[]> canh = new ArrayList<>();
        if (marriages == null) {
            return canh;
        }
        for (MarriageRow m : marriages) {
            PersonRow chong = theoMa.get(m.husbandCode());
            PersonRow vo = theoMa.get(m.wifeCode());
            if (chong == null || vo == null) {
                continue;
            }
            boolean chongMoCoi = !chong.coCha() && !chong.coMe();
            boolean voMoCoi = !vo.coCha() && !vo.coMe();
            if (voMoCoi) {
                canh.add(new String[] {chong.externalCode(), vo.externalCode()});
            } else if (chongMoCoi) {
                canh.add(new String[] {vo.externalCode(), chong.externalCode()});
            }
        }
        return canh;
    }

    private static void themPhuThuoc(Map<String, PersonRow> theoMa, Map<String, Set<String>> sauNo,
                                     Map<String, Integer> bacVao, String sau, String truoc) {
        if (truoc == null || truoc.isBlank() || sau == null) {
            return;
        }
        String truocMa = truoc.trim();
        if (!theoMa.containsKey(truocMa) || truocMa.equals(sau)) {
            return;
        }
        if (sauNo.computeIfAbsent(truocMa, k -> new TreeSet<>()).add(sau)) {
            bacVao.merge(sau, 1, Integer::sum);
        }
    }
}

package vn.giapha.dataimport.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dò <b>vòng lặp tổ tiên</b> trong đồ thị con-tới-cha của một lô.
 *
 * <h2>Vì sao không hỏi đồ thị AGE cho từng dòng</h2>
 * Hai lý do, và lý do thứ hai mới là lý do thật:
 * <ol>
 *   <li>400 dòng là 400 lượt duyệt Cypher — chậm, nhưng chỉ là chuyện hiệu năng;</li>
 *   <li><b>cạnh trong tệp chưa tồn tại trong đồ thị</b>, nên câu hỏi "A có phải tổ tiên của B
 *       không" đơn giản là <b>không trả lời được</b> cho một vòng lặp nằm trọn trong tệp. Đây
 *       không phải tối ưu hoá, đây là tính đúng đắn.</li>
 * </ol>
 *
 * <h2>Vòng lặp xuyên biên</h2>
 * Loại khó chịu nhất: một dòng trong tệp khai cha là một người <b>đã có trong phả</b>, mà người đã
 * có ấy lại là hậu duệ của một người khác <b>trong tệp</b>. Bắt được nó bằng cách <b>gieo</b> chuỗi
 * tổ tiên của các mỏ neo (người đã có mà tệp trỏ tới, thực tế 1–5 người một tệp) vào cùng một đồ
 * thị trong bộ nhớ — xem {@link #themCanh(String, String)}.
 *
 * <h2>Ngăn xếp tường minh, không đệ quy</h2>
 * Cùng đoạn mã này sẽ chạy cho tệp GEDCOM ở đợt sau, nơi 50.000 dòng là bình thường. Một chuỗi
 * trực hệ dài trong luồng web mà duyệt bằng đệ quy là {@code StackOverflowError} — rủi ro không
 * cần chuốc khi một {@link ArrayDeque} giải quyết xong.
 *
 * <p>Độ phức tạp O(V+E).</p>
 */
public final class CycleFinder {

    /** Trắng: chưa xét. Xám: đang nằm trên đường đi hiện tại. Đen: đã xong, không còn vòng nào. */
    private enum Mau { TRANG, XAM, DEN }

    /**
     * Cạnh {@code con -> cha}. {@link TreeMap} chứ không phải {@link HashMap}: thứ tự duyệt phải
     * tất định, nếu không hai lần chạy bộ kiểm trên cùng một tệp sẽ báo hai vòng lặp khác nhau khi
     * tệp có nhiều hơn một vòng.
     */
    private final Map<String, Set<String>> chaCua = new TreeMap<>();

    /** Thêm một cạnh con-tới-cha. Cả hai đầu đều là mã ngoài hoặc mã mỏ neo do bên gọi đặt. */
    public void themCanh(String conCode, String chaCode) {
        if (conCode == null || chaCode == null || conCode.isBlank() || chaCode.isBlank()) {
            return;
        }
        chaCua.computeIfAbsent(conCode, k -> new LinkedHashSet<>()).add(chaCode);
    }

    /**
     * Mọi vòng lặp tìm được, mỗi vòng là <b>đường đi thật</b> đã đóng kín.
     *
     * <p>Ví dụ {@code [AT-05-012, AT-04-003, AT-05-012]}. Một thông báo "có vòng lặp" trơ trọi
     * trên tệp 400 dòng là vô dụng: Trưởng chi cần ba cái mã để mở sổ ra tra.</p>
     *
     * <p>Kết quả đã sắp xếp tất định.</p>
     */
    public List<List<String>> timVongLap() {
        Map<String, Mau> mau = new HashMap<>();
        List<List<String>> ketQua = new ArrayList<>();
        Set<String> daBao = new LinkedHashSet<>();

        // Duyet cac dinh theo thu tu sap xep -> ket qua khong phu thuoc thu tu chen.
        List<String> dinh = new ArrayList<>(chaCua.keySet());
        dinh.sort(Comparator.naturalOrder());

        for (String goc : dinh) {
            if (mau.getOrDefault(goc, Mau.TRANG) != Mau.TRANG) {
                continue;
            }
            duyet(goc, mau, ketQua, daBao);
        }
        return ketQua;
    }

    /**
     * Duyệt sâu ba màu bằng ngăn xếp tường minh.
     *
     * <p>Mỗi khung trên ngăn xếp giữ đỉnh và <b>danh sách cha chưa xét</b> của nó, để lần quay lại
     * biết đi tiếp từ đâu — đúng thứ mà đệ quy làm hộ ta miễn phí và ta phải tự làm ở đây.</p>
     */
    private void duyet(String goc, Map<String, Mau> mau, List<List<String>> ketQua, Set<String> daBao) {
        Deque<Khung> nganXep = new ArrayDeque<>();
        // duongDi giu dung cac dinh mau XAM, theo thu tu, de cat ra chuoi vong lap.
        List<String> duongDi = new ArrayList<>();

        nganXep.push(new Khung(goc, new ArrayList<>(chaCua.getOrDefault(goc, Set.of()))));
        mau.put(goc, Mau.XAM);
        duongDi.add(goc);

        while (!nganXep.isEmpty()) {
            Khung khung = nganXep.peek();
            if (khung.viTri < khung.cha.size()) {
                String cha = khung.cha.get(khung.viTri++);
                Mau mauCha = mau.getOrDefault(cha, Mau.TRANG);
                if (mauCha == Mau.XAM) {
                    ghiNhanVong(duongDi, cha, ketQua, daBao);
                } else if (mauCha == Mau.TRANG) {
                    mau.put(cha, Mau.XAM);
                    duongDi.add(cha);
                    nganXep.push(new Khung(cha, new ArrayList<>(chaCua.getOrDefault(cha, Set.of()))));
                }
                // DEN: da xet xong, khong con vong nao qua no.
            } else {
                mau.put(khung.dinh, Mau.DEN);
                duongDi.remove(duongDi.size() - 1);
                nganXep.pop();
            }
        }
    }

    private void ghiNhanVong(List<String> duongDi, String dinhXam, List<List<String>> ketQua,
                             Set<String> daBao) {
        int tu = duongDi.lastIndexOf(dinhXam);
        if (tu < 0) {
            return;
        }
        List<String> vong = new ArrayList<>(duongDi.subList(tu, duongDi.size()));
        vong.add(dinhXam);
        // Cung mot vong co the cham toi tu nhieu diem xuat phat; chuan hoa bang phan tu nho nhat
        // de khong bao trung ba lan cho cung mot vong.
        String khoa = String.join(">", chuanHoa(vong));
        if (daBao.add(khoa)) {
            ketQua.add(List.copyOf(vong));
        }
    }

    /** Xoay vòng về điểm bắt đầu là mã nhỏ nhất, để hai lần phát hiện cùng một vòng cho cùng một khoá. */
    private static List<String> chuanHoa(List<String> vong) {
        List<String> than = vong.subList(0, vong.size() - 1);
        int nhoNhat = 0;
        for (int i = 1; i < than.size(); i++) {
            if (than.get(i).compareTo(than.get(nhoNhat)) < 0) {
                nhoNhat = i;
            }
        }
        List<String> xoay = new ArrayList<>(than.size());
        for (int i = 0; i < than.size(); i++) {
            xoay.add(than.get((nhoNhat + i) % than.size()));
        }
        return xoay;
    }

    private static final class Khung {
        private final String dinh;
        private final List<String> cha;
        private int viTri;

        private Khung(String dinh, List<String> cha) {
            this.dinh = dinh;
            this.cha = cha;
        }
    }
}

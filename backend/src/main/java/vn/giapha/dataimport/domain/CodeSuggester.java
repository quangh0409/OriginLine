package vn.giapha.dataimport.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Gợi ý <b>vài mã gần giống</b> khi báo "không tìm thấy mã cha".
 *
 * <h2>Vì sao việc này đáng làm</h2>
 * "Không tìm thấy mã AT-04-O03" trên một tệp 400 dòng là một câu đố: người nhập phải cuộn tay đi
 * tìm, và họ sẽ không tìm ra, vì sai ở đúng cái ký tự mắt không phân biệt được — chữ O thay vì số
 * 0. Thêm một dòng "ý anh là AT-04-003 (Nguyễn Văn Cẩn) phải không?" biến một câu đố thành một cú
 * sửa ba giây. Đây là chênh lệch giữa một bộ kiểm dùng được và một bộ kiểm khiến Trưởng chi bỏ
 * cuộc ở lần tải thứ ba.
 *
 * <h2>Khoảng cách Levenshtein có chặn trần</h2>
 * Dừng sớm khi đã vượt trần thay vì tính hết bảng — với 400 dòng nhân 400 ứng viên thì phép cắt
 * này là khác biệt giữa mấy chục mili-giây và mấy giây.
 *
 * <p>Trần được nới theo độ dài mã: mã 3 ký tự cho phép sai 1, mã dài như {@code AT-04-003} cho
 * phép sai 2. Cho phép sai nhiều hơn thì gợi ý biến thành nhiễu, và một gợi ý sai khiến người nhập
 * sửa <b>đúng thành sai</b> — tệ hơn hẳn không gợi ý.</p>
 */
public final class CodeSuggester {

    private CodeSuggester() {
    }

    /**
     * @param target mã không tìm thấy, đã chuẩn hoá
     * @param known các mã có thật trong tệp và trong {@code person_external_ref} của chi
     * @return tối đa {@link ImportLimits#MAX_CODE_SUGGESTIONS} mã, gần nhất trước; rỗng khi không
     *         có gì đủ gần
     */
    public static List<String> goiY(String target, Collection<String> known) {
        if (target == null || target.isBlank() || known == null || known.isEmpty()) {
            return List.of();
        }
        int tran = tranSaiSo(target.length());
        List<UngVien> ungVien = new ArrayList<>();
        for (String ma : known) {
            if (ma == null || ma.isBlank() || ma.equals(target)) {
                continue;
            }
            int d = khoangCach(target, ma, tran);
            if (d <= tran) {
                ungVien.add(new UngVien(ma, d));
            }
        }
        ungVien.sort(Comparator.comparingInt(UngVien::khoangCach).thenComparing(UngVien::ma));
        return ungVien.stream()
                .limit(ImportLimits.MAX_CODE_SUGGESTIONS)
                .map(UngVien::ma)
                .toList();
    }

    private static int tranSaiSo(int doDai) {
        if (doDai <= 3) {
            return 1;
        }
        return doDai <= 12 ? 2 : 3;
    }

    /**
     * Levenshtein, dừng ngay khi cả một hàng đã vượt trần.
     *
     * @return khoảng cách, hoặc {@code tran + 1} khi chắc chắn đã vượt
     */
    static int khoangCach(String a, String b, int tran) {
        if (Math.abs(a.length() - b.length()) > tran) {
            return tran + 1;
        }
        int[] truoc = new int[b.length() + 1];
        int[] hienTai = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            truoc[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            hienTai[0] = i;
            int nhoNhatHang = hienTai[0];
            for (int j = 1; j <= b.length(); j++) {
                int thay = truoc[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                hienTai[j] = Math.min(thay, Math.min(truoc[j] + 1, hienTai[j - 1] + 1));
                nhoNhatHang = Math.min(nhoNhatHang, hienTai[j]);
            }
            if (nhoNhatHang > tran) {
                return tran + 1;
            }
            int[] tam = truoc;
            truoc = hienTai;
            hienTai = tam;
        }
        return truoc[b.length()];
    }

    private record UngVien(String ma, int khoangCach) {
    }
}

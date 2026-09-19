package vn.giapha.dataimport.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import vn.giapha.dataimport.domain.ImportIssue;

/**
 * <b>Vân tay của tập cảnh báo</b> — thứ làm cho "tôi đã xem cảnh báo" là một lời khai kiểm chứng
 * được thay vì một cái tích.
 *
 * <h2>Vấn đề nó giải</h2>
 * Đối soát là một vòng lặp: tick "đã xem" → sửa tệp → kiểm lại → sửa nữa. Nếu cái tick chỉ là một
 * dấu thời gian thì nó sống sót qua mọi lần kiểm lại — kể cả lần kiểm sinh ra <b>cảnh báo mới</b>.
 * Lúc đó hệ thống ghi "đã xác nhận" cho những dòng người duyệt chưa từng nhìn thấy, và nó ghi nhân
 * danh họ. Đó là kiểu sai lệch không bao giờ tự lộ ra, vì mọi thứ trên màn hình vẫn xanh.
 *
 * <p>Lưu vân tay ở <b>hai</b> thời điểm — lúc kiểm và lúc tick — rồi so sánh, thì phép kiểm "còn
 * hiệu lực không" trở thành một phép so chuỗi, không cần ai nhớ đi xoá gì cả.</p>
 *
 * <h2>Vì sao câu chữ nằm trong vân tay</h2>
 * Vì thứ người ta đọc <b>là câu chữ</b>. Hai cảnh báo cùng mã, cùng dòng, cùng cột vẫn có thể nói
 * hai điều khác nhau: "nghi trùng với 1 hồ sơ" và "nghi trùng với 3 hồ sơ" là cùng
 * {@code IMP_SUSPECT_DUPLICATE} trên cùng ô. Băm mỗi danh tính thì lần kiểm thứ hai trông như
 * không đổi, và người duyệt không bao giờ được hỏi lại.
 *
 * <p>Cái giá phải trả là có thật và đã cân nhắc: sửa lời một thông báo trong mã nguồn sẽ làm hết
 * hiệu lực các xác nhận đang treo ở những lô <i>được kiểm lại sau đó</i>. Đó là hướng an toàn —
 * thà bắt đọc lại một danh sách không đổi còn hơn ghi nhận một sự đồng ý không có thật.</p>
 *
 * <h2>Vì sao không băm {@code context}</h2>
 * {@code ImportIssue.context} đi qua {@code Map.copyOf}, mà thứ tự duyệt của map bất biến trong JDK
 * <b>đổi theo từng lần chạy JVM</b> (nó có một hằng muối ngẫu nhiên). Băm nó thì cùng một lô sẽ ra
 * hai vân tay khác nhau sau một lần khởi động lại — và triệu chứng là các xác nhận tự hết hiệu lực
 * một cách ngẫu nhiên, đúng loại lỗi tốn nhiều ngày nhất để truy.
 */
public final class WarningDigest {

    private WarningDigest() {
    }

    /**
     * Vân tay của <b>các cảnh báo</b> trong danh sách (lỗi chặn bị bỏ qua: chúng đi lối khác, và
     * còn một lỗi chặn thì không ai duyệt được gì).
     *
     * @return chuỗi hex SHA-256, hoặc {@code null} khi không có cảnh báo nào — không có gì để xem
     *         thì không có gì để xác nhận
     */
    public static String cua(List<ImportIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return null;
        }
        List<ImportIssue> canhBao = new ArrayList<>(issues.stream().filter(i -> !i.chan()).toList());
        if (canhBao.isEmpty()) {
            return null;
        }
        // Thu tu tat dinh, vi hai lan kiem tren cung mot tep PHAI ra cung mot van tay. Mot ORDER BY
        // thieu o day bieu hien thanh "cu kiem lai la mat hieu luc xac nhan", khong bieu hien thanh
        // mot loi.
        canhBao.sort(ImportIssue.TAT_DINH);

        StringBuilder sb = new StringBuilder();
        sb.append(canhBao.size()).append('\n');
        for (ImportIssue issue : canhBao) {
            sb.append(issue.code().name()).append('\u001f')
                    .append(issue.sheet()).append('\u001f')
                    .append(issue.rowNo() == null ? "" : issue.rowNo()).append('\u001f')
                    .append(issue.field() == null ? "" : issue.field()).append('\u001f')
                    .append(issue.message()).append('\n');
        }
        return sha256(sb.toString());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM khong co SHA-256", ex);
        }
    }
}

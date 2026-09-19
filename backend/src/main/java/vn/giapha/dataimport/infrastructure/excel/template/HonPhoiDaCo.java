package vn.giapha.dataimport.infrastructure.excel.template;

import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.genealogy.application.DisclosedPerson;

/**
 * Một cuộc hôn phối <b>đã có trong phả</b> của chi này, ở dạng sẵn sàng ghi ra một dòng Excel.
 *
 * <h2>Che theo cùng một kết luận với {@link NhanKhauDaCo}</h2>
 * Năm cưới, năm kết thúc và lý do kết thúc là dữ kiện đời tư của <b>hai</b> người. Một năm ly hôn
 * lọt vào tệp Excel rồi đi qua Zalo của cả chi là chuyện không thu hồi được.
 *
 * <p>Trước đây điều kiện che là "có ít nhất một người còn sống" — một luật <b>tự đặt</b>, cố định,
 * không ai ngoài tệp này biết. Nay nó là {@code chiTietHienDuoc}, lấy từ kết luận của bộ lọc riêng
 * tư cho <b>chính người đang tải mẫu về</b>: cạnh hôn phối chỉ chở chi tiết khi người gọi được xem
 * khối dữ liệu phả hệ ngoài nhóm đồng thuận của <b>cả hai</b> vợ chồng
 * ({@link DisclosedPerson#duLieuNgoaiNhomHienDuoc()}). Với hai người đã khuất, điều đó luôn đúng —
 * đúng như hành vi cũ. Với một cặp còn sống, Hội đồng Tộc biểu vẫn thấy, Trưởng chi thì không —
 * đúng như trên màn hình.</p>
 *
 * <p>Hai mã và bậc thì ở lại: chúng là <b>quan hệ lõi</b>, và nếu thiếu bậc thì danh xưng con của
 * các bà tính sai — đúng thứ mà cột "Bậc" sinh ra để chữa. Hai mã ấy cũng chỉ tồn tại khi cả hai
 * người đã đi qua bộ lọc và hiện được; cạnh có một đầu bị giấu thì không có dòng nào cả.</p>
 *
 * @param chiTietHienDuoc người gọi được xem chi tiết cuộc hôn phối này
 */
public record HonPhoiDaCo(String maChong,
                          String maVo,
                          String bac,
                          String tuNam,
                          String denNam,
                          String lyDoKetThuc,
                          boolean chiTietHienDuoc) {

    public HonPhoiDaCo {
        if (!chiTietHienDuoc) {
            tuNam = null;
            denNam = null;
            lyDoKetThuc = null;
        }
    }

    /** Giá trị của một cột, dùng khi ghi ra Excel. {@code null} nghĩa là để trống ô. */
    public String giaTri(MarriageColumn cot) {
        return switch (cot) {
            case MA_CHONG -> maChong;
            case MA_VO -> maVo;
            case BAC -> bac;
            case TU_NAM -> tuNam;
            case DEN_NAM -> denNam;
            case LY_DO_KET_THUC -> lyDoKetThuc;
        };
    }

    /** Dòng bị che bớt — bộ sinh tô khác màu để Trưởng chi biết ô trống là cố ý. */
    public boolean phaiCheTangTren() {
        return !chiTietHienDuoc;
    }
}

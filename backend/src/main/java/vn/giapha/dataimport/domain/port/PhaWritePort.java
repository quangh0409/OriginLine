package vn.giapha.dataimport.domain.port;

import java.util.Map;
import java.util.UUID;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.shared.vo.Gender;

/**
 * Cổng <b>ghi vào phả thật</b> — ranh giới duy nhất mà đường nhập liệu được phép vượt qua.
 *
 * <h2>Không có phương thức nào ở đây tự mở transaction</h2>
 * Mọi lời gọi chạy trong transaction của use case ghi lô, và đó là điều kiện để lô là
 * <b>tất-cả-hoặc-không</b>. 400 người vào trọn vẹn hoặc không một ai: nửa vời là trạng thái tệ
 * nhất, vì Trưởng chi không có cách nào biết cái gì đã vào và phải dò tay cả buổi chiều.
 *
 * <h2>Bất biến mà adapter của cổng này tuyệt đối không được lách</h2>
 * <b>Cạnh trong đồ thị AGE là nguồn chân lý của quan hệ; bảng {@code relationship} là bản chiếu
 * của nó; cả hai được ghi trong CÙNG một transaction.</b> Hiện thực của cổng này vì thế
 * <b>không</b> được gõ một câu SQL hay Cypher nào của riêng mình — nó uỷ thác cho đúng use case
 * mà màn "Thêm nhân khẩu" vẫn dùng. Một đường ghi thứ hai dựng riêng cho nhập liệu chính là cách
 * bất biến ấy bị vi phạm sáu tháng sau, bởi một người không đọc {@code V2__core.sql} mục 2.4.
 * Nhập liệu hàng loạt là phép thử nặng nhất cho bất biến này, không phải ngoại lệ của nó.
 */
public interface PhaWritePort {

    /**
     * Một nhân khẩu sắp vào phả, nói bằng từ vựng của <b>khu vực chờ</b>.
     *
     * @param gio ngày giỗ như người nhập gõ; {@code null} với người còn sống. Có thể <b>thiếu
     *        năm</b> — "mất tháng 8, không rõ năm" là chuyện thường trong sổ cũ.
     * @param vongChongId vợ/chồng của một <b>dâu/rể</b> — người không có cha mẹ trong họ. Cạnh hôn
     *        phối được nối ngay lúc tạo người ấy, vì đời thứ suy từ liên kết: tạo trước rồi nối
     *        sau thì người ấy vĩnh viễn không có đời thứ.
     * @param laChong {@code true} khi người đang được tạo đứng ở vai chồng của cạnh ấy (ca rể)
     * @param bacHonPhoi bậc của cạnh ấy — vợ cả là 1
     * @param ghiChuNguon "lô … · dòng … · tệp …" — ba tháng sau, mở hồ sơ một cụ là biết ngay dòng
     *        nào của tệp nào đã sinh ra nó
     */
    record NhanKhauMoi(String thuongGoi,
                       String huy,
                       String thuy,
                       String hanNom,
                       Gender gender,
                       boolean conSong,
                       Integer namSinh,
                       LunarDeathDate gio,
                       String nguyenQuan,
                       String maNguyenQuan,
                       UUID chiId,
                       UUID chaId,
                       UUID meId,
                       UUID vongChongId,
                       boolean laChong,
                       Integer bacHonPhoi,
                       boolean conNuoi,
                       Map<String, Object> thuocTinh,
                       String ghiChuNguon) {

        public NhanKhauMoi {
            thuocTinh = thuocTinh == null ? Map.of() : Map.copyOf(thuocTinh);
        }
    }

    /**
     * Một cạnh quan hệ sắp được nối.
     *
     * @param loai {@code PARENT_BIO} · {@code PARENT_ADOPT} · {@code SPOUSE} · {@code HEIR}
     * @param bac bậc hôn phối — vợ cả là 1. Chỉ có nghĩa với {@code SPOUSE}; thiếu nó thì danh xưng
     *        con của các bà tính sai mà không có gì báo
     * @param loaiKeTu {@code DICH_TON} · {@code THUA_TU} · {@code KE_TU}; bắt buộc với {@code HEIR}
     */
    record CanhMoi(UUID tu, UUID den, String loai, Integer bac, String loaiKeTu, String ghiChu) {

        public static CanhMoi cha(UUID chaId, UUID conId, CellCodec.ParentRel quanHe, String ghiChu) {
            return new CanhMoi(chaId, conId,
                    quanHe == CellCodec.ParentRel.ADOPT ? "PARENT_ADOPT" : "PARENT_BIO",
                    null, null, ghiChu);
        }

        public static CanhMoi honPhoi(UUID chongId, UUID voId, Integer bac, String ghiChu) {
            return new CanhMoi(chongId, voId, "SPOUSE", bac, null, ghiChu);
        }

        /**
         * Kế tự. Chiều: {@code deLaiId} là người <b>để lại</b> hương hoả (cụ tuyệt tự),
         * {@code noiDoiId} là người được lập để <b>nối dõi</b>. Vẽ ngược cạnh này là đảo ngược
         * quan hệ thừa tự của cả một chi.
         */
        public static CanhMoi keTu(UUID deLaiId, UUID noiDoiId, CellCodec.HeirType loai,
                                   String ghiChu) {
            return new CanhMoi(deLaiId, noiDoiId, "HEIR", null, loai.name(), ghiChu);
        }
    }

    /** Tạo một nhân khẩu, nối luôn vào cha/mẹ đã biết. Trả về định danh vừa tạo. */
    UUID them(NhanKhauMoi nk);

    /** Cập nhật hồ sơ một nhân khẩu <b>đã có</b> — nhánh UPDATE của việc tải lại tệp đã sửa. */
    void capNhat(UUID personId, NhanKhauMoi nk);

    /** Nối một cạnh; trả về định danh dòng bản chiếu. */
    UUID noiQuanHe(CanhMoi canh);

    /**
     * Đã có cạnh loại này giữa hai người chưa — hỏi trước khi nối, cho lần <b>tải lại</b>.
     *
     * <p>Tệp đã sửa thường giữ nguyên phần lớn quan hệ. Nối lại một cạnh đã có sẽ đâm vào ràng
     * buộc duy nhất của bảng {@code relationship} và cuộn lại cả lô 400 người vì một cạnh trùng
     * hoàn toàn vô hại.</p>
     */
    boolean daCoCanh(UUID tu, UUID den, String loai);

    /**
     * Đặt đời thứ <b>chỉ khi</b> người ấy chưa có đời thứ nào.
     *
     * <p>Thuỷ tổ của một lô không có cha, không có mẹ, nên hệ thống không suy ra được đời thứ của
     * người ấy — và hậu quả lan xuống toàn bộ hậu duệ: cả cây nhập vào không có đời thứ, phả đồ
     * không xếp được một hàng nào. Cột <b>Đời</b> trong tệp là nguồn duy nhất còn lại cho đúng các
     * dòng gốc ấy. Điều kiện "chỉ khi chưa có" giữ cho lối này không thành cửa hậu: một khi đồ thị
     * đã suy ra được thì con số trong tệp không được ghi đè lên nó.</p>
     *
     * @return {@code true} nếu thực sự có đặt
     */
    boolean datDoiNeuTrong(UUID personId, Integer doi);

    /**
     * Xoá <b>mềm</b> một nhân khẩu.
     *
     * <p>Không có đường xoá cứng, kể cả khi gỡ cả một lô. Xoá cứng một người là cắt đứt đường nối
     * giữa tổ tiên và toàn bộ hậu duệ của người ấy — mất một người thành mất cả một nhánh.</p>
     */
    void xoaMem(UUID personId, String lyDo);

    /**
     * Gỡ một cạnh khỏi <b>cả</b> đồ thị lẫn bản chiếu.
     *
     * <p>Bước gỡ cạnh AGE là bắt buộc chứ không phải dọn dẹp cho đẹp: node đã xoá mềm vẫn được đi
     * <b>xuyên qua</b> khi duyệt cây, nên để nguyên cạnh thì người vừa gỡ thành một <b>người cha
     * ma</b> — vẫn ảnh hưởng danh xưng và LCA, và không có gì báo.</p>
     *
     * @return số cạnh thực sự đã gỡ
     */
    int goCanh(UUID tu, UUID den, String loai);
}

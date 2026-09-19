package vn.giapha.dataimport.infrastructure.excel.template;

import vn.giapha.genealogy.application.DisclosedPerson;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Dựng {@link DisclosedPerson} cho test đơn vị — <b>giả lập kết quả</b> của bộ lọc riêng tư, không
 * giả lập bản thân bộ lọc.
 *
 * <h2>Ba lối dựng, ứng với ba câu trả lời mà bộ lọc thật có thể đưa ra</h2>
 * <ul>
 *   <li>{@link #daKhuat} — người đã khuất: dữ liệu công khai (BA v2 §10), mọi trường đều ra được.</li>
 *   <li>{@link #conSongTang1} — người còn sống nhìn bởi một thành viên/Trưởng chi: chỉ tên, giới,
 *       đời. Đây là hình dạng mà mọi ô ngoài Tầng 1 <b>không có gì để ghi</b>.</li>
 *   <li>{@link #conSongDayDu} — người còn sống nhìn bởi Hội đồng Tộc biểu/chính chủ: thấy đủ.
 *       Có nó thì các ca "che" mới không xanh vì lý do sai.</li>
 * </ul>
 *
 * <p><b>Không</b> chép luật riêng tư vào đây. Việc "người còn sống nhìn bởi vai nào thì ra hình
 * dạng nào" do {@code PrivacyTierService} quyết, và được kiểm ở
 * {@code PersonDisclosureServiceTest} (đơn vị) cùng {@code ImportTemplateBranchIT} (CSDL thật).
 * Lớp này chỉ nói "giả sử bộ lọc trả về chừng này thì mẫu Excel phải trông thế nào".</p>
 */
final class HoSoGia {

    private final String thuongGoi;
    private final boolean conSong;
    private final boolean duLieuNgoaiNhomHienDuoc;
    private Gender gender = Gender.MALE;
    private Integer doi = 2;
    private String huy;
    private String thuy;
    private String hanNom;
    private Integer namSinh;
    private LunarDate ngayGio;
    private String nguyenQuan;

    private HoSoGia(String thuongGoi, boolean conSong, boolean duLieuNgoaiNhomHienDuoc) {
        this.thuongGoi = thuongGoi;
        this.conSong = conSong;
        this.duLieuNgoaiNhomHienDuoc = duLieuNgoaiNhomHienDuoc;
    }

    /** Người đã khuất — công khai với mọi người gọi, kể cả Khách. */
    static HoSoGia daKhuat(String hoTen) {
        return new HoSoGia(hoTen, false, true);
    }

    /** Người còn sống nhìn bởi thành viên/Trưởng chi: khối dữ liệu ngoài nhóm bị đóng. */
    static HoSoGia conSongTang1(String hoTen) {
        return new HoSoGia(hoTen, true, false);
    }

    /** Người còn sống nhìn bởi Hội đồng Tộc biểu hoặc chính chủ. */
    static HoSoGia conSongDayDu(String hoTen) {
        return new HoSoGia(hoTen, true, true);
    }

    HoSoGia gioi(Gender value) {
        this.gender = value;
        return this;
    }

    HoSoGia doi(Integer value) {
        this.doi = value;
        return this;
    }

    HoSoGia huy(String value) {
        this.huy = value;
        return this;
    }

    HoSoGia thuy(String value) {
        this.thuy = value;
        return this;
    }

    HoSoGia hanNom(String value) {
        this.hanNom = value;
        return this;
    }

    HoSoGia namSinh(Integer value) {
        this.namSinh = value;
        return this;
    }

    HoSoGia ngayGio(LunarDate value) {
        this.ngayGio = value;
        return this;
    }

    HoSoGia nguyenQuan(String value) {
        this.nguyenQuan = value;
        return this;
    }

    DisclosedPerson xong() {
        return new DisclosedPerson(null, thuongGoi, huy, thuy, hanNom, gender, doi, conSong,
                namSinh, ngayGio, nguyenQuan, duLieuNgoaiNhomHienDuoc);
    }
}

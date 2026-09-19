package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.application.DisclosureAudience;

/**
 * Cổng roster giả cho test đơn vị — <b>không</b> chạm cơ sở dữ liệu.
 *
 * <p>Vòng khép kín "sinh → đọc lại bằng bộ đọc thật" là bài kiểm quan trọng nhất của gói này, và
 * nó không cần Postgres: thứ cần kiểm là hợp đồng cột và mã hoá ký tự, không phải SQL. Bài kiểm có
 * CSDL nằm ở {@code ImportTemplateBranchIT} và kiểm chuyện khác — rằng truy vấn lấy đúng người và
 * rằng bộ lọc riêng tư thật đứng vững.</p>
 *
 * <p><b>Cổng giả này cố tình bỏ qua {@link DisclosureAudience}</b>: nó trả về những
 * {@link NhanKhauDaCo} đã dựng sẵn, tức đã "lọc xong". Phép lọc theo người gọi không được giả lập ở
 * đây, vì giả lập nó nghĩa là viết bản luật thứ hai — đúng thứ vừa bị xoá khỏi gói này. Nơi kiểm
 * phép lọc thật là {@code PersonDisclosureServiceTest} và {@code ImportTemplateBranchIT}.</p>
 */
final class FakeBranchRoster implements BranchRosterPort {

    private final List<NhanKhauDaCo> nguoi = new ArrayList<>();
    private final List<HonPhoiDaCo> honPhoi = new ArrayList<>();
    private final List<MaDiaDanh> diaDanh = new ArrayList<>();
    private BranchRosterPort.Chi chi = new Chi(UUID.randomUUID(), "Chi Ất", "goc.chi_at", 0);

    FakeBranchRoster khongCoChi() {
        this.chi = null;
        return this;
    }

    FakeBranchRoster chi(String ten, String path) {
        this.chi = new Chi(UUID.randomUUID(), ten, path, nguoi.size());
        return this;
    }

    FakeBranchRoster them(NhanKhauDaCo n) {
        nguoi.add(n);
        return this;
    }

    FakeBranchRoster them(HonPhoiDaCo h) {
        honPhoi.add(h);
        return this;
    }

    FakeBranchRoster diaDanh(MaDiaDanh... ma) {
        diaDanh.addAll(List.of(ma));
        return this;
    }

    @Override
    public DisclosureAudience nguoiTaiVe() {
        // Muc kin nhat he thong co: neu mot ngay nao do bo sinh tu y hoi lai bo loc that thay vi
        // dung thu cong nay dua cho, ket qua se la MOT TEP TRONG chu khong phai mot tep ro du lieu.
        return DisclosureAudience.khach();
    }

    @Override
    public Chi chi(UUID branchId) {
        return chi;
    }

    @Override
    public List<NhanKhauDaCo> nhanKhau(UUID branchId, DisclosureAudience nguoiTaiVe) {
        return List.copyOf(nguoi);
    }

    @Override
    public List<HonPhoiDaCo> honPhoi(UUID branchId, DisclosureAudience nguoiTaiVe) {
        return List.copyOf(honPhoi);
    }

    @Override
    public List<MaDiaDanh> danhMucDiaDanh() {
        return List.copyOf(diaDanh);
    }
}

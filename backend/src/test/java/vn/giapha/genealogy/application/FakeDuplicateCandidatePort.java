package vn.giapha.genealogy.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.port.DuplicateCandidatePort;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Cổng ứng viên nghi trùng trong bộ nhớ.
 *
 * <p><b>Phép bỏ dấu ở đây chỉ là bản giả.</b> Bản thật là hàm {@code vn_unaccent} của Postgres
 * (V6) — nó và cột generated {@code person_name.name_unaccented} là nguồn chân lý duy nhất. Bản
 * giả này đủ dùng để kiểm chứng logic chấm điểm mà không cần Docker; việc "gõ Nguyen Van Tuan phải
 * khớp Nguyễn Văn Tuân" được kiểm ở {@code DuplicatePersonDetectionIT} trên CSDL thật.</p>
 */
public final class FakeDuplicateCandidatePort implements DuplicateCandidatePort {

    private final List<PersonSignature> daCo = new ArrayList<>();

    /** Số lần cửa lọc theo tên bị gọi — dùng để chứng minh cả lô chỉ tốn một vòng gọi. */
    public int soLanTraUngVien;

    /** Số lần bỏ dấu bị gọi. */
    public int soLanBoDau;

    public int limitDaNhan;

    public UUID them(NguoiGia nguoi) {
        UUID id = nguoi.personId == null ? UUID.randomUUID() : nguoi.personId;
        daCo.add(nguoi.toSignature(id));
        return id;
    }

    public void xoaHet() {
        daCo.clear();
    }

    @Override
    public List<String> unaccent(List<String> rawNames) {
        soLanBoDau++;
        List<String> out = new ArrayList<>(rawNames.size());
        for (String raw : rawNames) {
            out.add(boDau(raw));
        }
        return out;
    }

    @Override
    public List<PersonSignature> findByAnyName(Collection<String> unaccentedNames, int limit) {
        soLanTraUngVien++;
        limitDaNhan = limit;
        return daCo.stream()
                .filter(p -> p.names().stream().anyMatch(n -> unaccentedNames.contains(n.unaccented())))
                .limit(limit)
                .toList();
    }

    /** Bản giả của {@code vn_unaccent}: bỏ dấu phụ, đ→d, hạ chữ thường. */
    public static String boDau(String raw) {
        if (raw == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return decomposed.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    /** Bộ dựng nhân khẩu đã có trong gia phả, cho gọn ở test. */
    public static final class NguoiGia {

        private UUID personId;
        private final List<NameKey> names = new ArrayList<>();
        private String displayName;
        private Gender gender = Gender.MALE;
        private Integer generation;
        private UUID branchId;
        private Integer birthYear;
        private Integer deathYear;
        private LunarDate gio;
        private String nativePlace;

        public static NguoiGia ten(String fullName) {
            NguoiGia n = new NguoiGia();
            n.displayName = fullName;
            return n.themTen(NameType.THUONG_GOI, fullName);
        }

        public NguoiGia themTen(NameType type, String fullName) {
            names.add(new NameKey(type, fullName, boDau(fullName)));
            return this;
        }

        public NguoiGia id(UUID value) {
            this.personId = value;
            return this;
        }

        public NguoiGia gioiTinh(Gender value) {
            this.gender = value;
            return this;
        }

        public NguoiGia doi(Integer value) {
            this.generation = value;
            return this;
        }

        public NguoiGia chi(UUID value) {
            this.branchId = value;
            return this;
        }

        public NguoiGia namSinh(Integer value) {
            this.birthYear = value;
            return this;
        }

        public NguoiGia namMat(Integer value) {
            this.deathYear = value;
            return this;
        }

        /** Ngày giỗ âm lịch — nguồn chân lý, mạnh hơn năm sinh. */
        public NguoiGia gio(int thang, int ngay) {
            this.gio = new LunarDate(deathYear == null ? 0 : deathYear, thang, ngay, false);
            return this;
        }

        /** Ngày giỗ rơi vào <b>tháng nhuận</b> — bỏ cờ này ra khỏi phép so là lệch cả một tháng. */
        public NguoiGia gioNhuan(int thang, int ngay) {
            this.gio = new LunarDate(deathYear == null ? 0 : deathYear, thang, ngay, true);
            return this;
        }

        public NguoiGia nguyenQuan(String value) {
            this.nativePlace = value;
            return this;
        }

        /** Chữ ký để đưa thẳng vào {@link DuplicateScorer} mà không qua CSDL. */
        public PersonSignature chuKy() {
            return toSignature(personId == null ? UUID.randomUUID() : personId);
        }

        private PersonSignature toSignature(UUID id) {
            return new PersonSignature(id, displayName, names, gender, generation, branchId,
                    birthYear, deathYear, gio, boDau(nativePlace));
        }
    }
}

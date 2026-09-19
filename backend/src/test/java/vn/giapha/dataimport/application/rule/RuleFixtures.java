package vn.giapha.dataimport.application.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.PlannedAction;
import vn.giapha.shared.vo.Gender;

/** Dựng dòng chờ và {@link ValidationContext} cho test luật, đọc ra gần giống một dòng Excel thật. */
public final class RuleFixtures {

    public static final UUID CHI_AT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID CHI_GIAP = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private RuleFixtures() {
    }

    public static Builder row(int rowNo, String ma, String hoTen) {
        return new Builder(rowNo, ma, hoTen);
    }

    public static ValidationContext ctx(List<PersonRow> rows) {
        return new ValidationContext(UUID.randomUUID(), CHI_AT, rows, List.of(), Map.of(),
                Set.of(), Map.of());
    }

    public static ValidationContext ctx(List<PersonRow> rows, List<MarriageRow> marriages) {
        return new ValidationContext(UUID.randomUUID(), CHI_AT, rows, marriages, Map.of(),
                Set.of(), Map.of());
    }

    /** Bối cảnh của lần tải thứ hai: chi đã có sẵn một số mã. */
    public static ValidationContext ctxTaiLai(List<PersonRow> rows, Map<String, UUID> daCo) {
        Set<String> codes = new LinkedHashSet<>(daCo.keySet());
        Map<String, UUID> chuSoHuu = new LinkedHashMap<>();
        codes.forEach(c -> chuSoHuu.put(c, CHI_AT));
        List<PersonRow> daDoiSoat = new ArrayList<>(rows.size());
        for (PersonRow r : rows) {
            UUID id = daCo.get(r.externalCode());
            daDoiSoat.add(r.withResolution(id,
                    id == null ? PlannedAction.CREATE : PlannedAction.UPDATE));
        }
        return new ValidationContext(UUID.randomUUID(), CHI_AT, daDoiSoat, List.of(), daCo, codes,
                chuSoHuu);
    }

    public static List<IssueCode> codes(ValidationContext ctx) {
        return ctx.issues().stream().map(ImportIssue::code).toList();
    }

    public static List<ImportIssue> of(ValidationContext ctx, IssueCode code) {
        return ctx.issues().stream().filter(i -> i.code() == code).toList();
    }

    public static final class Builder {

        private final int rowNo;
        private final String ma;
        private final String hoTen;
        private String tenHuy;
        private Gender gioi = Gender.MALE;
        private Integer doi;
        private String maCha;
        private String maMe;
        private Boolean conSong = Boolean.FALSE;
        private Integer namSinh;
        private LunarDeathDate ngayMat;
        private String ngayMatGoc;
        private String nguyenQuan;
        private String maNguyenQuan;

        private Builder(int rowNo, String ma, String hoTen) {
            this.rowNo = rowNo;
            this.ma = ma;
            this.hoTen = hoTen;
        }

        public Builder tenHuy(String v) {
            this.tenHuy = v;
            return this;
        }

        public Builder gioi(Gender v) {
            this.gioi = v;
            return this;
        }

        public Builder doi(Integer v) {
            this.doi = v;
            return this;
        }

        public Builder cha(String v) {
            this.maCha = v;
            return this;
        }

        public Builder me(String v) {
            this.maMe = v;
            return this;
        }

        public Builder conSong(Boolean v) {
            this.conSong = v;
            return this;
        }

        public Builder namSinh(Integer v) {
            this.namSinh = v;
            return this;
        }

        /** Ghi cả giá trị đã đọc lẫn ô gốc — luật ngày âm đọc lại ô gốc để phân biệt loại lỗi. */
        public Builder ngayMatAm(String goc) {
            this.ngayMatGoc = goc;
            this.ngayMat = LunarDeathDate.doc(goc)
                    .filter(LunarDeathDate.KetQua::thanhCong)
                    .map(LunarDeathDate.KetQua::value)
                    .orElse(null);
            return this;
        }

        public Builder nguyenQuan(String ten, String ma) {
            this.nguyenQuan = ten;
            this.maNguyenQuan = ma;
            return this;
        }

        public PersonRow build() {
            Map<String, String> normalized = new LinkedHashMap<>();
            normalized.put(ImportColumn.MA.tieuDe(), ma);
            normalized.put(ImportColumn.HO_TEN.tieuDe(), hoTen);
            if (ngayMatGoc != null) {
                normalized.put(ImportColumn.NGAY_MAT_AM.tieuDe(), ngayMatGoc);
            }
            return new PersonRow(rowNo, ma, normalized, normalized, hoTen, tenHuy, null, null,
                    gioi, doi, maCha, maMe, CellCodec.ParentRel.BIO, conSong, namSinh, ngayMat,
                    nguyenQuan, maNguyenQuan, null, null, null, PlannedAction.CREATE);
        }
    }
}
